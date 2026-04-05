package no.jamph.llmValidation

import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.Routes
import no.jamph.ragumami.umami.UmamiRAGService
import kotlinx.coroutines.runBlocking

private const val AKSEL_ID = "fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1"

private data class Rule(val name: String, val check: (String) -> Boolean)

private data class TestCase(
    val question: String,
    val url: String,
    val rules: List<Rule>
)

private fun isCorrect(sql: String, rules: List<Rule>): Boolean =
    rules.all { it.check(sql) }

fun LlmSqlLogic(
    modelName: String,
    generateFn: suspend (String) -> String = { prompt ->
        OllamaClient(
            baseUrl = System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl,
            model = modelName
        ).generate(prompt)
    },
    debugLog: (String) -> Unit = ::println
): Double = runBlocking {
    val schemaService = BigQuerySchemaServiceMock()
    val websites = schemaService.getWebsites()
    
    val ollamaClient = OllamaClient(
        baseUrl = System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl,
        model = modelName
    )
    val ragService = UmamiRAGService(ollamaClient, schemaService)

    val testCases = listOf(
        TestCase(
            question = "Hvor mange sidevisninger er det per dag i 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains EVENT_TYPE") { sql -> sql.uppercase().contains("EVENT_TYPE") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
                Rule("contains FORMAT_TIMESTAMP/DATE()/EXTRACT(DAY)/DATE_TRUNC") { sql -> sql.contains("FORMAT_TIMESTAMP") || sql.contains("DATE(") || sql.uppercase().contains("EXTRACT(DAY") || sql.uppercase().contains("DATE_TRUNC") },
            )
        ),
        TestCase(
            question = "Topp 12 mest besøkte undersider i 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains url_path or path column") { sql -> sql.lowercase().contains("url_path") || sql.lowercase().contains("path") || sql.lowercase().contains("page") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
                Rule("contains ORDER BY") { sql -> sql.uppercase().contains("ORDER BY") },
                Rule("contains LIMIT 12") { sql -> Regex("LIMIT\\s+12", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
            )
        ),
        TestCase(
            question = "Hvor mange sidevisninger er det per måned i 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains EXTRACT(MONTH)/month") { sql -> sql.uppercase().contains("EXTRACT(MONTH") || sql.lowercase().contains("month") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
            )
        ),
        TestCase(
            question = "Hva er topp 15 trafikkilder i november 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains month=11 or 'november'") { sql -> Regex("= ?11\\b|november", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                Rule("contains referrer_domain") { sql -> sql.lowercase().contains("referrer_domain") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
            )
        ),
        TestCase(
            question = "Hvilke nettsider kommer besøkere til siden fra",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains referrer_domain") { sql -> sql.lowercase().contains("referrer_domain") },
            )
        ),
        TestCase(
            question = "Lineær regresjon: trend i daglige sidevisninger",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains WITH (CTE)") { sql -> sql.uppercase().contains("WITH") },
                Rule("contains COVAR_SAMP/VAR_SAMP/slope") { sql -> sql.uppercase().contains("COVAR_SAMP") || sql.uppercase().contains("VAR_SAMP") || sql.lowercase().contains("slope") },
            )
        ),
        TestCase(
            question = "Kan jeg få fire tall for katgoriene. Unike besøkende, Utførte handlinger, Navigering uten handling, Forlot nettstedet",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains session_id") { sql -> sql.lowercase().contains("session_id") },
                Rule("contains event_type") { sql -> sql.uppercase().contains("EVENT_TYPE") },
            )
        ),
        TestCase(
            question = "Hvilket operativsystem bruker brukerne i 2025?",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains 'os'") { sql -> Regex("\\bos\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                Rule("contains session") { sql -> sql.lowercase().contains("session") },
            )
        ),
        TestCase(
            question = "Hvor navigerer brukere etter å ha søkt på siden i 2025?",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains event_name") { sql -> sql.lowercase().contains("event_name") },
                Rule("contains 'sok'/'søk'/search") { sql -> Regex("'sok'|'søk'|search", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                Rule("contains ROW_NUMBER or JOIN") { sql -> sql.uppercase().contains("ROW_NUMBER") || sql.uppercase().contains("JOIN") },
            )
        ),
    )

    var correctCount = 0
    testCases.forEachIndexed { index, testCase ->
        debugLog("  SQL test ${index + 1}/${testCases.size}: ${testCase.question}")
        debugLog("  URL: ${testCase.url}")
        
        val generatedSql = ragService.generateSQL(testCase.question, testCase.url, websites)
        debugLog("  Generated SQL: ${generatedSql.replace("\n", " ")}")
        
        var rulesPassed = 0
        testCase.rules.forEach { rule ->
            val ok = rule.check(generatedSql)
            if (ok) rulesPassed++
            debugLog("    ${if (ok) "✓" else "✗"} ${rule.name}")
        }
        val passed = rulesPassed == testCase.rules.size
        if (passed) correctCount++
        debugLog("  → ${if (passed) "PASS" else "FAIL"} ($rulesPassed/${testCase.rules.size} rules)")
    }

    correctCount.toDouble() / testCases.size
}