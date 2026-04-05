package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import no.jamph.ragumami.core.llm.OllamaClient
import org.junit.jupiter.api.*
import kotlin.test.assertTrue


// To run this test, insert into terminal:
// mvn test -Dmaven.test.skip=false -Dtest=LongPromptTimerTest

class LongPromptTimerTest {
    private lateinit var wireMock: WireMockServer
    private lateinit var measurer: LongPromptTimer

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        val ollamaClient = OllamaClient("http://localhost:${wireMock.port()}", "qwen2.5-coder:7b")
        measurer = LongPromptTimer(ollamaClient)
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `measureLlmWithLargeSchema runs 10 iterations and returns average`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT * FROM events;\"}")
                )
        )

        val result = measurer.measureLlmWithLargeSchema("Count events", iterations = 10)

        assertTrue(result.averageDurationMs >= 0)
        assertTrue(result.sql.contains("SELECT"))
        assertTrue(result.iterations == 10)
    }

    @Test
    fun `measureLlmWithLargeSchema records non-negative duration`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"ok\"}")
                )
        )

        val result = measurer.measureLlmWithLargeSchema("Test query", iterations = 5)

        assertTrue(result.averageDurationMs >= 0)
    }
}
