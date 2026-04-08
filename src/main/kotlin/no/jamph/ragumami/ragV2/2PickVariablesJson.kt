package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import org.slf4j.LoggerFactory


data class ExtractedVariables(
    val variables: JsonObject,
    val siteId: String,
    val urlPath: String,
    val userPrompt: String
)

class VariableExtractor(
    private val ollamaClient: OllamaClient,
    private val prebuiltSchemas: PrebuiltSchemaProvider
) {
    private val logger = LoggerFactory.getLogger(VariableExtractor::class.java)
    private val gson = Gson()
    
    companion object {
        private const val MAX_RETRIES = 3
    }
    

    suspend fun extractVariables(
        queryType: String,
        siteId: String,
        urlPath: String,
        userPrompt: String
    ): ExtractedVariables {
        logger.info("Extracting variables for query type '{}' from prompt: '{}'", queryType, userPrompt)
        
        val bigQuerySchema = prebuiltSchemas.getBigQuerySchema(queryType)
        val sqlTemplate = prebuiltSchemas.getSqlTemplate(queryType)
        val jsonSchema = prebuiltSchemas.getJsonSchema(queryType)
        
        for (attempt in 1..MAX_RETRIES) {
            try {
                val extractionPrompt = buildExtractionPrompt(
                    userPrompt = userPrompt,
                    bigQuerySchema = bigQuerySchema,
                    sqlTemplate = sqlTemplate,
                    jsonSchema = jsonSchema,
                    attempt = attempt
                )
                
                val response = ollamaClient.generate(extractionPrompt)
                val extractedJson = parseAndValidateJson(response, jsonSchema)
                
                if (extractedJson != null) {
                    logger.info("Successfully extracted variables on attempt {}", attempt)
                    return ExtractedVariables(
                        variables = extractedJson,
                        siteId = siteId,
                        urlPath = urlPath,
                        userPrompt = userPrompt
                    )
                }
                
                logger.warn("Attempt {}/{}: Invalid JSON response: '{}'", 
                    attempt, MAX_RETRIES, response.take(200))
                
            } catch (e: Exception) {
                logger.error("Attempt {}/{}: Error during variable extraction", attempt, MAX_RETRIES, e)
                if (attempt == MAX_RETRIES) throw e
            }
        }
        
        throw IllegalStateException(
            "Failed to extract variables after $MAX_RETRIES attempts for query type '$queryType'"
        )
    }
    

    private fun buildExtractionPrompt(
        userPrompt: String,
        bigQuerySchema: String,
        sqlTemplate: String,
        jsonSchema: String,
        attempt: Int
    ): String {
        return if (attempt == 1) {
            """
            You are a SQL BigQuery expert. Your task is to extract variable values from the user's question
            and fill them into the JSON template.
            
            BigQuery Schema:
            $bigQuerySchema
            
            SQL Template:
            $sqlTemplate
            
            User Question: $userPrompt
            
            Fill in the missing variables in this JSON object. Return ONLY the JSON object with values filled in, no other text:
            
            $jsonSchema
            """.trimIndent()
        } else {
            """
            CRITICAL: Return ONLY valid JSON. NO explanations. NO markdown. NO code blocks.
            
            BigQuery Schema:
            $bigQuerySchema
            
            SQL Template:
            $sqlTemplate
            
            User Question: $userPrompt
            
            Return this JSON with values filled in:
            $jsonSchema
            
            JSON:
            """.trimIndent()
        }
    }
    

    private fun parseAndValidateJson(response: String, expectedSchema: String): JsonObject? {
        try {
            val jsonText = extractJsonFromResponse(response)
            val parsed = JsonParser.parseString(jsonText)
            
            if (!parsed.isJsonObject) {
                logger.warn("Response is not a JSON object")
                return null
            }
            
            val jsonObject = parsed.asJsonObject
            
            if (jsonObject.size() == 0) {
                logger.warn("JSON object is empty")
                return null
            }
            
            return jsonObject
            
        } catch (e: JsonSyntaxException) {
            logger.warn("Failed to parse JSON: {}", e.message)
            return null
        } catch (e: Exception) {
            logger.warn("Unexpected error parsing JSON", e)
            return null
        }
    }

    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()
        
        val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = codeBlockRegex.find(trimmed)
        
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val startIndex = trimmed.indexOf('{')
        val endIndex = trimmed.lastIndexOf('}')
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return trimmed.substring(startIndex, endIndex + 1)
        }
        

        return trimmed
    }
}
