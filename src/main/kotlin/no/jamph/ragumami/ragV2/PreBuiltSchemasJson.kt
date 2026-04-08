package no.jamph.ragumami.ragV2


object PreBuiltSchemasJson {
    
    fun getSchemaForType(queryType: String): String {
        return when (queryType) {
            "default" -> getDefaultSchema()
            "linear" -> getLinearSchema()
            "pageviews" -> getDefaultSchema()
            "unique_users" -> getDefaultSchema()
            "actions" -> getDefaultSchema()
            "search_terms" -> getDefaultSchema()
            "error" -> getDefaultSchema()
            "others" -> getDefaultSchema()
            else -> getDefaultSchema()
        }
    }

    private fun getDefaultSchema(): String {
        return """
        {
            "GROUPING_FIELD": ,
            "AGGREGATION_FUNCTION":,
            "TABLE_NAME": "event",
            "PATH_FILTER": "",
            "ORDER_BY": "metric_value DESC",
            "LIMIT_CLAUSE": "LIMIT 100"
        }
        """.trimIndent()
    }
    
    /**
     * Linear/trend analysis schema.
     */
    private fun getLinearSchema(): String {
        return """
        {
            "TIME_FIELD": "created_at",
            "COUNT_FIELD": "event_id",
            "TABLE_NAME": "event",
            "START_DATE": "2025-01-01",
            "END_DATE": "2025-12-31",
            "PATH_FILTER": ""
        }
        """.trimIndent()
    }
    
    private fun getPageviewsSchema(): String {
        return """
        {
            "TIME_GROUP": "DATE(created_at)",
            "DATE_FILTER": "AND created_at >= '2025-01-01'",
            "PATH_FILTER": "",
            "LIMIT_CLAUSE": "LIMIT 100"
        }
        """.trimIndent()
    }
    
    /**
     * Unique users schema.
     */
    private fun getUniqueUsersSchema(): String {
        return """
        {
            "TIME_GROUP": "DATE(created_at)",
            "DATE_FILTER": "AND created_at >= '2025-01-01'",
            "LIMIT_CLAUSE": "LIMIT 100"
        }
        """.trimIndent()
    }
    

    private fun getActionsSchema(): String {
        return """
        {
            "ACTION_IDENTIFIER": "url_path",
            "DATE_FILTER": "AND created_at >= '2025-01-01'",
            "ACTION_FILTER": "",
            "LIMIT_CLAUSE": "LIMIT 50"
        }
        """.trimIndent()
    }
    

    private fun getSearchTermsSchema(): String {
        return """
        {
            "SEARCH_TERM_EXTRACTION": "REGEXP_EXTRACT(url_path, r'[?&]q=([^&]*)')",
            "DATE_FILTER": "AND created_at >= '2025-01-01'",
            "SEARCH_PATH_PATTERN": "search",
            "LIMIT_CLAUSE": "LIMIT 50"
        }
        """.trimIndent()
    }
    

    private fun getErrorSchema(): String {
        return """
        {
            "DATE_FILTER": "AND created_at >= '2025-01-01'",
            "ERROR_CONDITION": "TRUE",
            "LIMIT_CLAUSE": "LIMIT 100"
        }
        """.trimIndent()
    }
}

