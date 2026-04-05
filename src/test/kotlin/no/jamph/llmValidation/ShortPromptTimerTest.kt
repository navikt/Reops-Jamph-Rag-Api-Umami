package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import no.jamph.ragumami.core.llm.OllamaClient
import org.junit.jupiter.api.*
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
        measurer = ShortPromptTimer(ollamaClient)
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `measureLlmWithSmallSchema runs iterations and returns average`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT * FROM pageviews WHERE date > NOW() - INTERVAL 7 DAY;\"}")
                )
        )

        val result = measurer.measureLlmWithSmallSchema("How many page views last week?", iterations = 10)

        assertTrue(result.averageDurationMs >= 0)
        assertTrue(result.sql.contains("SELECT"))
        assertTrue(result.iterations == 10)
    }

    @Test
    fun `measureLlmWithSmallSchema records non-negative durationMs`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT 1;\"}")
                )
        )

        val result = measurer.measureLlmWithSmallSchema("Simple query", iterations = 5)

        assertTrue(result.averageDurationMs >= 0, "Duration should be non-negative")
    }
}
