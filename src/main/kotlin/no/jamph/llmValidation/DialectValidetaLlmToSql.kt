// function to validate llm to sql with dialects
fun DialectValidetaLlmToSql():
    # Values
    val llmQueriesSidevisninger2025 = listOf(
        "Kor mange brukarar har besøkt sida i 2025?",
        "How many users visited the site in 2025?",
        "Hvor mange brukere har besøkt siden i 2025?",
        "Hvor mange users har visited siden i 2025?"
        "ke mange brukere he besøkt sida sia 2025?",
        "hvor mange folk har vært på sia i 2025?",
        "kor mange folk har gått innom sia i hele 2025?")

    val llmQueriesMestBesøkteUndersider2025 = listOf(
        "Hvilke er de mest besøkte undersidene i 2025?",
        "What are the most visited subpages in 2025?",
        "Hvor dro bruker etter denne siden i 2025?",
        "hvor dro folk etter denne siden i 2025?",
        "hvor dro folk etter denne siden i hele 2025?",
        "Kva gjorde brukarane etter denne sida i 2025?",
        "Kva gjorde folk etter denne sida i 2025?")

    val sqlAnswerSidevisninger2025 = ""
        