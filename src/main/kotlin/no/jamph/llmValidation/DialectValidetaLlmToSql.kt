package no.jamph.llmValidation

import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.ragumami.core.llm.OllamaClient
import kotlinx.coroutines.runBlocking

// Function to validate LLM-to-SQL across dialect variations
fun DialectValidetaLlmToSql(
    modellName: String,
    generateFn: suspend (String) -> String = { prompt ->
        OllamaClient(baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434", model = modellName).generate(prompt)
    }
): Double {
    return runBlocking {
        val schemaContext = BigQuerySchemaServiceMock().getSchemaContext()

        val llmQueriesSidevisninger2025 = listOf(
            "Kor mange brukarar har besøkt sida aksel i 2025?",
            "How many users visited the site on aksel 2025?",
            "Hvor mange brukere har besøkt siden aksel i 2025?",
            "Hvor mange users har visited siden aksel i 2025?",
            "ke mange brukere he besøkt sida aksel 2025?",
            "hvor mange folk har vært på aksel i 2025?",
            "kor mange folk har gått innom aksel i hele 2025?",
        )

        val llmQueriesMestBesokteUndersider2025 = listOf(
            "Hvilke er de mest besøkte undersidene på aksel i 2025?",
            "What are the most visited subpages on aksel in 2025?",
            "Hvor dro brukere på Aksel etter denne siden i 2025?",
            "hvor dro folk på Aksel etter denne siden i 2025?",
            "hvor dro folk på Aksel etter denne siden i hele 2025?",
            "Kva gjorde brukarane på Aksel etter denne sida i 2025?",
            "Kva gjorde folk etter på å vært Aksel i 2025?",
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

        fun buildPrompt(query: String): String = """
            You are a BigQuery SQL expert for Umami Analytics.

            IMPORTANT INSTRUCTIONS:
            - Generate ONLY valid BigQuery SQL, no explanations or markdown
            - Use backticks (`) for table names
            - Always use fully qualified table names as shown in schema
            - When user mentions a website (like "Aksel"), find the matching website_id from the Available Websites list
            - Add WHERE website_id = '<matched-id>' when querying event or event_data tables
            - Return only the SQL query, nothing else

            $schemaContext

            User Query: $query

            Generate the BigQuery SQL query:
        """.trimIndent()

        val allQueries = llmQueriesSidevisninger2025 + llmQueriesMestBesokteUndersider2025
        var validCount = 0
        for (query in allQueries) {
            val generatedSql = generateFn(buildPrompt(query))
            if (isSqlQueryValid(generatedSql)) validCount++
        }

        validCount.toDouble() / allQueries.size
    }
}
