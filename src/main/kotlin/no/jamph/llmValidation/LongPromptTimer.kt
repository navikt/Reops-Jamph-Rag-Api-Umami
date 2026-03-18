package no.jamph.llmValidation

import no.jamph.ragumami.umami.domain.UmamiRAGService
import org.slf4j.LoggerFactory

const val LONG_CONTEXT_THRESHOLD = 1000

data class LongPromptTime(
    val query: String,
    val sql: String,
    val durationMs: Long,
    val queryLength: Int,
    val sqlLength: Int,
    val exceedsThreshold: Boolean
)

class LongPromptTimer(
    private val ragService: UmamiRAGService,
    private val threshold: Int = LONG_CONTEXT_THRESHOLD
) {
    private val logger = LoggerFactory.getLogger(LongPromptTimer::class.java)

    suspend fun measureLongContext(query: String): LongPromptTime {
        require(query.length > threshold) {
            "Query length (${query.length}) must exceed threshold ($threshold) for long context measurement"
        }

        val start = System.currentTimeMillis()
        val sql = ragService.generateSQL(query)  // covers BigQuery schema + Ollama
        val durationMs = System.currentTimeMillis() - start

        val exceedsThreshold = sql.length > threshold

        logger.info(
            "LONG_CTX: query_length={} sql_length={} exceeds_threshold={} durationMs={}",
            query.length, sql.length, exceedsThreshold, durationMs
        )

        return LongPromptTime(
            query = query,
            sql = sql,
            durationMs = durationMs,
            queryLength = query.length,
            sqlLength = sql.length,
            exceedsThreshold = exceedsThreshold
        )
    }
}
