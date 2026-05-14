package com.example.lifedots.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class UmrCellMathTest {

    private val weekMs = 7L * 24L * 60L * 60L * 1000L

    @Test fun `unset birthday returns -1`() {
        assertEquals(-1, weekIndexFor(birthdayMs = 0L, nowMs = 1_700_000_000_000L))
    }

    @Test fun `future birthday returns -1`() {
        assertEquals(-1, weekIndexFor(birthdayMs = 2_000_000_000_000L, nowMs = 1_700_000_000_000L))
    }

    @Test fun `same instant returns cell 0`() {
        val t = 1_500_000_000_000L
        assertEquals(0, weekIndexFor(birthdayMs = t, nowMs = t))
    }

    @Test fun `one week later returns cell 1`() {
        val birth = 1_500_000_000_000L
        assertEquals(1, weekIndexFor(birthdayMs = birth, nowMs = birth + weekMs))
    }

    @Test fun `50 years yields about 2600 cells`() {
        val birth = 1_500_000_000_000L
        val nowMs = birth + 50L * 52L * weekMs
        assertEquals(50 * 52, weekIndexFor(birthdayMs = birth, nowMs = nowMs))
    }

    @Test fun `120-year-old clamps to last cell`() {
        val totalCells = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS
        val birth = 1_000_000_000_000L
        val nowMs = birth + 120L * 52L * weekMs
        assertEquals(totalCells - 1, weekIndexFor(birthdayMs = birth, nowMs = nowMs))
    }
}
