package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQuerySchemaProvider
import org.slf4j.LoggerFactory


class RagV2SqlService(
    private val ollamaClient: OllamaClient,
    private val bigQueryService: BigQuerySchemaProvider
) {
    private val logger = LoggerFactory.getLogger(RagV2SqlService::class.java)
    
    // Initialize the pipeline components
    private val schemaProvider = PrebuiltSchemaService(bigQueryService)
    private val queryTypeClassifier = QueryTypeClassifier(ollamaClient, bigQueryService)
    private val variableExtractor = VariableExtractor(ollamaClient, schemaProvider)
    private val sqlConstructor = SqlConstructor(schemaProvider)
    

    suspend fun generateSql(
        userPrompt: String,
        url: String,
        pathOperator: String = "starts-with"
    ): String {
        logger.info("Starting RAG v2 SQL generation for prompt: '{}'", userPrompt)
        
        try {
            // Step 1: Classify the query type
            logger.debug("Step 1: Classifying query type...")
            val classificationResult = queryTypeClassifier.classifyQueryType(
                userPrompt = userPrompt,
                url = url,
                pathOperator = pathOperator
            )
            logger.info("Classified as query type: '{}'", classificationResult.queryType)
            
            // Step 2: Extract variables using LLM
            logger.debug("Step 2: Extracting variables...")
            val extractedVariables = variableExtractor.extractVariables(
                queryType = classificationResult.queryType,
                siteId = classificationResult.siteId,
                urlPath = classificationResult.urlPath,
                userPrompt = classificationResult.userPrompt
            )
            logger.info("Successfully extracted {} variables", extractedVariables.variables.size())
            
            // Step 3: Construct the final SQL
            logger.debug("Step 3: Constructing SQL...")
            val sql = sqlConstructor.constructSql(
                queryType = classificationResult.queryType,
                variables = extractedVariables.variables,
                siteId = extractedVariables.siteId,
                urlPath = extractedVariables.urlPath
            )
            
            logger.info("Successfully generated SQL query")
            return sql
            
        } catch (e: Exception) {
            logger.error("Failed to generate SQL for prompt: '{}'", userPrompt, e)
            throw IllegalStateException("SQL generation failed: ${e.message}", e)
        }
    }
    

    suspend fun generateSqlWithDebugInfo(
        userPrompt: String,
        url: String,
        pathOperator: String = "starts-with"
    ): SqlGenerationResult {
        val classificationResult = queryTypeClassifier.classifyQueryType(userPrompt, url, pathOperator)
        val extractedVariables = variableExtractor.extractVariables(
            classificationResult.queryType,
            classificationResult.siteId,
            classificationResult.urlPath,
            classificationResult.userPrompt
        )
        val sql = sqlConstructor.constructSql(
            classificationResult.queryType,
            extractedVariables.variables,
            extractedVariables.siteId,
            extractedVariables.urlPath
        )
        
        return SqlGenerationResult(
            sql = sql,
            queryType = classificationResult.queryType,
            siteId = classificationResult.siteId,
            urlPath = classificationResult.urlPath,
            extractedVariables = extractedVariables.variables.toString()
        )
    }
}

data class SqlGenerationResult(
    val sql: String,
    val queryType: String,
    val siteId: String,
    val urlPath: String,
    val extractedVariables: String
)
