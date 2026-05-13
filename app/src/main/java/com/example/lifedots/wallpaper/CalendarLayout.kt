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
