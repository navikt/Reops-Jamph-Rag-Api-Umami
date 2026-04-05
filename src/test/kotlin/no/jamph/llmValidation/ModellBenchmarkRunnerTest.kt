package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// To run this test, insert into terminal:
// mvn test -Dmaven.test.skip=false -Dtest=ModellBenchmarkRunnerTest

class ModellBenchmarkRunnerTest {

    // -------------------------------------------------------------------------
    // ModelBenchmarkResult – data class property tests (no I/O needed)
    // -------------------------------------------------------------------------

    @Test
    fun `ModelBenchmarkResult stores all properties correctly`() {
        val result = ModelBenchmarkResult(
            model           = "llama3",
            timestamp       = "2025-01-01T00:00:00Z",
            sqlAccuracy     = 0.85,
            dialectAccuracy = 0.75,
            averageCostMB   = 55.5,
            endToEndMs      = 1500L,
            longPromptMs    = 3200L,
            shortPromptMs   = 800L,
            tokensPerSecond = 12.5,
            promptTokens    = 100,
            responseTokens  = 50,
            evalDurationMs  = 4000L
        )
        assertEquals("llama3", result.model)
        assertEquals("2025-01-01T00:00:00Z", result.timestamp)
        assertEquals(0.85, result.sqlAccuracy)
        assertEquals(0.75, result.dialectAccuracy)
        assertEquals(55.5, result.averageCostMB)
        assertEquals(1500L, result.endToEndMs)
        assertEquals(3200L, result.longPromptMs)
        assertEquals(800L, result.shortPromptMs)
        assertEquals(12.5, result.tokensPerSecond)
        assertEquals(100, result.promptTokens)
        assertEquals(50, result.responseTokens)
        assertEquals(4000L, result.evalDurationMs)
    }

    @Test
    fun `ModelBenchmarkResult equality holds for identical instances`() {
        val r1 = ModelBenchmarkResult("m1", "2025-01-01T00:00:00Z", 1.0, 1.0, 50.0, 1000L, 2000L, 500L, 10.0, 10, 20, 1000L)
        val r2 = ModelBenchmarkResult("m1", "2025-01-01T00:00:00Z", 1.0, 1.0, 50.0, 1000L, 2000L, 500L, 10.0, 10, 20, 1000L)
        assertEquals(r1, r2)
    }

    @Test
    fun `ModelBenchmarkResult copy changes only the specified field`() {
        val original = ModelBenchmarkResult("llama3", "ts", 0.5, 0.6, 50.0, 1000L, 2000L, 500L, 5.0, 10, 20, 500L)
        val copy     = original.copy(model = "deepseek")
        assertEquals("deepseek", copy.model)
        assertEquals("llama3", original.model)
        assertEquals(original.sqlAccuracy, copy.sqlAccuracy)
        assertEquals(original.evalDurationMs, copy.evalDurationMs)
    }

    @Test
    fun `ModelBenchmarkResult with all-zero values is valid`() {
        val result = ModelBenchmarkResult("model", "ts", 0.0, 0.0, 0.0, 0L, 0L, 0L, 0.0, 0, 0, 0L)
        assertEquals(0.0, result.sqlAccuracy)
        assertEquals(0.0, result.dialectAccuracy)
        assertEquals(0.0, result.averageCostMB)
        assertEquals(0L, result.endToEndMs)
        assertEquals(0.0, result.tokensPerSecond)
        assertEquals(0L, result.evalDurationMs)
    }

    @Test
    fun `ModelBenchmarkResult hashCode matches for equal instances`() {
        val r1 = ModelBenchmarkResult("md", "ts", 0.5, 0.5, 50.0, 1000L, 2000L, 500L, 5.0, 5, 5, 500L)
        val r2 = ModelBenchmarkResult("md", "ts", 0.5, 0.5, 50.0, 1000L, 2000L, 500L, 5.0, 5, 5, 500L)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    // -------------------------------------------------------------------------
    // runBenchmark – WireMock stubs the Ollama speed-probe endpoint.
    // LlmSqlLogic and DialectValidetaLlmToSql are injected as lambdas so no
    // real LLM call is made and no System.getenv mocking is needed.
    // -------------------------------------------------------------------------

    private lateinit var wireMock: WireMockServer

    @BeforeEach
    fun startWireMock() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        // eval_count=100, eval_duration=2_000_000_000 ns → 50.0 tokens/sec, 2000 ms
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"response":"SELECT 1","prompt_eval_count":50,"eval_count":100,"eval_duration":2000000000}""")
                )
        )
    }

    @AfterEach
    fun stopWireMock() {
        wireMock.stop()
    }

    private fun wireMockUrl() = "http://localhost:${wireMock.port()}"

    @Test
    fun `runBenchmark returns one result per model`() {
        val results = runBenchmark(
            models            = listOf("model-a", "model-b"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.8 },
            dialectValidateFn = { _, _ -> 0.9 }
        )
        assertEquals(2, results.size)
    }

    @Test
    fun `runBenchmark preserves model name in result`() {
        val results = runBenchmark(
            models            = listOf("deepseek-coder"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.8 },
            dialectValidateFn = { _, _ -> 0.9 }
        )
        assertEquals("deepseek-coder", results[0].model)
    }

    @Test
    fun `runBenchmark result timestamp is a valid ISO-8601 instant string`() {
        val results = runBenchmark(
            models            = listOf("test-model"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.0 },
            dialectValidateFn = { _, _ -> 0.0 }
        )
        assertTrue(results[0].timestamp.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*Z")))
    }

    @Test
    fun `runBenchmark calculates tokensPerSecond correctly from stub data`() {
        // 100 tokens / 2.0 s = 50.0 t/s
        val results = runBenchmark(
            models            = listOf("bench-model"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.0 },
            dialectValidateFn = { _, _ -> 0.0 }
        )
        assertEquals(50.0, results[0].tokensPerSecond, 0.01)
    }

    @Test
    fun `runBenchmark parses promptTokens and responseTokens from stub`() {
        val results = runBenchmark(
            models            = listOf("bench-model"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.0 },
            dialectValidateFn = { _, _ -> 0.0 }
        )
        assertEquals(50, results[0].promptTokens)
        assertEquals(100, results[0].responseTokens)
    }

    @Test
    fun `runBenchmark converts evalDurationMs correctly from nanoseconds`() {
        // 2_000_000_000 ns → 2000 ms
        val results = runBenchmark(
            models            = listOf("bench-model"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.0 },
            dialectValidateFn = { _, _ -> 0.0 }
        )
        assertEquals(2000L, results[0].evalDurationMs)
    }

    @Test
    fun `runBenchmark forwards sqlAccuracy from injected function`() {
        val results = runBenchmark(
            models            = listOf("bench-model"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.75 },
            dialectValidateFn = { _, _ -> 0.0 }
        )
        assertEquals(0.75, results[0].sqlAccuracy, 0.001)
    }

    @Test
    fun `runBenchmark forwards dialectAccuracy from injected function`() {
        val results = runBenchmark(
            models            = listOf("bench-model"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.0 },
            dialectValidateFn = { _, _ -> 1.0 }
        )
        assertEquals(1.0, results[0].dialectAccuracy, 0.001)
    }

    @Test
    fun `runBenchmark passes the model name into the injected accuracy functions`() {
        val seenModels = mutableListOf<String>()
        runBenchmark(
            models            = listOf("llama3", "qwen"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { m, _ -> seenModels += m; 1.0 },
            dialectValidateFn = { _, _ -> 1.0 }
        )
        assertEquals(listOf("llama3", "qwen"), seenModels)
    }

    @Test
    fun `runBenchmark preserves order of models in result list`() {
        val results = runBenchmark(
            models            = listOf("alpha", "beta", "gamma"),
            ollamaBaseUrl     = wireMockUrl(),
            llmSqlLogicFn     = { _, _ -> 0.0 },
            dialectValidateFn = { _, _ -> 0.0 }
        )
        assertEquals(listOf("alpha", "beta", "gamma"), results.map { it.model })
    }

    @Test
    fun `runBenchmark with empty model list returns empty list`() {
        val results = runBenchmark(emptyList())
        assertTrue(results.isEmpty())
    }
}
