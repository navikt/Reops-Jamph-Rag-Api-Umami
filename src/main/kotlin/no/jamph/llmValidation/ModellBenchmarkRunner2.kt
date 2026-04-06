package no.jamph.llmValidation

import kotlinx.coroutines.runBlocking
import java.time.Instant
import no.jamph.ragumami.Routes

data class ModelBenchmarkResult(
    val model: String,
    val timestamp: String,
    val sqlAccuracy: Double,
    val dialectAccuracy: Double,
    val averageCostMB: Double,
    val endToEndMs: Long,
    val longPromptMs: Long,
    val shortPromptMs: Long,
    val tokensPerSecond: Double,
    val promptTokens: Int,
    val responseTokens: Int,
    val evalDurationMs: Long
)

private const val SPEED_PROBE = "Write a BigQuery SQL query that counts rows in a table."
private const val TIMER_PROBE = "Show me pageviews per day for https://aksel.nav.no"

fun runBenchmark(
    models: List<String>,
    ollamaBaseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl,
    llmSqlLogicFn: (String, (String) -> Unit) -> Double = { model, log -> LlmSqlLogic(model, debugLog = log) },
    dialectValidateFn: (String, (String) -> Unit) -> Double = { model, log -> DialectValidetaLlmToSql(model, debugLog = log) },
    costValidateFn: (String, (String) -> Unit) -> Double = { model, log -> CostValidateLLmEstimator(model, debugLog = log) },
    endToEndTimerFn: (String, String, (String) -> Unit) -> Long = { url, model, log ->
        val client = no.jamph.ragumami.core.llm.OllamaClient(System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl, model)
        val schema = no.jamph.bigquery.BigQuerySchemaServiceMock()
        val rag = no.jamph.ragumami.umami.UmamiRAGService(client, schema)
        log("  Running end-to-end pipeline...")
        val ms = kotlinx.coroutines.runBlocking { EndToEndTimer(rag).measureFullPipeline(TIMER_PROBE, url, schema.getWebsites()).durationMs }
        log("  Result: $ms ms")
        ms
    },
    longPromptTimerFn: (String, (String) -> Unit) -> Long = { model, log ->
        val client = no.jamph.ragumami.core.llm.OllamaClient(System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl, model)
        kotlinx.coroutines.runBlocking { LongPromptTimer(client, log).measureLlmWithLargeSchema(TIMER_PROBE).averageDurationMs }
    },
    shortPromptTimerFn: (String, (String) -> Unit) -> Long = { model, log ->
        val client = no.jamph.ragumami.core.llm.OllamaClient(System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl, model)
        kotlinx.coroutines.runBlocking { ShortPromptTimer(client, log).measureLlmWithSmallSchema(TIMER_PROBE).averageDurationMs }
    },
    debugLog: (String) -> Unit = ::println
): List<ModelBenchmarkResult> = runBlocking {
    models.map { model ->
    debugLog("▶ Benchmarking: $model")

    val sqlAccuracy = llmSqlLogicFn(model, debugLog)
    debugLog("  SQL accuracy:     ${"%.0f".format(sqlAccuracy * 100)}%")

    val dialectAccuracy = dialectValidateFn(model, debugLog)
    debugLog("  Dialect accuracy: ${"%.0f".format(dialectAccuracy * 100)}%")

    val averageCostMB = costValidateFn(model, debugLog)
    debugLog("  Total cost:       ${"%.2f".format(averageCostMB)} MB")

    debugLog("--- Measuring end-to-end ---")
    val endToEndMs = endToEndTimerFn("https://aksel.nav.no", model, debugLog)
    debugLog("  End-to-end:       $endToEndMs ms")

    debugLog("--- Measuring long prompt ---")
    val longPromptMs = longPromptTimerFn(model, debugLog)
    debugLog("  Long prompt:      $longPromptMs ms")

    debugLog("--- Measuring short prompt ---")
    val shortPromptMs = shortPromptTimerFn(model, debugLog)
    debugLog("  Short prompt:     $shortPromptMs ms")

    val speedResult = TokenSpeedMeasurer(ollamaBaseUrl, model).measure(SPEED_PROBE)
    debugLog("  Tokens/sec:       ${"%.1f".format(speedResult.tokensPerSecond)} (avg of 5)")

        ModelBenchmarkResult(
            model           = model,
            timestamp       = Instant.now().toString(),
            sqlAccuracy     = sqlAccuracy,
            dialectAccuracy = dialectAccuracy,
            averageCostMB   = averageCostMB,
            endToEndMs      = endToEndMs,
            longPromptMs    = longPromptMs,
            shortPromptMs   = shortPromptMs,
            tokensPerSecond = speedResult.tokensPerSecond,
            promptTokens    = speedResult.promptTokens,
            responseTokens  = speedResult.responseTokens,
            evalDurationMs  = speedResult.evalDurationMs
        )
    }
}

fun main() {
    val models = listOf("deepseek-coder-v2:16b")  // test one first

    val results = runBenchmark(models)
}