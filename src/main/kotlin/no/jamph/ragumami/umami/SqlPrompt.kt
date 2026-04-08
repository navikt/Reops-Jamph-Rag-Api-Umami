package no.jamph.ragumami.umami

/**
 * Universal SQL prompt builder for BigQuery + Umami Analytics.
 */
object SqlPrompt {
    
    /**
     * Builds an LLM prompt for generating BigQuery SQL (backward compatible signature).
     * 
     * @param question Natural language question from user
     * @param siteId Website ID (already resolved from URL/domain)
     * @param urlPath URL path to filter (can be empty for whole site, or with % for LIKE)
     * @param schemaContext BigQuery schema information (tables, columns)
     * @return Formatted prompt string ready for LLM
     */
    fun buildPrompt(
        question: String,
        siteId: String,
        urlPath: String,
        schemaContext: String
    ): String = buildPrompt(question, siteId, urlPath, schemaContext, "")
    
    /**
     * Builds an LLM prompt for generating BigQuery SQL with optional schema additions.
     * 
     * @param question Natural language question from user
     * @param siteId Website ID (already resolved from URL/domain)
     * @param urlPath URL path to filter (can be empty for whole site, or with % for LIKE)
     * @param schemaContext BigQuery schema information (tables, columns)
     * @param schemaAddition Optional RAG-selected additional context (e.g., linear regression hints)
     * @return Formatted prompt string ready for LLM
     */
    fun buildPrompt(
        question: String,
        siteId: String,
        urlPath: String,
        schemaContext: String,
        schemaAddition: String
    ): String {
        val pathInstruction = if (urlPath.isNotEmpty()) {
            "- url_path LIKE '$urlPath'"
        } else {
            ""
        }
        
        val additionalRules = if (schemaAddition.isNotEmpty()) {
            "\n$schemaAddition"
        } else {
            ""
        }
        
        return """
    You are a BigQuery SQL expert for Umami Analytics.

    User Query: $question

    CRITICAL:
    - Output ONLY raw SQL (no explanations, no markdown)
    - Use BigQuery syntax: EXTRACT(YEAR FROM created_at) not YEAR()
    - Use backticks for table names
    - website_id = '$siteId' (already resolved)
    $pathInstruction$additionalRules
    
    $schemaContext

    User Query: $question

    Generate SQL:
""".trimIndent()
    }
}
