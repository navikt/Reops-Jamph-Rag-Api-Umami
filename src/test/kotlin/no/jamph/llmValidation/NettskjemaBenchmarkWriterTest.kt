package no.jamph.llmValidation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

// To run this test, insert into terminal:
// mvn test -Dmaven.test.skip=false -Dtest=NettskjemaBenchmarkWriterTest

class NettskjemaBenchmarkWriterTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var writer: NettskjemaBenchmarkWriter

    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()

        writer = NettskjemaBenchmarkWriter(
            baseUrl = "http://localhost:${wireMock.port()}"
        )

        wireMock.stubFor(
            post(urlPathMatching("/api/v3/form/.*/submission"))
                .willReturn(aResponse().withStatus(200).withBody("OK"))
        )
    }

    @AfterEach
    fun teardown() {
        wireMock.stop()
    }

    private fun sampleResult(
        model: String = "test-model",
        sqlAccuracy: Double = 0.85,
        dialectAccuracy: Double = 0.75,
        tokensPerSecond: Double = 12.5,
        promptTokens: Int = 100,
        responseTokens: Int = 50,
        evalDurationMs: Long = 4000L
    ) = ModelBenchmarkResult(
        model           = model,
        timestamp       = "2025-01-01T00:00:00Z",
        sqlAccuracy     = sqlAccuracy,
        dialectAccuracy = dialectAccuracy,
        tokensPerSecond = tokensPerSecond,
        promptTokens    = promptTokens,
        responseTokens  = responseTokens,
        evalDurationMs  = evalDurationMs
    )

    // -------------------------------------------------------------------------
    // Request counts
    // -------------------------------------------------------------------------

    @Test
    fun `appendRows with single result makes exactly one POST request`() {
        writer.appendRows(listOf(sampleResult()))
        wireMock.verify(1, postRequestedFor(urlPathMatching("/api/v3/form/.*/submission")))
    }

    @Test
    fun `appendRows with empty list makes no POST requests`() {
        writer.appendRows(emptyList())
        wireMock.verify(0, postRequestedFor(urlPathMatching("/api/v3/form/.*/submission")))
    }

    @Test
    fun `appendRows with two results makes two POST requests`() {
        writer.appendRows(listOf(sampleResult("model-a"), sampleResult("model-b")))
        wireMock.verify(2, postRequestedFor(urlPathMatching("/api/v3/form/.*/submission")))
    }

    // -------------------------------------------------------------------------
    // Request URL – formId is embedded in the path
    // -------------------------------------------------------------------------

    @Test
    fun `appendRows posts to URL containing the configured formId`() {
        writer.appendRows(listOf(sampleResult()))
        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v3/form/614069/submission")))
    }

    // -------------------------------------------------------------------------
    // Request body – field text values
    // -------------------------------------------------------------------------

    @Test
    fun `appendRows sends model name as text answer`() {
        writer.appendRows(listOf(sampleResult(model = "my-llm-model")))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"my-llm-model""""))
        )
    }

    @Test
    fun `appendRows sends timestamp as text answer`() {
        writer.appendRows(listOf(sampleResult()))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"2025-01-01T00:00:00Z""""))
        )
    }

    @Test
    fun `appendRows sends sqlAccuracy formatted as percentage with one decimal`() {
        // 0.85 * 100 → "85.0"
        writer.appendRows(listOf(sampleResult(sqlAccuracy = 0.85)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"85.0""""))
        )
    }

    @Test
    fun `appendRows sends dialectAccuracy formatted as percentage with one decimal`() {
        // 0.75 * 100 → "75.0"
        writer.appendRows(listOf(sampleResult(dialectAccuracy = 0.75)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"75.0""""))
        )
    }

    @Test
    fun `appendRows sends tokensPerSecond formatted with two decimals`() {
        // 12.5 → "12.50"
        writer.appendRows(listOf(sampleResult(tokensPerSecond = 12.5)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"12.50""""))
        )
    }

    @Test
    fun `appendRows sends promptTokens as plain integer string`() {
        writer.appendRows(listOf(sampleResult(promptTokens = 100)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"100""""))
        )
    }

    @Test
    fun `appendRows sends responseTokens as plain integer string`() {
        writer.appendRows(listOf(sampleResult(responseTokens = 50)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"50""""))
        )
    }

    @Test
    fun `appendRows sends evalDurationMs as plain long string`() {
        writer.appendRows(listOf(sampleResult(evalDurationMs = 4000L)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"4000""""))
        )
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    fun `appendRows handles a 500 server error without throwing`() {
        wireMock.stubFor(
            post(urlPathMatching("/api/v3/form/.*/submission"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
        )
        // Must not throw
        writer.appendRows(listOf(sampleResult()))
    }

    @Test
    fun `appendRows handles a 400 bad request without throwing`() {
        wireMock.stubFor(
            post(urlPathMatching("/api/v3/form/.*/submission"))
                .willReturn(aResponse().withStatus(400).withBody("Bad Request"))
        )
        writer.appendRows(listOf(sampleResult()))
    }

    // -------------------------------------------------------------------------
    // Edge-case accuracy values
    // -------------------------------------------------------------------------

    @Test
    fun `appendRows sends zero sqlAccuracy as 0,0`() {
        writer.appendRows(listOf(sampleResult(sqlAccuracy = 0.0)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"0.0""""))
        )
    }

    @Test
    fun `appendRows sends perfect sqlAccuracy as 100,0`() {
        writer.appendRows(listOf(sampleResult(sqlAccuracy = 1.0)))
        wireMock.verify(
            postRequestedFor(urlPathMatching("/api/v3/form/.*/submission"))
                .withRequestBody(containing(""""text":"100.0""""))
        )
    }
}
