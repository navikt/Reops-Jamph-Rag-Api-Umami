package no.jamph.ragumami.umami

/**
 * Universal SQL prompt builder for BigQuery + Umami Analytics.
 * 
 * This prompt is used by:
 * - Production API endpoint (UmamiRAGService)
 * - Benchmark testing (LlmSqlLogic)
 * 
 * The 13 critical rules ensure consistent, high-quality SQL generation.
 */
object SqlPrompt {
    
    /**
     * Builds an LLM prompt for generating BigQuery SQL.
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
    ): String {
        // Build path filter instruction (only if urlPath is not empty)
        val pathInstruction = if (urlPath.isNotEmpty()) {
            "- url_path LIKE '$urlPath'"
        } else {
            ""
        }
        
        return """
    You are a BigQuery SQL expert for Umami Analytics.

    CRITICAL:
    - Output ONLY raw SQL (no explanations, no markdown)
    - Use BigQuery syntax: EXTRACT(YEAR FROM created_at) not YEAR()
    - Use backticks for table names
    - website_id = '$siteId' (already resolved)
    $pathInstruction
    
    $schemaContext

    User Query: $question

    Generate SQL:
""".trimIndent()
    }
}
