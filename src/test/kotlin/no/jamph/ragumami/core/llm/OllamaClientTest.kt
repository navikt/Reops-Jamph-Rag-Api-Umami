package no.jamph.ragumami.core.llm

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Disabled
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Disabled("Integration test - enable when Ollama is running")
class OllamaClientTest {
    private lateinit var wireMock: WireMockServer
    private lateinit var client: OllamaClient
    
    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        client = OllamaClient("http://localhost:${wireMock.port()}", "qwen2.5-coder:7b")
    }
    
    @AfterEach
    fun teardown() {
        wireMock.stop()
    }
    
    @Test
    fun `test successful generation`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT * FROM website;\"}")
                )
        )
        
        val result = client.generate("Show all websites")
        
        assertEquals(true, result.contains("SELECT"))
    }
    
    @Test
    fun `test timeout handling`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withFixedDelay(35000) // Exceeds 30s timeout
                )
        )
        
        assertFailsWith<Exception> {
            client.generate("Test timeout")
        }
    }
    
    @Test
    fun `test retry on 500 then succeeds`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .inScenario("Retry")
                .whenScenarioStateIs("Started")
                .willReturn(
                    aResponse()
                        .withStatus(500)
                )
                .willSetStateTo("First retry")
        )
        
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .inScenario("Retry")
                .whenScenarioStateIs("First retry")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"Success\"}")
                )
        )
        
        val result = client.generate("Test retry")
        assertEquals(true, result.contains("Success"))
    }
    
    @Test
    fun `test retry gives up after max attempts`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(503) // Service Unavailable
                )
        )
        
        assertFailsWith<Exception> {
            client.generate("Test retry failure")
        }
        
        // Verify retried 3 times (initial + 3 retries = 4 total)
        wireMock.verify(4, postRequestedFor(urlEqualTo("/api/generate")))
    }
}