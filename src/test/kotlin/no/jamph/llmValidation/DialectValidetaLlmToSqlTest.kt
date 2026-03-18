package no.jamph.llmValidation

import kotlin.test.Test
import kotlin.test.assertEquals

class DialectValidetaLlmToSqlTest {

    private val validSql = "SELECT url_path, COUNT(*) AS sidevisninger FROM `fagtorsdag-prod-81a6.umami_student.event` WHERE website_id = 'fb69e1e9-1bd3-4fd9-b700-9d035cbf44e1' AND event_type = 1 GROUP BY url_path ORDER BY sidevisninger DESC"
    private val invalidSql = "this is not sql"

    // 14 valid → 1.0
    @Test
    fun `all valid SQL gives 1,0`() {
        val result = DialectValidetaLlmToSql(modellName = "mock") { validSql }
        assertEquals(1.0, result)
    }

    // 14 invalid → 0.0
    @Test
    fun `all invalid SQL gives 0,0`() {
        val result = DialectValidetaLlmToSql(modellName = "mock") { invalidSql }
        assertEquals(0.0, result)
    }

    // 13 valid, 1 invalid → 13/14 ≈ 0.928
    @Test
    fun `one invalid SQL gives 0,928`() {
        var callCount = 0
        val result = DialectValidetaLlmToSql(modellName = "mock") {
            callCount++
            if (callCount == 1) invalidSql else validSql
        }
        assertEquals(13.0 / 14.0, result)
    }

    // 7 valid, 7 invalid → 0.5
    @Test
    fun `half valid SQL gives 0,5`() {
        var callCount = 0
        val result = DialectValidetaLlmToSql(modellName = "mock") {
            callCount++
            if (callCount % 2 == 0) invalidSql else validSql
        }
        assertEquals(0.5, result)
    }
}
