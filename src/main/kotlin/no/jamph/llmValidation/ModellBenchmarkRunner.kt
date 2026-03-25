package no.jamph.llmValidation

import kotlinx.coroutines.runBlocking
import java.time.Instant

data class ModelBenchmarkResult(
    val model: String,
    val timestamp: String,
    val sqlAccuracy: Double,
    val dialectAccuracy: Double,
    val tokensPerSecond: Double,
    val promptTokens: Int,
    val responseTokens: Int,
    val evalDurationMs: Long
)

private const val SPEED_PROBE = "Write a BigQuery SQL query that counts rows in a table."

fun runBenchmark(
    models: List<String>,
    ollamaBaseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
): List<ModelBenchmarkResult> = models.map { model ->
    println("▶ Benchmarking: $model")

    val sqlAccuracy = LlmSqlLogic(model)
    println("  SQL accuracy:     ${"%.0f".format(sqlAccuracy * 100)}%")

    val dialectAccuracy = DialectValidetaLlmToSql(model)
    println("  Dialect accuracy: ${"%.0f".format(dialectAccuracy * 100)}%")

    val speedResult = runBlocking {
        TokenSpeedMeasurer(ollamaBaseUrl, model).measure(SPEED_PROBE)
    }
    println("  Tokens/sec:       ${"%.1f".format(speedResult.tokensPerSecond)}")

    ModelBenchmarkResult(
        model           = model,
        timestamp       = Instant.now().toString(),
        sqlAccuracy     = sqlAccuracy,
        dialectAccuracy = dialectAccuracy,
        tokensPerSecond = speedResult.tokensPerSecond,
        promptTokens    = speedResult.promptTokens,
        responseTokens  = speedResult.responseTokens,
        evalDurationMs  = speedResult.evalDurationMs
    )
}

fun main() {
    val models = listOf("deepseek-coder-v2:16b")  // test one first

    val results = runBenchmark(models)
    NettskjemaBenchmarkWriter().appendRows(results)
}