package no.jamph.llmValidation

import no.jamph.ragumami.umami.UmamiRAGService
import org.slf4j.LoggerFactory

private const val END_TO_END_QUERY_MAX_LENGTH = 4000

data class EndToEndTimerResult(
    val query: String,
    val sql: String,
    val durationMs: Long,
    val queryLength: Int
)

class EndToEndTimer(
    private val ragService: UmamiRAGService
) {
    private val logger = LoggerFactory.getLogger(EndToEndTimer::class.java)

    suspend fun measureFullPipeline(query: String, url: String, websites: List<no.jamph.bigquery.Website>): EndToEndTimerResult {
        require(query.length <= END_TO_END_QUERY_MAX_LENGTH) {
            "Query length (${query.length}) must be $END_TO_END_QUERY_MAX_LENGTH characters or fewer to prevent infinite execution"
        }

        val startNanos = System.nanoTime()
        val sql = ragService.generateSQL(query, url, websites)  // Full pipeline: BigQuery schema + URL parsing + Ollama
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000

        logger.info("END_TO_END: query_length={} durationMs={}", query.length, durationMs)

        return EndToEndTimerResult(
            query = query,
            sql = sql,
            durationMs = durationMs,
            queryLength = query.length
        )
    }
}
