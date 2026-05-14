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
        // gridBottomPx is safeTopPx + gridHeightPx; it must not encroach on the bottom safe band.
        // Bottom band is at least 6% of height by spec, so gridBottomPx must stay above 94% of height.
        val bottomLimit = 2400f * 0.94f
        assertTrue(
            "grid bottom ${l.gridBottomPx} must stay above bottom safe band ($bottomLimit)",
            l.gridBottomPx <= bottomLimit + 0.5f
        )
    }

    @Test fun `Galaxy A11 at 720x1560 stays within bounds`() {
        val l = compute(720, 1560)
        assertTrue(l.gridWidthPx <= 720f - 2f * l.paddingXPx + 0.5f)
        assertTrue("grid bottom ${l.gridBottomPx} must stay above 94% of 1560",
            l.gridBottomPx <= 1560f * 0.94f + 0.5f)
        assertTrue(l.dotSizePx >= 1f)
    }

    @Test fun `dot size never exceeds the hard cap`() {
        val l = compute(4000, 8000)
        assertTrue(
            "dotSize ${l.dotSizePx} must be <= ${UmrLayoutCompute.DOT_SIZE_CAP_PX}px",
            l.dotSizePx <= UmrLayoutCompute.DOT_SIZE_CAP_PX + 0.001f
        )
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

    @Test fun `counter band is reserved above the grid on tall phones`() {
        val l = compute(1080, 2400)
        assertTrue("counter band must reserve > 0px on a 1080x2400 canvas", l.counterBandHeightPx > 0f)
        assertEquals(
            "gridTopPx must equal safeTopPx + counterBandHeightPx",
            l.safeTopPx + l.counterBandHeightPx,
            l.gridTopPx,
            0.5f,
        )
    }

    @Test fun `each row has a wider gap every 4 weeks for month grouping`() {
        val l = compute(1080, 2400)
        assertTrue("month gap must be wider than regular gap", l.monthGapPx > l.dotGapPx)
        // 13 groups of 4 in 52 cols => 12 month-gap insertions per row
        val regularGaps = (UmrLayoutCompute.COLS - 1) - 12
        val expectedWidth = UmrLayoutCompute.COLS * l.dotSizePx +
                            regularGaps * l.dotGapPx +
                            12 * l.monthGapPx
        assertEquals(
            "gridWidthPx must include month gaps",
            expectedWidth,
            l.gridWidthPx,
            0.5f,
        )
    }

    @Test fun `cellCenter shifts horizontally past month boundaries`() {
        val l = compute(1080, 2400)
        val (cx3, _) = UmrLayoutCompute.cellCenter(l, 3)   // last cell in group 0
        val (cx4, _) = UmrLayoutCompute.cellCenter(l, 4)   // first cell in group 1
        // Gap between cell 3 and cell 4 should equal dotSize + monthGap, not dotSize + dotGap.
        val gap34 = (cx4 - cx3) - l.dotSizePx
        assertEquals("col 3 -> col 4 must use month gap", l.monthGapPx, gap34, 0.5f)
    }
}
