# Umr family overlay + X-mode visualization + dedicated settings page — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Mom/Dad birthday overlays + 3 stat counters + X-mode visualization to the Umr life-in-weeks wallpaper, and split the Settings screen into a top Yil/Umr pill dispatching to two extracted screen composables.

**Architecture:** Additive — extend `UmrSettings` with parent DOBs, visual mode, and Umr-only alphas; layer new draw passes onto the existing `drawUmrView()` (gradient band → dots/crosses → parent rings → counter band); extract Yil settings into its own composable file and add the new Umr settings composable in parallel. Migration v11 is non-destructive.

**Tech Stack:** Kotlin · Jetpack Compose · Android `Canvas` (wallpaper) · JUnit (unit tests) · SharedPreferences (persistence) · `java.time.YearMonth` (leap-year math).

**Spec:** [docs/superpowers/specs/2026-05-14-umr-family-and-visualization-design.md](../specs/2026-05-14-umr-family-and-visualization-design.md)

**Working directory:** `/Users/rr/Documents/launcher/LifeDots` (no worktree — working on `master`).

**Build env (every gradle call):**
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
PATH=$JAVA_HOME/bin:$PATH \
./gradlew <task>
```

**Policy:** No `git push`. Stay at versionCode 10201 / versionName 1.2.1-dev until user explicitly bumps.

---

## File map

**Create:**
- `app/src/main/java/com/example/lifedots/wallpaper/UmrCellMath.kt` — pure helper `weekIndexFor()`
- `app/src/main/java/com/example/lifedots/ui/components/WheelDatePicker.kt` — Apple-style 3-column roller
- `app/src/main/java/com/example/lifedots/ui/components/WhoTabs.kt` — `[Me][Dad][Mom]` segmented control
- `app/src/main/java/com/example/lifedots/ui/screens/YilSettingsScreen.kt` — extracted Yil sections
- `app/src/main/java/com/example/lifedots/ui/screens/UmrSettingsScreen.kt` — new Umr screen
- `app/src/test/java/com/example/lifedots/wallpaper/UmrCellMathTest.kt` — unit tests for cell-index helper

**Modify:**
- `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt` — add fields, keys, migration v11, setters
- `app/src/main/java/com/example/lifedots/wallpaper/CalendarLayout.kt` — extend `UmrLayoutCompute` with counter band
- `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt` — new draw layers
- `app/src/main/java/com/example/lifedots/SettingsActivity.kt` — becomes thin dispatcher
- `app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt` — extend assertions

---

## Task 1: Data model — extend UmrSettings

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt` (find `data class UmrSettings`, currently ~3 lines around line 219)

- [ ] **Step 1.1: Add `UmrVisualMode` enum**

Locate the existing `enum class TopViewMode { YIL, UMR }` line. Add immediately below it:

```kotlin
enum class UmrVisualMode { DOTS, X_MARKS }
```

- [ ] **Step 1.2: Replace `UmrSettings` data class**

Replace the current `data class UmrSettings(val birthdayEpochMs: Long = 0L,)` with:

```kotlin
data class UmrSettings(
    val birthdayEpochMs: Long = 0L,
    val momBirthdayEpochMs: Long = 0L,
    val dadBirthdayEpochMs: Long = 0L,
    val visualMode: UmrVisualMode = UmrVisualMode.DOTS,
    val livedAlpha: Float = 1.0f,
    val emptyAlpha: Float = 0.6f,
    val totalWeeks: Int = 4000,
)
```

- [ ] **Step 1.3: Build to verify the data class compiles**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 1.4: Commit**

```bash
git add app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt
git commit -m "$(cat <<'EOF'
prefs: extend UmrSettings with parents, visual mode, Umr alphas

Pure data shape change. Adds momBirthdayEpochMs, dadBirthdayEpochMs,
visualMode (DOTS|X_MARKS), livedAlpha, emptyAlpha, totalWeeks. No
SharedPreferences read/write paths touched yet — those land in
the migration v11 task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: SharedPreferences keys + migration v11 + setters

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt` (companion object keys, `runMigrationsIfNeeded()`, `loadSettings()`, new setters)

Use Read to find each insertion point — the file is ~1124 lines.

- [ ] **Step 2.1: Add 6 new SharedPreferences key constants**

In the companion object near the existing `private const val KEY_UMR_BIRTHDAY_MS = "umr_birthday_ms"` (line ~1103), add immediately after that line:

```kotlin
        private const val KEY_UMR_MOM_BIRTHDAY_MS = "umr_mom_birthday_ms"
        private const val KEY_UMR_DAD_BIRTHDAY_MS = "umr_dad_birthday_ms"
        private const val KEY_UMR_VISUAL_MODE = "umr_visual_mode"
        private const val KEY_UMR_LIVED_ALPHA = "umr_lived_alpha"
        private const val KEY_UMR_EMPTY_ALPHA = "umr_empty_alpha"
        private const val KEY_UMR_TOTAL_WEEKS = "umr_total_weeks"
```

- [ ] **Step 2.2: Bump prefs version to 11 and add migration**

Find `private const val PREFS_VERSION = 10` (somewhere in the companion). Change to `11`.

Find `runMigrationsIfNeeded()` and the `when` block over `currentVersion`. Add a new arm:

```kotlin
                10 -> {
                    // v11: introduce parent birthdays + Umr visual mode + Umr-only alphas.
                    // Pure additive — only writes defaults for missing keys, never touches
                    // existing user data. Safe to re-run if interrupted.
                    val edit = prefs.edit()
                    if (!prefs.contains(KEY_UMR_MOM_BIRTHDAY_MS)) edit.putLong(KEY_UMR_MOM_BIRTHDAY_MS, 0L)
                    if (!prefs.contains(KEY_UMR_DAD_BIRTHDAY_MS)) edit.putLong(KEY_UMR_DAD_BIRTHDAY_MS, 0L)
                    if (!prefs.contains(KEY_UMR_VISUAL_MODE)) edit.putString(KEY_UMR_VISUAL_MODE, UmrVisualMode.DOTS.name)
                    if (!prefs.contains(KEY_UMR_LIVED_ALPHA)) edit.putFloat(KEY_UMR_LIVED_ALPHA, 1.0f)
                    if (!prefs.contains(KEY_UMR_EMPTY_ALPHA)) edit.putFloat(KEY_UMR_EMPTY_ALPHA, 0.6f)
                    if (!prefs.contains(KEY_UMR_TOTAL_WEEKS)) edit.putInt(KEY_UMR_TOTAL_WEEKS, 4000)
                    edit.putInt(KEY_PREFS_VERSION, 11).apply()
                }
```

- [ ] **Step 2.3: Extend the `UmrSettings(...)` block in `loadSettings()`**

Find `umrSettings = UmrSettings(` (around line ~504). Replace the whole `UmrSettings(...)` call with:

```kotlin
            umrSettings = UmrSettings(
                birthdayEpochMs = prefs.getLong(KEY_UMR_BIRTHDAY_MS, 0L),
                momBirthdayEpochMs = prefs.getLong(KEY_UMR_MOM_BIRTHDAY_MS, 0L),
                dadBirthdayEpochMs = prefs.getLong(KEY_UMR_DAD_BIRTHDAY_MS, 0L),
                visualMode = prefs.getString(KEY_UMR_VISUAL_MODE, UmrVisualMode.DOTS.name)
                    ?.let { runCatching { UmrVisualMode.valueOf(it) }.getOrNull() }
                    ?: UmrVisualMode.DOTS,
                livedAlpha = prefs.getFloat(KEY_UMR_LIVED_ALPHA, 1.0f),
                emptyAlpha = prefs.getFloat(KEY_UMR_EMPTY_ALPHA, 0.6f),
                totalWeeks = prefs.getInt(KEY_UMR_TOTAL_WEEKS, 4000),
            ),
```

- [ ] **Step 2.4: Add five new setters**

Find the existing `fun setUmrBirthday(epochMs: Long)` (around line ~972). Add the following methods immediately after it:

```kotlin
    fun setUmrMomBirthday(epochMs: Long) {
        prefs.edit().putLong(KEY_UMR_MOM_BIRTHDAY_MS, epochMs).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(momBirthdayEpochMs = epochMs)
        )
    }

    fun setUmrDadBirthday(epochMs: Long) {
        prefs.edit().putLong(KEY_UMR_DAD_BIRTHDAY_MS, epochMs).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(dadBirthdayEpochMs = epochMs)
        )
    }

    fun setUmrLivedAlpha(alpha: Float) {
        val v = alpha.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_UMR_LIVED_ALPHA, v).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(livedAlpha = v)
        )
    }

    fun setUmrEmptyAlpha(alpha: Float) {
        val v = alpha.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_UMR_EMPTY_ALPHA, v).apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(emptyAlpha = v)
        )
    }

    /**
     * Atomic mode toggle: writes visualMode AND the mode's default alpha pair
     * in a single edit, so the UI can't observe a half-updated state.
     */
    fun setUmrVisualMode(mode: UmrVisualMode) {
        val (lived, empty) = when (mode) {
            UmrVisualMode.DOTS -> 1.0f to 0.6f
            UmrVisualMode.X_MARKS -> 0.30f to 1.0f
        }
        prefs.edit()
            .putString(KEY_UMR_VISUAL_MODE, mode.name)
            .putFloat(KEY_UMR_LIVED_ALPHA, lived)
            .putFloat(KEY_UMR_EMPTY_ALPHA, empty)
            .apply()
        val current = _settingsFlow.value
        _settingsFlow.value = current.copy(
            umrSettings = current.umrSettings.copy(
                visualMode = mode, livedAlpha = lived, emptyAlpha = empty,
            )
        )
    }
```

> **Note on test coverage:** spec §8.1 mentions `UmrVisualModeDefaultsTest` and a v10→v11 migration test. Those require a SharedPreferences fake (Robolectric or refactored DI), which is not currently set up in the project. **Deferred to manual verification in Task 15.4** rather than adding a Robolectric dependency in this PR. If the manual checks find regressions, a follow-up adds Robolectric.

- [ ] **Step 2.5: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.6: Commit**

```bash
git add app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt
git commit -m "$(cat <<'EOF'
prefs: migration v11 + setters for parents + Umr visual mode

Adds 6 new SharedPreferences keys (umr_mom_birthday_ms,
umr_dad_birthday_ms, umr_visual_mode, umr_lived_alpha,
umr_empty_alpha, umr_total_weeks), wires them through loadSettings,
and adds 5 new setters. setUmrVisualMode is atomic — it writes
mode + both alpha defaults in a single edit() so the UI never sees
a half-updated state. Migration is pure additive.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `weekIndexFor()` helper + unit tests (TDD)

**Files:**
- Create: `app/src/main/java/com/example/lifedots/wallpaper/UmrCellMath.kt`
- Create: `app/src/test/java/com/example/lifedots/wallpaper/UmrCellMathTest.kt`

- [ ] **Step 3.1: Write the failing test file first**

Create `app/src/test/java/com/example/lifedots/wallpaper/UmrCellMathTest.kt`:

```kotlin
package com.example.lifedots.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class UmrCellMathTest {

    private val weekMs = 7L * 24L * 60L * 60L * 1000L

    @Test fun `unset birthday returns -1`() {
        assertEquals(-1, weekIndexFor(birthdayMs = 0L, nowMs = 1_700_000_000_000L))
    }

    @Test fun `future birthday returns -1`() {
        assertEquals(-1, weekIndexFor(birthdayMs = 2_000_000_000_000L, nowMs = 1_700_000_000_000L))
    }

    @Test fun `same instant returns cell 0`() {
        val t = 1_500_000_000_000L
        assertEquals(0, weekIndexFor(birthdayMs = t, nowMs = t))
    }

    @Test fun `one week later returns cell 1`() {
        val birth = 1_500_000_000_000L
        assertEquals(1, weekIndexFor(birthdayMs = birth, nowMs = birth + weekMs))
    }

    @Test fun `50 years yields about 2600 cells`() {
        val birth = 1_500_000_000_000L
        val nowMs = birth + 50L * 52L * weekMs
        assertEquals(50 * 52, weekIndexFor(birthdayMs = birth, nowMs = nowMs))
    }

    @Test fun `120-year-old clamps to last cell`() {
        val totalCells = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS
        val birth = 1_000_000_000_000L
        val nowMs = birth + 120L * 52L * weekMs
        assertEquals(totalCells - 1, weekIndexFor(birthdayMs = birth, nowMs = nowMs))
    }
}
```

- [ ] **Step 3.2: Run the failing test**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:testReleaseUnitTest --tests "com.example.lifedots.wallpaper.UmrCellMathTest"
```

Expected: compile-error / "unresolved reference: weekIndexFor".

- [ ] **Step 3.3: Create the helper file**

Create `app/src/main/java/com/example/lifedots/wallpaper/UmrCellMath.kt`:

```kotlin
package com.example.lifedots.wallpaper

/**
 * Maps a birthday + "now" to a cell index on the 52x80 Umr grid.
 *
 * Contract:
 * - birthdayMs <= 0L            -> -1 (unset)
 * - nowMs < birthdayMs          -> -1 (future birthday, treat as unset)
 * - weeks elapsed clamped to    -> 0 .. (ROWS*COLS - 1)
 *
 * Pure / side-effect-free / no allocations — safe to call from the
 * wallpaper draw path on every frame.
 */
fun weekIndexFor(birthdayMs: Long, nowMs: Long): Int {
    if (birthdayMs <= 0L) return -1
    if (nowMs < birthdayMs) return -1
    val weeks = ((nowMs - birthdayMs) / WEEK_MS).toInt()
    val maxIndex = UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS - 1
    return weeks.coerceAtMost(maxIndex)
}

private const val WEEK_MS: Long = 7L * 24L * 60L * 60L * 1000L
```

- [ ] **Step 3.4: Re-run the test**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:testReleaseUnitTest --tests "com.example.lifedots.wallpaper.UmrCellMathTest"
```

Expected: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 3.5: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/UmrCellMath.kt app/src/test/java/com/example/lifedots/wallpaper/UmrCellMathTest.kt
git commit -m "$(cat <<'EOF'
wallpaper: add weekIndexFor() pure helper + tests

Maps an arbitrary birthday + nowMs to a cell index on the 52x80
Umr grid. Returns -1 for unset/future birthdays and clamps very
old birthdays to the last cell. Six JUnit tests cover the boundary
cases: unset, future, same-instant, +1 week, 50yo, 120yo.

Used by the upcoming parent-ring + stat-counter draw passes.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `UmrLayoutCompute` reserves counter band (TDD)

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/CalendarLayout.kt` (object `UmrLayoutCompute` ~line 205)
- Modify: `app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt`

- [ ] **Step 4.1: Read current `UmrLayout` data class**

Use Read to find the `data class UmrLayout` in `CalendarLayout.kt` (look around line 180-205). Note the existing fields — we're adding `counterBandHeightPx: Float = 0f`, `counterBandTopPx: Float = 0f`, `gridTopPx: Float = 0f`.

- [ ] **Step 4.2: Add a failing test**

Append to `app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt`:

```kotlin
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
```

(If `safeTopPx` is not a public field on `UmrLayout`, replace the expected calc with `l.gridTopPx - l.counterBandHeightPx > 0f`. Inspect the existing data class before writing.)

- [ ] **Step 4.3: Run — expect compile or assertion failure**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:testReleaseUnitTest --tests "com.example.lifedots.wallpaper.UmrLayoutComputeTest"
```

Expected: FAIL (compile or assertion).

- [ ] **Step 4.4: Extend `UmrLayout` data class**

Add three fields to the existing `data class UmrLayout(...)` — keep all existing fields:

```kotlin
    val counterBandHeightPx: Float = 0f,
    val counterBandTopPx: Float = 0f,
    val gridTopPx: Float = 0f,
```

(If `safeTopPx` is already a field, leave it; if not, add it too for the test.)

- [ ] **Step 4.5: Add `counterBandRatio` + reserve band height in `compute()`**

Inside `UmrLayoutCompute` (above `compute()`), add:

```kotlin
    /** Fraction of canvas height reserved for the 3 stat counters above the grid. */
    private fun counterBandRatio(aspect: Float): Float = when {
        aspect > 2.1f -> 0.05f
        aspect > 2.0f -> 0.055f
        else -> 0.06f
    }
```

Inside `compute()`, after the existing `val safeTopPx = ...` calculation and **before** `availHeight` is computed, insert:

```kotlin
        val counterBandHeightPx = height * counterBandRatio(aspect)
        val gridTopPx = safeTopPx + counterBandHeightPx
```

Change the `availHeight` line from:
```kotlin
        val availHeight = (height - safeTopPx - safeBottomPx).coerceAtLeast(1f)
```
to:
```kotlin
        val availHeight = (height - gridTopPx - safeBottomPx).coerceAtLeast(1f)
```

In the existing `UmrLayout(...)` constructor call at the end of `compute()`, **add** these three named arguments:

```kotlin
            counterBandHeightPx = counterBandHeightPx,
            counterBandTopPx = safeTopPx,
            gridTopPx = gridTopPx,
```

If `gridBottomPx` is computed from `safeTopPx + gridHeightPx`, change it to `gridTopPx + gridHeightPx` so the dots draw below the reserved band.

- [ ] **Step 4.6: Re-run the test**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:testReleaseUnitTest --tests "com.example.lifedots.wallpaper.UmrLayoutComputeTest"
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4.7: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/CalendarLayout.kt app/src/test/java/com/example/lifedots/wallpaper/UmrLayoutComputeTest.kt
git commit -m "$(cat <<'EOF'
wallpaper: UmrLayoutCompute reserves a counter band above the grid

Adds counterBandHeightPx + counterBandTopPx + gridTopPx fields and a
counterBandRatio() that returns 5-6% of canvas height depending on
aspect. compute() now subtracts the band from availHeight so the dot
grid sizes down to fit. Existing layout tests still pass; new test
asserts the band > 0 and gridTopPx alignment.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Wallpaper paints read from `umrSettings.*` alphas

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt` (lines ~875-882 inside `drawUmrView`)

This is the alpha-source migration documented in spec §4.0. Two-line edit.

- [ ] **Step 5.1: Read current paint-config block**

Use Read on `LifeDotsWallpaperService.kt` lines 870-890 to confirm the exact existing lines that set `umrFilledPaint.alpha` and `umrEmptyPaint.alpha`.

- [ ] **Step 5.2: Replace alpha source**

Find:
```kotlin
            umrFilledPaint.alpha = (settings.filledDotAlpha * 255f).toInt().coerceIn(0, 255)
```
Replace with:
```kotlin
            umrFilledPaint.alpha = (settings.umrSettings.livedAlpha * 255f).toInt().coerceIn(0, 255)
```

Find:
```kotlin
            umrEmptyPaint.alpha = (settings.emptyDotAlpha * 255f).toInt().coerceIn(0, 255)
```
Replace with:
```kotlin
            umrEmptyPaint.alpha = (settings.umrSettings.emptyAlpha * 255f).toInt().coerceIn(0, 255)
```

- [ ] **Step 5.3: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.4: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt
git commit -m "$(cat <<'EOF'
wallpaper: Umr paints read alpha from umrSettings, not the Yil pair

Decouples Umr's filled/empty alpha from Yil's top-level
filledDotAlpha/emptyDotAlpha so the two modes can be tuned
independently. Required precursor to the X-mode visual toggle which
auto-snaps umrSettings.livedAlpha and .emptyAlpha to different
defaults than what Yil uses.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: X-mode rendering branch

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt` (paint fields ~line 95, draw loop in `drawUmrView` ~line 893-905)

- [ ] **Step 6.1: Add a new paint field**

Find the block where `umrFilledPaint`, `umrEmptyPaint`, etc. are declared (around lines 95-98). Add:

```kotlin
        private val umrCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
```

- [ ] **Step 6.2: Configure crossPaint each draw**

In `drawUmrView`, near the existing block that sets `umrFilledPaint.color = colors.filledDot; umrFilledPaint.alpha = ...`, add **directly after** that block:

```kotlin
            umrCrossPaint.color = colors.filledDot
            umrCrossPaint.alpha = (settings.umrSettings.livedAlpha * 255f).toInt().coerceIn(0, 255)
            umrCrossPaint.strokeWidth = layout.dotSizePx * 0.18f
```

- [ ] **Step 6.3: Add the import for `UmrVisualMode`**

Near the top of `LifeDotsWallpaperService.kt`, add the import (alphabetical-ish):

```kotlin
import com.example.lifedots.preferences.UmrVisualMode
```

- [ ] **Step 6.4: Branch the draw loop on visualMode**

Find the draw loop in `drawUmrView` (around lines 887-905). Locate the branch:
```kotlin
                    i < weeksLived -> canvas.drawCircle(cx, cy, r, umrFilledPaint)
```

Replace that line with:

```kotlin
                    i < weeksLived -> {
                        if (settings.umrSettings.visualMode == UmrVisualMode.X_MARKS) {
                            val s = r * 0.85f
                            canvas.drawLine(cx - s, cy - s, cx + s, cy + s, umrCrossPaint)
                            canvas.drawLine(cx - s, cy + s, cx + s, cy - s, umrCrossPaint)
                        } else {
                            canvas.drawCircle(cx, cy, r, umrFilledPaint)
                        }
                    }
```

- [ ] **Step 6.5: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.6: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt
git commit -m "$(cat <<'EOF'
wallpaper: X-mode renders lived weeks as vector crosses

Adds umrCrossPaint (STROKE, ROUND cap, AA) configured per-draw from
colors.filledDot + umrSettings.livedAlpha + dotSize*0.18 stroke
width. In the per-cell draw loop, the i < weeksLived branch now
chooses between drawCircle (DOTS) and two drawLine calls (X_MARKS)
based on settings.umrSettings.visualMode. Today-glow path is
unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Year-row gradient band

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt` (drawUmrView)

- [ ] **Step 7.1: Add a band paint field**

Near the other paint declarations, add:

```kotlin
        private val umrYearBandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
```

- [ ] **Step 7.2: Add the gradient draw block**

In `drawUmrView`, **before** the existing dot loop (look for `for (i in 0 until totalCells)` ~line 887), insert:

```kotlin
            // Year-row gradient — soft "you are here" stripe under your
            // current year row. Drawn first so dots paint on top.
            if (weeksLived in 0..(UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS - 1)) {
                val yourRow = weeksLived / UmrLayoutCompute.COLS
                val rowTop = layout.gridTopPx + yourRow * (layout.dotSizePx + layout.dotGapPx)
                val rowBottom = rowTop + layout.dotSizePx
                val pad = layout.dotSizePx * 0.35f
                val goldArgb = colors.filledDot
                val goldR = (goldArgb shr 16) and 0xFF
                val goldG = (goldArgb shr 8) and 0xFF
                val goldB = goldArgb and 0xFF
                val midColor = android.graphics.Color.argb(20, goldR, goldG, goldB)  // ~8% alpha
                umrYearBandPaint.shader = android.graphics.LinearGradient(
                    layout.gridLeftPx, 0f, layout.gridLeftPx + layout.gridWidthPx, 0f,
                    intArrayOf(0x00000000, midColor, 0x00000000),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                canvas.drawRect(
                    layout.gridLeftPx,
                    rowTop - pad,
                    layout.gridLeftPx + layout.gridWidthPx,
                    rowBottom + pad,
                    umrYearBandPaint,
                )
                umrYearBandPaint.shader = null   // release reference
            }
```

(If `colors.filledDot` is an `Int` ARGB, the above works. If it's a `Color` object, replace the bit-extraction with `.red/.green/.blue` accessors.)

- [ ] **Step 7.3: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.4: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt
git commit -m "$(cat <<'EOF'
wallpaper: paint a soft horizontal stripe under your current year row

Adds an 8% alpha gold gradient (transparent -> mid -> transparent)
that spans all 52 cells of the row containing your current week.
Painted before the dot loop so the dots are crisp on top, with
~35% dot-size vertical padding above and below the row to feather
the edges. Released the shader each frame to avoid leaking
LinearGradient instances.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Parent outline rings

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt` (drawUmrView, after the dot loop)

- [ ] **Step 8.1: Add ring paint fields + colour constants**

Near the other paint declarations:

```kotlin
        private val umrMomRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFE53935.toInt()   // mom = warm red
        }
        private val umrDadRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF2D75A8.toInt()   // dad = steel blue
        }
```

- [ ] **Step 8.2: Helper to find a cell centre**

In the `Engine` class scope (or inline in `drawUmrView`), add this private helper near `drawUmrView`:

```kotlin
        private fun umrCellCenter(layout: UmrLayout, cellIndex: Int): Pair<Float, Float> {
            val row = cellIndex / UmrLayoutCompute.COLS
            val col = cellIndex % UmrLayoutCompute.COLS
            val step = layout.dotSizePx + layout.dotGapPx
            val cx = layout.gridLeftPx + col * step + layout.dotSizePx / 2f
            val cy = layout.gridTopPx + row * step + layout.dotSizePx / 2f
            return cx to cy
        }
```

- [ ] **Step 8.3: Draw the rings after the dot loop**

In `drawUmrView`, **after** the existing `for (i in 0 until totalCells)` loop ends (and after `i == weeksLived` today-glow block), insert:

```kotlin
            // Parent rings — outline-only circles at each parent's current
            // week-of-life cell. Drawn after the dots so they read on top.
            val ringStroke = layout.dotSizePx * 0.18f
            val ringRadius = layout.dotSizePx * 0.62f
            umrMomRingPaint.strokeWidth = ringStroke
            umrDadRingPaint.strokeWidth = ringStroke

            val momCell = weekIndexFor(settings.umrSettings.momBirthdayEpochMs, now)
            if (momCell >= 0) {
                val (cx, cy) = umrCellCenter(layout, momCell)
                canvas.drawCircle(cx, cy, ringRadius, umrMomRingPaint)
            }
            val dadCell = weekIndexFor(settings.umrSettings.dadBirthdayEpochMs, now)
            if (dadCell >= 0) {
                val (cx, cy) = umrCellCenter(layout, dadCell)
                canvas.drawCircle(cx, cy, ringRadius, umrDadRingPaint)
            }
```

- [ ] **Step 8.4: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8.5: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt
git commit -m "$(cat <<'EOF'
wallpaper: draw mom + dad current-week outline rings

After the per-cell dot loop, plot one red ring (0xFFE53935) at mom's
current-week cell and one blue ring (0xFF2D75A8) at dad's. Stroke
width is 18% of dot diameter, radius 62%. weekIndexFor returns -1
for unset/future parents and the corresponding ring is skipped.
Drawn after dots so the ring overlays any filled dot underneath.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Stat counter band

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt`

- [ ] **Step 9.1: Add counter paint fields**

```kotlin
        private val umrCounterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
        private val umrCounterSwatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
```

- [ ] **Step 9.2: Add the band-draw block at the top of drawUmrView**

In `drawUmrView`, immediately after `layout` is computed but **before** any other draw call, insert:

```kotlin
            // Stat counter band — 3 columns above the grid.
            run {
                val bandTop = layout.counterBandTopPx
                val bandBottom = layout.gridTopPx
                val bandHeight = (bandBottom - bandTop).coerceAtLeast(1f)
                val textSize = bandHeight * 0.30f
                umrCounterTextPaint.textSize = textSize
                umrCounterTextPaint.color = colors.filledDot

                val totalWeeks = settings.umrSettings.totalWeeks
                val youWeeks = weekIndexFor(settings.umrSettings.birthdayEpochMs, now).let {
                    if (it < 0) null else it
                }
                val momWeeks = weekIndexFor(settings.umrSettings.momBirthdayEpochMs, now).let {
                    if (it < 0) null else it
                }
                val dadWeeks = weekIndexFor(settings.umrSettings.dadBirthdayEpochMs, now).let {
                    if (it < 0) null else it
                }

                val cellWidth = canvas.width.toFloat() / 3f
                val swatchRadius = textSize * 0.32f
                val swatchY = bandTop + bandHeight * 0.38f
                val numberY = bandTop + bandHeight * 0.55f
                val labelY = bandTop + bandHeight * 0.88f

                data class Col(val label: String, val weeks: Int?, val color: Int)
                val cols = listOf(
                    Col("Me",  youWeeks, colors.filledDot),
                    Col("Mom", momWeeks, 0xFFE53935.toInt()),
                    Col("Dad", dadWeeks, 0xFF2D75A8.toInt()),
                )
                cols.forEachIndexed { idx, c ->
                    val cx = cellWidth * (idx + 0.5f)
                    val numberText = if (c.weeks == null) "— / $totalWeeks"
                                     else "${c.weeks} / $totalWeeks"
                    umrCounterTextPaint.alpha = if (c.weeks == null) 100 else 230
                    umrCounterSwatchPaint.color = c.color
                    umrCounterSwatchPaint.alpha = if (c.weeks == null) 80 else 220
                    canvas.drawCircle(cx - textSize * 1.6f, swatchY, swatchRadius, umrCounterSwatchPaint)
                    canvas.drawText(numberText, cx, numberY, umrCounterTextPaint)
                    umrCounterTextPaint.textSize = textSize * 0.55f
                    canvas.drawText(c.label, cx, labelY, umrCounterTextPaint)
                    umrCounterTextPaint.textSize = textSize  // restore
                }
            }
```

- [ ] **Step 9.3: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9.4: Commit**

```bash
git add app/src/main/java/com/example/lifedots/wallpaper/LifeDotsWallpaperService.kt
git commit -m "$(cat <<'EOF'
wallpaper: 3 stat counters above the Umr grid

In the band reserved by UmrLayoutCompute, render Me / Mom / Dad
counters with a 4dp colour swatch (gold / red / blue) and two text
rows: "<lived> / <totalWeeks>" and the label. Unset parents show
"— / 4000" at 100/255 alpha. Reads weeksLived from weekIndexFor on
each draw, so weekly transitions are picked up automatically by the
existing midnight + visibility refresh.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: `WhoTabs` component

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/components/WhoTabs.kt`

- [ ] **Step 10.1: Create the component**

```kotlin
package com.example.lifedots.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WhoTab { ME, DAD, MOM }

@Composable
fun WhoTabs(
    selected: WhoTab,
    onSelected: (WhoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = Color(0xFF1B1A16)
    val inactive = Color(0x33FFFFFF)
    val gold = Color(0xFFFFB300)
    val mutedGold = Color(0xFFC7A35F)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0x14FFFFFF),
        border = BorderStroke(1.dp, Color(0x33FFC62E)),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            WhoTab.values().forEach { tab ->
                val isActive = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isActive) active else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelected(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (tab) {
                            WhoTab.ME -> "Me"
                            WhoTab.DAD -> "Dad"
                            WhoTab.MOM -> "Mom"
                        },
                        color = if (isActive) gold else mutedGold,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 10.2: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10.3: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/WhoTabs.kt
git commit -m "$(cat <<'EOF'
ui: WhoTabs segmented control for Me / Dad / Mom selection

3-segment pill with rounded-corner active chip and gold border, used
inside the Umr life-data editor sheet to pick which person's
birthday is being edited. Active label uses brand AmberGold +
SemiBold; inactive labels use muted gold.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: `WheelDatePicker` component

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/components/WheelDatePicker.kt`

This is the biggest single component — ~260 lines. Implementation in one step because it's tightly coupled internally.

- [ ] **Step 11.1: Create the file**

```kotlin
package com.example.lifedots.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private val InkBlack = Color(0xFF0A0906)
private val InkBlackElevated = Color(0xFF14110B)
private val HairlineGold = Color(0x4DFFC62E)
private val AmberGold = Color(0xFFFFC62E)
private val OffWhite = Color(0xFFEDE8DE)

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private const val ROW_HEIGHT_DP = 64
private const val VISIBLE_ROWS = 5
private val COLUMN_HEIGHT_DP = (ROW_HEIGHT_DP * VISIBLE_ROWS).dp

@Composable
fun WheelDatePicker(
    initialDay: Int,
    initialMonth: Int,
    initialYear: Int,
    onChange: (day: Int, month: Int, year: Int) -> Unit,
    modifier: Modifier = Modifier,
    minYear: Int = LocalDate.now().year - 120,
    maxYear: Int = LocalDate.now().year,
) {
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList() }
    val months = remember { (1..12).toList() }

    val yearState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialYear - minYear).coerceAtLeast(0)
    )
    val monthState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialMonth - 1).coerceAtLeast(0)
    )
    val dayState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialDay - 1).coerceAtLeast(0)
    )

    val selectedYear by remember {
        derivedStateOf { years.getOrNull(yearState.firstVisibleItemIndex) ?: initialYear }
    }
    val selectedMonth by remember {
        derivedStateOf { months.getOrNull(monthState.firstVisibleItemIndex) ?: initialMonth }
    }
    val dayCount by remember {
        derivedStateOf {
            runCatching { YearMonth.of(selectedYear, selectedMonth).lengthOfMonth() }.getOrDefault(31)
        }
    }
    val days = remember(dayCount) { (1..dayCount).toList() }
    val selectedDay by remember {
        derivedStateOf {
            val idx = dayState.firstVisibleItemIndex.coerceAtMost(days.size - 1)
            days.getOrNull(idx) ?: initialDay
        }
    }

    // Emit changes whenever any selection settles.
    LaunchedEffect(yearState, monthState, dayState) {
        snapshotFlow { Triple(selectedDay, selectedMonth, selectedYear) }
            .collectLatest { (d, m, y) -> onChange(d, m, y) }
    }

    Column(modifier = modifier.wrapContentHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelColumn(
                items = days,
                state = dayState,
                formatter = { it.toString().padStart(2, '0') },
                modifier = Modifier.weight(1f),
            )
            ColonSeparator()
            WheelColumn(
                items = months,
                state = monthState,
                formatter = { MONTH_NAMES[it - 1] },
                modifier = Modifier.weight(1.4f),
            )
            ColonSeparator()
            WheelColumn(
                items = years,
                state = yearState,
                formatter = { it.toString() },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ColumnLabel("DD", Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            ColumnLabel("MM", Modifier.weight(1.4f))
            Spacer(modifier = Modifier.width(8.dp))
            ColumnLabel("YYYY", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColonSeparator() {
    Text(
        text = ":",
        color = AmberGold.copy(alpha = 0.85f),
        fontSize = 22.sp,
        fontWeight = FontWeight.Light,
    )
}

@Composable
private fun ColumnLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = AmberGold.copy(alpha = 0.7f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun <T> WheelColumn(
    items: List<T>,
    state: LazyListState,
    formatter: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    Surface(
        modifier = modifier.height(COLUMN_HEIGHT_DP),
        shape = RoundedCornerShape(20.dp),
        color = InkBlack,
        border = BorderStroke(1.dp, HairlineGold),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(InkBlackElevated, InkBlack, InkBlackElevated)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Centre selection chip overlay — drawn behind the list rows.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT_DP.dp)
                    .padding(horizontal = 8.dp)
                    .background(
                        color = Color(0x14FFFFFF),
                        shape = RoundedCornerShape(14.dp),
                    )
            )
            LazyColumn(
                state = state,
                flingBehavior = fling,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val pad = (VISIBLE_ROWS / 2)
                items(pad) { Spacer(modifier = Modifier.height(ROW_HEIGHT_DP.dp)) }
                items(items) { item ->
                    val idx = items.indexOf(item)
                    val delta = remember(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
                        abs(idx - state.firstVisibleItemIndex) +
                            state.firstVisibleItemScrollOffset / ROW_HEIGHT_DP.toFloat()
                    }
                    val rowAlpha = when {
                        delta < 0.5f -> 1f
                        delta < 1.5f -> 0.78f
                        delta < 2.5f -> 0.50f
                        else -> 0f
                    }
                    val rowSize = when {
                        delta < 0.5f -> 28.sp
                        delta < 1.5f -> 18.sp
                        else -> 16.sp
                    }
                    val rowColor = if (delta < 0.5f) AmberGold else OffWhite
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT_DP.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatter(item),
                            color = rowColor.copy(alpha = rowAlpha),
                            fontSize = rowSize,
                            fontWeight = if (delta < 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(pad) { Spacer(modifier = Modifier.height(ROW_HEIGHT_DP.dp)) }
            }
        }
    }
}
```

- [ ] **Step 11.2: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11.3: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/WheelDatePicker.kt
git commit -m "$(cat <<'EOF'
ui: WheelDatePicker — Apple-style 3-column rolling date selector

Three rounded column cards (DD, MM, YYYY) with snap-fling behaviour,
hairline-gold borders, vertical gradient fill, and a centre
selection chip. Rows fade in size + alpha by distance from centre
(28sp 100% -> 18sp 78% -> 16sp 50%). Colons sit between columns;
labels DD/MM/YYYY in small-caps amber underneath. Leap-year math via
java.time.YearMonth.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Extract `YilSettingsScreen.kt`

This is the largest single move, but it's a pure cut-paste of existing code from `SettingsActivity.kt`. No behaviour change.

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/screens/YilSettingsScreen.kt`
- Modify: `app/src/main/java/com/example/lifedots/SettingsActivity.kt`

- [ ] **Step 12.1: Identify the Yil block in SettingsActivity**

Use grep + Read to locate:
- The top of the Yil section (typically the start of the Yil-specific `SettingsSection` composables, just after the top toggle row).
- The end of the Yil section (the line before the existing `UmrSettingsSection` call).

Note the boundary line numbers. The Yil content is ~1500 lines.

- [ ] **Step 12.2: Create the screen file with the extracted content**

Create `app/src/main/java/com/example/lifedots/ui/screens/YilSettingsScreen.kt`. The file should declare:

```kotlin
package com.example.lifedots.ui.screens

// Re-import every symbol that the moved code references — Compose
// imports, BrandColors, LifeDotsPreferences, individual section
// composables, etc. Use the same import list that's currently at the
// top of SettingsActivity.kt, minus what's only used by Umr code.

@Composable
fun YilSettingsScreen(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onAddGoal: () -> Unit,
    onEditGoal: (Goal) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Paste the Yil block here, verbatim. Wrap in Column with the
    // same modifier the original code uses (verticalScroll +
    // padding, etc.).
}
```

The exact body comes from the lines you noted in Step 12.1 — move them verbatim.

- [ ] **Step 12.3: Delete the moved block from SettingsActivity.kt**

Remove the same line range from `SettingsActivity.kt`. Leave a single call site:

```kotlin
YilSettingsScreen(
    settings = settings,
    preferences = preferences,
    snackbarHostState = snackbarHostState,
    scope = scope,
    onAddGoal = { editingGoal = null; showGoalEditor = true },
    onEditGoal = { goal -> editingGoal = goal; showGoalEditor = true },
)
```

- [ ] **Step 12.4: Add import to SettingsActivity**

```kotlin
import com.example.lifedots.ui.screens.YilSettingsScreen
```

- [ ] **Step 12.5: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL. If there are unresolved-reference errors, fix imports in `YilSettingsScreen.kt`.

- [ ] **Step 12.6: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/screens/YilSettingsScreen.kt app/src/main/java/com/example/lifedots/SettingsActivity.kt
git commit -m "$(cat <<'EOF'
refactor: extract Yil settings sections into YilSettingsScreen.kt

Pure code move — no behaviour change. The Yil-specific section
composables (~1500 lines) move out of SettingsActivity into a new
ui/screens/YilSettingsScreen.kt. SettingsActivity keeps the
top-level toggle plumbing and now invokes YilSettingsScreen via a
single call site. Sets up the file structure for the upcoming
UmrSettingsScreen + dispatcher.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Create `UmrSettingsScreen.kt`

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/screens/UmrSettingsScreen.kt`

- [ ] **Step 13.1: Sketch the screen layout**

```kotlin
package com.example.lifedots.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.UmrVisualMode
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.ui.components.WhoTab
import com.example.lifedots.ui.components.WhoTabs
import com.example.lifedots.ui.components.WheelDatePicker
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun UmrSettingsScreen(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    modifier: Modifier = Modifier,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingWho by remember { mutableStateOf(WhoTab.ME) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LifeDataCard(
            meMs = settings.umrSettings.birthdayEpochMs,
            dadMs = settings.umrSettings.dadBirthdayEpochMs,
            momMs = settings.umrSettings.momBirthdayEpochMs,
            onEdit = { showEditor = true; editingWho = WhoTab.ME },
            onEditWho = { who -> showEditor = true; editingWho = who },
        )

        VisualizationToggleSection(
            current = settings.umrSettings.visualMode,
            onChange = { preferences.setUmrVisualMode(it) },
        )

        TransparencySection(
            livedAlpha = settings.umrSettings.livedAlpha,
            emptyAlpha = settings.umrSettings.emptyAlpha,
            onLivedChange = { preferences.setUmrLivedAlpha(it) },
            onEmptyChange = { preferences.setUmrEmptyAlpha(it) },
        )

        // Theme / background / position / animation reuse Yil's section
        // composables — they drive shared WallpaperSettings fields.
    }

    if (showEditor) {
        LifeDataEditorSheet(
            settings = settings,
            preferences = preferences,
            initialWho = editingWho,
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun LifeDataCard(
    meMs: Long,
    dadMs: Long,
    momMs: Long,
    onEdit: () -> Unit,
    onEditWho: (WhoTab) -> Unit,
) {
    val fmt = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF14110B),
        border = BorderStroke(1.dp, Color(0x33FFC62E)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Life Data",
                color = Color(0xFFFFC62E),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row {
                Text("Me   ", color = Color(0x99FFFFFF), fontSize = 14.sp)
                Text(
                    if (meMs > 0L) fmt.format(Date(meMs)) else "Not set",
                    color = Color(0xFFEDE8DE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row {
                Text("Dad  ", color = Color(0x99FFFFFF), fontSize = 14.sp)
                Text(
                    if (dadMs > 0L) fmt.format(Date(dadMs)) else "Not set",
                    color = Color(0xFFEDE8DE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row {
                Text("Mom  ", color = Color(0x99FFFFFF), fontSize = 14.sp)
                Text(
                    if (momMs > 0L) fmt.format(Date(momMs)) else "Not set",
                    color = Color(0xFFEDE8DE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun VisualizationToggleSection(
    current: UmrVisualMode,
    onChange: (UmrVisualMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "VISUALIZATION",
            color = Color(0xFFFFC62E),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0x14FFFFFF),
            border = BorderStroke(1.dp, Color(0x33FFC62E)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                listOf(UmrVisualMode.DOTS to "Dots", UmrVisualMode.X_MARKS to "X-marks").forEach { (mode, label) ->
                    val isActive = mode == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isActive) Color(0xFF1B1A16) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onChange(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isActive) Color(0xFFFFC62E) else Color(0xFFC7A35F),
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransparencySection(
    livedAlpha: Float,
    emptyAlpha: Float,
    onLivedChange: (Float) -> Unit,
    onEmptyChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "TRANSPARENCY",
            color = Color(0xFFFFC62E),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF14110B),
            border = BorderStroke(1.dp, Color(0x33FFC62E)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lived", color = Color(0xFFEDE8DE), modifier = Modifier.weight(1f))
                    Text("${(livedAlpha * 100).toInt()}%", color = Color(0xFFFFC62E))
                }
                Slider(value = livedAlpha, onValueChange = onLivedChange, valueRange = 0f..1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Empty", color = Color(0xFFEDE8DE), modifier = Modifier.weight(1f))
                    Text("${(emptyAlpha * 100).toInt()}%", color = Color(0xFFFFC62E))
                }
                Slider(value = emptyAlpha, onValueChange = onEmptyChange, valueRange = 0f..1f)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LifeDataEditorSheet(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    initialWho: WhoTab,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialWho) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Resolve "starting date" for each tab — fall back to today if unset.
    val today = LocalDate.now()
    fun msToLocal(ms: Long): LocalDate =
        if (ms <= 0L) today
        else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun localToMs(d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val current = when (selected) {
        WhoTab.ME  -> msToLocal(settings.umrSettings.birthdayEpochMs)
        WhoTab.DAD -> msToLocal(settings.umrSettings.dadBirthdayEpochMs)
        WhoTab.MOM -> msToLocal(settings.umrSettings.momBirthdayEpochMs)
    }
    var picked by remember(selected) { mutableStateOf(current) }
    val isFuture = picked.isAfter(today)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0906),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enter your life data",
                    color = Color(0xFFEDE8DE),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val ms = localToMs(picked)
                        when (selected) {
                            WhoTab.ME  -> preferences.setUmrBirthday(ms)
                            WhoTab.DAD -> preferences.setUmrDadBirthday(ms)
                            WhoTab.MOM -> preferences.setUmrMomBirthday(ms)
                        }
                        onDismiss()
                    },
                    enabled = !isFuture,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFC62E)),
                ) { Text("Done") }
            }
            WhoTabs(selected = selected, onSelected = { selected = it })
            Text(
                text = "Date of Birth",
                color = Color(0xFFFFC62E),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            WheelDatePicker(
                initialDay = picked.dayOfMonth,
                initialMonth = picked.monthValue,
                initialYear = picked.year,
                onChange = { d, m, y ->
                    picked = runCatching { LocalDate.of(y, m, d.coerceAtMost(28)) }
                        .getOrElse { LocalDate.of(y, m, 1) }
                },
            )
            if (isFuture) {
                Text(
                    text = "Date can't be in the future.",
                    color = Color(0xFFE53935),
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 13.2: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL. Resolve any missing imports.

- [ ] **Step 13.3: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/screens/UmrSettingsScreen.kt
git commit -m "$(cat <<'EOF'
ui: new UmrSettingsScreen — life data card + editor sheet + viz toggle

Screen sections in order: Life Data summary card (Me / Dad / Mom
DOBs), Visualization toggle (Dots / X-marks), Transparency sliders
(Lived + Empty), and a ModalBottomSheet editor with WhoTabs +
WheelDatePicker that persists the picked date via the right
setter for the selected tab. Future dates disable Done with a
warning. Theme / background / etc. will be wired into the parent
dispatcher in the next task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: `SettingsActivity` becomes the Yil / Umr dispatcher

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/SettingsActivity.kt`

- [ ] **Step 14.1: Replace the body of `ModernSettingsContent`**

In `SettingsActivity.kt`, find the existing `ModernSettingsContent` function (added when SettingsActivity was rebuilt). Replace its `Column(...)` content body with a dispatcher that drives off `WallpaperSettings.topViewMode`:

```kotlin
import com.example.lifedots.ui.screens.YilSettingsScreen
import com.example.lifedots.ui.screens.UmrSettingsScreen
import com.example.lifedots.ui.components.ModeTogglePill
import com.example.lifedots.preferences.TopViewMode
```

(Add at top of file if not already imported.)

Replace the Column body:

```kotlin
Column(
    modifier = modifier
        .fillMaxSize()
        .background(BrandColors.InkBlack),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    // existing top bar (rename / back / etc.) stays here

    Spacer(modifier = Modifier.height(12.dp))

    ModeTogglePill(
        leftLabel = "Yil",
        rightLabel = "Umr",
        selected = if (settings.topViewMode == TopViewMode.UMR) 1 else 0,
        onSelectedChange = { idx ->
            preferences.setTopViewMode(
                if (idx == 0) TopViewMode.YIL else TopViewMode.UMR
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    when (settings.topViewMode) {
        TopViewMode.YIL -> YilSettingsScreen(
            settings = settings,
            preferences = preferences,
            snackbarHostState = snackbarHostState,
            scope = scope,
            onAddGoal = onAddGoal,
            onEditGoal = onEditGoal,
            modifier = Modifier.weight(1f),
        )
        TopViewMode.UMR -> UmrSettingsScreen(
            settings = settings,
            preferences = preferences,
            modifier = Modifier.weight(1f),
        )
    }
}
```

(Inspect `ModeTogglePill`'s parameter list — it may already take `leftLabel/rightLabel/selected/onSelectedChange`; if the signature differs, adapt.)

- [ ] **Step 14.2: Verify `preferences.setTopViewMode` exists**

If not, grep `LifeDotsPreferences.kt` — it should already be there from the prior Umr commit. If not, add:

```kotlin
fun setTopViewMode(mode: TopViewMode) {
    prefs.edit().putString(KEY_TOP_VIEW_MODE, mode.name).apply()
    val current = _settingsFlow.value
    _settingsFlow.value = current.copy(topViewMode = mode)
}
```

- [ ] **Step 14.3: Build**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:compileReleaseKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 14.4: Commit**

```bash
git add app/src/main/java/com/example/lifedots/SettingsActivity.kt app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt
git commit -m "$(cat <<'EOF'
ui: SettingsActivity becomes Yil/Umr dispatcher

Top ModeTogglePill dispatches to YilSettingsScreen or
UmrSettingsScreen based on WallpaperSettings.topViewMode. Switching
the pill writes the new mode back via preferences.setTopViewMode
so the wallpaper view follows the settings page. The activity
shrinks dramatically — the heavy lifting now lives in the two
screen composables.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Full build, install, manual verification

**Files:** none.

- [ ] **Step 15.1: Run full unit-test suite**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew :app:testReleaseUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 15.2: Assemble release APK**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools PATH=$JAVA_HOME/bin:$PATH ./gradlew assembleRelease -PappVersionCode=10201 -PappVersionName=1.2.1-dev
```

Expected: APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 15.3: Install over existing (uninstall first if signature mismatch)**

```bash
adb install -r /Users/rr/Documents/launcher/LifeDots/app/build/outputs/apk/release/app-release.apk
```

If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the existing build was signed with a different key. Uninstall first:

```bash
adb uninstall com.example.lifedots
adb install /Users/rr/Documents/launcher/LifeDots/app/build/outputs/apk/release/app-release.apk
```

(Uninstall loses goals + prefs — confirm with user before running.)

- [ ] **Step 15.4: Launch settings and walk the verification matrix**

```bash
adb shell am start -n com.example.lifedots/.SettingsActivity
```

Verify each row of spec §8.2:

- [ ] Fresh open, Umr selected → grid renders; no mom/dad rings; counters show `— / 4000` for unset parents.
- [ ] Set your DOB through the wheel picker → wallpaper redraws on return; year-row gradient appears on the correct row.
- [ ] Set Mom DOB 50 yrs back → red ring at row 50 col 0.
- [ ] Set Dad DOB → blue ring at his cell; counter updates.
- [ ] Flip Visualization to X-marks → lived cells become crosses; the Transparency sliders read 30 % / 100 %.
- [ ] Flip back to Dots → crosses revert; sliders read 100 % / 60 %.
- [ ] Switch to Yil at the top pill → Yil settings render with no behaviour change vs. before this PR.

- [ ] **Step 15.5: Take screenshots of the rendered wallpaper for the next session**

```bash
adb shell screencap -p /sdcard/umr-after.png
adb pull /sdcard/umr-after.png /tmp/
```

Open `/tmp/umr-after.png`. If any rendering deviates from the spec mockup, file a follow-up note before moving on.

- [ ] **Step 15.6: No commit needed** — this task is verification only.

---

## Task 16: Stop here — wait for user signal before pushing or bumping version

- [ ] **Step 16.1: Do not push.** Per policy in spec §8.3 and conversation, no `git push` until user explicitly says.
- [ ] **Step 16.2: Do not bump version.** Stay at 10201 / 1.2.1-dev unless instructed.
- [ ] **Step 16.3: Report.** Tell the user the plan is fully landed locally and ask whether they want to (a) flip to a 1.3.0-dev bump and push for a release, or (b) iterate on visual tuning first.

---

## Definition of Done

- All 14 commits in place locally on `master`.
- `./gradlew :app:testReleaseUnitTest` passes.
- `./gradlew assembleRelease -PappVersionCode=10201 -PappVersionName=1.2.1-dev` produces a working APK.
- Manual verification matrix in §15.4 passes on the connected S21+.
- No push, no version bump.
