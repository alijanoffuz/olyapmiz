package com.example.lifedots.wallpaper

/**
 * All "where does the calendar fit on this screen" maths in one place.
 *
 * Why this exists. Samsung's lockscreen clock occupies different
 * proportions of screen real-estate depending on the device class:
 *
 *   HD+ budget phones (Galaxy A11): clock + date area ~ 33% of screen height
 *   FHD+ flagships (S21+, A35, A36): same UI ~ 17–20% of screen height
 *
 * There's no public API for the actual clock bounds and no inset is
 * delivered to a wallpaper service for the lockscreen-only widgets. We
 * use a combination of:
 *
 *   - `aspect = height / width`  → tall phones vs. shorter ones
 *   - `density`                  → HD+ vs FHD+ vs QHD+ panels
 *   - `widthPx`                  → 720px-class vs 1080px vs 1440px
 *   - WindowInsets at the engine surface (caller passes them in)
 *
 * The renderer applies the returned `safeTopPx` as the GRID's starting
 * Y. The user's slider then translates the whole grid further by
 * `slider% × canvas.height` after the layout is finalized. So
 * `effective_top = safeTopPx + slider% × height`.
 *
 * Adding a new device class. Read the screenshot of the offending
 * device, measure where the lockscreen clock+date ends as a pixel
 * count, add a branch keyed on whichever of `aspect`, `density`, or
 * `widthPx` separates it cleanly. Stay declarative; never lookup table
 * by `Build.MODEL` — that drifts the moment Samsung renames a SKU.
 */
data class CalendarLayout(
    /** Pixel padding on each horizontal side of the month-grid block. */
    val paddingXPx: Float,
    /** Pixel offset from canvas top where the GRID begins drawing (pre-slider). */
    val safeTopPx: Float,
    /** Pixel baseline of the bottommost stats line. */
    val statsBottomBaselinePx: Float,
    /** Multiplier on `dotSize` used to compute the gap between dots in a month grid. */
    val dotGapRatio: Float,
    /** Hard upper bound on dot diameter, in pixels. */
    val dotSizeCapPx: Float,
) {
    companion object {

        /**
         * Compute layout for the given canvas dimensions + system insets.
         *
         * @param widthPx canvas width in pixels
         * @param heightPx canvas height in pixels
         * @param topOffsetPx height of the goals-at-top widget area (0 if disabled)
         * @param bottomOffsetPx height of the goals-at-bottom + footer area
         * @param systemSafeInsetTopPx WindowInsets top inset, or 0 if not available
         * @param systemSafeInsetBottomPx WindowInsets bottom inset, or 0
         * @param systemSafeInsetLeftPx WindowInsets left inset (display cutouts), or 0
         * @param systemSafeInsetRightPx WindowInsets right inset, or 0
         */
        fun compute(
            widthPx: Int,
            heightPx: Int,
            topOffsetPx: Float,
            bottomOffsetPx: Float,
            systemSafeInsetTopPx: Int,
            systemSafeInsetBottomPx: Int,
            systemSafeInsetLeftPx: Int,
            systemSafeInsetRightPx: Int,
        ): CalendarLayout {
            val width = widthPx.toFloat()
            val height = heightPx.toFloat()
            val aspect = height / width

            // Horizontal padding. Tighter on tall aspect ratios (S21+/A35/A36)
            // so the 7-column month grid has air; looser on shorter aspects.
            val paddingXRatio = when {
                aspect > 2.1f -> 0.12f
                aspect > 2.0f -> 0.15f
                else -> 0.18f
            }
            val paddingXPx = maxOf(
                width * paddingXRatio,
                systemSafeInsetLeftPx.toFloat(),
                systemSafeInsetRightPx.toFloat()
            )

            // Vertical safe-top. The reserved band above the grid that the
            // lockscreen clock/date typically occupies. Empirical from
            // device screenshots:
            //   aspect > 2.1 (Galaxy A11 720×1560, A35 1080×2340 …): 28%
            //   aspect > 2.0 (square-ish tall phones): 25%
            //   else (foldable cover screens, tablets in portrait): 22%
            // The user's vertical-offset slider adds further translate
            // *on top* of this, so the effective starting position is
            // `safeTopPx + slider% × heightPx`.
            val safeTopRatio = when {
                aspect > 2.1f -> 0.28f
                aspect > 2.0f -> 0.25f
                else -> 0.22f
            }
            val safeTopPx = maxOf(
                height * safeTopRatio,
                topOffsetPx,
                systemSafeInsetTopPx.toFloat()
            )

            // Vertical safe-bottom band — under-display fingerprint hint
            // zone on the S21+, nav-handle on most newer Samsung phones.
            // The bottommost stats line baselines here.
            val safeBottomPx = maxOf(
                height * 0.06f,
                bottomOffsetPx,
                systemSafeInsetBottomPx.toFloat()
            )
            val statsBottomBaselinePx = height - safeBottomPx * 0.55f

            // Gap between adjacent dots in a month grid, expressed as a
            // multiplier on dotSize. Smaller phones need a tighter gap
            // or the 7-column grid overflows the month cell.
            val dotGapRatio = when {
                width <= 720f -> 0.55f
                width <= 900f -> 0.62f
                else -> 0.70f
            }

            // Hard cap on dot diameter, kept constant in px regardless
            // of density — bigger dots make the layout feel cluttered
            // on high-density displays.
            val dotSizeCapPx = 20f

            return CalendarLayout(
                paddingXPx = paddingXPx,
                safeTopPx = safeTopPx,
                statsBottomBaselinePx = statsBottomBaselinePx,
                dotGapRatio = dotGapRatio,
                dotSizeCapPx = dotSizeCapPx,
            )
        }
    }
}

/**
 * Layout result for the Umr (life-in-weeks) grid. 80 rows × 52 cols
 * of dots, no stats line at the bottom, plus a small safe-bottom for
 * the nav handle / fingerprint hint zone.
 */
data class UmrLayout(
    /** Pixel padding on each horizontal side. */
    val paddingXPx: Float,
    /** Pixel offset from canvas top where the GRID begins drawing (pre-slider). */
    val safeTopPx: Float,
    /** Pixel offset from canvas top where the GRID ends. */
    val gridBottomPx: Float,
    /** Diameter of each dot, in pixels. */
    val dotSizePx: Float,
    /** Gap between adjacent dots, in pixels (already multiplied out). */
    val dotGapPx: Float,
    /** Total grid width: 52*dot + 51*gap. */
    val gridWidthPx: Float,
    /** Total grid height: 80*dot + 79*gap. */
    val gridHeightPx: Float,
    /** Where to start drawing the grid's leftmost column (centered in the available band). */
    val gridLeftPx: Float,
)

/**
 * Compute the Umr layout for a given canvas + insets. Mirrors the
 * `CalendarLayout.compute` style: aspect-aware safe-top, declarative
 * device-class buckets (no Build.MODEL lookups), hard pixel cap on
 * dot size to keep visual density consistent across DPI tiers.
 */
object UmrLayoutCompute {
    const val ROWS = 80
    const val COLS = 52

    /** Multiplier on dot size used to compute the gap. Smaller phones tighter. */
    private fun dotGapRatio(widthPx: Int): Float = when {
        widthPx <= 720 -> 0.55f
        widthPx <= 900 -> 0.62f
        else -> 0.70f
    }

    /** Aspect-aware top-band ratio (clock + date area on lockscreen). */
    private fun safeTopRatio(aspect: Float): Float = when {
        aspect > 2.1f -> 0.28f
        aspect > 2.0f -> 0.25f
        else -> 0.22f
    }

    /** Aspect-aware horizontal padding ratio. */
    private fun paddingXRatio(aspect: Float): Float = when {
        aspect > 2.1f -> 0.06f   // tighter than Yil's 0.12 — we need width for 52 cols
        aspect > 2.0f -> 0.08f
        else -> 0.10f
    }

    /** Hard cap on dot diameter — keeps 4160 dots from looking chunky on high-DPI displays. */
    const val DOT_SIZE_CAP_PX = 12f

    fun compute(
        widthPx: Int,
        heightPx: Int,
        topOffsetPx: Float,
        bottomOffsetPx: Float,
        systemSafeInsetTopPx: Int,
        systemSafeInsetBottomPx: Int,
        systemSafeInsetLeftPx: Int,
        systemSafeInsetRightPx: Int,
    ): UmrLayout {
        val width = widthPx.toFloat()
        val height = heightPx.toFloat()
        val aspect = if (width > 0f) height / width else 2.0f

        val paddingXPx = maxOf(
            width * paddingXRatio(aspect),
            systemSafeInsetLeftPx.toFloat(),
            systemSafeInsetRightPx.toFloat(),
        )

        val safeTopPx = maxOf(
            height * safeTopRatio(aspect),
            topOffsetPx,
            systemSafeInsetTopPx.toFloat(),
        )

        // Bottom band: smaller than Yil because there's no stats line.
        val safeBottomPx = maxOf(
            height * 0.06f,
            bottomOffsetPx,
            systemSafeInsetBottomPx.toFloat(),
        )

        val availWidth = (width - 2f * paddingXPx).coerceAtLeast(1f)
        val availHeight = (height - safeTopPx - safeBottomPx).coerceAtLeast(1f)

        val gapRatio = dotGapRatio(widthPx)
        // gridWidth = COLS*d + (COLS-1)*gapRatio*d  =>  d = availWidth / (COLS + (COLS-1)*gapRatio)
        val maxDotByWidth = availWidth / (COLS + (COLS - 1) * gapRatio)
        val maxDotByHeight = availHeight / (ROWS + (ROWS - 1) * gapRatio)
        val dotSizePx = minOf(maxDotByWidth, maxDotByHeight, DOT_SIZE_CAP_PX).coerceAtLeast(1f)
        val dotGapPx = dotSizePx * gapRatio

        val gridWidthPx = COLS * dotSizePx + (COLS - 1) * dotGapPx
        val gridHeightPx = ROWS * dotSizePx + (ROWS - 1) * dotGapPx
        val gridLeftPx = paddingXPx + (availWidth - gridWidthPx) / 2f
        val gridBottomPx = safeTopPx + gridHeightPx

        return UmrLayout(
            paddingXPx = paddingXPx,
            safeTopPx = safeTopPx,
            gridBottomPx = gridBottomPx,
            dotSizePx = dotSizePx,
            dotGapPx = dotGapPx,
            gridWidthPx = gridWidthPx,
            gridHeightPx = gridHeightPx,
            gridLeftPx = gridLeftPx,
        )
    }
}
