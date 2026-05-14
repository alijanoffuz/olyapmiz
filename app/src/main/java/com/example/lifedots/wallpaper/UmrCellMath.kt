package com.example.lifedots.wallpaper

/**
 * Maps a birthday + "now" to a cell index on the 52x80 Umr grid.
 *
 * Contract:
 * - birthdayMs == 0L            -> -1 (unset; 0L is the sentinel)
 * - nowMs < birthdayMs          -> -1 (future birthday, treat as unset)
 * - weeks elapsed clamped to    -> 0 .. (ROWS*COLS - 1)
 *
 * Pre-1970 birthdays produce NEGATIVE epoch-ms and are perfectly valid
 * input — only 0L means "no birthday entered yet".
 *
 * Pure / side-effect-free / no allocations — safe to call from the
 * wallpaper draw path on every frame.
 */
fun weekIndexFor(birthdayMs: Long, nowMs: Long): Int {
    if (birthdayMs == 0L) return -1
    if (nowMs < birthdayMs) return -1
    val weeks = ((nowMs - birthdayMs) / WEEK_MS).toInt()
    val maxIndex = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS - 1
    return weeks.coerceAtMost(maxIndex)
}

private const val WEEK_MS: Long = 7L * 24L * 60L * 60L * 1000L
