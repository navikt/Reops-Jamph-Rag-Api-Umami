package no.jamph.ragumami.ragV2

import com.google.gson.JsonObject
import no.jamph.ragumami.Routes

class ConstructSQL(
    private val prebuiltSchemas: PrebuiltSchemaProvider
) {
    fun constructSql(
        queryType: String,
        variables: JsonObject,
        siteId: String,
        urlPath: String,
        prefix: String = Routes.prefixUmami,
    ): String {
        var sql = prebuiltSchemas.getSqlTemplate(queryType)
        sql = replaceVariablesFromJson(sql, variables)
        sql = injectPredeterminedVariables(sql, siteId, urlPath, prefix)
        
        // Clean up optional filters if not provided
        sql = sql.replace("[SELECT_FILTERS]", "")
        sql = sql.replace("[WHERE_FILTERS]", "")
        
        return sql
    }
    
  
    fun validateNoMissingVariables(sql: String): List<String> {
        return Regex("\\[([A-Z_]+)\\]")
        .findAll(sql)
        .map { it.groupValues[1] }
        .toList()
    }
}

private fun injectPredeterminedVariables(
    sql: String,
    siteId: String,
    urlPath: String,
    prefix: String
): String {
    var result = sql
    
    result = result.replace("[WEBSITE_ID]", siteId)
    result = result.replace("[PATH]", urlPath)

    result = result.replace("`prefix.", "`$prefix")
    result = result.replace("prefix.", prefix)

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

// Error Codes Reference:
// 10003: SQL template construction failed. Variable replacement or template processing error.
//        Possible causes: Missing required variables, invalid JSON structure, or template mismatch.
//        Resolution: Verify extracted variables match template placeholders, check JSON schema validity.
