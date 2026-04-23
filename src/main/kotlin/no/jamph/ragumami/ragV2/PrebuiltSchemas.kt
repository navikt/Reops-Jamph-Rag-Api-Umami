package no.jamph.ragumami.ragV2

import no.jamph.bigquery.BigQuerySchemaProvider

data class SchemaTriple(
    val bigQuerySchema: String,
    val sqlTemplate: String,
    val simplifiedSql: String,
    val jsonSchema: String
)

object PrebuiltSchemas {

    private fun resolve(type: String, schemaProvider: BigQuerySchemaProvider): SchemaTriple {
        return when (type) {
            "linear" -> linearSchema(schemaProvider)
            "rankings" -> rankingsSchema(schemaProvider)
            "search" -> searchSchema(schemaProvider)
            "journey" -> journeySchema(schemaProvider)
            "cards" -> cardsSchema(schemaProvider)
            else -> defaultSchema(schemaProvider)
        }
    }

    fun getBigQuerySchema(type: String, schemaProvider: BigQuerySchemaProvider) = resolve(type, schemaProvider).bigQuerySchema
    fun getSqlTemplate(type: String, schemaProvider: BigQuerySchemaProvider) = resolve(type, schemaProvider).sqlTemplate
    fun getSimplifiedSql(type: String, schemaProvider: BigQuerySchemaProvider) = resolve(type, schemaProvider).simplifiedSql
    fun getJsonSchema(type: String, schemaProvider: BigQuerySchemaProvider) = resolve(type, schemaProvider).jsonSchema
    

    //you should describe in the schema text that METRIC_SQL must be an aggregate metric.
    private fun linearSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuerySchema = """

=== DATABASE TABLES ===

Table: `prefix.session`
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

Table: `prefix.event`
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

  Important:
  METRIC_SQL must be an aggregate expression that produces a single numeric value for each day. For example, "COUNT(*)" or "SUM(p.number_value)" where p is an unnested parameter. The x-axis will be days since the start date, and the y-axis will be the value of this metric.

        """.trimIndent(),
        simplifiedSql = """
        For context only:
        WITH base AS (
        SELECT CAST(x AS FLOAT64) AS x, CAST(y AS FLOAT64) AS y FROM (
            SELECT
            DATE_DIFF(DATE(created_at), DATE('[START_DATE]'), DAY) + 1 AS x,
            [METRIC_SQL] AS y
            FROM `[TABLE]`
            WHERE website_id = -- is handled is handled
            AND created_at >= TIMESTAMP('[START_DATE]')
            AND created_at < TIMESTAMP_ADD(TIMESTAMP('[END_DATE]'), INTERVAL 1 DAY)
            [WHERE_FILTERS] -- (optional)Specific filters based on the users question, e.g. "AND url_path LIKE '%/blogg/%'" or "AND browser = 'Chrome'"
            GROUP BY x
            )
        ),
        """.trimIndent(),//website_id is handled
        sqlTemplate = """ 
        WITH base AS (
        SELECT CAST(x AS FLOAT64) AS x, CAST(y AS FLOAT64) AS y FROM (
            SELECT
            DATE_DIFF(DATE(created_at), DATE('[START_DATE]'), DAY) + 1 AS x,
            [METRIC_SQL] AS y
            FROM [TABLE]
            WHERE website_id = '[WEBSITE_ID]'
            AND created_at >= TIMESTAMP('[START_DATE]')
            AND created_at < TIMESTAMP_ADD(TIMESTAMP('[END_DATE]'), INTERVAL 1 DAY)
            [WHERE_FILTERS]
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
        -- Approksimerer p-verdier for a og b ved å bruke t-verdiene og en normalfordeling
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
        "TABLE": [TABLE],
        "START_DATE": [START_DATE],
        "END_DATE": [END_DATE],
        "METRIC_SQL": [METRIC_SQL],
        "WHERE_FILTERS": [WHERE_FILTERS] 
        }
        """.trimIndent() // website_id, TABLE, prefix are predetermined.
    )
    
    // event types and table is missing.

    private fun rankingsSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuerySchema = """
            Table: `prefix.event`
              - website_id (STRING, NULLABLE)
              - url_path (STRING, NULLABLE)      -- the page URL path, e.g. '/artikkel/tilgjengelighet'
              - page_title (STRING, NULLABLE)    -- human-readable page title
              - event_type (INT64, NULLABLE)     -- 1 = page view, 2 = custom event
              - created_at (TIMESTAMP, NULLABLE)

            Table: `prefix.session`
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
            SELECT [RANK_COLUMN] AS x, COUNT(*) AS count
            [SELECT_FILTERS]
            FROM [TABLE]
            WHERE website_id //is handled
                [WHERE_FILTERS]
                AND created_at >= '[START_DATE]'
                AND created_at < '[END_DATE]'
            GROUP BY x
            ORDER BY count DESC
            LIMIT [LIMIT]
        """.trimIndent(),

        sqlTemplate = """
            SELECT [RANK_COLUMN] AS x , COUNT(*) AS count
            [SELECT_FILTERS]
            FROM [TABLE]
            WHERE website_id = '[WEBSITE_ID]'
                [WHERE_FILTERS]
                AND created_at >= TIMESTAMP('[START_DATE]')
                AND created_at < TIMESTAMP('[END_DATE]')
            GROUP BY x
            ORDER BY count DESC
            LIMIT [LIMIT]
        """.trimIndent(),
        jsonSchema = """
            {
              "TABLE": [TABLE],
              "RANK_COLUMN": [RANK_COLUMN],
              "WHERE_FILTERS": [WHERE_FILTERS],
              "START_DATE": [START_DATE],
              "END_DATE": [END_DATE],
              "LIMIT": [LIMIT]
            }
        """.trimIndent()
    )
    
    private fun defaultSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuerySchema = """${schemaProvider.getSchemaContext()}""".trimIndent(),
        simplifiedSql = """Write your own SQLwith Bigquery dialect here""".trimIndent(),
        sqlTemplate = """N/A""".trimIndent(),
        jsonSchema = """RETURN SQL""".trimIndent()
    )

    // For questions like "how many searched for X" / "hvor mange søker etter X"
    // Finds search events (event_name='sok') and counts how many match the given term

    //Why it fails, 
    // event_name = 'sok' exists (7,994 records) BUT those are organic search referrals (visitors from Google), NOT internal site searches.
    // NO event_data records
    // query key does not exist

    // only 11 searches total. most for "hei".
    // Reality can use url_path to find searches. see file.
    // NAV needs to have tracking for us to complete this schema.
    private fun searchSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        // Only the tables and columns needed for this query
        bigQuerySchema = """
            Table: `prefix.event`
              - event_id (STRING, REQUIRED)
              - website_id (STRING, NULLABLE)
              - session_id (STRING, NULLABLE)
              - created_at (TIMESTAMP, NULLABLE)
              - event_type (INT64, NULLABLE)  -- 2 = custom event
              - event_name (STRING, NULLABLE) -- 'sok' = search

            Table: `prefix.event_data`
              - website_event_id (STRING, REQUIRED) -- join: event.event_id = event_data.website_event_id
              - website_id (STRING, NULLABLE)
              - event_parameters (ARRAY<STRUCT<data_key STRING, string_value STRING>>, REQUIRED)
                CROSS JOIN UNNEST(event_parameters) AS p
                p.data_key = 'query' → p.string_value is the search term typed by the user
        """.trimIndent(),

        // The SQL that gets run against BigQuery
        sqlTemplate = """
            SELECT COUNT(*) AS total_searches
            FROM `prefix.event` e
            JOIN `prefix.event_data` ed ON e.event_id = ed.website_event_id
            CROSS JOIN UNNEST(ed.event_parameters) AS p
            WHERE e.website_id = '[WEBSITE_ID]'
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

    private fun journeySchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        
        bigQuerySchema = """
        Current time: 2025-12-30
        
        Known values of url_path: '/komponenter/core', '/komponenter/ikoner', '/designsystemet', '/grunnleggende/styling/design-tokens', '/god-praksis', '/komponenter/core/button', '/komponenter/core/linkcard', '/komponenter/core/table', '/komponenter/primitives/box', '/komponenter/core/datepicker', '/komponenter/core/typography', '/komponenter/core/accordion', '/grunnleggende/darkside/ny-versjon-av-aksel-darkside', '/komponenter/core/actionmenu', '/komponenter/core/combobox', '/komponenter/core/alert', '/produktbloggen', '/komponenter/core/textfield', '/grunnleggende/darkside/design-tokens', '/komponenter/primitives/hstack', '/komponenter/core/expansioncard', '/komponenter/core/modal', '/komponenter/primitives/page', '/komponenter/core/radio', '/komponenter/core/link', '/komponenter/core/chips', '/komponenter/core/select', '/komponenter/core/checkbox', '/komponenter/core/tag', '/grunnleggende/styling/farger', '/komponenter/core/stepper', '/komponenter/core/eksperimenter', '/komponenter/core/process', '/god-praksis/brukerinnsikt', '/'
        if no time is specified, use the latest year.
        """.trimIndent(),
        simplifiedSql = """
        WITH config AS (
        SELECT 
            '[START_PATH]' AS start_path,
            '[END_PATH]' AS end_path
        ),

        all_events AS (
        SELECT 
            session_id,
            url_path,
            event_type,
            event_name,
            created_at,
            ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY created_at) AS step_in_session
        FROM `prefix.event`
        WHERE website_id = //is handled
            AND created_at >= TIMESTAMP('[START_DATE]')
            AND created_at < TIMESTAMP('[END_DATE]')

        -- function() -- this function fixes everything.
        )
        """.trimIndent(),
        sqlTemplate = """

        -- CONFIGURATION: Change paths here
        WITH config AS (
        SELECT 
            '[START_PATH]' AS start_path,
            '[END_PATH]' AS end_path
        ),

        all_events AS (
        SELECT 
            session_id,
            url_path,
            event_type,
            event_name,
            created_at,
            ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY created_at) AS step_in_session
        FROM `prefix.event`
        WHERE website_id = '[WEBSITE_ID]'
            AND created_at >= TIMESTAMP('[START_DATE]')
            AND created_at < TIMESTAMP('[END_DATE]')
        ),

        from_homepage AS (
        SELECT DISTINCT ae.session_id, ae.step_in_session AS homepage_step
        FROM all_events ae
        CROSS JOIN config c
        WHERE ae.url_path = c.start_path
            AND ae.event_type = 1
        ),

        steps AS (
        SELECT 0 AS step_num
        UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
        UNION ALL SELECT 9
        ),

        all_step_actions AS (
        SELECT 
            s.step_num,
            ae.session_id,
            CASE 
            WHEN ae.url_path = c.end_path THEN 'Fullførte'
            WHEN ae.event_type = 1 THEN ae.url_path
            WHEN ae.event_type = 2 THEN CONCAT('Event: ', ae.event_name)
            END AS action,
            -- Mark when this session succeeded
            MAX(CASE WHEN ae.url_path = c.end_path THEN s.step_num END) 
            OVER (PARTITION BY ae.session_id) AS succeeded_at_step
        FROM steps s
        CROSS JOIN from_homepage fh
        CROSS JOIN config c
        JOIN all_events ae ON ae.session_id = fh.session_id
        WHERE ae.step_in_session = fh.homepage_step + s.step_num
            -- Exclude sessions that already succeeded in earlier steps
            AND NOT EXISTS (
            SELECT 1 FROM all_events ae2, config c2
            WHERE ae2.session_id = fh.session_id
                AND ae2.url_path = c2.end_path
                AND ae2.step_in_session BETWEEN fh.homepage_step AND fh.homepage_step + s.step_num - 1
            )
        ),

        -- Track sessions that dropped out (had activity in previous step but not current)
        dropouts AS (
        SELECT 
            s.step_num,
            fh.session_id,
            'Forlot siden' AS action
        FROM steps s
        CROSS JOIN from_homepage fh
        WHERE s.step_num > 0  -- Can't drop out at step 0
            -- Had activity at previous step
            AND EXISTS (
            SELECT 1 FROM all_events ae
            WHERE ae.session_id = fh.session_id
                AND ae.step_in_session = fh.homepage_step + s.step_num - 1
            )
            -- But NO activity at current step
            AND NOT EXISTS (
            SELECT 1 FROM all_events ae
            WHERE ae.session_id = fh.session_id
                AND ae.step_in_session = fh.homepage_step + s.step_num
            )
            -- And didn't succeed in earlier steps
            AND NOT EXISTS (
            SELECT 1 FROM all_events ae2, config c2
            WHERE ae2.session_id = fh.session_id
                AND ae2.url_path = c2.end_path
                AND ae2.step_in_session < fh.homepage_step + s.step_num
            )
        ),

        -- It's not practical to implement the logic for people who gave up but stayed on the site. 

        combined_actions AS (
        SELECT step_num, session_id, action FROM all_step_actions
        UNION ALL
        SELECT step_num, session_id, action FROM dropouts
        ),

        top_10_per_step AS (
        SELECT 
            step_num,
            action,
            COUNT(DISTINCT session_id) AS visitor_count,
            ROW_NUMBER() OVER (PARTITION BY step_num ORDER BY COUNT(DISTINCT session_id) DESC) AS rank
        FROM combined_actions
        GROUP BY step_num, action
        QUALIFY rank <= 10
        ),

        homepage_count AS (
        SELECT CONCAT((SELECT start_path FROM config), ' ', COUNT(DISTINCT session_id)) AS count_text
        FROM from_homepage
        ),

        -- Calculate summary statistics for column 10
        summary_stats AS (
        SELECT
            (SELECT COUNT(DISTINCT session_id) FROM from_homepage) AS total_sessions,
            (SELECT COUNT(DISTINCT ae.session_id) 
            FROM all_events ae 
            CROSS JOIN config c
            JOIN from_homepage fh ON ae.session_id = fh.session_id
            WHERE ae.url_path = c.end_path
            AND ae.step_in_session >= fh.homepage_step) AS succeeded,
            (SELECT COUNT(DISTINCT session_id) FROM dropouts) AS failed
        )

        -- Pivot: Show all steps aligned in rows
        SELECT 
        CASE WHEN rank = 1 THEN (SELECT count_text FROM homepage_count) END AS `0`,
        MAX(CASE WHEN step_num = 1 THEN CONCAT(action, ' ', visitor_count) END) AS `1`,
        MAX(CASE WHEN step_num = 2 THEN CONCAT(action, ' ', visitor_count) END) AS `2`,
        MAX(CASE WHEN step_num = 3 THEN CONCAT(action, ' ', visitor_count) END) AS `3`,
        MAX(CASE WHEN step_num = 4 THEN CONCAT(action, ' ', visitor_count) END) AS `4`,
        MAX(CASE WHEN step_num = 5 THEN CONCAT(action, ' ', visitor_count) END) AS `5`,
        MAX(CASE WHEN step_num = 6 THEN CONCAT(action, ' ', visitor_count) END) AS `6`,
        MAX(CASE WHEN step_num = 7 THEN CONCAT(action, ' ', visitor_count) END) AS `7`,
        MAX(CASE WHEN step_num = 8 THEN CONCAT(action, ' ', visitor_count) END) AS `8`,
        MAX(CASE WHEN step_num = 9 THEN CONCAT(action, ' ', visitor_count) END) AS `9`,
        CASE WHEN rank = 1 THEN (
            SELECT CONCAT(
            'Fullførte: ', succeeded, ' | ',
            'Forlot: ', failed, ' | ',
            'Ubestemt: ', (total_sessions - succeeded - failed)
            )
            FROM summary_stats
        ) END AS `10`
        FROM top_10_per_step
        GROUP BY rank
        ORDER BY rank;

        """.trimIndent(),
        jsonSchema = """
        "START_PATH": '[START_PATH]',
        "END_PATH": '[END_PATH]',
        "START_DATE": '[START_DATE]',
        "END_DATE": '[END_DATE]',
        """.trimIndent()
    )
    
    private fun cardsSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuerySchema = """
        ${schemaProvider.getSchemaContext()}  
        Important:       
        - If the user asks for fewer than 4 facts, set unused fact names to 'empty'.
        - For WHERE conditions, use TRUE if no additional filter is needed for that fact.
        
        """.trimIndent(),

        simplifiedSql = """
            WITH facts AS (
              SELECT '[FACT1_NAME]' AS category, [SELECT1] AS value
              FROM [TABLE1]
              WHERE website_id //is handled
                AND created_at >= '[START_DATE]'
                AND created_at < '[END_DATE]'
                AND [WHERE1]

              UNION ALL

              SELECT '[FACT2_NAME]' AS category, [SELECT2] AS value
              FROM [TABLE2]
              WHERE website_id //is handled
                AND created_at >= '[START_DATE]'
                AND created_at < '[END_DATE]'
                AND [WHERE2]

              UNION ALL

              SELECT '[FACT3_NAME]' AS category, [SELECT3] AS value
              FROM [TABLE3]
              WHERE website_id //is handled
                AND created_at >= '[START_DATE]'
                AND created_at < '[END_DATE]'
                AND [WHERE3]

              UNION ALL

              SELECT '[FACT4_NAME]' AS category, [SELECT4] AS value
              FROM [TABLE4]
              WHERE website_id //is handled
                AND created_at >= '[START_DATE]'
                AND created_at < '[END_DATE]'
                AND [WHERE4]
            )
            SELECT category, value
            FROM facts
            ORDER BY category
        """.trimIndent(),

        sqlTemplate = """
            -- Fakta 1
            SELECT '[FACT1_NAME]' AS category, [SELECT1] AS value
            FROM `[TABLE1]`
            WHERE website_id = '[WEBSITE_ID]'
              AND created_at >= TIMESTAMP('[START_DATE]')
              AND created_at < TIMESTAMP('[END_DATE]')
              AND [WHERE1]

            UNION ALL

            -- Fakta 2
            SELECT '[FACT2_NAME]' AS category, [SELECT2] AS value
            FROM `[TABLE2]`
            WHERE website_id = '[WEBSITE_ID]'
              AND created_at >= TIMESTAMP('[START_DATE]')
              AND created_at < TIMESTAMP('[END_DATE]')
              AND [WHERE2]

            UNION ALL

            -- Fakta 3 (valgfri) -- du kan slette denne delen. (SELCECT - UNION ALL)
            SELECT '[FACT3_NAME]' AS category, [SELECT3] AS value
            FROM `[TABLE3]`
            WHERE website_id = '[WEBSITE_ID]'
              AND created_at >= TIMESTAMP('[START_DATE]')
              AND created_at < TIMESTAMP('[END_DATE]')
              AND [WHERE3]
              AND '[FACT3_NAME]' != 'empty'

            UNION ALL

            -- Fakta 4 (valgfri) -- du kan slette denne delen. (SELCECT - UNION ALL)
            SELECT '[FACT4_NAME]' AS category, [SELECT4] AS value
            FROM `[TABLE4]`
            WHERE website_id = '[WEBSITE_ID]'
              AND created_at >= TIMESTAMP('[START_DATE]')
              AND created_at < TIMESTAMP('[END_DATE]')
              AND [WHERE4]
              AND '[FACT4_NAME]' != 'empty'

            ORDER BY category;

        """.trimIndent(),

        jsonSchema = """
            {
              "FACT1_NAME": [FACT1_NAME],
              "SELECT1": [SELECT1],
              "TABLE1": [TABLE1],
              "WHERE1": [WHERE1],
              "FACT2_NAME": [FACT2_NAME],
              "SELECT2": [SELECT2],
              "TABLE2": [TABLE2],
              "WHERE2": [WHERE2],
              "FACT3_NAME": [FACT3_NAME],
              "SELECT3": [SELECT3],
              "TABLE3": [TABLE3],
              "WHERE3": [WHERE3],
              "FACT4_NAME": [FACT4_NAME],
              "SELECT4": [SELECT4],
              "TABLE4": [TABLE4],
              "WHERE4": [WHERE4],
              "START_DATE": [START_DATE],
              "END_DATE": [END_DATE]
            }
        """.trimIndent()
    )

}
