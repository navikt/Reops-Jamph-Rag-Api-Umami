package no.jamph.llmValidation

import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.parser.CCJSqlParserManager
import java.io.StringReader

private val BLOCKED = Regex(
    "\\b(DELETE|DROP|TRUNCATE|UPDATE|INSERT|ALTER|MERGE|REPLACE|CREATE)\\b",
    RegexOption.IGNORE_CASE
)

// Function that validates SQL 
fun isSqlQueryValid(sql: String): Boolean {
    
    if (sql.isBlank()) return false  // Check if SQL is empty
    if (BLOCKED.containsMatchIn(sql)) return false  // Check if SQL query contains dangerous commands

    return try {
        // Try formatting/parsing to catch syntax error
        CCJSqlParserManager().parse(StringReader(sql))
        true
    } catch (e: JSQLParserException) {
        false
    }
}