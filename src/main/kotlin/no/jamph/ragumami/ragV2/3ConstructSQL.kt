package no.jamph.ragumami.ragV2

import com.google.gson.JsonObject

class ConstructSQL(
    private val prebuiltSchemas: PrebuiltSchemaProvider
) {
    fun constructSql(
        queryType: String,
        variables: JsonObject,
        siteId: String,
        urlPath: String
    ): String {
        var sql = prebuiltSchemas.getSqlTemplate(queryType)
        sql = injectPredeterminedVariables(sql, siteId, urlPath)
        sql = replaceVariablesFromJson(sql, variables)
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
                else -> value.toString()
            }
            
            result = result.replace(placeholder, sqlValue)
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
