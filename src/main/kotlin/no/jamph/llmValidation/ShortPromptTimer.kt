
package no.jamph.llmValidation

import no.jamph.ragumami.umami.domain.UmamiRAGService
import org.slf4j.LoggerFactory

data class ShortPromptTime(
    val query: String,
    val sql: String,
    val durationMs: Long
)

class ShortPromptTimer(
    private val ragService: UmamiRAGService
) {
    private val logger = LoggerFactory.getLogger(ShortPromptTimer::class.java)

    suspend fun measureTime(query: String): ShortPromptTime {
        require(query.length <= LONG_CONTEXT_THRESHOLD) {
            "Query length (${query.length}) must be $LONG_CONTEXT_THRESHOLD characters or fewer"
        }

        val startNanos = System.nanoTime()
        val sql = ragService.generateSQL(query)  // covers BigQuery schema + Ollama
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000

        logger.info("E2E: query_length={} durationMs={}", query.length, durationMs)

        return ShortPromptTime(query = query, sql = sql, durationMs = durationMs)
    }
}



