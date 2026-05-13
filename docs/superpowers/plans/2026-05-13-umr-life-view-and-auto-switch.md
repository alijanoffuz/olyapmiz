# Umr Life View + Yil/Umr Auto-Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second top-level wallpaper mode "Umr" (life-in-80-years-as-4160-weeks grid) alongside the existing "Yil" (yearly calendar) view, plus a wall-clock-driven auto-switch between the two with a configurable interval.

**Architecture:** A pure-function `currentEffectiveMode(now, settings)` resolves which mode is current from wall-clock + saved settings — the engine reads this on every draw and never holds rotation state. A new `AutoSwitchRotator` owns `Handler.postDelayed` + `AlarmManager.setExactAndAllowWhileIdle` scheduling that triggers redraws at interval boundaries. The Umr renderer reuses existing theme/color/position settings and extends `CalendarLayout` with `UmrLayoutCompute` for the 80×52 grid sizing. Settings UI gains a fixed top section (pill toggle + auto-switch + interval) and a mode-aware Umr section (birthday picker, sticky CTA until set).

**Tech Stack:** Kotlin, Jetpack Compose, Android `WallpaperService`, `SharedPreferences` singleton + `StateFlow`, `AlarmManager`, JUnit 4 (already configured for tests).

**Reference docs:** [spec](../specs/2026-05-13-umr-life-view-and-auto-switch-design.md), [PROJECT_NOTES.md](../../../PROJECT_NOTES.md)

---

## File Structure

### New files

- `app/src/main/java/com/example/lifedots/wallpaper/AutoSwitchRotator.kt` — owns Handler.postDelayed + AlarmManager.setExactAndAllowWhileIdle for interval flips
- `app/src/main/java/com/example/lifedots/ui/components/ModeTogglePill.kt` — Yil/Umr styled pill toggle
- `app/src/test/java/com/example/lifedots/preferences/AutoSwitchModeTest.kt` — pure-logic tests for `currentEffectiveMode`
- `app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt` — pure-logic tests for the layout solver

### Modified files

- `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt` — new enum + data classes added to settings tree, new pref keys + setters, `currentEffectiveMode` top-level function, migration v9 bump
- `app/src/main/java/com/example/lifedots/wallpaper/CalendarLayout.kt` — append `UmrLayout` data class + `UmrLayoutCompute.compute(...)` object
- `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt` — top-of-`drawDots` dispatch by `currentEffectiveMode`, new `drawUmrView`, wire `AutoSwitchRotator` lifecycle
- `app/src/main/java/com/example/lifedots/receiver/DateChangeReceiver.kt` — handle new `ACTION_AUTO_SWITCH_TICK`, re-arm auto-switch on boot
- `app/src/main/AndroidManifest.xml` — add `ACTION_AUTO_SWITCH_TICK` to the existing `DateChangeReceiver` intent-filter
- `app/src/main/java/com/example/lifedots/SettingsActivity.kt` — new top section (pill toggle, auto-switch switch, interval picker); mode-aware existing-section visibility; new Umr section with birthday picker

---

## Task 1: Data model + rotation formula (pure logic + tests)

**Goal:** Establish all new types, the wall-clock `currentEffectiveMode` resolver, new pref keys + setters, and the v9 migration. Cover the formula with JUnit tests. App still behaves identically (Yil default; auto-switch off) — this task lays the foundation only.

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt`
- Create: `app/src/test/java/com/example/lifedots/preferences/AutoSwitchModeTest.kt`

- [ ] **Step 1.1: Add enum + data classes near the top of `LifeDotsPreferences.kt`**

Insert after `enum class VisualTheme { ... }` (around line 208), before `data class WallpaperSettings`:

```kotlin
enum class TopViewMode { YIL, UMR }

data class AutoSwitchSettings(
    val enabled: Boolean = false,
    val intervalMs: Long = 5_000L,           // 5s test default; user picks from a fixed list
    val referenceMs: Long = 0L,               // wall-clock when auto was last enabled
    val startMode: TopViewMode = TopViewMode.YIL,
)

data class UmrSettings(
    val birthdayEpochMs: Long = 0L,          // 0L means "not set"
)
```

- [ ] **Step 1.2: Add new fields to `WallpaperSettings`**

Append to the `WallpaperSettings` data class (currently around line 210–233), at the end of the parameter list, before the closing paren:

```kotlin
    val topViewMode: TopViewMode = TopViewMode.YIL,
    val autoSwitchSettings: AutoSwitchSettings = AutoSwitchSettings(),
    val umrSettings: UmrSettings = UmrSettings(),
```

- [ ] **Step 1.3: Add the pure-function resolver as a top-level function**

Place it just below the `WallpaperSettings` data class, above `class LifeDotsPreferences`. This is the single source of truth for "which mode is the wallpaper showing right now":

```kotlin
/**
 * Resolve which view mode is currently effective.
 *
 * When auto-switch is off, returns the user-picked `topViewMode`.
 * When auto-switch is on, returns the mode based on a wall-clock
 * formula: `mode = floor((now - referenceMs) / intervalMs) % 2`,
 * starting from `startMode`. This is a pure function and is called
 * by the wallpaper engine on every draw. No state, no drift.
 *
 * Safety: if auto-switch is enabled but birthday is unset, returns
 * YIL — Umr's "weeks lived" can't be computed without it.
 */
fun currentEffectiveMode(now: Long, settings: WallpaperSettings): TopViewMode {
    val auto = settings.autoSwitchSettings
    if (!auto.enabled) return settings.topViewMode
    if (settings.umrSettings.birthdayEpochMs == 0L) return TopViewMode.YIL
    val elapsed = now - auto.referenceMs
    val ticks = if (auto.intervalMs > 0L) elapsed / auto.intervalMs else 0L
    val startIsYil = auto.startMode == TopViewMode.YIL
    val onStartSide = (ticks % 2L) == 0L
    return when {
        onStartSide && startIsYil -> TopViewMode.YIL
        onStartSide && !startIsYil -> TopViewMode.UMR
        !onStartSide && startIsYil -> TopViewMode.UMR
        else -> TopViewMode.YIL
    }
}
```

- [ ] **Step 1.4: Add the new pref-key constants alongside existing ones**

In the `companion object` of `LifeDotsPreferences` (look near the existing `KEY_VIEW_MODE`, `KEY_HIGHLIGHT_TODAY` etc.), add:

```kotlin
private const val KEY_TOP_VIEW_MODE = "top_view_mode"
private const val KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
private const val KEY_AUTO_SWITCH_INTERVAL_MS = "auto_switch_interval_ms"
private const val KEY_AUTO_SWITCH_REFERENCE_MS = "auto_switch_reference_ms"
private const val KEY_AUTO_SWITCH_START_MODE = "auto_switch_start_mode"
private const val KEY_UMR_BIRTHDAY_MS = "umr_birthday_ms"
```

- [ ] **Step 1.5: Bump `CURRENT_MIGRATION_VERSION` and add a v9 block**

Find `private const val CURRENT_MIGRATION_VERSION = 8` and change it to `9`. Then in `runMigrationsIfNeeded()`, after the existing `if (stored < 8) { ... }` block and before the final `editor.putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION).apply()`, add:

```kotlin
if (stored < 9) {
    // v9: introduces Umr life view + Yil/Umr auto-switch. No destructive
    // writes — fresh installs and upgraders both pick up the data-class
    // defaults (Yil selected, auto-switch off, birthday unset). The
    // migration just marks the schema seen so future migrations can
    // assume any v9-or-later state.
}
```

- [ ] **Step 1.6: Extend `loadSettings()` to read the new keys**

Find the existing `private fun loadSettings(): WallpaperSettings = WallpaperSettings(...)` body. At the end of the constructor call (just before the closing paren), add:

```kotlin
    topViewMode = prefs.getString(KEY_TOP_VIEW_MODE, TopViewMode.YIL.name)
        ?.let { runCatching { TopViewMode.valueOf(it) }.getOrNull() } ?: TopViewMode.YIL,
    autoSwitchSettings = AutoSwitchSettings(
        enabled = prefs.getBoolean(KEY_AUTO_SWITCH_ENABLED, false),
        intervalMs = prefs.getLong(KEY_AUTO_SWITCH_INTERVAL_MS, 5_000L),
        referenceMs = prefs.getLong(KEY_AUTO_SWITCH_REFERENCE_MS, 0L),
        startMode = prefs.getString(KEY_AUTO_SWITCH_START_MODE, TopViewMode.YIL.name)
            ?.let { runCatching { TopViewMode.valueOf(it) }.getOrNull() } ?: TopViewMode.YIL,
    ),
    umrSettings = UmrSettings(
        birthdayEpochMs = prefs.getLong(KEY_UMR_BIRTHDAY_MS, 0L),
    ),
```

- [ ] **Step 1.7: Add setters (follow the existing pattern)**

Add these in the setter section of `LifeDotsPreferences` (near the bottom of the file, alongside other `setX` methods like `setHighlightToday`):

```kotlin
fun setTopViewMode(mode: TopViewMode) {
    prefs.edit().putString(KEY_TOP_VIEW_MODE, mode.name).apply()
    _settingsFlow.value = _settingsFlow.value.copy(topViewMode = mode)
    notifyWallpaperChanged()
}

/**
 * Enable/disable the wall-clock-driven auto-switch.
 *
 * On enable, snapshot the current pick + wall-clock time into the
 * "reference" so the first interval starts on whichever side the
 * user is currently viewing. On disable, the formula falls back to
 * `topViewMode` (no state-clearing needed besides the flag).
 */
fun setAutoSwitchEnabled(enabled: Boolean) {
    val current = _settingsFlow.value
    val newAuto = if (enabled) {
        current.autoSwitchSettings.copy(
            enabled = true,
            referenceMs = System.currentTimeMillis(),
            startMode = current.topViewMode,
        )
    } else {
        current.autoSwitchSettings.copy(enabled = false)
    }
    prefs.edit()
        .putBoolean(KEY_AUTO_SWITCH_ENABLED, newAuto.enabled)
        .putLong(KEY_AUTO_SWITCH_REFERENCE_MS, newAuto.referenceMs)
        .putString(KEY_AUTO_SWITCH_START_MODE, newAuto.startMode.name)
        .apply()
    _settingsFlow.value = current.copy(autoSwitchSettings = newAuto)
    notifyWallpaperChanged()
}

/**
 * Set the auto-switch interval. Also resets reference to "now" so
 * the new interval starts cleanly from the currently-rendering mode.
 */
fun setAutoSwitchIntervalMs(intervalMs: Long) {
    val current = _settingsFlow.value
    val now = System.currentTimeMillis()
    // When interval changes, restart the cycle from the currently-rendering side.
    val nowMode = currentEffectiveMode(now, current)
    val newAuto = current.autoSwitchSettings.copy(
        intervalMs = intervalMs,
        referenceMs = now,
        startMode = nowMode,
    )
    prefs.edit()
        .putLong(KEY_AUTO_SWITCH_INTERVAL_MS, newAuto.intervalMs)
        .putLong(KEY_AUTO_SWITCH_REFERENCE_MS, newAuto.referenceMs)
        .putString(KEY_AUTO_SWITCH_START_MODE, newAuto.startMode.name)
        .apply()
    _settingsFlow.value = current.copy(autoSwitchSettings = newAuto)
    notifyWallpaperChanged()
}

fun setUmrBirthday(epochMs: Long) {
    prefs.edit().putLong(KEY_UMR_BIRTHDAY_MS, epochMs).apply()
    _settingsFlow.value = _settingsFlow.value.copy(
        umrSettings = _settingsFlow.value.umrSettings.copy(birthdayEpochMs = epochMs)
    )
    // If birthday is cleared while auto is on, the resolver already falls back
    // to YIL — no extra teardown needed.
    notifyWallpaperChanged()
}
```

- [ ] **Step 1.8: Write the failing tests**

Create `app/src/test/java/com/example/lifedots/preferences/AutoSwitchModeTest.kt`:

```kotlin
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
        birthdayMs: Long = 1L,    // non-zero so the safety fallback doesn't trip
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
        // 30s into the first interval — still on startMode
        assertEquals(TopViewMode.YIL, currentEffectiveMode(31_000L, s))
    }

    @Test fun `auto on second interval flips`() {
        val s = settings(enabled = true, intervalMs = 60_000L, referenceMs = 0L, startMode = TopViewMode.YIL)
        // 70s in — past the first 60s boundary
        assertEquals(TopViewMode.UMR, currentEffectiveMode(70_000L, s))
    }

    @Test fun `auto on third interval flips back`() {
        val s = settings(enabled = true, intervalMs = 60_000L, referenceMs = 0L, startMode = TopViewMode.YIL)
        // 130s in — past two boundaries
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
        // Defensive: zero-interval is a degenerate config, treat as "always startMode"
        assertEquals(TopViewMode.YIL, currentEffectiveMode(99_999L, s))
    }
}
```

- [ ] **Step 1.9: Run the tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.lifedots.preferences.AutoSwitchModeTest"`
Expected: 8 tests, 0 failures, 0 errors.

- [ ] **Step 1.10: Compile-check the whole app**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. No new warnings about unused types (they're referenced from `WallpaperSettings`).

- [ ] **Step 1.11: Commit**

```bash
git add app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt \
        app/src/test/java/com/example/lifedots/preferences/AutoSwitchModeTest.kt
git commit -m "$(cat <<'EOF'
umr: data model + currentEffectiveMode resolver + v9 migration

Adds TopViewMode/AutoSwitchSettings/UmrSettings, hooked into the
WallpaperSettings root. The wall-clock-driven `currentEffectiveMode`
function is the single source of truth for which mode the wallpaper
shows; the engine reads it on every draw. Migration v9 is a no-op
bump — class defaults cover both fresh installs and upgrades.

Covered by 8 JUnit tests for the resolver math, including the
birthday-unset safety fallback and the 5s test interval.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Umr layout math (extend `CalendarLayout`)

**Goal:** Add `UmrLayout` + `UmrLayoutCompute.compute(...)` to the existing layout module. Solves for a `dotSize` that fits 80 rows × 52 cols inside the screen between the aspect-aware safe-top (clock band) and a small safe-bottom (nav-handle/fingerprint). Cover with tests for representative canvas sizes.

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/CalendarLayout.kt`
- Create: `app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt`

- [ ] **Step 2.1: Append the `UmrLayout` data class to `CalendarLayout.kt`**

After the existing `CalendarLayout` companion object's closing brace at the bottom of the file, add:

```kotlin

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
    private const val DOT_SIZE_CAP_PX = 12f

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
        // gridWidth = COLS*d + (COLS-1)*gapRatio*d  =>  d = gridWidth / (COLS + (COLS-1)*gapRatio)
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
```

- [ ] **Step 2.2: Write the failing tests**

Create `app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt`:

```kotlin
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
        // 52 cols + 51 gaps fit within (width - 2*pad)
        assertTrue("grid width $l.gridWidthPx must fit in available width",
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
        // A huge canvas would naively want big dots — cap keeps them sensible.
        val l = compute(4000, 8000)
        assertTrue("dotSize ${l.dotSizePx} must be <= cap (12px)", l.dotSizePx <= 12f + 0.001f)
    }

    @Test fun `grid is horizontally centered in the available band`() {
        val l = compute(1080, 2400)
        val availWidth = 1080f - 2f * l.paddingXPx
        val centerSlack = availWidth - l.gridWidthPx
        // gridLeftPx == paddingXPx + centerSlack/2
        assertEquals(l.paddingXPx + centerSlack / 2f, l.gridLeftPx, 0.5f)
    }

    @Test fun `tighter padding than CalendarLayout — 52 cols needs the width`() {
        val l = compute(1080, 2400)   // aspect 2.22 — Yil uses 12% padding here
        // Umr uses 6% padding at this aspect, so pad should be ~65px not ~130px
        assertTrue("Umr padding ${l.paddingXPx} should be tighter than Yil's 12% (130px)",
            l.paddingXPx < 100f)
    }

    @Test fun `zero canvas size produces a sane fallback rather than NaN`() {
        val l = compute(0, 0)
        assertTrue(l.dotSizePx >= 1f)
        assertTrue(l.gridWidthPx >= 0f)
    }
}
```

- [ ] **Step 2.3: Run the tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.lifedots.wallpaper.UmrLayoutComputeTest"`
Expected: 7 tests, 0 failures.

- [ ] **Step 2.4: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/CalendarLayout.kt \
        app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt
git commit -m "$(cat <<'EOF'
umr: UmrLayoutCompute for 80x52 grid sizing

Solves for dotSize so 52 cols + 80 rows fit within available band
(aspect-aware safe-top, smaller bottom band than Yil since no stats
line). Tighter horizontal padding than Yil (6%/8%/10% vs 12/15/18)
to give 52 columns the width they need. 12px hard cap on dot size.

Covered by 7 unit tests for S21+, A11, oversize canvas, centering,
and zero-size fallback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Umr renderer + draw dispatch

**Goal:** Add the `drawUmrView(...)` path to `LifeDotsWallpaperService`. At the very top of `drawDots()`, dispatch by `currentEffectiveMode(now, settings)` — if UMR, render the 80×52 grid and return; otherwise let the existing flow continue exactly as before. Yil rendering is untouched.

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt`

- [ ] **Step 3.1: Add an import for the new types**

At the top of `LifeDotsWallpaperService.kt`, ensure these imports exist (most likely the preferences package is already imported wholesale — but be explicit if needed):

```kotlin
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.currentEffectiveMode
```

- [ ] **Step 3.2: Add a private `drawUmrView` method inside the `LifeDotsEngine` class**

Place this alongside the existing `drawCalendarView`, `drawContinuousView`, etc. — anywhere in the engine class body. Keep it self-contained:

```kotlin
private fun drawUmrView(canvas: Canvas, settings: WallpaperSettings, colors: ThemeColors) {
    val birthdayMs = settings.umrSettings.birthdayEpochMs
    val now = System.currentTimeMillis()

    val msPerWeek = 7L * 24L * 60L * 60L * 1000L
    val weeksLived = if (birthdayMs > 0L) ((now - birthdayMs) / msPerWeek).toInt() else -1
    val totalCells = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS  // 4160

    val layout = UmrLayoutCompute.compute(
        widthPx = canvas.width,
        heightPx = canvas.height,
        topOffsetPx = 0f,
        bottomOffsetPx = 0f,
        systemSafeInsetTopPx = 0,
        systemSafeInsetBottomPx = 0,
        systemSafeInsetLeftPx = 0,
        systemSafeInsetRightPx = 0,
    )

    // Background
    canvas.drawColor(colors.background)

    // Apply position transform (horizontal/vertical offset + scale) like Yil does.
    val position = settings.positionSettings
    val offsetX = canvas.width * (position.horizontalOffset / 100f)
    val offsetY = canvas.height * (position.verticalOffset / 100f)
    canvas.save()
    canvas.translate(offsetX, offsetY)
    canvas.scale(position.scale, position.scale, canvas.width / 2f, canvas.height / 2f)

    val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.filledDot
        alpha = (settings.filledDotAlpha * 255f).toInt().coerceIn(0, 255)
        style = Paint.Style.FILL
    }
    val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.emptyDot
        alpha = (settings.emptyDotAlpha * 255f).toInt().coerceIn(0, 255)
        style = Paint.Style.FILL
    }
    val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.todayDot
        style = Paint.Style.FILL
    }
    // Subtle glow for current-week dot, matching Yil's todayGlow vibe.
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.todayDot
        alpha = 80
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(layout.dotSizePx * 1.5f, BlurMaskFilter.Blur.NORMAL)
    }

    val r = layout.dotSizePx / 2f

    for (i in 0 until totalCells) {
        val row = i / UmrLayoutCompute.COLS
        val col = i % UmrLayoutCompute.COLS
        val cx = layout.gridLeftPx + col * (layout.dotSizePx + layout.dotGapPx) + r
        val cy = layout.safeTopPx + row * (layout.dotSizePx + layout.dotGapPx) + r

        when {
            // Birthday unset — render everything as future (empty).
            weeksLived < 0 -> canvas.drawCircle(cx, cy, r, emptyPaint)
            // Past
            i < weeksLived -> canvas.drawCircle(cx, cy, r, filledPaint)
            // Current week — single tinted dot with a soft glow.
            i == weeksLived -> {
                canvas.drawCircle(cx, cy, r * 1.4f, glowPaint)
                canvas.drawCircle(cx, cy, r, todayPaint)
            }
            // Future
            else -> canvas.drawCircle(cx, cy, r, emptyPaint)
        }
    }

    canvas.restore()

    // Optional shared footer text (user's signature line), if enabled.
    if (settings.footerTextSettings.enabled) {
        drawFooterText(canvas, settings.footerTextSettings, canvas.height - 40f)
    }
}
```

- [ ] **Step 3.3: Wire the dispatch at the top of `drawDots`**

In `drawDots(canvas: Canvas)` (around line 282), at the very top of the function body — before any of the existing drawing logic runs — add:

```kotlin
private fun drawDots(canvas: Canvas) {
    val settings = preferences.settings
    val colors = getThemeColors(settings)

    // NEW: top-level mode dispatch. When Umr is active, render the
    // life-in-weeks grid and return; Yil's existing flow is untouched.
    if (currentEffectiveMode(System.currentTimeMillis(), settings) == TopViewMode.UMR) {
        drawUmrView(canvas, settings, colors)
        return
    }

    // ===== Existing Yil rendering flow continues unchanged below =====
    // Draw background color first
    canvas.drawColor(colors.background)
    // ... rest of method unchanged
}
```

(Keep the rest of `drawDots` exactly as it is. The two new lines and the `if` block above them are the only additions.)

- [ ] **Step 3.4: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. New `drawUmrView` is private; only called from the dispatcher.

- [ ] **Step 3.5: Manual visual verification (requires the user's phone)**

Build a debug APK, install, then via adb manually flip `top_view_mode` and set a birthday to verify the renderer:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# pin the wallpaper as live wallpaper through the user's phone (manual)

# Then force Umr + a birthday from 25 years ago:
adb shell run-as com.example.lifedots sh -c '
  AGE_MS=$((25 * 365 * 24 * 60 * 60 * 1000))
  NOW_MS=$(date +%s)000
  BD=$((NOW_MS - AGE_MS))
  cat > shared_prefs/lifedots_prefs.xml <<XML
<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map>
  ...
</map>
XML'
```

(Note: directly overwriting the prefs file is fragile. Easier: temporarily add a debug-only "force Umr now" button to MainActivity, or use the not-yet-built Settings UI from Task 6 to drive verification. If Task 6 hasn't landed yet, defer full visual verification to Task 7.)

Expected: when forced into Umr, the lockscreen wallpaper shows an 80-row × 52-col grid of dots, with about 23×52 = 1196 dots filled (for a 23-year-old), one tinted dot at the current-week position, and the rest dim.

- [ ] **Step 3.6: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt
git commit -m "$(cat <<'EOF'
umr: render 80x52 life-in-weeks grid in wallpaper engine

drawUmrView dispatches at the top of drawDots based on
currentEffectiveMode. Past weeks use filledDotColor, future use
emptyDotColor, the current week is a tinted dot with a soft glow
matching Yil's today treatment. Birthday-unset renders everything
as future. Position transform (offset/scale) and footer text both
apply identically to Yil. Yil's existing flow is left untouched.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: AutoSwitchRotator + manifest + boot re-arm

**Goal:** New `AutoSwitchRotator` class owns the Handler + AlarmManager scheduling. The wallpaper engine creates one in `onCreate`, calls `rotator.refresh()` from `onVisibilityChanged(true)` and on settings changes. `DateChangeReceiver` handles a new `ACTION_AUTO_SWITCH_TICK` broadcast and pokes the engine to redraw. Manifest gets the new action registered. Boot re-arms the alarm via the existing `BOOT_COMPLETED` handler.

**Files:**
- Create: `app/src/main/java/com/example/lifedots/wallpaper/AutoSwitchRotator.kt`
- Modify: `app/src/main/java/com/example/lifedots/receiver/DateChangeReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt`

- [ ] **Step 4.1: Create `AutoSwitchRotator.kt`**

```kotlin
package com.example.lifedots.wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.receiver.DateChangeReceiver

/**
 * Owns the timing for Yil/Umr auto-switch.
 *
 * The mode itself is a pure function of wall-clock + settings —
 * the engine reads it on every draw and never holds rotation state
 * (see `currentEffectiveMode`). This rotator only triggers redraws
 * at interval boundaries:
 *
 *  - Handler.postDelayed: fires while wallpaper is visible. Works
 *    cleanly for the 5s test interval.
 *  - AlarmManager.setExactAndAllowWhileIdle: fires at the next
 *    boundary even while screen is off (with the OS-imposed Doze
 *    quota for short intervals — fine, the user can't see
 *    while screen is off anyway).
 *
 * Lifecycle: engine.onCreate creates one and calls refresh() on
 * every settings change + every visibility change. engine.onDestroy
 * calls cancel().
 */
class AutoSwitchRotator(
    private val context: Context,
    private val onTick: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { onTick(); reschedule() }
    private var lastSettings: WallpaperSettings? = null
    private var visibleNow: Boolean = false

    /** Call when wallpaper visibility changes. */
    fun setVisible(visible: Boolean) {
        visibleNow = visible
        reschedule()
    }

    /** Call after settings change. */
    fun refresh(settings: WallpaperSettings) {
        lastSettings = settings
        reschedule()
    }

    fun cancel() {
        handler.removeCallbacks(tickRunnable)
        cancelAlarm()
    }

    private fun reschedule() {
        handler.removeCallbacks(tickRunnable)
        val s = lastSettings ?: return
        val auto = s.autoSwitchSettings
        if (!auto.enabled || auto.intervalMs <= 0L) {
            cancelAlarm()
            return
        }
        val now = System.currentTimeMillis()
        val elapsed = now - auto.referenceMs
        val sinceLastBoundary = if (auto.intervalMs > 0L) elapsed % auto.intervalMs else 0L
        val msUntilNextBoundary = (auto.intervalMs - sinceLastBoundary).coerceAtLeast(1L)

        // Handler — only worth setting while visible.
        if (visibleNow) {
            handler.postDelayed(tickRunnable, msUntilNextBoundary)
        }

        // AlarmManager — best-effort wake-up at the boundary so we can redraw
        // the moment the user wakes the phone, even after long idle.
        scheduleAlarm(now + msUntilNextBoundary)
    }

    private fun scheduleAlarm(triggerAtMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = DateChangeReceiver.ACTION_AUTO_SWITCH_TICK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        } catch (e: SecurityException) {
            // SCHEDULE_EXACT_ALARM permission denied — fall back to inexact.
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } catch (e2: Exception) {
                Log.w("LifeDots", "AutoSwitchRotator: failed to schedule alarm", e2)
            }
        }
    }

    private fun cancelAlarm() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = DateChangeReceiver.ACTION_AUTO_SWITCH_TICK
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags) ?: return
        am.cancel(pi)
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 1042
    }
}
```

- [ ] **Step 4.2: Extend `DateChangeReceiver` to handle the new action**

Replace the existing `onReceive` body in `DateChangeReceiver.kt` to add a branch for `ACTION_AUTO_SWITCH_TICK` and also re-arm the auto-switch alarm on `BOOT_COMPLETED`. Modify the receiver as follows:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON" -> {
            Log.i("LifeDots", "Boot completed — scheduling daily refresh alarm")
            scheduleDailyAlarm(context)
            // Re-arm auto-switch by poking prefs so the engine reschedules its alarm.
            pokeAutoSwitchRedraw(context)
        }
        ACTION_DAILY_TICK,
        Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED -> {
            Log.i("LifeDots", "Date/time changed (${intent.action}) — forcing wallpaper redraw")
            pokeAutoSwitchRedraw(context)
            scheduleDailyAlarm(context)
        }
        ACTION_AUTO_SWITCH_TICK -> {
            Log.i("LifeDots", "Auto-switch tick — forcing wallpaper redraw")
            pokeAutoSwitchRedraw(context)
        }
    }
}

/**
 * Force a wallpaper redraw and let the engine reschedule its next
 * alarm — by toggling a no-op preference, we leverage the existing
 * notifyWallpaperChanged() listener chain.
 */
private fun pokeAutoSwitchRedraw(context: Context) {
    try {
        val prefs = com.example.lifedots.preferences.LifeDotsPreferences.getInstance(context)
        prefs.setHighlightToday(prefs.settings.highlightToday)
    } catch (e: Exception) {
        Log.e("LifeDots", "Failed to poke prefs on auto-switch tick", e)
    }
}
```

And add to the `companion object`:

```kotlin
const val ACTION_AUTO_SWITCH_TICK = "com.example.lifedots.AUTO_SWITCH_TICK"
```

- [ ] **Step 4.3: Add the new action to `AndroidManifest.xml`**

Find the existing `<receiver android:name=".receiver.DateChangeReceiver" ...>` block and add a new `<action>` line inside its `<intent-filter>`:

```xml
<action android:name="com.example.lifedots.AUTO_SWITCH_TICK" />
```

The existing receiver block should end up looking like:

```xml
<receiver android:name=".receiver.DateChangeReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.DATE_CHANGED" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.example.lifedots.DAILY_TICK" />
        <action android:name="com.example.lifedots.AUTO_SWITCH_TICK" />
    </intent-filter>
</receiver>
```

- [ ] **Step 4.4: Wire `AutoSwitchRotator` into the engine**

In `LifeDotsWallpaperService.kt`, inside the `LifeDotsEngine` class:

Add a private property near the top of the class body (next to `handler`, `visible`, etc.):

```kotlin
private val autoSwitchRotator = AutoSwitchRotator(applicationContext) {
    // onTick — just request a redraw; the formula will pick up the new mode.
    if (visible) draw()
}
```

In `onCreate(holder: SurfaceHolder)`, after the existing `LifeDotsPreferences.addWallpaperChangeListener(...)` line, add:

```kotlin
autoSwitchRotator.refresh(preferences.settings)
```

Modify the existing `settingsChangeListener` (around line 146) so it also refreshes the rotator. Change:

```kotlin
private val settingsChangeListener: () -> Unit = {
    if (visible) draw()
}
```

to:

```kotlin
private val settingsChangeListener: () -> Unit = {
    autoSwitchRotator.refresh(preferences.settings)
    if (visible) draw()
}
```

In `onVisibilityChanged(visible: Boolean)` (around line 179), update the rotator on every visibility change. Add at the start of the method body (after `this.visible = visible`):

```kotlin
autoSwitchRotator.setVisible(visible)
```

In `onDestroy()` (around line 171), add:

```kotlin
autoSwitchRotator.cancel()
```

- [ ] **Step 4.5: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.6: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/AutoSwitchRotator.kt \
        app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt \
        app/src/main/java/com/example/lifedots/receiver/DateChangeReceiver.kt \
        app/src/main/AndroidManifest.xml
git commit -m "$(cat <<'EOF'
umr: AutoSwitchRotator + alarm-on-boot re-arm

Handler.postDelayed fires while wallpaper is visible (clean 5s test
flips). AlarmManager.setExactAndAllowWhileIdle covers idle/Doze with
best-effort delivery at long intervals; falls back to set() if exact
isn't permitted. Engine wires the rotator into onCreate /
onVisibilityChanged / settings-change / onDestroy. DateChangeReceiver
gets a new ACTION_AUTO_SWITCH_TICK branch and re-arms on boot.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `ModeTogglePill` Compose component

**Goal:** A styled two-option pill toggle (matching the reference screenshot) used for picking Yil vs Umr at the top of Settings. Standalone Compose component; previewable.

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/components/ModeTogglePill.kt`

- [ ] **Step 5.1: Create the component**

```kotlin
package com.example.lifedots.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ModeTogglePill(
    leftLabel: String,
    rightLabel: String,
    isLeftSelected: Boolean,
    onSelect: (left: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        color = Color(0xFFEDECF1),       // soft lavender-grey, matches reference
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillSide(
                label = leftLabel,
                selected = isLeftSelected,
                onClick = { onSelect(true) },
                modifier = Modifier.weight(1f),
            )
            PillSide(
                label = rightLabel,
                selected = !isLeftSelected,
                onClick = { onSelect(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PillSide(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val bg = if (selected) Color.White else Color.Transparent
    val textColor = if (selected) Color.Black else Color(0xFF8E8C9A)
    val textWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = modifier
            .padding(4.dp)
            .height(46.dp)
            .clip(shape)
            .then(if (selected) Modifier.shadow(elevation = 2.dp, shape = shape) else Modifier)
            .background(color = bg, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = textWeight),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ModeTogglePillPreview() {
    var leftSelected by remember { mutableStateOf(false) }
    ModeTogglePill(
        leftLabel = "Avtomatik",
        rightLabel = "Bir martalik",
        isLeftSelected = leftSelected,
        onSelect = { leftSelected = it },
        modifier = Modifier.padding(16.dp),
    )
}
```

- [ ] **Step 5.2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.3: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/ModeTogglePill.kt
git commit -m "$(cat <<'EOF'
ui: ModeTogglePill component for Yil/Umr toggle

Styled two-option pill matching the reference screenshot: rounded
full-pill outer in soft lavender-grey, inactive labels muted, active
label sits in a white pilled chip with a 2dp shadow. Reusable —
caller supplies labels and selection state.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Settings UI — top section + Umr section + mode-aware visibility

**Goal:** Add the Yil/Umr pill toggle, Auto-switch toggle, and interval picker as a new section at the top of `SettingsActivity`. When Umr is selected, hide Yil-only sections (Goals, View Mode, Highlight today, Calendar columns) and show a new Umr section with a birthday picker. Enable auto-switch only when birthday is set.

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/SettingsActivity.kt`

Because `SettingsActivity.kt` is 1774 lines today, this task adds a new top section above the existing content and wraps mode-conditional sections in `if`-guards. Existing sections remain in-place unless wrapped.

- [ ] **Step 6.1: Add imports at the top of `SettingsActivity.kt`**

```kotlin
import com.example.lifedots.preferences.TopViewMode
import com.example.lifedots.preferences.AutoSwitchSettings
import com.example.lifedots.preferences.UmrSettings
import com.example.lifedots.ui.components.ModeTogglePill
import com.example.lifedots.ui.components.DatePickerDialog as LifeDotsDatePickerDialog
```

- [ ] **Step 6.2: Add the top section composable**

Find the existing top-of-scrollable-content area in `SettingsActivity` (the `LazyColumn` or `Column` with the existing settings sections). Insert a new top section as the first child:

```kotlin
@Composable
private fun ModeTopSection(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    onBirthdayNeeded: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pill toggle
        ModeTogglePill(
            leftLabel = "Yil",
            rightLabel = "Umr",
            isLeftSelected = settings.topViewMode == TopViewMode.YIL,
            onSelect = { isLeft ->
                preferences.setTopViewMode(if (isLeft) TopViewMode.YIL else TopViewMode.UMR)
            },
        )

        // Auto-switch row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Auto-switch",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = settings.autoSwitchSettings.enabled,
                onCheckedChange = { wantOn ->
                    if (wantOn && settings.umrSettings.birthdayEpochMs == 0L) {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Set your birthday first",
                                actionLabel = "Set",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) onBirthdayNeeded()
                        }
                    } else {
                        preferences.setAutoSwitchEnabled(wantOn)
                    }
                }
            )
        }

        // Interval picker — only visible when auto is on
        if (settings.autoSwitchSettings.enabled) {
            Text(
                text = "Switch every",
                style = MaterialTheme.typography.bodyMedium,
            )
            IntervalChips(
                currentMs = settings.autoSwitchSettings.intervalMs,
                onPick = { preferences.setAutoSwitchIntervalMs(it) },
            )
            Text(
                text = "Auto-switch is on — wallpaper rotates automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Divider()
}

@Composable
private fun IntervalChips(currentMs: Long, onPick: (Long) -> Unit) {
    val options = listOf(
        "5s" to 5_000L,
        "1m" to 60_000L,
        "2m" to 120_000L,
        "5m" to 300_000L,
        "30m" to 1_800_000L,
        "1h" to 3_600_000L,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (label, ms) ->
            FilterChip(
                selected = currentMs == ms,
                onClick = { onPick(ms) },
                label = { Text(label) },
            )
        }
    }
}
```

- [ ] **Step 6.3: Add the Umr section composable**

Place this next to `ModeTopSection` in the same file:

```kotlin
@Composable
private fun UmrSettingsSection(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    showBirthdayDialog: Boolean,
    onShowBirthdayDialogChange: (Boolean) -> Unit,
) {
    val birthdayMs = settings.umrSettings.birthdayEpochMs
    val isSet = birthdayMs > 0L
    val fmt = remember { java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Umr",
            style = MaterialTheme.typography.titleMedium,
        )

        // Birthday row with sticky red CTA when unset.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowBirthdayDialogChange(true) },
            shape = RoundedCornerShape(12.dp),
            color = if (isSet) MaterialTheme.colorScheme.surfaceVariant
                    else Color(0xFFE53935),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSet) "Birthday" else "Set your birthday",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSet) MaterialTheme.colorScheme.onSurfaceVariant
                                else Color.White,
                    )
                    Text(
                        text = if (isSet) fmt.format(java.util.Date(birthdayMs))
                               else "Required to render your life-in-weeks grid",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSet) MaterialTheme.colorScheme.onSurfaceVariant
                                else Color.White,
                    )
                }
            }
        }
    }
    Divider()

    if (showBirthdayDialog) {
        LifeDotsDatePickerDialog(
            initialDate = if (isSet) birthdayMs else System.currentTimeMillis() - (25L * 365 * 24 * 60 * 60 * 1000),
            onDismiss = { onShowBirthdayDialogChange(false) },
            onConfirm = { picked ->
                preferences.setUmrBirthday(picked)
                onShowBirthdayDialogChange(false)
            },
        )
    }
}
```

(Note: `LifeDotsDatePickerDialog` is the existing component at `ui/components/DatePickerDialog.kt`. If its signature differs from the call above, adjust accordingly — its parameter names may be `selectedDate`/`onDateSelected`. Read [DatePickerDialog.kt](../../../app/src/main/java/com/example/lifedots/ui/components/DatePickerDialog.kt) and match its API.)

- [ ] **Step 6.4: Wire mode-aware visibility in the main settings composable**

Find the main scrollable content in `SettingsActivity` (likely a `Column { ... }` inside `Scaffold` content). At the top of that scrollable, render:

```kotlin
val showBirthdayDialog = remember { mutableStateOf(false) }
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()
val settings by preferences.settingsFlow.collectAsState()

ModeTopSection(
    settings = settings,
    preferences = preferences,
    onBirthdayNeeded = {
        preferences.setTopViewMode(TopViewMode.UMR)
        showBirthdayDialog.value = true
    },
    snackbarHostState = snackbarHostState,
    scope = scope,
)
```

Then, immediately after that, branch the rest of the page:

```kotlin
when (settings.topViewMode) {
    TopViewMode.UMR -> {
        UmrSettingsSection(
            settings = settings,
            preferences = preferences,
            showBirthdayDialog = showBirthdayDialog.value,
            onShowBirthdayDialogChange = { showBirthdayDialog.value = it },
        )
        // Render only the shared sections in Umr mode: Theme, Position, Custom Colors, Footer Text
        ThemeSection(settings, preferences)
        PositionSection(settings, preferences)
        CustomColorsSection(settings, preferences)
        FooterTextSection(settings, preferences)
    }
    TopViewMode.YIL -> {
        // Existing full Yil sections (Goals, View Mode, Highlight Today,
        // Calendar columns, Theme, Transparency, Position, Footer, Custom
        // Colors, etc.) — render them all as today.
        YilFullSections(settings, preferences)
    }
}
```

(Note: This assumes you can group existing sections into reusable composables named `ThemeSection`, `PositionSection`, etc. If the existing code inlines them all in a giant `Column`, refactor JUST the Yil-mode body into a private `@Composable YilFullSections` that contains the existing flow verbatim — minimum movement, no behavior change. Then the Umr branch references the smaller composables it needs.)

If extracting helper composables is risky for the engineer (large file, lots of state plumbing), an acceptable simpler approach is:

```kotlin
when (settings.topViewMode) {
    TopViewMode.UMR -> UmrSettingsSection(...)   // Umr-only sections at top
    TopViewMode.YIL -> { /* nothing extra at top */ }
}
// Existing flow continues — but wrap Yil-only sections in:
if (settings.topViewMode == TopViewMode.YIL) { /* Goals section */ }
if (settings.topViewMode == TopViewMode.YIL) { /* View Mode section */ }
if (settings.topViewMode == TopViewMode.YIL) { /* Highlight Today switch */ }
if (settings.topViewMode == TopViewMode.YIL) { /* Calendar columns */ }
```

Pick whichever is less invasive to the existing file. Either is fine.

- [ ] **Step 6.5: Add the Snackbar host to `Scaffold`**

Wherever `Scaffold(...)` is invoked in `SettingsActivity`, ensure it has a `snackbarHost`:

```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    // ... existing args
) { paddingValues -> ... }
```

(If the existing code uses a different layout — e.g. plain `Surface` — add `SnackbarHost` near the top of the screen.)

- [ ] **Step 6.6: Compile-check + lint**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

If `DatePickerDialog`'s real signature differs, the compile fails — fix by matching the actual API and recompile.

- [ ] **Step 6.7: Build the debug APK and install**

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 6.8: Manual UX smoke test (on the user's S21+)**

Open the app, go to Settings. Verify:
- The Yil/Umr pill toggle is the first thing visible. Pill defaults to Yil.
- Below it: "Auto-switch" row with an OFF switch.
- Tap the pill toggle's Umr side. The page below changes: Goals, View Mode, Highlight today, Calendar columns sections disappear. A new "Umr" section appears with a red "Set your birthday" CTA.
- Tap the red CTA. Date picker opens. Pick a date 25 years ago. Confirm. Card now shows the chosen date.
- Try to toggle Auto-switch ON: it should succeed now (birthday is set).
- Clear birthday (set it to 0 or via adb), try to toggle Auto-switch ON again: should show "Set your birthday first" snackbar.
- With Auto-switch ON and 5s interval picked: lockscreen wallpaper visibly flips between Yil and Umr every 5 seconds.

- [ ] **Step 6.9: Commit**

```bash
git add app/src/main/java/com/example/lifedots/SettingsActivity.kt
git commit -m "$(cat <<'EOF'
umr: Settings UI — pill toggle, auto-switch, interval, Umr section

New top section in SettingsActivity: Yil/Umr pill (ModeTogglePill),
Auto-switch toggle, and an interval chip row (5s/1m/2m/5m/30m/1h)
that appears only when auto is on. When Umr is picked, Yil-only
sections (Goals, View Mode, Highlight today, Calendar columns)
hide; a new Umr section shows the birthday picker with a sticky
red CTA until set. Auto-switch refuses to turn on without a
birthday, surfacing a Snackbar with a "Set" action.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: End-to-end verification + cleanup

**Goal:** Verify the full feature on the user's S21+ device, capture screenshots for the PROJECT_NOTES log, and append a v1.1.0 entry to PROJECT_NOTES.md's version trajectory table.

**Files:**
- Modify: `PROJECT_NOTES.md`

- [ ] **Step 7.1: Fresh install on the S21+ and confirm migration v9 is clean**

```bash
adb uninstall com.example.lifedots
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell run-as com.example.lifedots cat shared_prefs/lifedots_prefs.xml | grep migration_version
```

Expected: `<int name="migration_version" value="9" />` (after app is launched once).

- [ ] **Step 7.2: Run through all UX flows**

Verify each in turn:

1. Fresh install: app opens to Yil. Settings page top section shows Yil/Umr pill (Yil active), Auto-switch off.
2. Flip pill to Umr: red "Set your birthday" CTA appears. Goals + View Mode + Highlight Today + Calendar columns sections are hidden.
3. Tap CTA, set birthday 25 years ago, confirm. Card shows the date.
4. Set the wallpaper as live wallpaper. Lockscreen shows 80×52 dot grid, ~22 rows filled, one tinted current-week dot, rest dim.
5. Flip pill back to Yil. Wallpaper shows existing year calendar (unchanged from before this feature).
6. Toggle Auto-switch ON with 5s interval. Lockscreen wallpaper visibly flips every 5 seconds.
7. Increase interval to 1m, lock phone for 2 minutes, wake — confirm wallpaper shows whichever mode the wall-clock dictates.
8. Disable Auto-switch. Wallpaper sticks on the currently shown mode.

- [ ] **Step 7.3: Add a `v1.1.0` row to the version trajectory table in `PROJECT_NOTES.md`**

Find section 13 "Version trajectory" in `PROJECT_NOTES.md`. Below the existing `v1.0.11` row, add:

```markdown
| `v1.1.0` | Umr (life-in-weeks) view + Yil/Umr auto-switch | New top-level wallpaper mode rendering an 80×52 grid (one dot per week of 80 years). Settings page top section adds a Yil/Umr pill toggle, an auto-switch ON/OFF, and a 5s/1m/2m/5m/30m/1h interval picker. Birthday picker gates auto-switch. Wall-clock-driven rotation engine via Handler + AlarmManager. Migration v9 (no-op bump). New: `UmrLayoutCompute`, `AutoSwitchRotator`, `ModeTogglePill`, `currentEffectiveMode` resolver. |
```

- [ ] **Step 7.4: Commit and tag**

```bash
git add PROJECT_NOTES.md
git commit -m "$(cat <<'EOF'
notes: log v1.1.0 — Umr life-in-weeks + Yil/Umr auto-switch

End-to-end smoke-tested on S21+: pill toggle, mode-aware sections,
birthday picker, 5s/1m/2m/5m/30m/1h interval picker, lockscreen
rotation visible at 5s and correct on wake at 1m+.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"

git tag v1.1.0
```

(Tagging triggers the GitHub Actions release workflow on push. Don't push without user approval.)

---

## Notes for the implementing engineer

- **Don't touch the existing Yil flow.** The dispatch in Task 3 is a single early-return; everything below it must keep working as it does today. If you find yourself editing `drawCalendarView`, stop and check whether you really need to.
- **The wallpaper engine's `applicationContext` is the right Context for the rotator.** Engine itself isn't a Context.
- **Birthday picker:** The existing `DatePickerDialog` in `ui/components/` is the one to use. Don't add the AndroidX `DatePickerDialog` — keep deps tight.
- **`FilterChip` is in `material3`** — already on the classpath.
- **Settings file is large (1774 lines).** Use the `if (settings.topViewMode == TopViewMode.YIL) { ... }` wrapping approach (Step 6.4's simpler alternative) if extracting helper composables looks expensive. Behavior is what matters.
- **`SCHEDULE_EXACT_ALARM` permission:** Not declared in the manifest. `setExactAndAllowWhileIdle` works without it on most builds, but the rotator code falls back to `set()` if a `SecurityException` is thrown. Don't add the permission unless verification shows the fallback isn't enough.

---
