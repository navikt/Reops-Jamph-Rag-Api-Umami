// Function to validate LLM-to-SQL across dialect variations
fun DialectValidetaLlmToSql(modellName: String) {
    val llmQueriesSidevisninger2025 = listOf(
        "Kor mange brukarar har besøkt sida i 2025?",
        "How many users visited the site in 2025?",
        "Hvor mange brukere har besøkt siden i 2025?",
        "Hvor mange users har visited siden i 2025?",
        "ke mange brukere he besøkt sida sia 2025?",
        "hvor mange folk har vært på sia i 2025?",
        "kor mange folk har gått innom sia i hele 2025?",
    )

    val llmQueriesMestBesokteUndersider2025 = listOf(
        "Hvilke er de mest besøkte undersidene i 2025?",
        "What are the most visited subpages in 2025?",
        "Hvor dro bruker etter denne siden i 2025?",
        "hvor dro folk etter denne siden i 2025?",
        "hvor dro folk etter denne siden i hele 2025?",
        "Kva gjorde brukarane etter denne sida i 2025?",
        "Kva gjorde folk etter denne sida i 2025?",
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

    // Test the LLM's ability to generate correct SQL for both queries
    val countersidevisninger2025 = 0
    for (query in llmQueriesSidevisninger2025) {
        val generatedSql = generateSqlFromLlm(modellName, query)
        val isValid = isSqlQueryValid(generatedSql)
        println("Query: $query")
        println("Generated SQL: $generatedSql")
        println("Is valid SQL: $isValid")
        println()
        if (isValid) {
            countersidevisninger2025++
        }
    }

    val counterMestBesokteUndersider2025 = 0
    for (query in llmQueriesMestBesokteUndersider2025)
    {
        val generatedSql = generateSqlFromLlm(modellName, query)
        val isValid = isSqlQueryValid(generatedSql)
        println("Query: $query")
        println("Generated SQL: $generatedSql")
        println("Is valid SQL: $isValid")
        println()
        if (isValid) {
            counterMestBesokteUndersider2025++
        }
    }

    fun 

}



