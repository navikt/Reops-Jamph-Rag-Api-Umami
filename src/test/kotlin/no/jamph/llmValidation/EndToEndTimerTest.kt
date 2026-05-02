package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import kotlinx.coroutines.runBlocking
import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.ragV2.RagV2SqlService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertTrue

// To run this test, insert into terminal:
// mvn test -Dmaven.test.skip=false -Dtest=EndToEndTimerTest

class EndToEndTimerTest {
    private lateinit var wireMock: WireMockServer
    private lateinit var timer: EndToEndTimer

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        val ollamaClient = OllamaClient("http://localhost:${wireMock.port()}", "qwen2.5-coder:7b")
        val schemaService = BigQuerySchemaServiceMock()
        val ragService = RagV2SqlService(ollamaClient, schemaService)
        timer = EndToEndTimer(ragService)
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    @Test
    fun `measureFullPipeline returns non-negative duration`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT COUNT(*) FROM events;\"}")
                )
        )
        val result = timer.measureFullPipeline("Count page views", "https://aksel.nav.no")

        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun `measureFullPipeline returns the generated sql`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT COUNT(*) FROM events;\"}")
                )
        )

        val result = timer.measureFullPipeline("Count page views", "https://aksel.nav.no")

        assertTrue(result.sql.isNotBlank())
    }

    @Test
    fun `measureFullPipeline stores the original query`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT 1;\"}")
                )
        )
        val query = "Count page views"

        val result = timer.measureFullPipeline(query, "https://aksel.nav.no")

        assertTrue(result.query == query)
    }

    @Test
    fun `measureFullPipeline stores query length`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/api/generate"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{\"response\":\"SELECT 1;\"}")
                )
        )
        val query = "Count page views"

        val result = timer.measureFullPipeline(query, "https://aksel.nav.no")

        assertTrue(result.queryLength == query.length)
    }
}
