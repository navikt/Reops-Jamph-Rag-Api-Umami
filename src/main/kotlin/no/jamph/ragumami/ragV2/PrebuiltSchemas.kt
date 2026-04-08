package no.jamph.ragumami.ragV2

import no.jamph.bigquery.BigQuerySchemaProvider

object PrebuiltSchemas {
    



fun sqlLinear(): String {
    return  """
            SELECT 
                DATE(created_at) AS date,
                COUNT(*) AS metric_value
            FROM event
            WHERE created_at >= '2025-01-01' AND created_at <= '2025-12-31'
            GROUP BY date
            ORDER BY date ASC
        """.trimIndent()
    }
}

fun bigqueryLinear(): String {
    return """
        Table: `session`
Columns:
  session_id (STRING)
  hostname (STRING)
  browser (STRING)
  os (STRING)
  device (STRING)
  screen (STRING)
  language (STRING)
  created_at (TIMESTAMP)
  session_parameters (ARRAY<STRUCT<data_key STRING, string_value STRING, number_value FLOAT64, date_value TIMESTAMP, data_type INT64>>, REQUIRED) - Unnest to access: CROSS JOIN UNNEST(session_parameters) AS p, then use p.data_key, p.string_value, etc.

Table: `event`
Columns:
  event_id (STRING, REQUIRED)
  website_id (STRING)
  session_id (STRING)
  created_at (TIMESTAMP)
  url_query (STRING)
  referrer_path (STRING)
  referrer_query (STRING)
  referrer_domain (STRING) - Origin domain of visitor
  page_title (STRING)
  event_type (INT64) - 1: page view, 2: custom event
  event_name (STRING) - Known values: navigere, sok, sidebar-subnav, god-praksis-chip, client-error, last ned, feedback-designsystem, 404, accordion lukket, skjema fullfort, accordion åpnet (only set when event_type = 2)
  visit_id (STRING) - Unique identifier for a specific visit within a session
  tag (STRING)
  utm_source (STRING)
  utm_content (STRING)
  utm_campaign (STRING)
  utm_medium (STRING)
  utm_term (STRING)
  hostname (STRING)
  website_name (STRING)
  website_domain (STRING)
  website_share_id (STRING)
  website_team_id (STRING)

Table: `public_website`
Columns:
  name (STRING)
  domain (STRING)
  share_id (STRING)
  reset_at (TIMESTAMP)
  user_id (STRING)
  created_at (TIMESTAMP)
  updated_at (TIMESTAMP)
  deleted_at (TIMESTAMP)
  created_by (STRING)
  team_id (STRING)

Table: `event_data`
Columns:
  event_parameters (ARRAY<STRUCT<data_key STRING, string_value STRING, number_value FLOAT64, date_value TIMESTAMP, data_type INT64>>, REQUIRED) - Unnest to access: CROSS JOIN UNNEST(event_parameters) AS p, then use p.data_key, p.string_value, etc.
  created_at (TIMESTAMP)
    """.trimIndent()
}
fun jsonLinear() : String {
    return """
        {
          "schema": "linear_trend_analysis",
          "description": "Schema for linear regression trend analysis on time-series data.",
          "X_axis": "session",
            "Y_axis": "event",
            "relevant_tables": {
        },
        """.trimIndent()