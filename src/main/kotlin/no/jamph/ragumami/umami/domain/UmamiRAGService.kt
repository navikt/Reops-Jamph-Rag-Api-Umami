package no.jamph.ragumami.umami.domain

import no.jamph.ragumami.core.rag.RAGOrchestrator
import no.jamph.ragumami.core.rag.QueryContext
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQuerySchemaService

class UmamiRAGService(
    private val ollamaClient: OllamaClient,
    private val bigQueryService: BigQuerySchemaService? = null
) {
    suspend fun chat(message: String): String {
        val prompt = """
        Du er en hjelpsom AI-assistent for Umami Analytics.
        Brukerens spørsmål: $message
        
        Gi et kort og nyttig svar.
        """.trimIndent()
        
        return ollamaClient.generate(prompt)
    }
    
    suspend fun generateSQL(naturalLanguageQuery: String): String {
        // Get schema context from BigQuery if available, otherwise use fallback
        val schemaContext = if (bigQueryService != null) {
            try {
                bigQueryService.getSchemaContext()
            } catch (e: Exception) {
                getFallbackSchemaContext()
            }
        } else {
            getFallbackSchemaContext()
        }
        
        val prompt = buildSQLPrompt(naturalLanguageQuery, schemaContext)
        val raw = ollamaClient.generate(prompt)
        return extractSql(raw)
    }

    private fun extractSql(response: String): String {
        val codeBlock = Regex("```(?:sql)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.get(1)?.trim()
        return codeBlock ?: response.trim()
    }
    
    private fun getFallbackSchemaContext(): String {
        return """
        === BIGQUERY DATABASE SCHEMA ===
        Note: BigQuery not configured. Using fallback schema.
        
        === AVAILABLE WEBSITES ===
        - Aksel (example)
        
        === DATABASE TABLES ===
        
        Table: `project.dataset.public_website`
        Columns:
          - website_id (STRING, REQUIRED) - Unique identifier for website
          - name (STRING, NULLABLE) - Website name
          - domain (STRING, NULLABLE) - Website domain
        
        Table: `project.dataset.event`
        Columns:
          - website_id (STRING, REQUIRED) - Reference to website
          - event_name (STRING, NULLABLE) - Event name
          - created_at (TIMESTAMP, REQUIRED) - Event timestamp
          - session_id (STRING, NULLABLE) - Session identifier
          - url_path (STRING, NULLABLE) - URL path
        
        === QUERY INSTRUCTIONS ===
        - Always use fully qualified table names with backticks
        - Filter by website_id when querying event tables
        - Match website names to appropriate website_id values
        """.trimIndent()
    }
    
    private fun buildSQLPrompt(query: String, schema: String): String {
        return """
        You are a BigQuery SQL expert for Umami Analytics.
        
        IMPORTANT INSTRUCTIONS:
        - Generate ONLY valid BigQuery SQL, no explanations or markdown
        - Use backticks (`) for table names
        - Always use fully qualified table names as shown in schema
        - When user mentions a website (like "Aksel"), find the matching website_id from the Available Websites list
        - Add WHERE website_id = '<matched-id>' when querying event or event_data tables
        - Return only the SQL query, nothing else
        
        $schema
        
        User Query: $query
        
        Generate the BigQuery SQL query:
        """.trimIndent()
    }
}