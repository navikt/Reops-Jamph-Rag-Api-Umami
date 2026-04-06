package no.jamph.llmValidation

import com.google.cloud.bigquery.BigQuery
import no.jamph.bigquery.BigQuerySchemaServiceMock
import no.jamph.ragumami.core.llm.OllamaClient
import no.jamph.ragumami.Routes
import no.jamph.ragumami.umami.UmamiRAGService
import kotlinx.coroutines.runBlocking

private const val AKSEL_ID = "fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1"

data class CostTestCase(
    val question: String,
    val url: String,
    val expectedCostMB: Double
)

data class CostResult(
    val question: String,
    val averageCostMB: Double,
    val expectedCostMB: Double,
    val withinRange: Boolean
)

private fun getOptimizedQueries(): List<Pair<String, String>> = listOf(
    "Unique users by device" to """
        SELECT
            COUNT(DISTINCT s.session_id) as unique_users,
            s.device,
            COUNT(*) as total_events
        FROM `fagtorsdag-prod-81a6.umami_student.event` e
        JOIN `fagtorsdag-prod-81a6.umami_student.session` s ON e.session_id = s.session_id
        WHERE e.website_id = '$AKSEL_ID'
            AND e.event_type = 1
            AND e.created_at >= '2025-01-01'
        GROUP BY s.device
        ORDER BY total_events DESC
        LIMIT 100
    """.trimIndent(),

    "Activity overview" to """
        SELECT
            DATE(created_at) as date,
            event_type,
            COUNT(*) as event_count,
            COUNT(DISTINCT session_id) as unique_sessions
        FROM `fagtorsdag-prod-81a6.umami_student.event`
        WHERE website_id = '$AKSEL_ID'
            AND created_at >= '2025-01-01'
        GROUP BY date, event_type
        ORDER BY date DESC
        LIMIT 100
    """.trimIndent(),

    "URL paths with sessions" to """
        SELECT
            url_path,
            COUNT(DISTINCT session_id) as unique_sessions,
            COUNT(*) as total_visits
        FROM `fagtorsdag-prod-81a6.umami_student.event`
        WHERE website_id = '$AKSEL_ID'
            AND event_type = 1
            AND created_at >= '2025-01-01'
        GROUP BY url_path
        ORDER BY total_visits DESC
        LIMIT 50
    """.trimIndent(),

    "Events per day with variants" to """
        SELECT
            DATE(created_at) as date,
            COUNT(*) as event_count,
            COUNT(DISTINCT event_name) as unique_event_names,
            COUNT(DISTINCT url_path) as unique_paths
        FROM `fagtorsdag-prod-81a6.umami_student.event`
        WHERE website_id = '$AKSEL_ID'
            AND created_at >= '2025-01-01'
        GROUP BY date
        ORDER BY date DESC
        LIMIT 100
    """.trimIndent(),

    "Session journey with time" to """
        WITH session_page_counts AS (
            SELECT
                session_id,
                COUNT(*) as page_count
            FROM `fagtorsdag-prod-81a6.umami_student.event`
            WHERE website_id = '$AKSEL_ID'
                AND event_type = 1
                AND created_at >= '2025-01-01'
            GROUP BY session_id
            HAVING COUNT(*) > 3
        ),
        session_events AS (
            SELECT
                e.session_id,
                e.url_path,
                e.created_at,
                LAG(e.created_at) OVER (PARTITION BY e.session_id ORDER BY e.created_at) as prev_time
            FROM `fagtorsdag-prod-81a6.umami_student.event` e
            INNER JOIN session_page_counts spc ON e.session_id = spc.session_id
            WHERE e.website_id = '$AKSEL_ID'
                AND e.event_type = 1
                AND e.created_at >= '2025-01-01'
        )
        SELECT
            session_id,
            url_path,
            created_at,
            TIMESTAMP_DIFF(created_at, prev_time, SECOND) as seconds_since_prev
        FROM session_events
        ORDER BY session_id, created_at
        LIMIT 500
    """.trimIndent(),

    "Top referrer domains" to """
        SELECT
            referrer_domain,
            COUNT(*) as visits
        FROM `fagtorsdag-prod-81a6.umami_student.event`
        WHERE website_id = '$AKSEL_ID'
            AND event_type = 1
            AND created_at >= '2025-01-01'
            AND referrer_domain IS NOT NULL
        GROUP BY referrer_domain
        ORDER BY visits DESC
        LIMIT 50
    """.trimIndent(),

    "Browser breakdown" to """
        SELECT
            s.browser,
            COUNT(DISTINCT s.session_id) as unique_sessions
        FROM `fagtorsdag-prod-81a6.umami_student.session` s
        WHERE s.website_id = '$AKSEL_ID'
            AND s.created_at >= '2025-01-01'
        GROUP BY s.browser
        ORDER BY unique_sessions DESC
    """.trimIndent(),

    "Country breakdown" to """
        SELECT
            s.country,
            COUNT(DISTINCT s.session_id) as unique_sessions
        FROM `fagtorsdag-prod-81a6.umami_student.session` s
        WHERE s.website_id = '$AKSEL_ID'
            AND s.created_at >= '2025-01-01'
        GROUP BY s.country
        ORDER BY unique_sessions DESC
    """.trimIndent(),

    "Custom events summary" to """
        SELECT
            event_name,
            COUNT(*) as occurrences
        FROM `fagtorsdag-prod-81a6.umami_student.event`
        WHERE website_id = '$AKSEL_ID'
            AND event_type = 2
            AND event_name IS NOT NULL
            AND created_at >= '2025-01-01'
        GROUP BY event_name
        ORDER BY occurrences DESC
    """.trimIndent(),

    "Pageviews per month" to """
        SELECT
            FORMAT_TIMESTAMP('%Y-%m', created_at) as month,
            COUNT(*) as pageviews
        FROM `fagtorsdag-prod-81a6.umami_student.event`
        WHERE website_id = '$AKSEL_ID'
            AND event_type = 1
            AND created_at >= '2025-01-01'
        GROUP BY month
        ORDER BY month
    """.trimIndent(),
)

fun CostValidateLLmEstimator(
    modelName: String,
    bigquery: BigQuery = defaultBigQuery(),
    debugLog: (String) -> Unit = ::println
): Double = runBlocking {
    val schemaService = BigQuerySchemaServiceMock()
    val websites = schemaService.getWebsites()

    val ollamaClient = OllamaClient(
        baseUrl = System.getenv("OLLAMA_BASE_URL") ?: Routes.ollamaUrl,
        model = modelName
    )
    val ragService = UmamiRAGService(ollamaClient, schemaService)

    val optimizedQueries = getOptimizedQueries()

    debugLog("--- Calculating expected costs from reference queries ---")
    val expectedCosts = optimizedQueries.mapIndexed { index, (name, sql) ->
        val costMB = estimateCostInMB(sql, bigquery)
        debugLog("  Cost test ${index + 1}/${optimizedQueries.size} $name: ${costMB.format(2)} MB")
        costMB
    }

    val testCases = optimizedQueries.mapIndexed { index, (_, _) ->
        val question = when (index) {
            0 -> "Hvem bruker siden og hva slags utstyr har de?"
            1 -> "Hva skjer på siden?"
            2 -> "Hvilke sider finnes på nettstedet?"
            3 -> "Hva gjør brukerne dag for dag?"
            4 -> "Hvordan beveger brukerne seg rundt på siden?"
            5 -> "Hvor kommer trafikken fra?"
            6 -> "Hva slags teknologi bruker de som besøker siden?"
            7 -> "Hvor i verden er brukerne?"
            8 -> "Hva interagerer brukerne med?"
            9 -> "Hvor populær er siden?"
            else -> ""
        }
        CostTestCase(question = question, url = "https://aksel.nav.no", expectedCostMB = expectedCosts[index])
    }

    val results = mutableListOf<CostResult>()

    testCases.forEachIndexed { index, testCase ->
        debugLog("  Cost test ${index + 1}/${testCases.size}: ${testCase.question}")
        val sql = ragService.generateSQL(testCase.question, testCase.url, websites)

        val costMB = if (isSqlQueryValid(sql)) {
            try {
                val c = estimateCostInMB(sql, bigquery)
                debugLog("    ${c.format(2)} MB (expected: ${testCase.expectedCostMB.format(2)} MB)")
                c
            } catch (e: Exception) {
                debugLog("    failed - ${e.message}")
                null
            }
        } else {
            debugLog("    invalid SQL")
            null
        }

        if (costMB != null) {
            val withinRange = costMB <= testCase.expectedCostMB * 1.5
            results.add(CostResult(testCase.question, costMB, testCase.expectedCostMB, withinRange))
            debugLog("  → ${if (withinRange) "PASS ✓" else "FAIL ✗"}")
        }
    }

    val totalCost = if (results.isNotEmpty()) results.sumOf { it.averageCostMB } else 0.0
    debugLog("  Total cost: ${totalCost.format(2)} MB")
    totalCost
}

private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
