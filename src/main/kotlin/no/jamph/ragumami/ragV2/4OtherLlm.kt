package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQuerySchemaProvider

class OtherLlm(
    private val ollamaClient: OllamaClient,
    private val schemaProvider: BigQuerySchemaProvider
) {
    suspend fun generateSql(
        userPrompt: String,
        siteId: String,
        urlPath: String
    ): String {
        val schema = schemaProvider.getSchemaContext()
        
        return tryCatchRetry(3, "Error 10002") {
            val prompt = buildPrompt(userPrompt, siteId, urlPath, schema)
            val response = ollamaClient.generate(prompt)
            extractSql(response)
        }
    }
    
    private fun buildPrompt(
        userPrompt: String,
        siteId: String,
        urlPath: String,
        schema: String
    ): String = """
        You are a BigQuery SQL expert for Umami analytics data.
        
        BigQuery Schema:
        $schema
        
        User Question: $userPrompt
        
        Context:
        - Website ID: $siteId
        - URL Path: $urlPath
        
        Generate a valid BigQuery SQL query. Include the website_id filter.
        Return ONLY the SQL query, no explanations.
    """.trimIndent()
    
    private fun extractSql(response: String): String? {
        val trimmed = response.trim()
        
        val codeBlockRegex = Regex("```(?:sql)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = codeBlockRegex.find(trimmed)
        
        if (match != null) {
            return match.groupValues[1].trim()
        }
        
        if (trimmed.uppercase().contains("SELECT")) {
            return trimmed
        }
        
        return null
    }
}


// Error Codes Reference:
// 10002: The model did not return valid SQL (no SELECT statement found).
//        Possible causes: LLM returned invalid SQL, no SELECT statement found, or malformed response.
//        Resolution: Check LLM service availability, review schema context, or simplify user prompt.
