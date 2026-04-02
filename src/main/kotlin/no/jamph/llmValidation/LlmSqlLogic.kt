package no.jamph.llmValidation

import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.Routes
import kotlinx.coroutines.runBlocking

private const val AKSEL_ID = "fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1"

private data class TestCase(
    val question: String,
    val url: String,
    val rules: List<(String) -> Boolean>
)

private fun isCorrect(sql: String, rules: List<(String) -> Boolean>): Boolean =
    isSqlQueryValid(sql) && rules.all { it(sql) }

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
    val schema = BigQuerySchemaServiceMock().getSchemaContext()

    val testCases = listOf(
        TestCase(
            question = "Daglige sidevisninger i 2025",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> sql.uppercase().contains("EVENT_TYPE") },
                { sql -> sql.uppercase().contains("GROUP BY") },
                { sql -> sql.contains("FORMAT_TIMESTAMP") || sql.contains("DATE(") || sql.uppercase().contains("EXTRACT(DAY") },
            )
        ),
        TestCase(
            question = "Topp 12 mest besøkte undersider i 2025",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> sql.lowercase().contains("url_path") },
                { sql -> sql.uppercase().contains("GROUP BY") },
                { sql -> sql.uppercase().contains("ORDER BY") },
                { sql -> Regex("LIMIT\\s+12", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
            )
        ),
        TestCase(
            question = "Sidevisninger per måned i 2025",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> sql.uppercase().contains("EXTRACT(MONTH") || sql.lowercase().contains("month") },
                { sql -> sql.uppercase().contains("GROUP BY") },
            )
        ),
        TestCase(
            question = "Trafikkilder i november 2025",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> Regex("= ?11\\b|november", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                { sql -> sql.lowercase().contains("referrer_domain") },
                { sql -> sql.uppercase().contains("GROUP BY") },
            )
        ),
        TestCase(
            question = "Eksterne nettsider besøkende kommer fra",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.lowercase().contains("referrer_domain") },
                { sql -> sql.lowercase().contains("session_id") },
            )
        ),
        TestCase(
            question = "Lineær regresjon: trend i daglige sidevisninger",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> sql.uppercase().contains("WITH") },
                { sql -> sql.uppercase().contains("COVAR_SAMP") || sql.uppercase().contains("VAR_SAMP") || sql.lowercase().contains("slope") },
            )
        ),
        TestCase(
            question = "Nøkkeltall: handlinger, navigering og frafall",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> sql.lowercase().contains("session_id") },
                { sql -> sql.uppercase().contains("UNION ALL") || sql.uppercase().contains("COUNTIF") },
            )
        ),
        TestCase(
            question = "Hvilket operativsystem bruker brukerne?",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> Regex("\\bos\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                { sql -> sql.lowercase().contains("session") },
            )
        ),
        TestCase(
            question = "Hvor navigerer brukere etter å ha søkt på siden?",
            url = "aksel.nav.no",
            rules = listOf(
                { sql -> sql.contains(AKSEL_ID) },
                { sql -> sql.contains("2025") },
                { sql -> sql.lowercase().contains("event_name") },
                { sql -> Regex("'sok'|'søk'|search", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                { sql -> sql.uppercase().contains("ROW_NUMBER") || sql.uppercase().contains("JOIN") },
            )
        ),
    )

    var correctCount = 0
    testCases.forEachIndexed { index, testCase ->
        debugLog("  SQL test ${index + 1}/${testCases.size}: ${testCase.question}")
        val raw = generateFn(buildLlmSqlPrompt(testCase.question, testCase.url, schema))
        val generatedSql = extractSqlFromResponse(raw)
        debugLog("  Generated SQL: ${generatedSql.replace("\n", " ")}")
        val passed = isCorrect(generatedSql, testCase.rules)
        if (passed) correctCount++
        debugLog("  → ${if (passed) "PASS ✓" else "FAIL ✗"}")
    }

    correctCount.toDouble() / testCases.size
}

private fun buildLlmSqlPrompt(question: String, url: String, schemaContext: String): String = """
    You are a BigQuery SQL expert for Umami Analytics.

    IMPORTANT INSTRUCTIONS:
    - Generate ONLY valid BigQuery SQL, no explanations or markdown
    - Use backticks (`) for table names
    - Always use fully qualified table names as shown in schema
    - The user is viewing the website: ${'$'}url — use this domain to find the matching website_id from the Available Websites list
    - Add WHERE website_id = '<matched-id>' when querying event or event_data tables
    - Return only the SQL query, nothing else

    $schemaContext

    User Query: $question

    Generate the BigQuery SQL query:
""".trimIndent()