package no.jamph.llmValidation

import no.jamph.ragumami.core.llm.OllamaClient
import org.slf4j.LoggerFactory

private const val LARGE_SCHEMA_QUERY_MAX_LENGTH = 4000

// Large schema: 26 tables with ~7000 characters - stress test for LLM context window
const val LARGE_FAKE_SCHEMA = """
CREATE TABLE session (
  session_id STRING,
  website_id STRING,
  created_at TIMESTAMP,
  hostname STRING,
  browser STRING,
  os STRING,
  device STRING,
  screen STRING,
  language STRING,
  country STRING,
  subdivision1 STRING,
  subdivision2 STRING,
  city STRING
);

CREATE TABLE event (
  event_id STRING,
  website_id STRING,
  session_id STRING,
  created_at TIMESTAMP,
  url_path STRING,
  url_query STRING,
  referrer_path STRING,
  referrer_query STRING,
  page_title STRING,
  event_type INT64,
  event_name STRING
);

CREATE TABLE public_website (
  website_id STRING,
  website_uuid STRING,
  website_name STRING,
  domain STRING,
  share_id STRING,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE user_profile (
  user_id STRING,
  username STRING,
  email STRING,
  created_at TIMESTAMP,
  role STRING
);

CREATE TABLE team (
  team_id STRING,
  team_name STRING,
  created_at TIMESTAMP,
  owner_user_id STRING
);

CREATE TABLE conversion_goal (
  goal_id STRING,
  website_id STRING,
  goal_name STRING,
  event_name STRING,
  url_pattern STRING,
  created_at TIMESTAMP
);

CREATE TABLE ab_test (
  test_id STRING,
  website_id STRING,
  test_name STRING,
  variant_a STRING,
  variant_b STRING,
  created_at TIMESTAMP,
  status STRING
);

CREATE TABLE funnel (
  funnel_id STRING,
  website_id STRING,
  funnel_name STRING,
  steps JSON,
  created_at TIMESTAMP
);

CREATE TABLE segment (
  segment_id STRING,
  website_id STRING,
  segment_name STRING,
  rules JSON,
  created_at TIMESTAMP
);

CREATE TABLE dashboard (
  dashboard_id STRING,
  user_id STRING,
  dashboard_name STRING,
  widgets JSON,
  created_at TIMESTAMP
);

CREATE TABLE alert (
  alert_id STRING,
  website_id STRING,
  alert_name STRING,
  condition STRING,
  threshold FLOAT64,
  created_at TIMESTAMP
);

CREATE TABLE report (
  report_id STRING,
  website_id STRING,
  report_name STRING,
  query STRING,
  schedule STRING,
  created_at TIMESTAMP
);

CREATE TABLE api_key (
  key_id STRING,
  user_id STRING,
  key_hash STRING,
  created_at TIMESTAMP,
  expires_at TIMESTAMP
);

CREATE TABLE page_performance (
  performance_id STRING,
  website_id STRING,
  url_path STRING,
  load_time_ms INT64,
  first_contentful_paint INT64,
  time_to_interactive INT64,
  created_at TIMESTAMP
);

CREATE TABLE user_journey (
  journey_id STRING,
  session_id STRING,
  website_id STRING,
  step_number INT64,
  url_path STRING,
  duration_seconds INT64,
  created_at TIMESTAMP
);

CREATE TABLE campaign (
  campaign_id STRING,
  website_id STRING,
  campaign_name STRING,
  utm_source STRING,
  utm_medium STRING,
  utm_campaign STRING,
  start_date TIMESTAMP,
  end_date TIMESTAMP
);

CREATE TABLE bounce_tracking (
  bounce_id STRING,
  session_id STRING,
  website_id STRING,
  entry_page STRING,
  exit_page STRING,
  time_on_page_seconds INT64,
  created_at TIMESTAMP
);

CREATE TABLE conversion_tracking (
  conversion_id STRING,
  session_id STRING,
  website_id STRING,
  goal_id STRING,
  value FLOAT64,
  conversion_path STRING,
  created_at TIMESTAMP
);

CREATE TABLE device_stats (
  device_stat_id STRING,
  website_id STRING,
  device_type STRING,
  browser_version STRING,
  os_version STRING,
  screen_resolution STRING,
  viewport_size STRING,
  created_at TIMESTAMP
);

CREATE TABLE geo_location (
  geo_id STRING,
  session_id STRING,
  website_id STRING,
  country_code STRING,
  region STRING,
  city STRING,
  postal_code STRING,
  latitude FLOAT64,
  longitude FLOAT64,
  created_at TIMESTAMP
);

CREATE TABLE referrer_analysis (
  referrer_id STRING,
  session_id STRING,
  website_id STRING,
  referrer_domain STRING,
  referrer_path STRING,
  referrer_type STRING,
  created_at TIMESTAMP
);

CREATE TABLE session_replay (
  replay_id STRING,
  session_id STRING,
  website_id STRING,
  recording_url STRING,
  duration_seconds INT64,
  created_at TIMESTAMP
);

CREATE TABLE error_tracking (
  error_id STRING,
  session_id STRING,
  website_id STRING,
  error_message STRING,
  error_stack STRING,
  url_path STRING,
  created_at TIMESTAMP
);

CREATE TABLE form_analytics (
  form_id STRING,
  session_id STRING,
  website_id STRING,
  form_name STRING,
  field_interactions INT64,
  time_to_submit_seconds INT64,
  abandonment_point STRING,
  created_at TIMESTAMP
);

CREATE TABLE scroll_depth (
  scroll_id STRING,
  session_id STRING,
  website_id STRING,
  url_path STRING,
  max_scroll_percentage INT64,
  scroll_events INT64,
  created_at TIMESTAMP
);

CREATE TABLE heatmap_data (
  heatmap_id STRING,
  website_id STRING,
  url_path STRING,
  click_x INT64,
  click_y INT64,
  viewport_width INT64,
  viewport_height INT64,
  created_at TIMESTAMP
);
"""

data class LongSchemaLlmResult(
    val query: String,
    val sql: String,
    val averageDurationMs: Long,
    val iterations: Int
)

class LongPromptTimer(
    private val ollamaClient: OllamaClient,
    private val debugLog: (String) -> Unit = {}
) {
    private val logger = LoggerFactory.getLogger(LongPromptTimer::class.java)

    suspend fun measureLlmWithLargeSchema(query: String, iterations: Int = 10): LongSchemaLlmResult {
        require(query.length <= LARGE_SCHEMA_QUERY_MAX_LENGTH) {
            "Query length (${query.length}) must be $LARGE_SCHEMA_QUERY_MAX_LENGTH characters or fewer to prevent infinite execution"
        }
        require(iterations > 0) { "Iterations must be positive" }

        val durations = mutableListOf<Long>()
        var lastSql = ""

        repeat(iterations) { iteration ->
            val prompt = buildSimplePrompt(query, LARGE_FAKE_SCHEMA)
            
            val startNanos = System.nanoTime()
            val rawResponse = ollamaClient.generate(prompt)
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            
            durations.add(durationMs)
            lastSql = extractSql(rawResponse)

            logger.info("LARGE_SCHEMA_LLM: iteration={} durationMs={}", iteration + 1, durationMs)
            debugLog("  Long prompt ${iteration + 1}/$iterations: ${durationMs} ms")
        }

        val avgDuration = durations.average().toLong()
        logger.info("LARGE_SCHEMA_LLM: average_durationMs={} iterations={}", avgDuration, iterations)

        return LongSchemaLlmResult(
            query = query,
            sql = lastSql,
            averageDurationMs = avgDuration,
            iterations = iterations
        )
    }
    
    private fun buildSimplePrompt(question: String, schema: String): String {
        return """
            You are a BigQuery SQL expert specializing in Umami Analytics data.
            
            CRITICAL REQUIREMENTS:
            - Output ONLY raw SQL (no explanations, no markdown, no code fences)
            - Use BigQuery syntax: EXTRACT(YEAR FROM created_at) not YEAR()
            - Use TIMESTAMP functions: TIMESTAMP_TRUNC, TIMESTAMP_DIFF, CURRENT_TIMESTAMP()
            - Use backticks for table names: `fagtorsdag-prod-81a6.umami_student.event`
            - For date ranges, use: WHERE created_at >= TIMESTAMP('2025-01-01')
            - For aggregations, use: COUNT(*), SUM(), AVG(), MAX(), MIN()
            - Always filter by website_id when analyzing specific sites
            - Use UNNEST for JSON fields like funnel.steps or segment.rules
            - For time series: GROUP BY TIMESTAMP_TRUNC(created_at, DAY)
            - For percentages: ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2)
            
            COMMON PATTERNS:
            - Page views: SELECT COUNT(*) FROM event WHERE event_type = 1
            - Unique visitors: SELECT COUNT(DISTINCT session_id) FROM session
            - Bounce rate: sessions with only 1 event / total sessions
            - Conversion funnel: JOIN multiple event steps with LAG/LEAD
            - Top pages: GROUP BY url_path ORDER BY COUNT(*) DESC LIMIT 10
            - Retention cohorts: Use DATE_DIFF and cohort analysis patterns
            - Geographic breakdown: JOIN geo_location on session_id
            - Device analysis: JOIN device_stats for browser/os/device metrics
            - Campaign performance: Filter by utm_source/utm_medium from campaign table
            
            PERFORMANCE OPTIMIZATION:
            - Limit date ranges to reduce data scanning
            - Use APPROX_COUNT_DISTINCT for large datasets
            - Avoid SELECT * - specify needed columns
            - Use WHERE before JOIN when possible
            - Consider partitioning on created_at field
            
            ADVANCED ANALYTICS:
            - For linear regression trends, use ML.LINEAR_REG or manual slope calculation
            - For moving averages: AVG() OVER (ORDER BY date ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)
            - For year-over-year growth: (current - previous) / previous * 100
            - For session duration: TIMESTAMP_DIFF(MAX(created_at), MIN(created_at), SECOND)
            
            Schema:
            $schema
            
            User Query: $question
            
            Generate the BigQuery / SQL query:
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
