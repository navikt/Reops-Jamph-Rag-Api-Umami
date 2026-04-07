package no.jamph.ragumami

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.http.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.slf4j.event.Level
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQueryQueryService
import no.jamph.bigquery.BigQuerySchemaService
import no.jamph.ragumami.umami.UmamiRAGService
import no.jamph.llmValidation.runBenchmark
import no.jamph.llmValidation.ModelBenchmarkResult
import no.jamph.llmValidation.LlmSqlLogic
import no.jamph.llmValidation.DialectValidetaLlmToSql
import no.jamph.llmValidation.TokenSpeedMeasurer
import java.time.Instant

private val log = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("API_PORT")?.toIntOrNull() ?: 8004,
        host = System.getenv("API_HOST") ?: "0.0.0.0"
    ) {
        configureLogging()
        configureSerialization()
        configureCORS()
        configureRouting()
    }.start(wait = true)
}

fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.INFO
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }
}

fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHost("localhost:3000")
        allowHost("localhost:5173")
        allowHost("localhost:5174")
        allowHost(Routes.frontendHost, schemes = listOf("https"))
        allowHost(Routes.ragApiHost, schemes = listOf("https"))
        allowCredentials = true
    }
}

fun Application.configureRouting() {
    val ollamaBaseUrl = environment.config.propertyOrNull("ollama.baseUrl")?.getString()
        ?: System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl
    val ollamaModel = environment.config.propertyOrNull("ollama.model")?.getString()
        ?: System.getenv("OLLAMA_MODEL") ?: Routes.defaultModel ?: runBlocking { OllamaClient.fetchDefaultModel(ollamaBaseUrl) }
    
    val ollamaClient = OllamaClient(ollamaBaseUrl, ollamaModel)
    
    // Initialize BigQuery services if credentials are available
    // Priority: 1) bigquery-credentials JSON from NAIS secret (env var)
    //           2) bigquery-credentials JSON from NAIS filesFrom (mounted file)
    //           3) GOOGLE_APPLICATION_CREDENTIALS file path
    val bigQueryService = try {
        // NAIS mounts secret keys as env vars - try hyphen and underscore variants
        val envChecks = mutableMapOf(
            "bigquery-credentials" to !System.getenv("bigquery-credentials").isNullOrBlank(),
            "bigquery_credentials" to !System.getenv("bigquery_credentials").isNullOrBlank(),
            "BIGQUERY_CREDENTIALS" to !System.getenv("BIGQUERY_CREDENTIALS").isNullOrBlank(),
            "GOOGLE_APPLICATION_CREDENTIALS" to !System.getenv("GOOGLE_APPLICATION_CREDENTIALS").isNullOrBlank()
        )
        var credentialsJson = System.getenv("bigquery-credentials")
            ?: System.getenv("bigquery_credentials")
            ?: System.getenv("BIGQUERY_CREDENTIALS")
        
        // Fallback: try reading from filesFrom mount
        val secretFile = java.io.File("/var/run/secrets/bigquery/bigquery-credentials")
        val secretFileExists = secretFile.exists()
        envChecks["file:/var/run/secrets/bigquery/bigquery-credentials"] = secretFileExists
        if (credentialsJson.isNullOrBlank() && secretFileExists) {
            credentialsJson = secretFile.readText()
        }
        
        val credentialsPath = environment.config.propertyOrNull("bigquery.credentialsPath")?.getString()
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        
        // Extract projectId from credentials JSON if not set explicitly
        val projectId = environment.config.propertyOrNull("bigquery.projectId")?.getString()
            ?: System.getenv("BIGQUERY_PROJECT_ID")
            ?: credentialsJson?.let {
                try {
                    JsonParser.parseString(it).asJsonObject.get("project_id")?.asString
                } catch (_: Exception) { null }
            }
            ?: "fagtorsdag-prod-81a6"  // hardcoded fallback, same as frontend
        val dataset = environment.config.propertyOrNull("bigquery.dataset")?.getString()
            ?: System.getenv("BIGQUERY_DATASET")
            ?: "umami_student"  // default Umami dataset
        val location = environment.config.propertyOrNull("bigquery.location")?.getString()
            ?: System.getenv("BIGQUERY_LOCATION") ?: "europe-north1"
        
        if (!credentialsJson.isNullOrBlank() || !credentialsPath.isNullOrBlank()) {
            val source = envChecks.entries.first { it.value }.key
            log.info("BigQuery: credentials found via '$source', initializing...")
            val queryService = BigQueryQueryService(projectId, dataset, location, credentialsPath, credentialsJson)
            BigQuerySchemaService(queryService)
        } else {
            val tried = envChecks.entries.joinToString(", ") { "${it.key}=${it.value}" }
            log.warn("BigQuery: no credentials found. Checked: $tried")
            null
        }
    } catch (e: Exception) {
        log.warn("BigQuery: init failed: ${e.message}", e)
        null
    }
    
    val ragService = UmamiRAGService(ollamaClient, bigQueryService)
    
    routing {
        get("/") {
            call.respondText("🍜 Jamph-Rag-Api-Umami API is running!")
        }
        
        get("/health") {
            val bigQueryHealthy = bigQueryService?.isHealthy() ?: false
            call.respond(
                mapOf(
                    "status" to "healthy",
                    "service" to "rag-umami",
                    "flavor" to "umami",
                    "bigquery" to if (bigQueryHealthy) "connected" else "not configured"
                )
            )
        }
        
        // BigQuery endpoints
        get("/api/bigquery/websites") {
            try {
                if (bigQueryService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("BigQuery not configured. Set BIGQUERY_PROJECT_ID and BIGQUERY_DATASET")
                    )
                    return@get
                }
                val websites = bigQueryService.getWebsites()
                call.respond(mapOf("websites" to websites))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch websites")
                )
            }
        }
        
        get("/api/bigquery/schema") {
            try {
                if (bigQueryService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("BigQuery not configured. Set BIGQUERY_PROJECT_ID and BIGQUERY_DATASET")
                    )
                    return@get
                }
                val schemaContext = bigQueryService.getSchemaContext()
                call.respond(mapOf("schema" to schemaContext))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch schema")
                )
            }
        }
        
        get("/api/bigquery/tables/{tableName}") {
            try {
                if (bigQueryService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("BigQuery not configured. Set BIGQUERY_PROJECT_ID and BIGQUERY_DATASET")
                    )
                    return@get
                }
                val tableName = call.parameters["tableName"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Table name required"))
                
                val schema = bigQueryService.getTableSchema(tableName)
                call.respond(schema)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Table not found"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch table schema")
                )
            }
        }
        
        post("/api/chat") {
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorResponse("Chat endpoint removed. Use /api/sql instead.")
            )
        }
        
        post("/api/sql") {
            try {
                val request = call.receive<SQLRequest>()
                
                if (bigQueryService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("BigQuery is not configured.")
                    )
                    return@post
                }
                
                if (request.url == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("URL is required")
                    )
                    return@post
                }
                
                val clientToUse = if (request.model != null && request.model != ollamaModel) {
                    OllamaClient(ollamaBaseUrl, request.model)
                } else {
                    ollamaClient
                }
                
                val serviceToUse = if (clientToUse !== ollamaClient) {
                    UmamiRAGService(clientToUse, bigQueryService)
                } else {
                    ragService
                }
                
                val websites = bigQueryService.getWebsites()
                val sql = serviceToUse.generateSQL(request.query, request.url, websites)
                call.respond(SQLResponse(sql))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error")
                )
            }
        }
        
        post("/api/benchmark") {
            try {
                val request = call.receive<BenchmarkRequest>()
                val model = request.model ?: ollamaModel
                val benchmarkOllamaUrl = request.ollamaBaseUrl ?: ollamaBaseUrl
                val results = withContext(Dispatchers.IO) {
                    runBenchmark(listOf(model), benchmarkOllamaUrl)
                }
                call.respond(results.first())
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Benchmark failed")
                )
            }
        }
        
        post("/api/benchmark/stream") {
            val request = call.receive<BenchmarkRequest>()
            val model = request.model ?: ollamaModel
            val benchmarkOllamaUrl = request.ollamaBaseUrl ?: ollamaBaseUrl
            val gson = Gson()
            val events = Channel<String>(Channel.UNLIMITED)
            
            fun emitEvent(type: String, message: String) {
                events.trySend("data: ${gson.toJson(mapOf("type" to type, "message" to message))}\n\n")
            }
            
            coroutineScope {
                // Heartbeat: send SSE comment every 20s to keep connection alive
                val heartbeatJob = launch {
                    while (true) {
                        kotlinx.coroutines.delay(20_000)
                        events.trySend(": heartbeat\n\n")
                    }
                }
                
                launch(Dispatchers.IO) {
                    try {
                        // Step 1: Test BigQuery connection
                        emitEvent("debug", "Testing BigQuery connection...")
                        emitEvent("debug", "  bigQueryService initialized: ${bigQueryService != null}")
                        val bqOk = try {
                            bigQueryService?.isHealthy() ?: false
                        } catch (e: Exception) {
                            emitEvent("debug", "  BigQuery health check error: ${e.message}")
                            false
                        }
                        if (bqOk) {
                            emitEvent("debug", "BigQuery: connected")
                            // Print the real schema context
                            emitEvent("debug", "--- BigQuery Schema ---")
                            try {
                                val schemaCtx = bigQueryService!!.getSchemaContext()
                                schemaCtx.lines().forEach { line -> emitEvent("debug", line) }
                            } catch (e: Exception) {
                                emitEvent("debug", "  Failed to fetch schema: ${e.message}")
                            }
                            emitEvent("debug", "--- End Schema ---")
                        } else if (bigQueryService != null) {
                            val detail = bigQueryService.healthCheckDetail()
                            emitEvent("debug", "BigQuery: initialized but health check failed: $detail")
                        } else {
                            emitEvent("debug", "BigQuery: not configured (using mock schema for benchmark)")
                            val secretFileExists = java.io.File("/var/run/secrets/bigquery/bigquery-credentials").exists()
                            emitEvent("debug", "  Checked: bigquery-credentials(env)=${!System.getenv("bigquery-credentials").isNullOrBlank()}, file=/var/run/secrets/bigquery/bigquery-credentials exists=$secretFileExists")
                        }
                        
                        // Step 2: Test Ollama connection
                        emitEvent("debug", "Testing Ollama connection at $benchmarkOllamaUrl ...")
                        val ollamaTagsBody: String
                        try {
                            val client = HttpClient(CIO)
                            val resp = client.get("$benchmarkOllamaUrl/api/tags")
                            ollamaTagsBody = resp.bodyAsText()
                            client.close()
                        } catch (e: Exception) {
                            emitEvent("debug", "Ollama connection failed: ${e.message}")
                            emitEvent("error", "Cannot connect to Ollama at $benchmarkOllamaUrl")
                            return@launch
                        }
                        emitEvent("debug", "Ollama: connected")
                        
                        // Step 3: Check if model is available
                        emitEvent("debug", "Checking if model '$model' is available...")
                        val availableModels = try {
                            val json = JsonParser.parseString(ollamaTagsBody).asJsonObject
                            json.getAsJsonArray("models")?.map {
                                it.asJsonObject.get("name").asString
                            } ?: emptyList()
                        } catch (e: Exception) { emptyList() }
                        
                        val modelFound = availableModels.any { it == model || it.startsWith("$model:") || it.split(":")[0] == model.split(":")[0] }
                        if (modelFound) {
                            emitEvent("debug", "Model '$model' is available")
                        } else {
                            emitEvent("debug", "Model '$model' NOT found")
                            emitEvent("debug", "Available models: ${availableModels.joinToString(", ")}")
                            emitEvent("error", "Model '$model' not available. Install with: ollama pull $model")
                            return@launch
                        }
                        
                        val skipSqlAccuracyTest = true
                        val skipDialectAccuracyTest = false
                        val skipTokenSpeedTest = false
                        val skipEndToEndTest = false
                        val skipLongPromptTest = false
                        val skipShortPromptTest = false
                        val skipCostEstimateTest = false

                        // Step 4: SQL accuracy test
                        emitEvent("debug", "--- Starting SQL accuracy test ---")
                        val sqlAccuracy = if (!skipSqlAccuracyTest) try {
                            LlmSqlLogic(model) { msg -> emitEvent("debug", msg) }
                        } catch (e: Exception) {
                            emitEvent("debug", "SQL accuracy test failed: ${e.message}")
                            0.0
                        } else { emitEvent("debug", "skip SQL accuracy test"); 0.0 }
                        emitEvent("debug", "SQL accuracy: ${"%.0f".format(sqlAccuracy * 100)}%")

                        // Step 5: Dialect accuracy test
                        emitEvent("debug", "--- Starting dialect accuracy test ---")
                        val dialectAccuracy = if (!skipDialectAccuracyTest) try {
                            DialectValidetaLlmToSql(model) { msg -> emitEvent("debug", msg) }
                        } catch (e: Exception) {
                            emitEvent("debug", "Dialect accuracy test failed: ${e.message}")
                            0.0
                        } else { emitEvent("debug", "skip dialect accuracy test"); 0.0 }
                        emitEvent("debug", "Dialect accuracy: ${"%.0f".format(dialectAccuracy * 100)}%")
                        
                        // Step 6: Token speed test
                        emitEvent("debug", "--- Measuring token speed ---")
                        val speedResult = if (!skipTokenSpeedTest) try {
                            TokenSpeedMeasurer(benchmarkOllamaUrl, model)
                                .measure("Write a BigQuery SQL query that counts rows in a table.")
                        } catch (e: Exception) {
                            emitEvent("debug", "Speed test failed: ${e.message}")
                            no.jamph.llmValidation.TokenSpeedResult(model, 0, 0, 0L, 0.0)
                        } else { emitEvent("debug", "skip token speed test"); no.jamph.llmValidation.TokenSpeedResult(model, 0, 0, 0L, 0.0) }
                        emitEvent("debug", "Token speed: ${"%.1f".format(speedResult.tokensPerSecond)} tokens/sec")

                        // Step 7: Timer tests
                        val ollamaClient = no.jamph.ragumami.core.llm.OllamaClient(benchmarkOllamaUrl, model)
                        val schemaService = no.jamph.bigquery.BigQuerySchemaServiceMock()
                        val ragService = no.jamph.ragumami.umami.UmamiRAGService(ollamaClient, schemaService)
                        val timerProbe = "Show me pageviews per day for https://aksel.nav.no"

                        emitEvent("debug", "--- Measuring end-to-end ---")
                        val endToEndMs = if (!skipEndToEndTest) try {
                            emitEvent("debug", "  Running end-to-end pipeline...")
                            val result = no.jamph.llmValidation.EndToEndTimer(ragService)
                                .measureFullPipeline(timerProbe, "https://aksel.nav.no", schemaService.getWebsites())
                            emitEvent("debug", "  Result: ${result.durationMs} ms")
                            result.durationMs
                        } catch (e: Exception) { emitEvent("debug", "End-to-end failed: ${e::class.simpleName}: ${e.message}"); -1L }
                        else { emitEvent("debug", "skip end-to-end test"); -1L }

                        emitEvent("debug", "--- Measuring long prompt ---")
                        val longPromptMs = if (!skipLongPromptTest) try {
                            val ms = no.jamph.llmValidation.LongPromptTimer(ollamaClient) { msg -> emitEvent("debug", msg) }
                                .measureLlmWithLargeSchema(timerProbe).averageDurationMs
                            emitEvent("debug", "  Average: $ms ms")
                            ms
                        } catch (e: Exception) { emitEvent("debug", "Long prompt failed: ${e::class.simpleName}: ${e.message}"); -1L }
                        else { emitEvent("debug", "skip long prompt test"); -1L }

                        emitEvent("debug", "--- Measuring short prompt ---")
                        val shortPromptMs = if (!skipShortPromptTest) try {
                            val ms = no.jamph.llmValidation.ShortPromptTimer(ollamaClient) { msg -> emitEvent("debug", msg) }
                                .measureLlmWithSmallSchema(timerProbe).averageDurationMs
                            emitEvent("debug", "  Average: $ms ms")
                            ms
                        } catch (e: Exception) { emitEvent("debug", "Short prompt failed: ${e::class.simpleName}: ${e.message}"); -1L }
                        else { emitEvent("debug", "skip short prompt test"); -1L }

                        emitEvent("debug", "--- Estimating cost ---")
                        val avgCostMB = if (!skipCostEstimateTest) try {
                            val bq = (bigQueryService as? no.jamph.bigquery.BigQuerySchemaService)?.bigQuery ?: no.jamph.llmValidation.defaultBigQuery()
                            no.jamph.llmValidation.CostValidateLLmEstimator(model, bigquery = bq) { msg -> emitEvent("debug", msg) }
                        } catch (e: Exception) { emitEvent("debug", "Cost estimate failed: ${e::class.simpleName}: ${e.message}: ${e.cause?.message}"); 0.0 }
                        else { emitEvent("debug", "skip cost estimate test"); 0.0 }

                        // Assemble and print results
                        emitEvent("debug", "")
                        emitEvent("debug", "========== RESULTS ==========")
                        emitEvent("debug", "Model:              $model")
                        emitEvent("debug", "Timestamp:          ${Instant.now()}")
                        emitEvent("debug", "SQL accuracy:       ${"%.0f".format(sqlAccuracy * 100)}%")
                        emitEvent("debug", "Dialect accuracy:   ${"%.0f".format(dialectAccuracy * 100)}%")
                        emitEvent("debug", "Total cost:         ${"%.2f".format(avgCostMB)} MB")
                        emitEvent("debug", "End-to-end:         $endToEndMs ms")
                        emitEvent("debug", "Long prompt:        $longPromptMs ms")
                        emitEvent("debug", "Short prompt:       $shortPromptMs ms")
                        emitEvent("debug", "Tokens/sec:         ${"%.1f".format(speedResult.tokensPerSecond)}")
                        emitEvent("debug", "Prompt tokens:      ${speedResult.promptTokens}")
                        emitEvent("debug", "Response tokens:    ${speedResult.responseTokens}")
                        emitEvent("debug", "Eval duration:      ${speedResult.evalDurationMs} ms")
                        emitEvent("debug", "=============================")
                        
                        // Step 7: External save (placeholder)
                        emitEvent("debug", "External save: not yet implemented")
                        emitEvent("done", "Benchmark complete")
                        
                    } catch (e: Exception) {
                        emitEvent("error", e.message ?: "Unknown error")
                    } finally {
                        heartbeatJob.cancel()
                        events.close()
                    }
                }
                
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    for (event in events) {
                        write(event)
                        flush()
                    }
                }
            }
        }
    }
}

data class ChatRequest(val message: String, val model: String? = null)
data class ChatResponse(val response: String)
data class SQLRequest(val query: String, val url: String? = null, val model: String? = null)
data class SQLResponse(val sql: String)
data class BenchmarkRequest(val model: String? = null, val ollamaBaseUrl: String? = null)
data class ErrorResponse(val error: String)