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
import no.jamph.ragumami.umami.domain.UmamiRAGService
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
        ?: System.getenv("OLLAMA_MODEL") ?: runBlocking { OllamaClient.fetchDefaultModel(ollamaBaseUrl) }
    
    val ollamaClient = OllamaClient(ollamaBaseUrl, ollamaModel)
    
    // Initialize BigQuery services if credentials are available
    // Priority: 1) bigquery-credentials JSON from NAIS secret
    //           2) GOOGLE_APPLICATION_CREDENTIALS file path
    //           3) application.conf settings
    val bigQueryService = try {
        val credentialsJson = System.getenv("bigquery-credentials")
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
        val dataset = environment.config.propertyOrNull("bigquery.dataset")?.getString()
            ?: System.getenv("BIGQUERY_DATASET")
            ?: "analytics_315058498"  // default Umami dataset
        val location = environment.config.propertyOrNull("bigquery.location")?.getString()
            ?: System.getenv("BIGQUERY_LOCATION") ?: "europe-north1"
        
        if (projectId != null) {
            if (!credentialsJson.isNullOrBlank()) {
                log.info("Using BigQuery credentials from bigquery-credentials secret (NAIS)")
            } else if (!credentialsPath.isNullOrBlank()) {
                log.info("Using BigQuery credentials from file: $credentialsPath")
            } else {
                log.info("Using default BigQuery credentials (ADC)")
            }
            val queryService = BigQueryQueryService(projectId, dataset, location, credentialsPath, credentialsJson)
            BigQuerySchemaService(queryService)
        } else {
            log.warn("BigQuery not configured: no projectId found")
            null
        }
    } catch (e: Exception) {
        log.warn("Failed to initialize BigQuery service: ${e.message}")
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
            try {
                val request = call.receive<ChatRequest>()
                // Use model from request if provided, otherwise use default
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
                val response = serviceToUse.chat(request.message)
                call.respond(ChatResponse(response))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error")
                )
            }
        }
        
        post("/api/sql") {
            try {
                val request = call.receive<SQLRequest>()
                // Use model from request if provided, otherwise use default
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
                val sql = serviceToUse.generateSQL(request.query)
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
            
            fun emitResult(result: ModelBenchmarkResult) {
                val map = mapOf(
                    "type" to "result",
                    "result" to mapOf(
                        "model" to result.model,
                        "timestamp" to result.timestamp,
                        "sqlAccuracy" to result.sqlAccuracy,
                        "dialectAccuracy" to result.dialectAccuracy,
                        "tokensPerSecond" to result.tokensPerSecond,
                        "promptTokens" to result.promptTokens,
                        "responseTokens" to result.responseTokens,
                        "evalDurationMs" to result.evalDurationMs
                    )
                )
                events.trySend("data: ${gson.toJson(map)}\n\n")
            }
            
            coroutineScope {
                launch(Dispatchers.IO) {
                    try {
                        // Step 1: Test BigQuery connection
                        emitEvent("debug", "Testing BigQuery connection...")
                        val bqOk = try {
                            bigQueryService?.isHealthy() ?: false
                        } catch (e: Exception) { false }
                        if (bqOk) {
                            emitEvent("debug", "BigQuery: connected")
                        } else {
                            emitEvent("debug", "BigQuery: not configured (using mock schema for benchmark)")
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
                        
                        // Step 4: SQL accuracy test
                        emitEvent("debug", "--- Starting SQL accuracy test ---")
                        val sqlAccuracy = try {
                            LlmSqlLogic(model) { msg -> emitEvent("debug", msg) }
                        } catch (e: Exception) {
                            emitEvent("debug", "SQL accuracy test failed: ${e.message}")
                            0.0
                        }
                        emitEvent("debug", "SQL accuracy: ${"%.0f".format(sqlAccuracy * 100)}%")
                        
                        // Step 5: Dialect accuracy test
                        emitEvent("debug", "--- Starting dialect accuracy test ---")
                        val dialectAccuracy = try {
                            DialectValidetaLlmToSql(model) { msg -> emitEvent("debug", msg) }
                        } catch (e: Exception) {
                            emitEvent("debug", "Dialect accuracy test failed: ${e.message}")
                            0.0
                        }
                        emitEvent("debug", "Dialect accuracy: ${"%.0f".format(dialectAccuracy * 100)}%")
                        
                        // Step 6: Token speed test
                        emitEvent("debug", "--- Measuring token speed ---")
                        val speedResult = try {
                            TokenSpeedMeasurer(benchmarkOllamaUrl, model)
                                .measure("Write a BigQuery SQL query that counts rows in a table.")
                        } catch (e: Exception) {
                            emitEvent("debug", "Speed test failed: ${e.message}")
                            no.jamph.llmValidation.TokenSpeedResult(model, 0, 0, 0L, 0.0)
                        }
                        emitEvent("debug", "Token speed: ${"%.1f".format(speedResult.tokensPerSecond)} tokens/sec")
                        
                        // Assemble result
                        val result = ModelBenchmarkResult(
                            model = model,
                            timestamp = Instant.now().toString(),
                            sqlAccuracy = sqlAccuracy,
                            dialectAccuracy = dialectAccuracy,
                            tokensPerSecond = speedResult.tokensPerSecond,
                            promptTokens = speedResult.promptTokens,
                            responseTokens = speedResult.responseTokens,
                            evalDurationMs = speedResult.evalDurationMs
                        )
                        emitResult(result)
                        
                        // Step 7: External save (placeholder)
                        emitEvent("debug", "External save: not yet implemented")
                        emitEvent("done", "Benchmark complete")
                        
                    } catch (e: Exception) {
                        emitEvent("error", e.message ?: "Unknown error")
                    } finally {
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
data class SQLRequest(val query: String, val model: String? = null)
data class SQLResponse(val sql: String)
data class BenchmarkRequest(val model: String? = null, val ollamaBaseUrl: String? = null)
data class ErrorResponse(val error: String)