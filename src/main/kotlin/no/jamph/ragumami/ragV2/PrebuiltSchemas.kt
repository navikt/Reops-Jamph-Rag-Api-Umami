package no.jamph.ragumami.ragV2

import no.jamph.bigquery.BigQuerySchemaProvider

data class SchemaTriple(
    val bigQuerySchema: String,
    val sqlTemplate: String,
    val simplifiedSql: String,
    val jsonSchema: String
)

object PrebuiltSchemas {
    private val cache = mutableMapOf<String, SchemaTriple>()
    
    private fun get(type: String, schemaProvider: BigQuerySchemaProvider?): SchemaTriple {
        return cache.getOrPut(type) {
            when (type) {
                "linear" -> linearSchema(schemaProvider!!)
                "rankings" -> rankingsSchema(schemaProvider!!)
                "search" -> searchSchema(schemaProvider!!)
                else -> defaultSchema(schemaProvider!!)
            }
        }
    }
    
    fun getBigQuerySchema(type: String, schemaProvider: BigQuerySchemaProvider) = get(type, schemaProvider).bigQuerySchema
    fun getSqlTemplate(type: String) = get(type, null).sqlTemplate
    fun getSimplifiedSql(type: String) = get(type, null).simplifiedSql
    fun getJsonSchema(type: String) = get(type, null).jsonSchema
    
    private fun linearSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuerySchema = """

=== DATABASE TABLES ===

Table: `session`
Columns:
  - session_id (STRING, NULLABLE) - Unique identifier for a visitor session
  - hostname (STRING, NULLABLE)
  - browser (STRING, NULLABLE)
  - os (STRING, NULLABLE)
  - device (STRING, NULLABLE)
  - screen (STRING, NULLABLE)
  - language (STRING, NULLABLE)
  - country (STRING, NULLABLE)
  - created_at (TIMESTAMP, NULLABLE)
  - session_parameters (ARRAY<STRUCT<data_key STRING, string_value STRING, number_value FLOAT64, date_value TIMESTAMP, data_type INT64>>, REQUIRED) - Unnest to access: CROSS JOIN UNNEST(session_parameters) AS p, then use p.data_key, p.string_value, etc.

Table: `event`
Columns:
  - event_id (STRING, REQUIRED)
  - session_id (STRING, NULLABLE)
  - url_path (STRING, NULLABLE)
  - url_query (STRING, NULLABLE)
  - referrer_path (STRING, NULLABLE)
  - referrer_query (STRING, NULLABLE)
  - referrer_domain (STRING, NULLABLE) - Origin domain of visitor
  - page_title (STRING, NULLABLE)
  - event_type (INT64, NULLABLE) - 1: page view, 2: custom event
  - event_name (STRING, NULLABLE) - Known values: navigere, sok, sidebar-subnav, god-praksis-chip, client-error, last ned, feedback-designsystem, 404, accordion lukket, skjema fullfort, accordion åpnet (only set when event_type = 2)
  - visit_id (STRING, NULLABLE) - Unique identifier for a specific visit within a session
  - tag (STRING, NULLABLE)
  - utm_source (STRING, NULLABLE)
  - utm_content (STRING, NULLABLE)
  - utm_campaign (STRING, NULLABLE)
  - utm_medium (STRING, NULLABLE)
  - utm_term (STRING, NULLABLE)
  - hostname (STRING, NULLABLE)
  - website_name (STRING, NULLABLE)
  - website_domain (STRING, NULLABLE)
  - website_share_id (STRING, NULLABLE)
  - website_team_id (STRING, NULLABLE)

        """.trimIndent(),
        simplifiedSql = """
        For context only:
        WITH base AS (
        SELECT
        DATE_DIFF(DATE(created_at), DATE('[START_DATE]'), DAY) + 1 AS x,
        COUNT(*) AS y
        FROM [TABLE_NAME]
        WHERE event_type = 1
        AND DATE(created_at) BETWEEN DATE('[START_DATE]') AND DATE('[END_DATE]')
        [ADD_FILTERS_HERE]
        GROUP BY x
        ),
        Do not complete the SQL.
        """.trimIndent(),
        sqlTemplate = """

        WITH base AS (
        SELECT CAST(x AS FLOAT64) AS x, CAST(y AS FLOAT64) AS y FROM (
            SELECT
            DATE_DIFF(DATE(created_at), DATE('[START_DATE]'), DAY) + 1 AS x,
            COUNT(*) AS y
            FROM [TABLE_NAME]
            WHERE event_type = 1
            AND website_id = '[WEBSITE_ID]'
            AND DATE(created_at) BETWEEN DATE('[START_DATE]') AND DATE('[END_DATE]')
            [ADD_FILTERS_HERE]
            GROUP BY x
        )
        ),
        stats AS (
        SELECT COUNT(*) AS n, AVG(x) AS x_bar, AVG(y) AS y_bar,
                VAR_SAMP(x) AS var_x, COVAR_SAMP(x, y) AS cov_xy
        FROM base
        ),
        params AS (
        SELECT n, x_bar, y_bar,
            SAFE_DIVIDE(cov_xy, var_x) AS slope,
            y_bar - SAFE_DIVIDE(cov_xy, var_x) * x_bar AS intercept
        FROM stats
        ),
        resid AS (
        SELECT b.x, b.y, p.n, p.x_bar, p.y_bar, p.slope, p.intercept,
            b.y - (p.intercept + p.slope * b.x) AS r
        FROM base b CROSS JOIN params p
        ),
        sums AS (
        SELECT MIN(n) AS n, MIN(intercept) AS a, MIN(slope) AS b,
                MIN(x_bar) AS x_bar, MIN(y_bar) AS y_bar,
            SUM(POW(r, 2)) AS sse,
            SUM(POW(y - y_bar, 2)) AS sst,
            SUM(POW(x - x_bar, 2)) AS sxx
        FROM resid
        ),
        m AS (
        SELECT n, a, b,
            1 - SAFE_DIVIDE(sse, sst) AS r2,
            SQRT(SAFE_DIVIDE(sse, n - 2)) AS rmse,
            SQRT(SAFE_DIVIDE(SAFE_DIVIDE(sse, n - 2), sxx)) AS se_b,
            SQRT(SAFE_DIVIDE(sse, n - 2) * (1.0 / n + POW(x_bar, 2) / sxx)) AS se_a
        FROM sums
        ),
        pv AS (
        SELECT n, a, b, r2, rmse, se_a, se_b,
            SAFE_DIVIDE(a, se_a) AS t_a,
            SAFE_DIVIDE(b, se_b) AS t_b,
            GREATEST(0, 2 * EXP(-0.5 * POW(ABS(SAFE_DIVIDE(a, se_a)), 2)) / 2.506628 * (
            0.4361836 / (1 + 0.33267 * ABS(SAFE_DIVIDE(a, se_a)))
            - 0.1201676 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(a, se_a)), 2)
            + 0.9372980 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(a, se_a)), 3))) AS p_a,
            GREATEST(0, 2 * EXP(-0.5 * POW(ABS(SAFE_DIVIDE(b, se_b)), 2)) / 2.506628 * (
            0.4361836 / (1 + 0.33267 * ABS(SAFE_DIVIDE(b, se_b)))
            - 0.1201676 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(b, se_b)), 2)
            + 0.9372980 / POW(1 + 0.33267 * ABS(SAFE_DIVIDE(b, se_b)), 3))) AS p_b
        FROM m
        )
        SELECT 'Skjæringspunkt (a)' AS term,
        ROUND(a, 4) AS estimat, ROUND(se_a, 4) AS std_feil,
        ROUND(t_a, 3) AS t_verdi, ROUND(p_a, 4) AS p_verdi,
        ROUND(r2, 4) AS r2, ROUND(rmse, 3) AS rmse, n
        FROM pv
        UNION ALL
        SELECT 'Stigningstall (b)',
        ROUND(b, 4), ROUND(se_b, 4),
        ROUND(t_b, 3), ROUND(p_b, 4),
        ROUND(r2, 4), ROUND(rmse, 3), n
        FROM pv
        ORDER BY term

        """.trimIndent(),
        jsonSchema = """
        {
        "START_DATE": [START_DATE],
        "END_DATE": [END_DATE],
        "TABLE_NAME": [TABLE_NAME],
        "ADD_FILTERS_HERE": [ADD_FILTERS_HERE]
        }
        """.trimIndent() // website_id and event prefix is predetermined.
    )
    
    private fun rankingsSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuerySchema = """
            Table: `event`
              - website_id (STRING, NULLABLE)
              - url_path (STRING, NULLABLE)      -- the page URL path, e.g. '/artikkel/tilgjengelighet'
              - page_title (STRING, NULLABLE)    -- human-readable page title
              - event_type (INT64, NULLABLE)     -- 1 = page view, 2 = custom event
              - created_at (TIMESTAMP, NULLABLE)

            Table: `session`
              - website_id (STRING, NULLABLE)
              - browser (STRING, NULLABLE)       -- e.g. 'Chrome', 'Firefox'
              - os (STRING, NULLABLE)            -- e.g. 'Windows', 'iOS', 'Android'
              - device (STRING, NULLABLE)        -- e.g. 'desktop', 'mobile', 'tablet'
              - screen (STRING, NULLABLE)        -- screen resolution, e.g. '1920x1080'
              - language (STRING, NULLABLE)      -- e.g. 'nb-NO', 'en-US'
              - country (STRING, NULLABLE)       -- e.g. 'NO', 'SE'
              - created_at (TIMESTAMP, NULLABLE)
        """.trimIndent(),

        simplifiedSql = """
            -- Example: top pages by views
            SELECT url_path, page_title, COUNT(*) AS count
            FROM event
            WHERE event_type = 1
                AND created_at >= '[START_DATE]'
                AND created_at < '[END_DATE]'
            GROUP BY url_path, page_title
            ORDER BY count DESC
            LIMIT [LIMIT]

            -- Example: top OS / browser / device / country (use session table instead)
            SELECT os, COUNT(*) AS count
            FROM session
            WHERE website_id = '[SITE_ID]'
                AND created_at >= '[START_DATE]'
                AND created_at < '[END_DATE]'
            GROUP BY os
            ORDER BY count DESC
            LIMIT [LIMIT]
        """.trimIndent(),

        sqlTemplate = """
            SELECT [RANK_COLUMN], COUNT(*) AS count
            FROM [TABLE_NAME]
            WHERE website_id = '[SITE_ID]'
                [EXTRA_FILTER]
                AND created_at >= TIMESTAMP('[START_DATE]')
                AND created_at < TIMESTAMP('[END_DATE]')
            GROUP BY [RANK_COLUMN]
            ORDER BY count DESC
            LIMIT [LIMIT]
        """.trimIndent(),
        jsonSchema = """
            {
              "TABLE_NAME": [TABLE_NAME],
              "RANK_COLUMN": [RANK_COLUMN],
              "EXTRA_FILTER": [EXTRA_FILTER],
              "START_DATE": [START_DATE],
              "END_DATE": [END_DATE],
              "LIMIT": [LIMIT]
            }
        """.trimIndent()
    )
    
    private fun defaultSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuerySchema = """${schemaProvider}""".trimIndent(),
        simplifiedSql = """Write your own SQLwith Bigquery dialect here""".trimIndent(),
        sqlTemplate = """N/A""".trimIndent(),
        jsonSchema = """RETURN SQL""".trimIndent()
    )

    // For questions like "how many searched for X" / "hvor mange søker etter X"
    // Finds search events (event_name='sok') and counts how many match the given term
    
    private fun searchSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        // Only the tables and columns needed for this query
        bigQuerySchema = """
            Table: `event`
              - event_id (STRING, REQUIRED)
              - website_id (STRING, NULLABLE)
              - session_id (STRING, NULLABLE)
              - created_at (TIMESTAMP, NULLABLE)
              - event_type (INT64, NULLABLE)  -- 2 = custom event
              - event_name (STRING, NULLABLE) -- 'sok' = search

            Table: `event_data`
              - website_event_id (STRING, REQUIRED) -- join: event.event_id = event_data.website_event_id
              - website_id (STRING, NULLABLE)
              - event_parameters (ARRAY<STRUCT<data_key STRING, string_value STRING>>, REQUIRED)
                CROSS JOIN UNNEST(event_parameters) AS p
                p.data_key = 'query' → p.string_value is the search term typed by the user
        """.trimIndent(),

        // The SQL that gets run against BigQuery
        sqlTemplate = """
            SELECT COUNT(*) AS total_searches
            FROM [TABLE_EVENT]
            JOIN `fagtorsdag-prod-81a6.umami_student.event_data` ed ON e.event_id = ed.website_event_id
            CROSS JOIN UNNEST(ed.event_parameters) AS p
            WHERE e.website_id = '[SITE_ID]'
                AND e.event_type = 2
                AND e.event_name = 'sok'
                AND p.data_key = 'query'
                AND LOWER(p.string_value) LIKE LOWER('%[SEARCH_TERM]%')
                AND e.created_at >= TIMESTAMP('[START_DATE]')
                AND e.created_at < TIMESTAMP('[END_DATE]')
        """.trimIndent(),

        // Shorter version shown to the LLM so it understands the shape
        simplifiedSql = """
            SELECT COUNT(*) AS total_searches
            FROM event e
            JOIN event_data ed ON e.event_id = ed.website_event_id
            CROSS JOIN UNNEST(ed.event_parameters) AS p
            WHERE e.event_type = 2
                AND e.event_name = 'sok'
                AND p.data_key = 'query'
                AND LOWER(p.string_value) LIKE '%[SEARCH_TERM]%'
                AND e.created_at >= '[START_DATE]'
                AND e.created_at < '[END_DATE]'
        """.trimIndent(),

        // The LLM fills these in from the user's question
        jsonSchema = """
            {
              "SEARCH_TERM": [SEARCH_TERM],
              "START_DATE": [START_DATE],
              "END_DATE": [END_DATE]
            }
        """.trimIndent()
    )
}
