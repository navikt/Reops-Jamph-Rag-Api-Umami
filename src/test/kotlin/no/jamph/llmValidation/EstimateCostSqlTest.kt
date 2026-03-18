package no.jamph.llmValidation

import com.google.cloud.bigquery.*
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class EstimateCostSqlTest {

    @Test
    fun `returns correct MB estimate`() {
        val mockStats = mockk<JobStatistics.QueryStatistics> {
            every { totalBytesProcessed } returns (1024L * 1024L * 10L) // 10 MB
        }
        val mockJob = mockk<Job> {
            every { getStatistics<JobStatistics.QueryStatistics>() } returns mockStats
        }
        val mockBigQuery = mockk<BigQuery> {
            every { create(any<JobInfo>()) } returns mockJob
        }

        val result = estimateCostInMB("SELECT 1", mockBigQuery)
        assertEquals(10.0, result)
    }
}