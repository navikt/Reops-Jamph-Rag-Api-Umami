package no.jamph.bigquery

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FieldValue
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.TableResult
import java.io.ByteArrayInputStream
import java.io.FileInputStream

class BigQueryQueryService(
    val projectId: String,
    val dataset: String,
    private val location: String = "europe-north1",
    credentialsPath: String? = null,
    credentialsJson: String? = null,
) {
    internal val bigQuery: BigQuery

    init {
        val builder = BigQueryOptions.newBuilder()
            .setProjectId(projectId)
            .setLocation(location)

        if (!credentialsJson.isNullOrBlank()) {
            try {
                val credentials = GoogleCredentials.fromStream(
                    ByteArrayInputStream(credentialsJson.toByteArray())
                )
                builder.setCredentials(credentials)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to parse BigQuery credentials JSON", e)
            }
        } else if (!credentialsPath.isNullOrBlank()) {
            try {
                val credentials = GoogleCredentials.fromStream(FileInputStream(credentialsPath))
                builder.setCredentials(credentials)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to load BigQuery credentials from: $credentialsPath", e)
            }
        }

        bigQuery = builder.build().service
    }

    fun runQueryAsCsv(query: String, maxRows: Long = 10_000): String {
        val result = runQuery(query)
        return tableResultToCsv(result, maxRows)
    }

    fun runQueryAsRows(query: String, maxRows: Long = 10_000): List<Map<String, Any?>> {
        val result = runQuery(query)
        val fieldNames = result.schema?.fields?.map { it.name } ?: emptyList()

        val rows = mutableListOf<Map<String, Any?>>()
        var count = 0L
        for (row in result.iterateAll()) {
            if (count >= maxRows) break
            val map = linkedMapOf<String, Any?>()
            for (fieldName in fieldNames) {
                val fieldValue = row.get(fieldName)
                map[fieldName] = fieldValueToAny(fieldValue)
            }
            rows.add(map)
            count++
        }
        return rows
    }

    internal fun runQuery(query: String): TableResult {
        val queryConfig = QueryJobConfiguration.newBuilder(query)
            .setUseLegacySql(false)
            .build()

        return try {
            bigQuery.query(queryConfig)
        } catch (e: Exception) {
            throw RuntimeException("Failed to run BigQuery query", e)
        }
    }

    private fun tableResultToCsv(result: TableResult, maxRows: Long): String {
        val fields = result.schema?.fields ?: emptyList()
        if (fields.isEmpty()) return ""

        val header = fields.joinToString(",") { csvEscape(it.name) }
        val sb = StringBuilder()
        sb.appendLine(header)

        var count = 0L
        for (row in result.iterateAll()) {
            if (count >= maxRows) break
            val values = fields.map { field ->
                val fieldValue = row.get(field.name)
                csvEscape(fieldValueToString(fieldValue))
            }
            sb.appendLine(values.joinToString(","))
            count++
        }

        return sb.toString().trimEnd()
    }

    private fun fieldValueToAny(value: FieldValue?): Any? {
        if (value == null || value.isNull) return null
        return when (value.attribute) {
            FieldValue.Attribute.PRIMITIVE -> value.value
            FieldValue.Attribute.REPEATED -> value.repeatedValue.map { fieldValueToAny(it) }
            FieldValue.Attribute.RECORD -> value.recordValue.map { fieldValueToAny(it) }
            else -> value.value
        }
    }

    private fun fieldValueToString(value: FieldValue?): String {
        val any = fieldValueToAny(value) ?: return ""
        return when (any) {
            is String -> any
            else -> any.toString()
        }
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
