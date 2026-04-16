package no.jamph.llmValidation

import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.Routes
import no.jamph.ragumami.umami.UmamiRAGService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

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
): Double = runBlocking { supervisorScope {
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
        // Testen utgår fordi søkefunksjonen ikke trackes. Jeg beholder den her fordi den ble brukt tidligere og er relevant for rapporten. tatt ut av test v2
        //TestCase(
        //    question = "Hvor navigerer brukere etter å ha søkt på siden i 2025?",
        //    url = "https://aksel.nav.no",
        //    rules = listOf(
        //        Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
        //        Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
        //        Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
        //        Rule("contains '2025'") { sql -> sql.contains("2025") },
        //        Rule("contains event_name") { sql -> sql.lowercase().contains("event_name") },
        //        Rule("contains 'sok'/'søk'/search") { sql -> Regex("'sok'|'søk'|search", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
        //        Rule("contains ROW_NUMBER or JOIN") { sql -> sql.uppercase().contains("ROW_NUMBER") || sql.uppercase().contains("JOIN") },
        //    )
        TestCase(// Spørsmålet kommer fra brukerundersøkelse test v2
            question = "Er det noen sider om ki på aksel",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains 'ki' or 'kunstig intelligens'") { sql -> Regex("'ki'|'kunstig intelligens'", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
                Rule("contains url_path or path column") { sql -> sql.lowercase().contains("url_path") || sql.lowercase().contains("path") || sql.lowercase().contains("page") },
            )
        ),
        TestCase(//  Spørsmålet kommer fra brukerundersøkelse test v2  // Default burde klare å svare på dette.
            question = "Hvor mange kommer fra 404 og hvor kommer de fra?",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '404'") { sql -> sql.contains("404") },
                Rule("contains referrer_domain") { sql -> sql.lowercase().contains("referrer_domain") },
            )
        ),
        // TestCase( //  Spørsmålet kommer fra brukerundersøkelse test v2 - Denne kan være umulig å gjøre ut ifra hva som spores på aksel.
        //    question = "bruker brukerne mus, keyboard og assistert teknologi. på accordion",
        //    url = "https://aksel.nav.no",
        //    rules = listOf(
        //        Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
        //        Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
        //        Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
        //        Rule("contains 'accordion'") { sql -> sql.lowercase().contains("accordion") },
        //    )
        //),
        
        TestCase( // Spørsmålet kommer fra brukertest v2 // Eget rag skjema er innført for å kunne hådtere denne typen spørsmål bedre, så denne testen er litt mer fleksibel på hva slags SQL som kommer
            question = "oversikt fra / til /komponenter/ikoner ",//oversikt, fra start til fullført søknad utloggede og innloggede sider.,
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
            )
        ),

        TestCase( // Spørsmålet kommer fra brukertest v2 // Denne er ikke implementert og det forventes at modellen feiler. Hvis den bruker journey, kan det telle positivt.
            question = "hvor mange går gjennomsnittlig i måneden fra forsiden til linkcardkomponentsiden?",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '/' and '/komponenter/core/linkcard'") { sql -> sql.lowercase().contains("forside") && sql.lowercase().contains("linkcardkomponentsiden") },
            )
        ),
        
        TestCase( // Spørsmålet kommer fra brukertest v2 // Denne er ikke implementert og det forventes at modellen feiler. Hvis den bruker journey, kan det telle positivt.
            question = "hvilken side brukes mest pr dag? Bortsett fra forsiden",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains 'not', 'forside'") { sql -> sql.lowercase().contains("not") && sql.lowercase().contains("forside") && sql.lowercase().contains("most used") },
            )
        ),

        TestCase(// Spørsmålet kommer fra brukertest v2 // Denne er ikke implementert og det forventes at modellen feiler. Hvis den bruker journey, kan det telle positivt.
            question = "Hva er den mest vanlige rekkeføgen og navigere til komponenter", 
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains '/komponenter/core'") { sql -> sql.lowercase().contains("/komponenter/core") },
            )
        ),
        
        TestCase(// Spørsmålet kommer fra brukertest v2
            question = "Hvor mye tid bruker folk på siden",
            url = "https://aksel.nav.no",
            rules = listOf(
                Rule("valid SQL syntax") { sql -> isSqlQueryValid(sql) },
                Rule("contains fagtorsdag project") { sql -> sql.contains("fagtorsdag-prod-81a6.umami_student") },
                Rule("contains website_id") { sql -> sql.contains(AKSEL_ID) },
                Rule("contains 'time on page' or 'session duration'") { sql -> Regex("'time on page'|'session duration'", RegexOption.IGNORE_CASE).containsMatchIn(sql) },
            )
        ),

        // for versjon 2: Spørsmål fra brukertest v2 som ble vurdert.
        //     question = "Er det noen sider om ki på aksel",
        //     question = "Hvor mange besøker 404 og hvor kommer de fra?"
        //     question = "bruker brukerne mus, keyboard og assistert teknologi. på accordion"
        //     question = "oversikt, fra start til fullført søknad. utloggede og innloggede sider"
        //     question = "hvor mange går gjennpomsnittlig i måneden fra forsiden til linkcardkomponentsiden?"
        //     question = "hvilken side brukes mest pr dag? Bortsett fra forsiden"
        //     question = "hvor mange laster ned svg ikon på siden?"
        //     question = "Hva er den mest vanlige rekkeføgen og navigere til /komponenter?" (mulig vi trenger skjema her)
        //     question = "prosent av besøkende på siden som får 404?"
        //     question = "Hvor mange søker WCAG eller universiell utforming på aksel?" (trenger muligens skjema for søkeresultater)
        //     question = "Har mange søkt på "produkt" type rullestol eller hjelpemiddel"
        //     question = "Hvor mye tid bruker folk på siden"
        //     question = "er apple mer populært enn windows? på siden" (kanskje liten skjema endring)
        //
    )

    var correctCount = 0
    testCases.forEachIndexed { index, testCase ->
        debugLog("  SQL test ${index + 1}/${testCases.size}: ${testCase.question}")
        debugLog("  URL: ${testCase.url}")
        try {
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
        } catch (e: Exception) {
            debugLog("  → FAIL (${e.message})")
        }
    }

    correctCount.toDouble() / testCases.size
} }