package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQuerySchemaProvider
import no.jamph.bigquery.urlToSiteIdAndPath

data class QueryTypeResult(
    val queryType: String,
    val siteId: String,
    val urlPath: String,
    val userPrompt: String,
    val rawLlmResponse: String? = null
)


class PickASqlQuestionTypeLlm(
    private val ollamaClient: OllamaClient,
    private val bigQueryService: BigQuerySchemaProvider
) {
    companion object {
        private val VALID_QUERY_TYPES = setOf(
            "linear",
            "rankings",
            "search",
            "default",
            "journey"
        )
    }
    

    suspend fun classifyQueryType(
        userPrompt: String,
        url: String,
        pathOperator: String = "starts-with",
        captureDebugInfo: Boolean = false
    ): QueryTypeResult {
        val websites = bigQueryService.getWebsites()
        val parsedUrl = urlToSiteIdAndPath(url, websites, pathOperator)
        
        var rawResponse: String? = null
        val extractedType = tryCatchRetry(3, "Error 10000") {
            val response = ollamaClient.generateConstrained(
                prompt = buildClassificationPrompt(userPrompt),
                temperature = 0.0,
                maxTokens = 20
            )
            if (captureDebugInfo) rawResponse = response
            extractQueryType(response)
        }
        
        return QueryTypeResult(
            queryType = extractedType,
            siteId = parsedUrl.siteId,
            urlPath = parsedUrl.urlPath,
            userPrompt = userPrompt,
            rawLlmResponse = if (captureDebugInfo) rawResponse else null
        )
    }
    

    private fun buildClassificationPrompt(userPrompt: String): String = """
        You are a SQL query classifier. Analyze the user's question and determine which type of query template to use.
        
        Available query types:
        - linear: ONLY For explicit TREND/REGRESSION analysis. Examples: "Hvordan endrer trafikken seg","gjør en trendanalyse"
        - rankings: ONLY For queries that ask for top/bottom results. Examples: "top pages", "most visited pages", "least popular pages"
        - search: ONLY For queries asking how many users searched for a SPECIFIC term. Examples: "hvor mange søker på accessibility", "hvor mange søker etter universell utforming", "søkeantall for ki"
        - default: Everything else. This can handle a wide variety of questions.
        - journey: ONLY For queries that ask about user journeys or sequences of page visits. Examples: "hvor mange går fra startsiden til produktsiden", "brukerreise fra blogg til kontakt oss", "hvor mange går fra A til B"
        User question: $userPrompt
        
        Output ONLY one word: linear, rankings, search, journey, or default. Do NOT return any explanations or additional text. No symbols.
    """.trimIndent()
    

    private fun extractQueryType(response: String): String {
        val cleaned = response.trim().lowercase()
            .replace("\"", "")
            .replace("'", "")
            .replace(":", "")
            .trim()
        
        return if (cleaned in VALID_QUERY_TYPES) {
            cleaned
        } else {
            VALID_QUERY_TYPES.find { cleaned.contains(it) } ?: "default"
        }
    }
}


// Error Codes Reference:
// 10000: The model did not answer with a valid query type word (linear/rankings/search/default).
//        Possible causes: LLM returned invalid response, unclear output, timeout, or model unavailable.
//        Resolution: Check LLM service status, review prompt clarity, or increase retry attempts.
 