package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQuerySchemaProvider
import no.jamph.bigquery.urlToSiteIdAndPath

data class QueryTypeResult(
    val queryType: String,
    val siteId: String,
    val urlPath: String,
    val userPrompt: String
)


class PickASqlQuestionTypeLlm(
    private val ollamaClient: OllamaClient,
    private val bigQueryService: BigQuerySchemaProvider
) {
    companion object {
        private val VALID_QUERY_TYPES = setOf(
            "linear",
            "rankings",
            "default"
        )
    }
    

    suspend fun classifyQueryType(
        userPrompt: String,
        url: String,
        pathOperator: String = "starts-with"
    ): QueryTypeResult {
        val websites = bigQueryService.getWebsites()
        val parsedUrl = urlToSiteIdAndPath(url, websites, pathOperator)
        
        val extractedType = tryCatchRetry(3, "Query classification failed") {
            val response = ollamaClient.generateConstrained(
                prompt = buildClassificationPrompt(userPrompt),
                temperature = 0.0,
                maxTokens = 20
            )
            extractQueryType(response)
        }
        
        return QueryTypeResult(
            queryType = extractedType,
            siteId = parsedUrl.siteId,
            urlPath = parsedUrl.urlPath,
            userPrompt = userPrompt
        )
    }
    

    private fun buildClassificationPrompt(userPrompt: String): String = """
        You are a SQL query classifier. Analyze the user's question and determine which type of query template to use.
        
        Available query types:
        - linear: Trend analysis or regression. Examples: "how is traffic changing", "trend analysis", "growth rate"
        - rankings: Queries that ask for top/bottom results. Examples: "top pages", "most visited pages", "least popular pages"
        - default: Any other type of query
        
        User question: $userPrompt
        
        Output ONLY the query type (one word):
    """.trimIndent()
    

    private fun extractQueryType(response: String): String? {
        val cleaned = response.trim().lowercase()
            .replace("\"", "")
            .replace("'", "")
            .replace(":", "")
            .trim()
        
        return if (cleaned in VALID_QUERY_TYPES) cleaned
        else VALID_QUERY_TYPES.find { cleaned.contains(it) }
    }
}
