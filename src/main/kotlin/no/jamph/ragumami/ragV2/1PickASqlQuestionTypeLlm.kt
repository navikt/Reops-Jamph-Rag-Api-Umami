package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQuerySchemaProvider
import no.jamph.bigquery.urlToSiteIdAndPath
import org.slf4j.LoggerFactory

data class QueryTypeResult(
    val queryType: String,
    val siteId: String,
    val urlPath: String,
    val userPrompt: String
)


class QueryTypeClassifier(
    private val ollamaClient: OllamaClient,
    private val bigQueryService: BigQuerySchemaProvider
) {
    private val logger = LoggerFactory.getLogger(QueryTypeClassifier::class.java)
    
    companion object {
        private const val MAX_RETRIES = 3
        
        // Valid query types that the LLM can choose from
        private val VALID_QUERY_TYPES = setOf(
            "unique_users",
            "rankings",
            "pageviews",
            "linear",
            "actions",
            "search_terms",
            "error",
            "user_path",
            "others"

        )
    }
    

    suspend fun classifyQueryType(
        userPrompt: String,
        url: String,
        pathOperator: String = "starts-with"
    ): QueryTypeResult {
        val websites = bigQueryService.getWebsites()
        val parsedUrl = urlToSiteIdAndPath(url, websites, pathOperator)
        
        logger.info("Classifying query type for prompt: '{}', url: '{}'", userPrompt, url)
        
        for (attempt in 1..MAX_RETRIES) {
            try {
                val classificationPrompt = buildClassificationPrompt(userPrompt, attempt)
                val response = ollamaClient.generateConstrained(
                    prompt = classificationPrompt,
                    temperature = 0.0,
                    maxTokens = 20
                )
                
                val extractedType = extractQueryType(response)
                
                if (extractedType != null) {
                    logger.info("Successfully classified query as type '{}' on attempt {}", extractedType, attempt)
                    return QueryTypeResult(
                        queryType = extractedType,
                        siteId = parsedUrl.siteId,
                        urlPath = parsedUrl.urlPath,
                        userPrompt = userPrompt
                    )
                }
                
                logger.warn("Attempt {}/{}: Could not extract valid query type from response: '{}'", 
                    attempt, MAX_RETRIES, response)
                
            } catch (e: Exception) {
                logger.error("Attempt {}/{}: Error during classification", attempt, MAX_RETRIES, e)
                if (attempt == MAX_RETRIES) throw e
            }
        }
        
        throw IllegalStateException(
            "Failed to classify query type after $MAX_RETRIES attempts for prompt: '$userPrompt'"
        )
    }
    

    private fun buildClassificationPrompt(userPrompt: String, attempt: Int): String {
        return if (attempt == 1) {
            """
            You are a SQL query classifier. Analyze the user's question and determine which type of query template to use.
            
            Available query types:
            - pageviews: Specific pageview metrics. Examples: "pageview statistics", "page views breakdown"
            - rankings: Queries that ask for top/bottom results. Examples: "top pages", "most visited pages", "least popular pages"
            - unique_users: User-specific metrics. Examples: "unique visitors", "distinct users"
            - linear: Trend analysis or regression. Examples: "how is traffic changing", "trend analysis", "growth rate"
            - actions: User actions and events. Examples: "button clicks", "form submissions", "user interactions"
            - search_terms: Search query analysis. Examples: "what are users searching for", "popular search terms"
            - error: Error analysis. Examples: "404 errors", "error rates", "broken links"
            - user_path: User navigation paths. Examples: "common user paths", "navigation flow", "user journeys"
            - others: Any other type of query that doesn't fit the above categories.
            
            User question: $userPrompt
            
            Output ONLY the query type (one word):
            """.trimIndent()
        } else {
            """
            OUTPUT EXACTLY ONE OF THESE WORDS:
            pageviews
            rankings
            linear
            unique_users
            actions
            search_terms
            error
            user_path
            others
            
            NO explanations. NO quotes. NO punctuation. NO other text. Just the word.
            
            Question: $userPrompt
            
            Type:
            """.trimIndent()
        }
    }
    

    private fun extractQueryType(response: String): String? {
        val cleaned = response.trim()
            .lowercase()
            .replace("\"", "")
            .replace("'", "")
            .replace(":", "")
            .trim()

        if (cleaned in VALID_QUERY_TYPES) {
            return cleaned
        }
        

        return VALID_QUERY_TYPES.find { type -> cleaned.contains(type) }
    }
}
