package no.jamph.llmValidation

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.runBlocking
import io.ktor.client.plugins.cookies.*

private data class NettskjemaAnswer(
    val questionId: Long,
    val answers: List<Map<String, String>>,
    val type: String = "TEXT"
)

private data class NettskjemaSubmission(
    val metadata: Map<String, Any> = emptyMap(),
    val answers: List<NettskjemaAnswer>
)

class NettskjemaBenchmarkWriter(
    private val formId: Long = 614069L,
    private val baseUrl: String = "https://nettskjema.no",
    private val qModel:           Long = 10239299,
    private val qTimestamp:       Long = 10239300,
    private val qSqlAccuracy:     Long = 10239301,
    private val qDialectAccuracy: Long = 10239302,
    private val qTokensPerSec:    Long = 10239303,
    private val qPromptTokens:    Long = 10239304,
    private val qResponseTokens:  Long = 10239305,
    private val qEvalDurationMs:  Long = 10239306
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { gson() }
        install(HttpCookies)   // ← stores session cookie from CSRF GET and resends it with POST
    }

    fun appendRows(results: List<ModelBenchmarkResult>) = runBlocking {

        results.forEach { r ->
            val submission = NettskjemaSubmission(
                metadata = mapOf("formId" to formId),
                answers = listOf(
                    NettskjemaAnswer(qModel,           listOf(mapOf("text" to r.model))),
                    NettskjemaAnswer(qTimestamp,       listOf(mapOf("text" to r.timestamp))),
                    NettskjemaAnswer(qSqlAccuracy,     listOf(mapOf("text" to "%.1f".format(r.sqlAccuracy * 100)))),
                    NettskjemaAnswer(qDialectAccuracy, listOf(mapOf("text" to "%.1f".format(r.dialectAccuracy * 100)))),
                    NettskjemaAnswer(qTokensPerSec,    listOf(mapOf("text" to "%.2f".format(r.tokensPerSecond)))),
                    NettskjemaAnswer(qPromptTokens,    listOf(mapOf("text" to r.promptTokens.toString()))),
                    NettskjemaAnswer(qResponseTokens,  listOf(mapOf("text" to r.responseTokens.toString()))),
                    NettskjemaAnswer(qEvalDurationMs,  listOf(mapOf("text" to r.evalDurationMs.toString())))
                )
            )

            // Step 2: include token in the POST header
            val response = client.post("$baseUrl/api/v3/form/$formId/submission") {
                contentType(ContentType.Application.Json)
                setBody(submission)
            }

            if (response.status.isSuccess()) {
                println("  ✓ Submitted ${r.model} to nettskjema")
            } else {
                println("  ✗ Failed for ${r.model}: ${response.status} — ${response.bodyAsText()}")
            }
        }
    }
}