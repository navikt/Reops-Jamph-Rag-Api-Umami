package no.jamph.ragumami.passthrough

import com.google.gson.Gson
import no.jamph.ragumami.core.llm.OllamaClient
import org.slf4j.LoggerFactory

data class PassthroughRequest(
    val prompt: String,
    val data: Any? = null
)

data class PassthroughResponse(
    val response: String,
    val model: String
)

class PassthroughService(
    private val ollamaBaseUrl: String,
    private val defaultModel: String,
    private val defaultClient: OllamaClient
) {
    private val logger = LoggerFactory.getLogger(PassthroughService::class.java)

    companion object {
        private val gson = Gson()

        /** Strips code blocks, backticks, brackets and common code symbols from a string. */
        fun cleanText(text: String): String = text
            .replace(Regex("```[\\s\\S]*?```"), "")   // fenced code blocks
            .replace(Regex("`[^`]*`"), "")             // inline code
            .replace(Regex("[\\[\\]{}()<>]"), "")      // brackets / braces
            .replace(Regex("[|\\\\*#_~^]"), "")        // markdown / code symbols
            .replace(Regex("\\s{3,}"), "\n\n")         // collapse excess whitespace
            .trim()
    }

    suspend fun generate(request: PassthroughRequest): PassthroughResponse {
        if (request.prompt.isBlank()) {
            throw IllegalArgumentException("Prompt kan ikke vaere tom")
        }

        val cleanedData = if (request.data != null) {
            cleanText(gson.toJson(request.data))
        } else null

        val fullPrompt = if (cleanedData != null) {
            "${request.prompt}\n\nData:\n$cleanedData"
        } else {
            request.prompt
        }

        logger.info("PASSTHROUGH: model={}, promptLength={}", defaultModel, fullPrompt.length)

        val rawResponse = defaultClient.generate(fullPrompt)
        val cleanedResponse = cleanText(rawResponse)

        logger.info("PASSTHROUGH: responseLength={}", cleanedResponse.length)
        return PassthroughResponse(response = cleanedResponse, model = defaultModel)
    }
}