package no.jamph.llmValidation

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.runBlocking

private data class NettskjemaAnswer(
    val questionId: Long,
    val answers: List<Map<String, String>>
)

private data class NettskjemaSubmission(
    val answers: List<NettskjemaAnswer>
)

class NettskjemaBenchmarkWriter(
    private val formId: Long = 614069L,
    private val qModel:           Long = 9106646,
    private val qTimestamp:       Long = 9106647,
    private val qSqlAccuracy:     Long = 9106648,
    private val qDialectAccuracy: Long = 9106649,
    private val qTokensPerSec:    Long = 9106650,
    private val qPromptTokens:    Long = 9106651,
    private val qResponseTokens:  Long = 9106652,
    private val qEvalDurationMs:  Long = 9106653
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { gson() }
    }

    fun appendRows(results: List<ModelBenchmarkResult>) = runBlocking {
        results.forEach { r ->
            val submission = NettskjemaSubmission(
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

            val response = client.post("https://nettskjema.no/api/v3/form/$formId/submission") {
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