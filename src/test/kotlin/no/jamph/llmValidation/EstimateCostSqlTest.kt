package no.jamph.llmValidation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstimateCostSqlTest {
    
    @Test
    fun `invalid SQL returns 0`() {
        assertEquals(0.0, estimateCostInMB("NOT VALID SQL!!!"))
    }
}