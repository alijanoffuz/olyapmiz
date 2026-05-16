package com.example.lifedots.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.ThemeOption
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.UmrVisualMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.preferences.currentEffectiveMode

/**
 * Renders a STATIC bitmap snapshot of the user's current Umr state.
 * Used by the wallpaper-apply dialog when the user picks "Lock screen only".
 *
 * Mirrors the wallpaper engine's drawUmrView as closely as a still image
 * can: same theme colour palette, same Umr layout maths, same dot/X mode,
 * year-row gradient, parent rings, event markers, and 3-counter band.
 * Animations + glow are downgraded to single-pass paints because there
 * isn't a frame loop on a still image.
 *
 * Yil mode + unset birthday fall back to a centred "O'lyapmiz" mark.
 */
object SnapshotRenderer {

    fun render(context: Context, width: Int, height: Int): Bitmap {
        val prefs = LifeDotsPreferences.getInstance(context)
        val settings = prefs.settings
        val now = System.currentTimeMillis()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(themeBackground(settings))

        val mode = currentEffectiveMode(now, settings)
        if (mode == TopViewMode.UMR && settings.umrSettings.birthdayEpochMs != 0L) {
            drawUmrSnapshot(canvas, settings, width, height, now)
        } else {
            drawTitleFallback(canvas, settings, width, height)
        }
        return bitmap
    }

    private fun drawUmrSnapshot(
        canvas: Canvas,
        settings: WallpaperSettings,
        widthPx: Int,
        heightPx: Int,
        now: Long,
    ) {
        val layout = UmrLayoutCompute.compute(
            widthPx = widthPx,
            heightPx = heightPx,
            topOffsetPx = 0f,
            bottomOffsetPx = 0f,
            systemSafeInsetTopPx = 0,
            systemSafeInsetBottomPx = 0,
            systemSafeInsetLeftPx = 0,
            systemSafeInsetRightPx = 0,
        )

        val filledColor = themeFilledDot(settings)
        val emptyColor = themeEmptyDot(settings)
        val isX = settings.umrSettings.visualMode == UmrVisualMode.X_MARKS
        val filledAlpha = (settings.umrSettings.livedAlpha * 255f).toInt().coerceIn(0, 255)
        val emptyAlpha = (settings.umrSettings.emptyAlpha * 255f).toInt().coerceIn(0, 255)

        // 1. Counter band (3 columns) — drawn before the grid transform.
        drawCounterBand(canvas, settings, layout, widthPx, heightPx, now)

        // 2. Apply position transform for the dot grid + everything below.
        val position = settings.umrSettings.position
        canvas.save()
        canvas.translate(
            widthPx * (position.horizontalOffset / 100f),
            heightPx * (position.verticalOffset / 100f),
        )
        canvas.scale(position.scale, position.scale)

        // 3. Year-row gradient under the user's current year.
        val birthdayMs = settings.umrSettings.birthdayEpochMs
        val msPerWeek = 7L * 24L * 60L * 60L * 1000L
        val totalCells = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS
        val weeksLived = if (birthdayMs != 0L && now >= birthdayMs) {
            ((now - birthdayMs) / msPerWeek).toInt().coerceAtMost(totalCells - 1)
        } else -1
        if (weeksLived in 0 until totalCells) {
            drawYearRowGradient(canvas, layout, filledColor, weeksLived)
        }

        // 4. The cell grid.
        val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = filledColor; alpha = filledAlpha
        }
        val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isX) 0xFFFFFFFF.toInt() else emptyColor
            alpha = emptyAlpha
        }
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = filledColor; alpha = filledAlpha
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
            strokeWidth = layout.dotSizePx * 0.18f
        }
        val r = layout.dotSizePx / 2f
        for (i in 0 until totalCells) {
            val (cx, cy) = UmrLayoutCompute.cellCenter(layout, i)
            val lived = weeksLived >= 0 && i <= weeksLived
            if (lived) {
                if (isX) {
                    val s = r * 0.85f
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, crossPaint)
                    canvas.drawLine(cx - s, cy + s, cx + s, cy - s, crossPaint)
                } else {
                    canvas.drawCircle(cx, cy, r, filledPaint)
                }
            } else {
                canvas.drawCircle(cx, cy, r, emptyPaint)
            }
        }

        // 5. Parent rings (mom = red, dad = blue), filled + glow.
        drawParentMarker(
            canvas, layout, settings.umrSettings.momBirthdayEpochMs, now,
            0xFFE53935.toInt(), isX,
        )
        drawParentMarker(
            canvas, layout, settings.umrSettings.dadBirthdayEpochMs, now,
            0xFF2D75A8.toInt(), isX,
        )

        // 6. Event markers (each in its own colour).
        if (settings.eventSettings.enabled) {
            for (event in settings.eventSettings.events) {
                val cell = weekIndexFor(birthdayMs, event.targetDate)
                if (cell < 0) continue
                val (cx, cy) = UmrLayoutCompute.cellCenter(layout, cell)
                drawEventMarker(canvas, cx, cy, r, event.color, isX, layout)
            }
        }

        canvas.restore()
    }

    private fun drawYearRowGradient(
        canvas: Canvas,
        layout: UmrLayout,
        filledColor: Int,
        weeksLived: Int,
    ) {
        val yourRow = weeksLived / UmrLayoutCompute.COLS
        val rowTop = layout.gridTopPx + yourRow * (layout.dotSizePx + layout.dotGapPx)
        val rowBottom = rowTop + layout.dotSizePx
        val pad = layout.dotSizePx * 0.75f
        val r = (filledColor shr 16) and 0xFF
        val g = (filledColor shr 8) and 0xFF
        val b = filledColor and 0xFF
        val mid = Color.argb(64, r, g, b)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                layout.gridLeftPx, 0f, layout.gridLeftPx + layout.gridWidthPx, 0f,
                intArrayOf(0x00000000, mid, 0x00000000),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(
            layout.gridLeftPx, rowTop - pad,
            layout.gridLeftPx + layout.gridWidthPx, rowBottom + pad,
            paint,
        )
    }

    private fun drawParentMarker(
        canvas: Canvas,
        layout: UmrLayout,
        birthdayMs: Long,
        now: Long,
        color: Int,
        isX: Boolean,
    ) {
        val cell = weekIndexFor(birthdayMs, now)
        if (cell < 0) return
        val (cx, cy) = UmrLayoutCompute.cellCenter(layout, cell)
        val r = layout.dotSizePx / 2f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            alpha = 150
            maskFilter = BlurMaskFilter(layout.dotSizePx * 1.6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, r * 2.3f, glowPaint)
        if (isX) {
            val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
                this.color = color; alpha = 255
                strokeWidth = layout.dotSizePx * 0.32f
            }
            val s = r * 0.95f
            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, crossPaint)
            canvas.drawLine(cx - s, cy + s, cx + s, cy - s, crossPaint)
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; this.color = color; alpha = 255
            }
            canvas.drawCircle(cx, cy, r, fillPaint)
        }
    }

    private fun drawEventMarker(
        canvas: Canvas,
        cx: Float, cy: Float, r: Float,
        eventColor: Int,
        isX: Boolean,
        layout: UmrLayout,
    ) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = eventColor; alpha = 150
            maskFilter = BlurMaskFilter(layout.dotSizePx * 1.6f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, r * 2.3f, glow)
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = eventColor; alpha = 255
        }
        canvas.drawCircle(cx, cy, r, solid)
        if (isX) {
            val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
                color = 0xFFFFFFFF.toInt(); alpha = 235
                strokeWidth = layout.dotSizePx * 0.20f
            }
            val s = r * 0.55f
            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, cross)
            canvas.drawLine(cx - s, cy + s, cx + s, cy - s, cross)
        }
    }

    private fun drawCounterBand(
        canvas: Canvas,
        settings: WallpaperSettings,
        layout: UmrLayout,
        widthPx: Int,
        heightPx: Int,
        now: Long,
    ) {
        val statsShift = heightPx * (settings.umrSettings.statsBandOffset / 100f)
        val bandTop = layout.counterBandTopPx + statsShift
        val bandBottom = layout.gridTopPx + statsShift
        val bandHeight = (bandBottom - bandTop).coerceAtLeast(1f)
        val textSize = bandHeight * 0.30f

        val filledColor = themeFilledDot(settings)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            this.textSize = textSize
            color = filledColor
        }
        val swatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val totalWeeks = settings.umrSettings.totalWeeks
        val you = weekIndexFor(settings.umrSettings.birthdayEpochMs, now).let { if (it < 0) null else it }
        val mom = weekIndexFor(settings.umrSettings.momBirthdayEpochMs, now).let { if (it < 0) null else it }
        val dad = weekIndexFor(settings.umrSettings.dadBirthdayEpochMs, now).let { if (it < 0) null else it }

        val cellWidth = widthPx.toFloat() / 3f
        val swatchRadius = textSize * 0.32f
        val swatchY = bandTop + bandHeight * 0.38f
        val numberY = bandTop + bandHeight * 0.55f
        val labelY = bandTop + bandHeight * 0.88f

        data class Col(val label: String, val weeks: Int?, val color: Int)
        val cols = listOf(
            Col("Me",  you, filledColor),
            Col("Mom", mom, 0xFFE53935.toInt()),
            Col("Dad", dad, 0xFF2D75A8.toInt()),
        )
        cols.forEachIndexed { idx, c ->
            val cx = cellWidth * (idx + 0.5f)
            val swatchX = cellWidth * idx + cellWidth * 0.12f
            val numberText = if (c.weeks == null) "— / $totalWeeks" else "${c.weeks} / $totalWeeks"
            text.alpha = if (c.weeks == null) 100 else 230
            swatch.color = c.color
            swatch.alpha = if (c.weeks == null) 80 else 220
            canvas.drawCircle(swatchX, swatchY, swatchRadius, swatch)
            text.textSize = textSize
            canvas.drawText(numberText, cx, numberY, text)
            text.textSize = textSize * 0.55f
            canvas.drawText(c.label, cx, labelY, text)
        }
    }

    private fun drawTitleFallback(
        canvas: Canvas,
        settings: WallpaperSettings,
        widthPx: Int,
        heightPx: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeFilledDot(settings)
            textAlign = Paint.Align.CENTER
            textSize = widthPx * 0.10f
            isSubpixelText = true
        }
        canvas.drawText("O'lyapmiz", widthPx / 2f, heightPx * 0.55f, paint)
    }

    // ---- Theme palette — EXACTLY mirrors LifeDotsWallpaperService.getThemeColors. ----

    private fun themeBackground(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> Color.parseColor("#F5F5F5")
        ThemeOption.DARK -> Color.parseColor("#1A1A1A")
        ThemeOption.AMOLED -> Color.parseColor("#000000")
        ThemeOption.CUSTOM -> settings.customColors.backgroundColor
    }

    private fun themeFilledDot(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> Color.parseColor("#2C2C2C")
        ThemeOption.DARK -> Color.parseColor("#E0E0E0")
        ThemeOption.AMOLED -> Color.parseColor("#FFFFFF")
        ThemeOption.CUSTOM -> settings.customColors.filledDotColor
    }

    private fun themeEmptyDot(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> Color.parseColor("#D0D0D0")
        ThemeOption.DARK -> Color.parseColor("#3A3A3A")
        ThemeOption.AMOLED -> Color.parseColor("#2A2A2A")
        ThemeOption.CUSTOM -> settings.customColors.emptyDotColor
    }
}
