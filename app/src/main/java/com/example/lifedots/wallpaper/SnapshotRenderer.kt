package com.example.lifedots.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.ThemeOption
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.UmrVisualMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.preferences.currentEffectiveMode

/**
 * Renders a STATIC bitmap snapshot of the user's current Yil / Umr state.
 * Used by the wallpaper-apply dialog when the user picks "Lock screen only"
 * or "Home only (snapshot on lock)" — Android does not let a third-party
 * app set a live wallpaper to the lock screen alone, so we hand the system
 * a still image instead.
 *
 * This is intentionally a minimal mirror of the wallpaper engine: it draws
 * the background + the Umr dot grid (or, for Yil / unset birthdays, a
 * centred "O'lyapmiz" mark). Animations, glow, parent rings, event
 * markers, year-row gradient, footers — all skipped. The user still gets
 * the live wallpaper on home and a representative still on lock.
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
            drawTitleFallback(canvas, width, height)
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

        val position = settings.umrSettings.position
        canvas.save()
        canvas.translate(
            widthPx * (position.horizontalOffset / 100f),
            heightPx * (position.verticalOffset / 100f),
        )
        canvas.scale(position.scale, position.scale)

        val birthdayMs = settings.umrSettings.birthdayEpochMs
        val msPerWeek = 7L * 24L * 60L * 60L * 1000L
        val totalCells = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS
        val weeksLived = if (birthdayMs != 0L && now >= birthdayMs) {
            ((now - birthdayMs) / msPerWeek).toInt().coerceAtMost(totalCells - 1)
        } else -1

        val filledAlpha = (settings.umrSettings.livedAlpha * 255f).toInt().coerceIn(0, 255)
        val emptyAlpha = (settings.umrSettings.emptyAlpha * 255f).toInt().coerceIn(0, 255)
        val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeFilledDot(settings)
            alpha = filledAlpha
        }
        val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeEmptyDot(settings, isXMode = settings.umrSettings.visualMode == UmrVisualMode.X_MARKS)
            alpha = emptyAlpha
        }
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeFilledDot(settings)
            alpha = filledAlpha
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = layout.dotSizePx * 0.18f
        }

        val r = layout.dotSizePx / 2f
        val isX = settings.umrSettings.visualMode == UmrVisualMode.X_MARKS
        for (i in 0 until totalCells) {
            val (cx, cy) = UmrLayoutCompute.cellCenter(layout, i)
            if (weeksLived >= 0 && i <= weeksLived) {
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

        canvas.restore()
    }

    private fun drawTitleFallback(canvas: Canvas, widthPx: Int, heightPx: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC5A266.toInt()
            textAlign = Paint.Align.CENTER
            textSize = widthPx * 0.10f
            isSubpixelText = true
        }
        canvas.drawText("O'lyapmiz", widthPx / 2f, heightPx * 0.55f, paint)
    }

    private fun themeBackground(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> 0xFFF5F0E1.toInt()
        ThemeOption.DARK -> 0xFF1A1A1A.toInt()
        ThemeOption.AMOLED -> 0xFF000000.toInt()
        ThemeOption.CUSTOM -> settings.customColors.backgroundColor
    }

    private fun themeFilledDot(settings: WallpaperSettings): Int = when (settings.theme) {
        ThemeOption.LIGHT -> 0xFF333333.toInt()
        ThemeOption.DARK -> 0xFFE0E0E0.toInt()
        ThemeOption.AMOLED -> 0xFFFFC62E.toInt()
        ThemeOption.CUSTOM -> settings.customColors.filledDotColor
    }

    private fun themeEmptyDot(settings: WallpaperSettings, isXMode: Boolean): Int = when {
        isXMode -> 0xFFFFFFFF.toInt()
        settings.theme == ThemeOption.LIGHT -> 0xFFBBBBBB.toInt()
        settings.theme == ThemeOption.DARK -> 0xFF555555.toInt()
        settings.theme == ThemeOption.AMOLED -> 0xFF3A3A3A.toInt()
        else -> settings.customColors.emptyDotColor
    }
}
