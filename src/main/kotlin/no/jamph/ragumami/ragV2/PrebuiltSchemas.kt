package no.jamph.ragumami.ragV2

import no.jamph.bigquery.BigQuerySchemaProvider

data class SchemaTriple(
    val bigQuery: String,
    val sql: String,
    val json: String
)

object PrebuiltSchemas {
    private val cache = mutableMapOf<String, SchemaTriple>()
    
    private fun get(type: String, schemaProvider: BigQuerySchemaProvider?): SchemaTriple {
        return cache.getOrPut(type) {
            when (type) {
                "linear" -> linearSchema(schemaProvider!!)
                "rankings" -> rankingsSchema(schemaProvider!!)
                else -> defaultSchema(schemaProvider!!)
            }
        }
    }
    
    fun bigquery(type: String, schemaProvider: BigQuerySchemaProvider) = get(type, schemaProvider).bigQuery
    fun sql(type: String) = get(type, null).sql
    fun json(type: String) = get(type, null).json
    
    private fun linearSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuery = """**bigquery schema**""".trimIndent(),
        sql = """**sql template**""".trimIndent(),
        json = """**json schema**""".trimIndent()
    )
    
    private fun rankingsSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuery = """**bigquery schema**""".trimIndent(),
        sql = """**sql template**""".trimIndent(),
        json = """**json schema**""".trimIndent()
    )
    
    private fun defaultSchema(schemaProvider: BigQuerySchemaProvider) = SchemaTriple(
        bigQuery = """**bigquery schema**""".trimIndent(),
        sql = """**sql template**""".trimIndent(),
        json = """**json schema**""".trimIndent()
    )
}
