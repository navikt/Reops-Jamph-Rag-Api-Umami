package no.jamph.llmValidation

import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.Routes
import no.jamph.ragumami.umami.UmamiRAGService
import kotlinx.coroutines.runBlocking

private const val AKSEL_Website_Id = "fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1"

fun DialectValidetaLlmToSql(
    modellName: String,
    generateFn: suspend (String) -> String = { prompt ->
        OllamaClient(baseUrl = System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl, model = modellName).generate(prompt)
    },
    debugLog: (String) -> Unit = ::println
): Double {
    return runBlocking {
        val schemaService = BigQuerySchemaServiceMock()
        val websites = schemaService.getWebsites()
        
        val ollamaClient = OllamaClient(
            baseUrl = System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl,
            model = modellName
        )
        val ragService = UmamiRAGService(ollamaClient, schemaService)

        val llmQueriesSidevisninger2025 = listOf(
            "Kor mange brukarar har besøkt sida i 2025?",
            "How many users visited the site in 2025?",
            "Hvor mange brukere har besøkt siden i 2025?",
            "Hvor mange users har visited siden i 2025?",
            "ke mange brukere he besøkt sida 2025?",
            "hvor mange folk har vært på i 2025?",
            "kor mange folk har gått innom i heile 2025?",
        )

        val llmQueriesMestBesokteUndersider2025 = listOf(
            "Hvilke er de mest besøkte undersidene i 2025?",
            "What are the most visited subpages in 2025?",
            "Hvor dro brukere etter denne siden i 2025?",
            "hvor dro folk etter denne siden i 2025?",
            "hvor dro folk etter denne siden i hele 2025?",
            "Kva gjorde brukarane etter denne sida i 2025?",
            "Kva gjorde folk etter på å vært sida i 2025?",
        )

        val sqlAnswerSidevisninger2025 = """
            [
              {"maaned": 3,  "sidevisninger": 19304},
              {"maaned": 4,  "sidevisninger": 18879},
              {"maaned": 5,  "sidevisninger": 18055},
              {"maaned": 6,  "sidevisninger": 18426},
              {"maaned": 7,  "sidevisninger": 9385},
              {"maaned": 8,  "sidevisninger": 16296},
              {"maaned": 9,  "sidevisninger": 16298},
              {"maaned": 10, "sidevisninger": 15380},
              {"maaned": 11, "sidevisninger": 13833},
              {"maaned": 12, "sidevisninger": 7417}
            ]
        """.trimIndent()

        val sqlAnswerMestBesokteUndersider2025 = """
            [
              {"side":"/","sidevisninger":14565},
              {"side":"/komponenter/core","sidevisninger":3545},
              {"side":"/komponenter/ikoner","sidevisninger":3540},
              {"side":"/grunnleggende/styling/design-tokens","sidevisninger":3403},
              {"side":"/designsystemet","sidevisninger":3300},
              {"side":"/komponenter","sidevisninger":3215},
              {"side":"/god-praksis","sidevisninger":2646},
              {"side":"/komponenter/core/button","sidevisninger":2353},
              {"side":"/komponenter/core/datepicker","sidevisninger":1988},
              {"side":"/komponenter/core/table","sidevisninger":1877},
              {"side":"/komponenter/primitives/box","sidevisninger":1737},
              {"side":"/komponenter/core/actionmenu","sidevisninger":1702}
            ]
        """.trimIndent()

        val url = "https://aksel.nav.no"
        
        fun validateSql(sql: String): Boolean {
            if (!isSqlQueryValid(sql)) return false
            if (!sql.contains("fagtorsdag-prod-81a6.umami_student")) return false
            if (!sql.contains(AKSEL_Website_Id)) return false
            return true
        }

        val allQueries = llmQueriesSidevisninger2025 + llmQueriesMestBesokteUndersider2025
        var validCount = 0
        allQueries.forEachIndexed { index, query ->
            debugLog("  Dialect test ${index + 1}/${allQueries.size}: ${query.take(50)}...")
            
            val generatedSql = ragService.generateSQL(query, url, websites)
            
            debugLog("  Generated SQL: ${generatedSql.replace("\n", " ")}")
            val passed = validateSql(generatedSql)
            if (passed) validCount++
            debugLog("  → ${if (passed) "PASS ✓" else "FAIL ✗"}")
        }

        validCount.toDouble() / allQueries.size
    }
}
