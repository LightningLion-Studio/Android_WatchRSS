package com.lightningstudio.watchrss.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCursorCrownAcceleratorTest {
    @Test
    fun slowTurn_scalesExistingCurveDownByFive() {
        val accelerator = NoteCursorCrownAccelerator()
        var totalSteps = 0

        repeat(49) { index ->
            totalSteps += accelerator.consume(1f, index * 300L)
        }
        assertEquals(0, totalSteps)

        totalSteps += accelerator.consume(1f, 49 * 300L)
        assertEquals(1, totalSteps)
    }

    @Test
    fun sustainedFastTurn_scalesExistingCurveDownByFive() {
        val accelerator = NoteCursorCrownAccelerator()

        assertEquals(0, accelerator.consume(1f, 0L))
        repeat(4) { index ->
            assertEquals(0, accelerator.consume(1f, (index + 1) * 40L))
        }
        assertEquals(1, accelerator.consume(1f, 5 * 40L))
    }

    @Test
    fun speedCurve_isNonlinearAndBounded() {
        assertEquals(0.02, noteCursorCharactersPerDetent(300.0), 0.000_001)
        assertEquals(0.2, noteCursorCharactersPerDetent(40.0), 0.000_001)

        val quarterSpeed = noteCursorCharactersPerDetent(200.0)
        val linearQuarterSpeed = (0.1 + (1.0 - 0.1) * 0.25) * 0.2
        assertTrue(quarterSpeed > 0.02)
        assertTrue(quarterSpeed < linearQuarterSpeed)
    }

    @Test
    fun directionChange_discardsPreviousFractionalProgress() {
        val accelerator = NoteCursorCrownAccelerator()
        repeat(49) { index ->
            assertEquals(0, accelerator.consume(1f, index * 300L))
        }

        repeat(49) { index ->
            assertEquals(0, accelerator.consume(-1f, 15_000L + index * 300L))
        }
        assertEquals(-1, accelerator.consume(-1f, 29_700L))
    }
}
