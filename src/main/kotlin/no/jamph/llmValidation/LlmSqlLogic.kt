package no.jamph.llmValidation

import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.bigquery.urlToSiteIdAndPath
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.Routes
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
    val schema = schemaService.getSchemaContext()
    val websites = schemaService.getWebsites()

    val testCases = listOf(
        TestCase(
            question = "Daglige sidevisninger i 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains EVENT_TYPE") { sql -> sql.uppercase().contains("EVENT_TYPE") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
                Rule("contains FORMAT_TIMESTAMP/DATE()/EXTRACT(DAY") { sql -> sql.contains("FORMAT_TIMESTAMP") || sql.contains("DATE(") || sql.uppercase().contains("EXTRACT(DAY") },
            )
        ),
        TestCase(
            question = "Topp 12 mest besøkte undersider i 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains url_path") { sql -> sql.lowercase().contains("url_path") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
                Rule("contains ORDER BY") { sql -> sql.uppercase().contains("ORDER BY") },
                Rule("contains LIMIT 12") { sql -> Regex("LIMIT\\s+12", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
            )
        ),
        TestCase(
            question = "Sidevisninger per måned i 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains EXTRACT(MONTH)/month") { sql -> sql.uppercase().contains("EXTRACT(MONTH") || sql.lowercase().contains("month") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
            )
        ),
        TestCase(
            question = "Trafikkilder i november 2025",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains month=11 or 'november'") { sql -> Regex("= ?11\\b|november", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                Rule("contains referrer_domain") { sql -> sql.lowercase().contains("referrer_domain") },
                Rule("contains GROUP BY") { sql -> sql.uppercase().contains("GROUP BY") },
            )
        ),
        TestCase(
            question = "Eksterne nettsider besøkende kommer fra",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains referrer_domain") { sql -> sql.lowercase().contains("referrer_domain") },
                Rule("contains session_id") { sql -> sql.lowercase().contains("session_id") },
            )
        ),
        TestCase(
            question = "Lineær regresjon: trend i daglige sidevisninger",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains WITH (CTE)") { sql -> sql.uppercase().contains("WITH") },
                Rule("contains COVAR_SAMP/VAR_SAMP/slope") { sql -> sql.uppercase().contains("COVAR_SAMP") || sql.uppercase().contains("VAR_SAMP") || sql.lowercase().contains("slope") },
            )
        ),
        TestCase(
            question = "Nøkkeltall: handlinger, navigering og frafall",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains session_id") { sql -> sql.lowercase().contains("session_id") },
                Rule("contains UNION ALL or COUNTIF") { sql -> sql.uppercase().contains("UNION ALL") || sql.uppercase().contains("COUNTIF") },
            )
        ),
        TestCase(
            question = "Hvilket operativsystem bruker brukerne?",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '2025'") { sql -> sql.contains("2025") },
                Rule("contains 'os'") { sql -> Regex("\\bos\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                Rule("contains session") { sql -> sql.lowercase().contains("session") },
            )
        ),
        TestCase(
            question = "Hvor navigerer brukere etter å ha søkt på siden?",
            url = "https://aksel.nav.no",
            rules = listOf(
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
        
        // Parse URL to get siteId and urlPath
        val parsed = urlToSiteIdAndPath(testCase.url, websites)
        debugLog("  Resolved: site_id=${parsed.siteId}, url_path=${parsed.urlPath}")
        
        val raw = generateFn(buildLlmSqlPrompt(testCase.question, parsed.siteId, parsed.urlPath, schema))
        val generatedSql = extractSqlFromResponse(raw)
        debugLog("  Generated SQL: ${generatedSql.replace("\n", " ")}")
        
        var rulesPassed = 0
        testCase.rules.forEachIndexed { ruleIndex, rule ->
            // For the first rule (website_id check), inject the actual siteId
            val ok = if (ruleIndex == 0 && rule.name == "contains website_id") {
                generatedSql.contains(parsed.siteId)
            } else {
                rule.check(generatedSql)
            }
            if (ok) rulesPassed++
            debugLog("    ${if (ok) "✓" else "✗"} ${rule.name}")
        }
        val passed = rulesPassed == testCase.rules.size
        if (passed) correctCount++
        debugLog("  → ${if (passed) "PASS" else "FAIL"} ($rulesPassed/${testCase.rules.size} rules)")
    }

    correctCount.toDouble() / testCases.size
}

private fun buildLlmSqlPrompt(question: String, siteId: String, urlPath: String, schemaContext: String): String = """
    You are a BigQuery SQL expert for Umami Analytics.

    CRITICAL REQUIREMENTS:
    1. Output ONLY raw SQL - NO explanations, NO markdown, NO code blocks, NO comments
    2. ALWAYS use fully qualified table names: `fagtorsdag-prod-81a6.umami_student.event` or `fagtorsdag-prod-81a6.umami_student.session`
    3. NEVER use short table names - ALWAYS include `fagtorsdag-prod-81a6.umami_student.`
    4. Use backticks (`) around table names
    5. ALWAYS add WHERE website_id = '$siteId' - this is already resolved.
    6. NEVER JOIN to public_website table - you already have the website_id
    7. NEVER use YEAR(), MONTH(), DAY() functions - use EXTRACT() instead: EXTRACT(YEAR FROM created_at)
    8. If the question mentions a year (e.g., "2025"), ALWAYS add: AND EXTRACT(YEAR FROM created_at) = 2025
    8. If the question mentions a month (e.g., "november"), ALWAYS add: AND EXTRACT(MONTH FROM created_at) = 11
    9. For "sidevisninger" (page views), ALWAYS add: AND event_type = 1
    10. If filtering by page, use: AND url_path LIKE '$urlPath'
    11. "Daglige" (daily) means: SELECT DATE(created_at) AS dag, COUNT(*) ... GROUP BY dag
    12. "Trafikkilder" (traffic sources) = referrer_domain column (where visitors come from)
    13. Always SELECT the columns needed to answer the question, not just COUNT(*)

    $schemaContext

    User Query: $question

    Generate the BigQuery SQL query:
""".trimIndent()