package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.umami.domain.UmamiRAGService
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue


// To run this test, insert into terminal:
// mvn test -Dmaven.test.skip=false -Dtest=ShortPromptTimerTest

class ShortPromptTimerTest {
    private lateinit var wireMock: WireMockServer
    private lateinit var measurer: ShortPromptTimer

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        val ollamaClient = OllamaClient("http://localhost:${wireMock.port()}", "qwen2.5-coder:7b")
        val ragService = UmamiRAGService(ollamaClient, null)
        measurer = ShortPromptTimer(ragService)
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `measureTime returns measurement with correct query`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT * FROM pageviews WHERE date > NOW() - INTERVAL 7 DAY;\"}")
                )
        )

        val result = measurer.measureTime("How many page views last week?")

        assertEquals("How many page views last week?", result.query)
    }

    @Test
    fun `measureTime captures response from Ollama`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT count(*) FROM events;\"}")
                )
        )

        val result = measurer.measureTime("Count all events")

        assertTrue(result.sql.contains("SELECT"), "SQL should contain SELECT from Ollama")
    }

    @Test
    fun `measureTime records non-negative durationMs`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT 1;\"}")
                )
        )

        val result = measurer.measureTime("Simple query")

        assertTrue(result.durationMs >= 0, "Duration should be non-negative")
    }

    @Test
    fun `measureTime returns measurement even when Ollama returns error`() = runBlocking {
        // First call returns 500, then subsequent calls succeed to avoid long retry backoff
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .inScenario("Ollama error then success")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("Recovered")
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")
                )
        )

        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .inScenario("Ollama error then success")
                .whenScenarioStateIs("Recovered")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT 1;\"}")
                )
        )

        val result = measurer.measureTime("Some prompt")

        assertEquals("Some prompt", result.query)
        assertTrue(result.durationMs >= 0)
    }
}
