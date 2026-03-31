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
import org.slf4j.event.Level
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQueryQueryService
import no.jamph.bigquery.BigQuerySchemaService
import no.jamph.ragumami.umami.domain.UmamiRAGService
import no.jamph.llmValidation.runBenchmark

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
        allowCredentials = true
    }
}

fun Application.configureRouting() {
    val ollamaBaseUrl = environment.config.propertyOrNull("ollama.baseUrl")?.getString()
        ?: System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
    val ollamaModel = environment.config.propertyOrNull("ollama.model")?.getString()
        ?: System.getenv("OLLAMA_MODEL") ?: "qwen2.5-coder:7b"
    
    val ollamaClient = OllamaClient(ollamaBaseUrl, ollamaModel)
    
    // Initialize BigQuery services if credentials are available
    val bigQueryService = try {
        val projectId = environment.config.propertyOrNull("bigquery.projectId")?.getString()
            ?: System.getenv("BIGQUERY_PROJECT_ID")
        val dataset = environment.config.propertyOrNull("bigquery.dataset")?.getString()
            ?: System.getenv("BIGQUERY_DATASET")
        val location = environment.config.propertyOrNull("bigquery.location")?.getString()
            ?: System.getenv("BIGQUERY_LOCATION") ?: "europe-north1"
        val credentialsPath = environment.config.propertyOrNull("bigquery.credentialsPath")?.getString()
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        
        if (projectId != null && dataset != null) {
            val queryService = BigQueryQueryService(projectId, dataset, location, credentialsPath)
            BigQuerySchemaService(queryService)
        } else {
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
    }
}

data class ChatRequest(val message: String, val model: String? = null)
data class ChatResponse(val response: String)
data class SQLRequest(val query: String, val model: String? = null)
data class SQLResponse(val sql: String)
data class BenchmarkRequest(val model: String? = null, val ollamaBaseUrl: String? = null)
data class ErrorResponse(val error: String)