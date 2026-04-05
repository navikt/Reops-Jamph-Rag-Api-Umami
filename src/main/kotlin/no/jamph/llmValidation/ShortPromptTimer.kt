
package no.jamph.llmValidation

import no.jamph.ragumami.core.llm.OllamaClient
import org.slf4j.LoggerFactory

private const val SMALL_SCHEMA_QUERY_MAX_LENGTH = 4000

// Small schema: 2 tables with ~500 characters
const val SMALL_FAKE_SCHEMA = """
CREATE TABLE event (
  event_id STRING,
  website_id STRING,
  session_id STRING,
  created_at TIMESTAMP,
  url_path STRING,
  event_type INT64,
  event_name STRING
);

CREATE TABLE session (
  session_id STRING,
  website_id STRING,
  created_at TIMESTAMP,
  hostname STRING,
  browser STRING,
  country STRING
);
"""

data class ShortSchemaLlmResult(
    val query: String,
    val sql: String,
    val averageDurationMs: Long,
    val iterations: Int
)

class ShortPromptTimer(
    private val ollamaClient: OllamaClient,
    private val debugLog: (String) -> Unit = {}
) {
    private val logger = LoggerFactory.getLogger(ShortPromptTimer::class.java)

    suspend fun measureLlmWithSmallSchema(query: String, iterations: Int = 10): ShortSchemaLlmResult {
        require(query.length <= SMALL_SCHEMA_QUERY_MAX_LENGTH) {
            "Query length (${query.length}) must be $SMALL_SCHEMA_QUERY_MAX_LENGTH characters or fewer to prevent infinite execution"
        }
        require(iterations > 0) { "Iterations must be positive" }

        val durations = mutableListOf<Long>()
        var lastSql = ""

        repeat(iterations) { iteration ->
            val prompt = buildSimplePrompt(query, SMALL_FAKE_SCHEMA)
            
            val startNanos = System.nanoTime()
            val rawResponse = ollamaClient.generate(prompt)
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            
            durations.add(durationMs)
            lastSql = extractSql(rawResponse)
            
            logger.info("SMALL_SCHEMA_LLM: iteration={} durationMs={}", iteration + 1, durationMs)
            debugLog("  Short prompt ${iteration + 1}/$iterations: ${durationMs} ms")
        }

        val avgDuration = durations.average().toLong()
        logger.info("SMALL_SCHEMA_LLM: average_durationMs={} iterations={}", avgDuration, iterations)

        return ShortSchemaLlmResult(
            query = query,
            sql = lastSql,
            averageDurationMs = avgDuration,
            iterations = iterations
        )
    }
    
    private fun buildSimplePrompt(question: String, schema: String): String {
        return """
            You are a BigQuery SQL expert.
            
            Schema:
            $schema
            
            User Query: $question
            
            Generate only the SQL query, no explanations:
        """.trimIndent()
    }
    
    private fun extractSql(raw: String): String {
        return raw.trim()
            .removePrefix("```sql")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}



