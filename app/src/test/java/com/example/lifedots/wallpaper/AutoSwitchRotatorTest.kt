package com.example.lifedots.wallpaper

import com.example.lifedots.preferences.AutoSwitchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSwitchRotatorTest {

    @Test
    fun `disabled auto switch has no next boundary`() {
        val auto = AutoSwitchSettings(enabled = false, intervalMs = 60_000L, referenceMs = 0L)

        assertNull(AutoSwitchRotator.millisUntilNextBoundary(now = 0L, auto = auto))
    }

    @Test
    fun `schedules the next normal interval boundary`() {
        val auto = AutoSwitchSettings(enabled = true, intervalMs = 60_000L, referenceMs = 0L)

        assertEquals(60_000L, AutoSwitchRotator.millisUntilNextBoundary(now = 0L, auto = auto))
        assertEquals(30_000L, AutoSwitchRotator.millisUntilNextBoundary(now = 30_000L, auto = auto))
        assertEquals(60_000L, AutoSwitchRotator.millisUntilNextBoundary(now = 60_000L, auto = auto))
    }

    @Test
    fun `future reference waits until the first real flip boundary`() {
        val auto = AutoSwitchSettings(enabled = true, intervalMs = 60_000L, referenceMs = 90_000L)

        assertEquals(150_000L, AutoSwitchRotator.millisUntilNextBoundary(now = 0L, auto = auto))
        assertEquals(61_000L, AutoSwitchRotator.millisUntilNextBoundary(now = 89_000L, auto = auto))
    }
}
