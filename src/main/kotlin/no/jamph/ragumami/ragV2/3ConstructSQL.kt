package no.jamph.ragumami.ragV2

import com.google.gson.JsonObject
import org.slf4j.LoggerFactory


class SqlConstructor(
    private val prebuiltSchemas: PrebuiltSchemaProvider
) {
    private val logger = LoggerFactory.getLogger(SqlConstructor::class.java)
    

    fun constructSql(
        queryType: String,
        variables: JsonObject,
        siteId: String,
        urlPath: String
    ): String {
        logger.info("Constructing SQL for query type '{}'", queryType)
        
        var sql = prebuiltSchemas.getSqlTemplate(queryType)
        sql = injectPredeterminedVariables(sql, siteId, urlPath)
        sql = replaceVariablesFromJson(sql, variables)
        
        logger.info("Successfully constructed SQL query")
        logger.debug("Final SQL: {}", sql)
        
        return sql
    }
    

    private fun injectPredeterminedVariables(
        sql: String,
        siteId: String,
        urlPath: String
    ): String {
        var result = sql
        
        result = result.replace("[SITE_ID]", siteId)
        result = result.replace("[WEBSITE_ID]", siteId)
        result = result.replace("[URL_PATH]", urlPath)
        result = result.replace("[PATH]", urlPath)
        
        return result
    }
    
    private fun replaceVariablesFromJson(
        sql: String,
        variables: JsonObject
    ): String {
        var result = sql
        
        for ((key, value) in variables.entrySet()) {
            val placeholder = "[$key]"
            val sqlValue = when {
                value.isJsonNull -> "NULL"
                value.isJsonPrimitive -> {
                    val primitive = value.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isNumber -> primitive.asNumber.toString()
                        primitive.isBoolean -> primitive.asBoolean.toString().uppercase()
                        else -> value.toString()
                    }
                }
                else -> {
                    logger.warn("Complex JSON value for variable '{}' will be converted to string", key)
                    value.toString()
                }
            }
            
            result = result.replace(placeholder, sqlValue)
            logger.debug("Replaced {} with '{}'", placeholder, sqlValue)
        }
        
        val remainingPlaceholders = Regex("\\[([A-Z_]+)\\]").findAll(result).toList()
        if (remainingPlaceholders.isNotEmpty()) {
            val missingVars = remainingPlaceholders.map { it.groupValues[1] }
            logger.warn("SQL still contains unreplaced placeholders: {}", missingVars)
        }
        
        return result
    }
    

    fun validateNoMissingVariables(sql: String): List<String> {
        return Regex("\\[([A-Z_]+)\\]")
            .findAll(sql)
            .map { it.groupValues[1] }
            .toList()
    }
}
