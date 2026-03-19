package no.jamph.llmValidation

import no.jamph.ragumami.core.llm.OllamaClient
import com.google.gson.JsonParser

data class TokenSpeedResult(
    val model: String,
    val promptTokens: Int,
    val responseTokens: Int,
    val evalDurationMs: Long,
    val tokensPerSecond: Double
)

class TokenSpeedMeasurer(
    private val ollamaBaseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: "https://jamph-ollama.ekstern.dev.nav.no/",
    private val model: String
) {

    private val client = OllamaClient(ollamaBaseUrl.trimEnd('/'), model)
    suspend fun measure(prompt: String): TokenSpeedResult {
        val rawJson = client.generate(prompt)  // returns raw JSON body

        val json = try {
            JsonParser.parseString(rawJson).asJsonObject
        } catch (e: Exception) {
            return TokenSpeedResult(model, 0, 0, 0L, 0.0)
        }

        val promptTokens = json.get("prompt_eval_count")?.asInt ?: 0
        val responseTokens = json.get("eval_count")?.asInt ?: 0
        val evalDurationNs = json.get("eval_duration")?.asLong ?: 0L
        val evalDurationMs = evalDurationNs / 1_000_000

        val tokensPerSecond = if (evalDurationNs > 0)
            responseTokens / (evalDurationNs / 1_000_000_000.0)
        else 0.0

        return TokenSpeedResult(
            model = model,
            promptTokens = promptTokens,
            responseTokens = responseTokens,
            evalDurationMs = evalDurationMs,
            tokensPerSecond = tokensPerSecond
        )
    }
}