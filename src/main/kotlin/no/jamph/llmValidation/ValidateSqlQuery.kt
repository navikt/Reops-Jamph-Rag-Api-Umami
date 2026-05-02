package no.jamph.llmValidation

import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.parser.CCJSqlParserManager
import java.io.StringReader

private val BLOCKED = Regex(
    "\\b(DELETE|DROP|TRUNCATE|UPDATE|INSERT|ALTER|MERGE|REPLACE|CREATE(?!\\s+(TEMP|TEMPORARY))|LOAD)\\b",
    RegexOption.IGNORE_CASE
)



// Function that validates SQL 
fun isSqlQueryValid(sql: String): Boolean {
    
    if (sql.isBlank()) return false  // Check if SQL is empty
    if (BLOCKED.containsMatchIn(sql)) return false  // Check if SQL query contains dangerous commands

    // Preprocess BigQuery-specific syntax that JSQLParser does not support
    val preprocessed = sql
        .replace(Regex("--[^\n]*"), "")                        // strip line comments
        .replace(Regex("\\bQUALIFY\\b.*"), "")             // strip QUALIFY clauses (BigQuery window filter)
        .replace(Regex(",\\s*(SECOND|MINUTE|HOUR)\\s*\\)"), ", 1)") // normalize time-unit keyword args

    return try {
        // Try formatting/parsing to catch syntax error
        CCJSqlParserManager().parse(StringReader(preprocessed))
        true
    } catch (e: JSQLParserException) {
        false
    }
}

fun extractSqlFromResponse(response: String): String {
    val codeBlock = Regex("```(?:sql)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        .find(response)?.groupValues?.get(1)?.trim()
    return codeBlock ?: response.trim()
}