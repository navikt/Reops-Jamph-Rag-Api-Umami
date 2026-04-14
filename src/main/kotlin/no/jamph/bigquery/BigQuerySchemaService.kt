package no.jamph.bigquery

import com.google.cloud.bigquery.QueryJobConfiguration

data class Website(
    val websiteId: String,
    val name: String,
    val domain: String?
)

data class TableColumn(
    val name: String,
    val type: String,
    val mode: String,
    val description: String?
)

data class TableSchema(
    val tableName: String,
    val columns: List<TableColumn>
)

class BigQuerySchemaService(
    private val queryService: BigQueryQueryService,
) : BigQuerySchemaProvider {
    val bigQuery = queryService.bigQuery
    private val projectId = queryService.projectId
    private val dataset = queryService.dataset

    /**
     * Fetches all websites from the public_website table.
     *
     * Uses BigQueryQueryService to execute SQL (so auth/client setup lives in one place).
     */
    override fun getWebsites(): List<Website> {
        val query = """
            SELECT 
                website_id,
                name,
                domain
            FROM 
                `$projectId.$dataset.public_website`
            ORDER BY name
        """.trimIndent()

        return try {
            val results = queryService.runQuery(query)
            val websites = mutableListOf<Website>()

            results.iterateAll().forEach { row ->
                websites.add(
                    Website(
                        websiteId = row.get("website_id").stringValue,
                        name = row.get("name").stringValue,
                        domain = row.get("domain")?.stringValue
                    )
                )
            }

            websites
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch websites from BigQuery", e)
        }
    }

    /**
     * Gets the schema for a specific table (metadata API, no data scanning).
     */
    fun getTableSchema(tableName: String): TableSchema {
        val tableId = com.google.cloud.bigquery.TableId.of(projectId, dataset, tableName)
        val table = bigQuery.getTable(tableId)
            ?: throw IllegalArgumentException("Table not found: $tableName")

        val columns = table.getDefinition<com.google.cloud.bigquery.StandardTableDefinition>()
            .schema
            ?.fields
            ?.map { field ->
                TableColumn(
                    name = field.name,
                    type = field.type.toString(),
                    mode = field.mode?.toString() ?: "NULLABLE",
                    description = field.description
                )
            } ?: emptyList()

        return TableSchema(tableName, columns)
    }

    /**
     * Lists all tables in the dataset.
     */
    fun listTables(): List<String> {
        val datasetId = com.google.cloud.bigquery.DatasetId.of(projectId, dataset)
        return bigQuery.listTables(datasetId)
            .iterateAll()
            .map { it.tableId.table }
            .toList()
    }

    /**
     * Generates a comprehensive schema context for LLM prompts.
     */
    override fun getSchemaContext(): String {
        val tables = listTables()

        val schemaBuilder = StringBuilder()

        schemaBuilder.appendLine("=== BIGQUERY DATABASE SCHEMA ===")
        schemaBuilder.appendLine("Project: $projectId")
        schemaBuilder.appendLine("Dataset: $dataset")
        schemaBuilder.appendLine("Current time: ${timeNow()}")
        schemaBuilder.appendLine()

        schemaBuilder.appendLine("=== DATABASE TABLES ===")
        tables.forEach { tableName ->
            try {
                val schema = getTableSchema(tableName)
                schemaBuilder.appendLine("\nTable: `$projectId.$dataset.$tableName`")
                schemaBuilder.appendLine("Columns:")
                schema.columns.forEach { col ->
                    val desc = col.description?.let { " - $it" } ?: ""
                    val extraInfo = when (col.name) {
                        "event_type" -> " - 1: page view, 2: custom event"
                        "session_id" -> " - Unique identifier for a visitor session"
                        "visit_id" -> " - Unique identifier for a specific visit within a session"
                        "event_name" -> " - Known values: navigere, sok, sidebar-subnav, god-praksis-chip, client-error, last ned, feedback-designsystem, 404, accordion lukket, skjema fullfort, accordion åpnet (only set when event_type = 2)"
                        "session_parameters" -> " - STRUCT fields: data_key STRING, string_value STRING, number_value FLOAT64, date_value TIMESTAMP, data_type INT64. Unnest with: CROSS JOIN UNNEST(session_parameters) AS p"
                        "event_parameters" -> " - STRUCT fields: data_key STRING, string_value STRING, number_value FLOAT64, date_value TIMESTAMP, data_type INT64. Unnest with: CROSS JOIN UNNEST(event_parameters) AS p"
                        "referrer_domain" -> " - Origin domain of visitor"
                        "website_event_id" -> " - Foreign key to event.event_id"
                        "browser" -> " - Known values: chrome, edge-chromium, firefox, ios, safari, crios, ios-webview, opera, facebook, samsung"
                        "os" -> " - Known values: Mac OS, Windows 10, iOS, Android OS, Linux, Chrome OS"

                        else -> ""
                    }
                    schemaBuilder.appendLine("  - ${col.name} (${col.type}, ${col.mode})$desc$extraInfo")
                }
            } catch (e: Exception) {
                schemaBuilder.appendLine("\nTable: `$projectId.$dataset.$tableName` - Error reading schema: ${e.message}")
            }
        }

        return schemaBuilder.toString()
    }

    override fun timeNow(): String = "2025-12-28T00:00:00Z"

    /**
     * Health check - verifies BigQuery connection.
     */
    fun isHealthy(): Boolean {
        return try {
            val datasetId = com.google.cloud.bigquery.DatasetId.of(projectId, dataset)
            bigQuery.getDataset(datasetId) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Health check that returns the error detail instead of swallowing it.
     */
    fun healthCheckDetail(): String {
        return try {
            val datasetId = com.google.cloud.bigquery.DatasetId.of(projectId, dataset)
            val ds = bigQuery.getDataset(datasetId)
            if (ds != null) "OK" else "dataset '$dataset' not found in project '$projectId'"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
