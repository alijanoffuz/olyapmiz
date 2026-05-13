package com.example.lifedots.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSwitchModeTest {

    private fun settings(
        topViewMode: TopViewMode = TopViewMode.YIL,
        enabled: Boolean = false,
        intervalMs: Long = 60_000L,
        referenceMs: Long = 0L,
        startMode: TopViewMode = TopViewMode.YIL,
        birthdayMs: Long = 1L,
    ) = WallpaperSettings(
        topViewMode = topViewMode,
        autoSwitchSettings = AutoSwitchSettings(
            enabled = enabled,
            intervalMs = intervalMs,
            referenceMs = referenceMs,
            startMode = startMode,
        ),
        umrSettings = UmrSettings(birthdayEpochMs = birthdayMs),
    )

    @Test fun `auto off returns the picked topViewMode`() {
        assertEquals(TopViewMode.YIL, currentEffectiveMode(0L, settings(topViewMode = TopViewMode.YIL)))
        assertEquals(TopViewMode.UMR, currentEffectiveMode(0L, settings(topViewMode = TopViewMode.UMR)))
    }

    @Test fun `auto on first interval shows startMode`() {
        val s = settings(enabled = true, intervalMs = 60_000L, referenceMs = 1_000L, startMode = TopViewMode.YIL)
        assertEquals(TopViewMode.YIL, currentEffectiveMode(31_000L, s))
    }

    @Test fun `auto on second interval flips`() {
        val s = settings(enabled = true, intervalMs = 60_000L, referenceMs = 0L, startMode = TopViewMode.YIL)
        assertEquals(TopViewMode.UMR, currentEffectiveMode(70_000L, s))
    }

    @Test fun `auto on third interval flips back`() {
        val s = settings(enabled = true, intervalMs = 60_000L, referenceMs = 0L, startMode = TopViewMode.YIL)
        assertEquals(TopViewMode.YIL, currentEffectiveMode(130_000L, s))
    }

    @Test fun `auto on with startMode UMR begins on UMR`() {
        val s = settings(enabled = true, intervalMs = 60_000L, referenceMs = 0L, startMode = TopViewMode.UMR)
        assertEquals(TopViewMode.UMR, currentEffectiveMode(10_000L, s))
        assertEquals(TopViewMode.YIL, currentEffectiveMode(70_000L, s))
    }

    @Test fun `birthday unset falls back to YIL even when auto is on`() {
        val s = settings(enabled = true, startMode = TopViewMode.UMR, birthdayMs = 0L)
        assertEquals(TopViewMode.YIL, currentEffectiveMode(System.currentTimeMillis(), s))
    }

    @Test fun `5 second test interval flips as expected`() {
        val s = settings(enabled = true, intervalMs = 5_000L, referenceMs = 0L, startMode = TopViewMode.YIL)
        assertEquals(TopViewMode.YIL, currentEffectiveMode(0L, s))
        assertEquals(TopViewMode.YIL, currentEffectiveMode(4_999L, s))
        assertEquals(TopViewMode.UMR, currentEffectiveMode(5_000L, s))
        assertEquals(TopViewMode.UMR, currentEffectiveMode(9_999L, s))
        assertEquals(TopViewMode.YIL, currentEffectiveMode(10_000L, s))
    }

    @Test fun `interval of zero never advances`() {
        val s = settings(enabled = true, intervalMs = 0L, referenceMs = 0L, startMode = TopViewMode.YIL)
        assertEquals(TopViewMode.YIL, currentEffectiveMode(99_999L, s))
    }

    @Test fun `negative elapsed clamps to startMode`() {
        // referenceMs is in the future (clock skew / backup restore)
        val s = settings(enabled = true, intervalMs = 60_000L, referenceMs = 90_000L, startMode = TopViewMode.YIL)
        // now = 0, so elapsed = -90_000 → ticks = -1 (odd), would flip away from startMode without the guard
        assertEquals(TopViewMode.YIL, currentEffectiveMode(0L, s))
    }
}
