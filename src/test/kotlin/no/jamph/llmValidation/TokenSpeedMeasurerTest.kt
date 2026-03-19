package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

// To run this test, insert into terminal:
// mvn test -Dmaven.test.skip=false -Dtest=TokenSpeedMeasurerTest

class TokenSpeedMeasurerTest {
    private lateinit var wireMock: WireMockServer
    private lateinit var measurer: TokenSpeedMeasurer

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        measurer = TokenSpeedMeasurer(
            ollamaBaseUrl = "http://localhost:${wireMock.port()}",
            model = "qwen2.5-coder:7b"
        )
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `measure returns correct model`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(200)
                    .withBody("""{"response":"SELECT 1;","prompt_eval_count":42,"eval_count":18,"eval_duration":1500000000}"""))
        )
        val result = measurer.measure("Show all events")
        assertEquals("qwen2.5-coder:7b", result.model)
    }

    @Test
    fun `measure parses prompt and response token counts`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(200)
                    .withBody("""{"response":"SELECT 1;","prompt_eval_count":42,"eval_count":18,"eval_duration":1500000000}"""))
        )
        val result = measurer.measure("Show all events")
        assertEquals(42, result.promptTokens)
        assertEquals(18, result.responseTokens)
    }

    @Test
    fun `measure calculates correct tokensPerSecond`() = runBlocking {
        // 18 tokens / 1.5s = 12.0 tokens/sec
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(200)
                    .withBody("""{"response":"SELECT 1;","prompt_eval_count":42,"eval_count":18,"eval_duration":1500000000}"""))
        )
        val result = measurer.measure("Show all events")
        assertEquals(12.0, result.tokensPerSecond, 0.01)
    }

    @Test
    fun `measure converts evalDurationMs correctly`() = runBlocking {
        // 1_500_000_000 ns = 1500 ms
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(200)
                    .withBody("""{"response":"SELECT 1;","prompt_eval_count":42,"eval_count":18,"eval_duration":1500000000}"""))
        )
        val result = measurer.measure("Show all events")
        assertEquals(1500L, result.evalDurationMs)
    }

    @Test
    fun `measure returns zero result when Ollama is unavailable`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
        )
        val result = measurer.measure("Show all events")
        assertEquals(0, result.promptTokens)
        assertEquals(0, result.responseTokens)
        assertEquals(0.0, result.tokensPerSecond)
    }
}