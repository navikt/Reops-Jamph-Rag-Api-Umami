package no.jamph.ragumami.ragV2

import no.jamph.ragumami.core.llm.OllamaClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException


data class ExtractedVariables(
    val variables: JsonObject,
    val siteId: String,
    val urlPath: String,
    val userPrompt: String
)

class PickVariableJsonLlm(
    private val ollamaClient: OllamaClient,
    private val prebuiltSchemas: PrebuiltSchemaProvider
) {
    suspend fun extractVariables(
        queryType: String,
        siteId: String,
        urlPath: String,
        userPrompt: String
    ): ExtractedVariables {
        val bigQuerySchema = prebuiltSchemas.getBigQuerySchema(queryType)
        val simplifiedSql = prebuiltSchemas.getSimplifiedSql(queryType)
        val jsonSchema = prebuiltSchemas.getJsonSchema(queryType)
        
        val variables = tryCatchRetry(3, "Error 10001") {
            val extractionPrompt = buildExtractionPrompt(userPrompt, bigQuerySchema, simplifiedSql, jsonSchema)
            val response = ollamaClient.generate(extractionPrompt)
            parseAndValidateJson(response)
        }
        
        return ExtractedVariables(
            variables = variables,
            siteId = siteId,
            urlPath = urlPath,
            userPrompt = userPrompt
        )
    }
    

    private fun buildExtractionPrompt(
        userPrompt: String,
        bigQuerySchema: String,
        simplifiedSql: String,
        jsonSchema: String
    ): String = """
        You are a SQL BigQuery expert. Your task is to extract variable values from the user's question
        and fill them into the JSON template.
        
        BigQuery Schema:
        $bigQuerySchema
        
        SQL Template:
        $simplifiedSql
        
        User Question: $userPrompt
        
        Fill in the missing variables in this JSON object. Return ONLY the JSON object with values filled in, no other text:
        
        $jsonSchema

        Return the JSON object:
    """.trimIndent()
    

    private fun parseAndValidateJson(response: String): JsonObject? {
        try {
            val jsonText = extractJsonFromResponse(response)
            val parsed = JsonParser.parseString(jsonText)
            
            if (!parsed.isJsonObject) return null
            
            val jsonObject = parsed.asJsonObject
            if (jsonObject.size() == 0) return null
            return jsonObject
            
        } catch (e: Exception) {
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


// Error Codes Reference:
// 10001: The model did not return valid JSON with extracted variables.
//        Possible causes: LLM returned malformed JSON, missing required fields, or invalid format.
//        Resolution: Check LLM response format, review JSON schema clarity, or adjust extraction prompt.
