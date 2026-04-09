package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.bigquery.BigQuerySchemaProvider

class RagV2SqlService(
    private val ollamaClient: OllamaClient,
    private val bigQueryService: BigQuerySchemaProvider
) {
    private val schemaProvider = PrebuiltSchemaService(bigQueryService)
    private val queryTypeClassifier = PickASqlQuestionTypeLlm(ollamaClient, bigQueryService)
    private val variableExtractor = PickVariableJsonLlm(ollamaClient, schemaProvider)
    private val sqlConstructor = ConstructSQL(schemaProvider)
    private val otherLlm = OtherLlm(ollamaClient, bigQueryService)

    suspend fun generateSql(
        userPrompt: String,
        url: String,
        pathOperator: String = "starts-with"
    ): String {
        val classificationResult = queryTypeClassifier.classifyQueryType(
            userPrompt = userPrompt,
            url = url,
            pathOperator = pathOperator
        )
        
        return if (classificationResult.queryType == "default") {
            otherLlm.generateSql(
                userPrompt = classificationResult.userPrompt,
                siteId = classificationResult.siteId,
                urlPath = classificationResult.urlPath
            )
        } else {
            val extractedVariables = variableExtractor.extractVariables(
                queryType = classificationResult.queryType,
                siteId = classificationResult.siteId,
                urlPath = classificationResult.urlPath,
                userPrompt = classificationResult.userPrompt
            )
            
            sqlConstructor.constructSql(
                queryType = classificationResult.queryType,
                variables = extractedVariables.variables,
                siteId = extractedVariables.siteId,
                urlPath = extractedVariables.urlPath
            )
        }
    }
    

    suspend fun generateSqlWithDebugInfo(
        userPrompt: String,
        url: String,
        pathOperator: String = "starts-with"
    ): SqlGenerationResult {
        val classificationResult = queryTypeClassifier.classifyQueryType(
            userPrompt = userPrompt,
            url = url,
            pathOperator = pathOperator,
            captureDebugInfo = true
        )
        
        return if (classificationResult.queryType == "default") {
            val sql = otherLlm.generateSql(
                userPrompt = classificationResult.userPrompt,
                siteId = classificationResult.siteId,
                urlPath = classificationResult.urlPath
            )
            SqlGenerationResult(
                sql = sql,
                queryType = classificationResult.queryType,
                siteId = classificationResult.siteId,
                urlPath = classificationResult.urlPath,
                extractedVariables = null,
                rawClassificationResponse = classificationResult.rawLlmResponse
            )
        } else {
            val extractedVariables = variableExtractor.extractVariables(
                queryType = classificationResult.queryType,
                siteId = classificationResult.siteId,
                urlPath = classificationResult.urlPath,
                userPrompt = classificationResult.userPrompt
            )
            val sql = sqlConstructor.constructSql(
                queryType = classificationResult.queryType,
                variables = extractedVariables.variables,
                siteId = extractedVariables.siteId,
                urlPath = extractedVariables.urlPath
            )
            
            SqlGenerationResult(
                sql = sql,
                queryType = classificationResult.queryType,
                siteId = classificationResult.siteId,
                urlPath = classificationResult.urlPath,
                extractedVariables = extractedVariables.variables.toString(),
                rawClassificationResponse = classificationResult.rawLlmResponse
            )
        }
    }
}

data class SqlGenerationResult(
    val sql: String,
    val queryType: String,
    val siteId: String,
    val urlPath: String,
    val extractedVariables: String?,
    val rawClassificationResponse: String? = null
)
