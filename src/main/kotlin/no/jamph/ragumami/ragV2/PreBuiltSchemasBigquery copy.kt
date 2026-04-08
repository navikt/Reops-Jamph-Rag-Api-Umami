package no.jamph.ragumami.ragV2

import no.jamph.bigquery.BigQuerySchemaProvider

class PreBuiltSchemasBigquery(
    private val bigQueryService: BigQuerySchemaProvider
) {
    fun getSchemaForType(queryType: String): String {
     */
    fun getSchemaForType(queryType: String): String {

        val fullSchema = bigQueryService.getSchemaContext()
        
           return when (queryType) {
            "default" -> getDefaultSchema(fullSchema)
            "linear" -> getLinearSchema(fullSchema)
            "pageviews" -> getPageviewsSchema(fullSchema)
            "unique_users" -> getUniqueUsersSchema(fullSchema)
            "actions" -> getActionsSchema(fullSchema)
            "search_terms" -> getSearchTermsSchema(fullSchema)
            "error" -> getErrorSchema(fullSchema)
            else -> fullSchema // Fallback to full schema
        }
    }

    private fun getDefaultSchema(fullSchema: String): String {
        return """
        === RELEVANT TABLES FOR GENERAL QUERIES ===
        
        Main tables:
        - session: Contains visitor session data (session_id, website_id, created_at, etc.)
        - event: Contains page view events (event_id, session_id, url_path, created_at, etc.)
        - website: Contains website metadata (website_id, name, domain)
        
        $fullSchema
        """.trimIndent()
    }
    

    private fun getLinearSchema(fullSchema: String): String {
        return """
        === RELEVANT TABLES FOR TREND ANALYSIS ===
        
        Focus on time-series aggregation:
        - session.created_at: Session start time (use for daily/monthly grouping)
        - event.created_at: Event timestamp
        
        $fullSchema
        """.trimIndent()
    }
    

    private fun getPageviewsSchema(fullSchema: String): String {
        return """
        === RELEVANT TABLES FOR PAGEVIEW QUERIES ===
        
        Main table: event
        - url_path: The page path
        - created_at: When the pageview occurred
        - session_id: Link to session data
        
        $fullSchema
        """.trimIndent()
    }
    

    private fun getUniqueUsersSchema(fullSchema: String): String {
        return """
        === RELEVANT TABLES FOR UNIQUE USER QUERIES ===
        
        Main table: session
        - Use COUNT(DISTINCT session_id) for unique visitors
        - visitor_id: For cross-session user tracking (if available)
        
        $fullSchema
        """.trimIndent()
    }
    

    private fun getActionsSchema(fullSchema: String): String {
        return """
        === RELEVANT TABLES FOR ACTION QUERIES ===
        
        Main table: event
        - Look for specific event types or URL patterns
        - Use event metadata for action details
        
        $fullSchema
        """.trimIndent()
    }
    

    private fun getSearchTermsSchema(fullSchema: String): String {
        return """
        === RELEVANT TABLES FOR SEARCH TERM QUERIES ===
        
        Main table: event
        - Extract search terms from url_path or query parameters
        - Look for search-related URL patterns
        
        $fullSchema
        """.trimIndent()
    }
    

    private fun getErrorSchema(fullSchema: String): String {
        return """
        === RELEVANT TABLES FOR ERROR QUERIES ===
        
        Main table: event
        - Look for 404 patterns in url_path
        - Check referrer_path for broken links
        
        $fullSchema
        """.trimIndent()
    }
}
