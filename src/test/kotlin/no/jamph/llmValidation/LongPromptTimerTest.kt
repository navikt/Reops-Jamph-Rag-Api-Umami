package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.umami.domain.UmamiRAGService
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


// To run this test, insert into terminal: 
" mvn test -Dmaven.test.skip=false -Dtest=LongPromptTimerTest "

class LongPromptTimerTest {
    private lateinit var wireMock: WireMockServer
    private lateinit var measurer: LongPromptTimer

    private val longPrompt = "a".repeat(LONG_CONTEXT_THRESHOLD + 1)
    private val longResponse = "b".repeat(LONG_CONTEXT_THRESHOLD + 1)

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        val ollamaClient = OllamaClient("http://localhost:${wireMock.port()}", "qwen2.5-coder:7b")
        val ragService = UmamiRAGService(ollamaClient, null)
        measurer = LongPromptTimer(ragService)
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `measureLongContext returns correct query`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"$longResponse\"}")
                )
        )

        val result = measurer.measureLongContext(longPrompt)

        assertEquals(longPrompt, result.query)
    }

    @Test
    fun `measureLongContext records queryLength and sqlLength`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"$longResponse\"}")
                )
        )

        val result = measurer.measureLongContext(longPrompt)

        assertTrue(result.queryLength > LONG_CONTEXT_THRESHOLD)
        assertTrue(result.sqlLength > 0)
    }

    @Test
    fun `exceedsThreshold is true when response is over threshold`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"$longResponse\"}")
                )
        )

        val result = measurer.measureLongContext(longPrompt)

        assertTrue(result.exceedsThreshold, "Expected sql to exceed threshold of $LONG_CONTEXT_THRESHOLD chars")
    }

    @Test
    fun `exceedsThreshold is false when response is under threshold`() = runBlocking {
        val shortResponse = "SELECT * FROM events;"
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"$shortResponse\"}")
                )
        )

        val result = measurer.measureLongContext(longPrompt)

        assertFalse(result.exceedsThreshold, "Expected sql to be under threshold of $LONG_CONTEXT_THRESHOLD chars")
    }

    @Test
    fun `measureLongContext records non-negative durationMs`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"ok\"}")
                )
        )

        val result = measurer.measureLongContext(longPrompt)

        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun `measureLongContext throws when prompt is below threshold`() {
        val shortPrompt = "too short"

        assertThrows<IllegalArgumentException> {
            runBlocking {
                measurer.measureLongContext(shortPrompt)
            }
        }
    }
}
