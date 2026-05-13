package com.example.lifedots.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UmrLayoutComputeTest {

    private fun compute(w: Int, h: Int) = UmrLayoutCompute.compute(
        widthPx = w, heightPx = h,
        topOffsetPx = 0f, bottomOffsetPx = 0f,
        systemSafeInsetTopPx = 0, systemSafeInsetBottomPx = 0,
        systemSafeInsetLeftPx = 0, systemSafeInsetRightPx = 0,
    )

    @Test fun `S21+ at 1080x2400 fits the grid horizontally`() {
        val l = compute(1080, 2400)
        assertTrue("grid width ${l.gridWidthPx} must fit in available width",
            l.gridWidthPx <= 1080f - 2f * l.paddingXPx + 0.5f)
    }

    @Test fun `S21+ grid also fits vertically between safeTop and bottom`() {
        val l = compute(1080, 2400)
        val available = 2400f - l.safeTopPx - 2400f * 0.06f
        assertTrue("grid height ${l.gridHeightPx} must fit in $available",
            l.gridHeightPx <= available + 0.5f)
    }

    @Test fun `Galaxy A11 at 720x1560 stays within bounds`() {
        val l = compute(720, 1560)
        assertTrue(l.gridWidthPx <= 720f - 2f * l.paddingXPx + 0.5f)
        assertTrue(l.gridHeightPx > 0f)
        assertTrue(l.dotSizePx >= 1f)
    }

    @Test fun `dot size never exceeds the hard cap`() {
        val l = compute(4000, 8000)
        assertTrue("dotSize ${l.dotSizePx} must be <= cap (12px)", l.dotSizePx <= 12f + 0.001f)
    }

    @Test fun `grid is horizontally centered in the available band`() {
        val l = compute(1080, 2400)
        val availWidth = 1080f - 2f * l.paddingXPx
        val centerSlack = availWidth - l.gridWidthPx
        assertEquals(l.paddingXPx + centerSlack / 2f, l.gridLeftPx, 0.5f)
    }

    @Test fun `tighter padding than CalendarLayout — 52 cols needs the width`() {
        val l = compute(1080, 2400)
        assertTrue("Umr padding ${l.paddingXPx} should be tighter than Yil's 12% (130px)",
            l.paddingXPx < 100f)
    }

    @Test fun `zero canvas size produces a sane fallback rather than NaN`() {
        val l = compute(0, 0)
        assertTrue(l.dotSizePx >= 1f)
        assertTrue(l.gridWidthPx >= 0f)
    }
}
