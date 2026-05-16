package no.jamph.ragumami.passthrough

import com.google.gson.Gson
import no.jamph.ragumami.core.llm.OllamaClient
import org.slf4j.LoggerFactory

data class PassthroughRequest(
    val role: String? = null,
    val question: String? = null,
    val code: String? = null,
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
        if (request.role.isNullOrBlank()) {
            throw IllegalArgumentException("Prompt kan ikke vaere tom")
        }

        val cleanedData = if (request.data != null) cleanText(gson.toJson(request.data)) else null
        val cleanedCode = request.code?.takeIf { it.isNotBlank() }
        val question = request.question?.takeIf { it.isNotBlank() }

        val fullPrompt = buildString {
            append("Du er en statistisk rådgiver som hjelper brukeren med å tolke webstatistikk. ")
            append("Vær bevisst på usikkerhet i dataene og unngå bastante konklusjoner som ikke støttes direkte av analysen. ")
            append("Du skal svare på dette spørsmålet")
            if (question != null) append(": \"$question\"")
            append(". ")
            append("Spørsmålet er besvart med denne koden")
            if (cleanedCode != null) append(":\n$cleanedCode")
            append(".\n")
            append("Koden ga dette svaret")
            if (cleanedData != null) append(":\n$cleanedData")
            append(".\n")
            append(request.role ?: "")
            append(" Svar i nøyaktig ett kortfattet avsnitt.")
        }

        logger.info("PASSTHROUGH: model={}, promptLength={}", defaultModel, fullPrompt.length)

        val rawResponse = defaultClient.generate(fullPrompt, mapOf(
            "temperature" to 0.3,
            "num_predict" to 350
        ))
        val cleanedResponse = cleanText(rawResponse)

        logger.info("PASSTHROUGH: responseLength={}", cleanedResponse.length)
        return PassthroughResponse(response = cleanedResponse, model = defaultModel)
    }
}