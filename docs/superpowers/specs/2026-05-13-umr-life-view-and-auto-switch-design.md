# Umr (Life-in-Weeks) view + Yil/Umr auto-switch — design spec

**Date:** 2026-05-13
**Target version:** `v1.1.0` (first minor bump — feature, not patch)
**Status:** approved by user, ready for implementation plan

---

## 1. Overview

Add a second top-level wallpaper view, **Umr** ("life" in Uzbek), alongside the existing **Yil** ("year") calendar. Umr renders an 80 × 52 grid of dots — one per week of an 80-year life — with weeks of life lived rendered bright, the current week tinted, and future weeks dim. The user picks between Yil and Umr via a pill toggle at the top of Settings; optionally enables an auto-switch that rotates the wallpaper between the two modes on a wall-clock timer.

The feature has three pieces:

1. **Umr renderer** — new draw path in the existing wallpaper engine, sharing colors, position, theme, and "fit any screen" math with Yil.
2. **Mode toggle + birthday flow** — new Compose UI controls at the top of `SettingsActivity`, plus a birthday picker that gates auto-switch.
3. **Auto-switch engine** — wall-clock-driven rotation that "always works": correct mode is shown on any screen wake regardless of how long the device was asleep, without burning battery rendering while invisible.

---

## 2. User-facing UX

### 2.1 New Settings top section

Fixed at the top of `SettingsActivity` (above all existing sections), scrolls with the page:

```
┌─────────────────────────────────────────────────┐
│  [  Yil  ]  [  Umr  ]    ← styled pill toggle  │
│                                                 │
│  Auto-switch                          [ ON / OFF]│
│  Every  [5s · 1m · 2m · 5m · 30m · 1h]   *      │
└─────────────────────────────────────────────────┘
                  * interval row only visible when Auto is ON
```

- The pill toggle visually matches the screenshot the user referenced: rounded full-pill outer, muted inactive label, bold white pilled chip with subtle shadow for active. Built as a new `ModeTogglePill` component in `ui/components/`.
- The interval is a 6-stop discrete slider (or segmented row of 6 chips — implementer's call; chips are easier to hit on a phone).

### 2.2 Mode-aware sections below

When **Yil** is selected at the top:
- All existing settings sections visible exactly as they are today.

When **Umr** is selected at the top:
- A new **Umr** section appears as the first section after the top controls. Contents:
  - **Birthday**: `[date picker row]` — shows the selected date or "Not set" with a prominent red CTA banner if empty.
- Goals section, View Mode section (Year / Monthly / 365), Highlight-current-week, and Calendar columns are hidden — they don't apply to Umr.
- Shared sections still visible: Theme, Position (X/Y/Scale), Custom Colors, Footer Text (so the user can still write their custom signature line — applies the same way in Umr).

### 2.3 Pill toggle behavior

- **Auto-switch OFF**: tapping the pill changes the wallpaper. The picked mode is what renders.
- **Auto-switch ON**: tapping the pill is purely a **preview-which-mode's-settings-am-I-editing** selector. The wallpaper is driven by the rotation timer. A subtle helper line under the auto-switch row reads: "Auto-switch is on — wallpaper rotates automatically."

### 2.4 Auto-switch + birthday interaction

- If birthday is **not set** and the user toggles Auto-switch ON: the toggle visually flips back OFF and a snackbar appears: "Set your birthday first." Tapping the snackbar action opens the date picker (or scrolls + focuses the birthday row when Umr is selected).
- If birthday is **set** and the user toggles Auto-switch ON: rotation begins immediately from the currently picked side (Yil or Umr).
- If the user clears their birthday while auto-switch is on (edge case): the engine falls back to Yil silently for safety.

### 2.5 Wallpaper-side behavior

- Auto-switch ON → engine renders whichever mode the wall-clock formula dictates at draw time.
- Auto-switch OFF → engine renders `topViewMode` as picked.
- All existing daily-refresh layers (visibility, midnight handler, `ACTION_DATE_CHANGED`, `DAILY_TICK` alarm) continue to work — Yil still updates at midnight, Umr still updates when the week rolls over.

---

## 3. Umr renderer

### 3.1 Grid shape

- **80 rows** (years 0..79, top to bottom) × **52 columns** (weeks 1..52 within each year, left to right). Total **4,160 dots**.
- The 53rd week that some ISO years technically contain is folded into week 52 (i.e., we count weeks-of-life as `floor((now - birthday) / 7days)`, not ISO-week math). This avoids a ragged-right edge.

### 3.2 Dot states

For each cell at global week index `i` (where `i = row*52 + col`, 0-indexed):

- `weeksLived = floor((now - birthday) / 7 days)`
- Past: `i < weeksLived` → `filledDotColor` at `filledDotAlpha`
- Current: `i == weeksLived` → `todayDotColor` (the existing Yil "today" accent) with the same glow treatment used for Yil's `goalOnThisDay` tinted dot
- Future: `i > weeksLived` → `emptyDotColor` at `emptyDotAlpha`

If `weeksLived < 0` (birthday in the future — user typo) → render all dots as future (empty). If `weeksLived >= 4160` (user older than 80) → render all dots as past (no current-week tint).

### 3.3 Visual choices that match Yil

- Same theme system (Light / Dark / AMOLED / Custom — only background, filled, empty, today colors).
- Same `dotShape` and `dotStyle` (but practically: keep the canonical circle/flat for Umr — anything fancier obscures the grid at this density). For the first release, force `DotShape.CIRCLE` + `DotStyle.FLAT` in Umr regardless of Yil pref.
- Position transform (`horizontalOffset`, `verticalOffset`, `scale`) applies identically.
- Footer text setting (user's custom signature line) renders the same way underneath the grid in Umr, when enabled.

### 3.4 No goals, no stats line in Umr

- Goal map is built only for Yil. Umr draws nothing extra below the grid besides the optional footer text.
- The bottom band reserved for Yil's stats line + countdown lines is recovered for Umr — its grid can use more vertical room.

---

## 4. Layout math (extending `CalendarLayout`)

`CalendarLayout` today computes Yil's `paddingXPx`, `safeTopPx`, `statsBottomBaselinePx`, `dotGapRatio`, `dotSizeCapPx` from canvas size + aspect + insets. We extend it for Umr with a second factory:

```kotlin
data class UmrLayout(
    val paddingXPx: Float,
    val safeTopPx: Float,
    val safeBottomPx: Float,
    val dotSizePx: Float,
    val dotGapPx: Float,
    val gridWidthPx: Float,
    val gridHeightPx: Float,
)

object UmrLayoutCompute {
    const val ROWS = 80
    const val COLS = 52
    fun compute(
        widthPx: Int, heightPx: Int,
        topOffsetPx: Float, bottomOffsetPx: Float,
        systemSafeInsetTopPx: Int, systemSafeInsetBottomPx: Int,
        systemSafeInsetLeftPx: Int, systemSafeInsetRightPx: Int,
    ): UmrLayout { ... }
}
```

### 4.1 The math

1. Reuse the same `paddingXRatio` table from `CalendarLayout`:
   - `aspect > 2.1f → 0.12f`, `> 2.0f → 0.15f`, else `0.18f`.
2. Reuse the same `safeTopRatio` table (clock band):
   - `aspect > 2.1f → 0.28f`, `> 2.0f → 0.25f`, else `0.22f`.
3. Bottom band: smaller than Yil's because there's no stats line:
   - `safeBottomRatio = max(0.06, systemSafeInsetBottom/height)` — just under-display fingerprint hint zone + nav handle.
4. Available area: `availWidth = width - 2*paddingX`, `availHeight = height - safeTop - safeBottom`.
5. Solve for `dotSize` so that 52 cols + 51 gaps fit horizontally AND 80 rows + 79 gaps fit vertically, with `gap = dotGapRatio * dotSize`:
   - `dotSize = min( availWidth / (COLS + (COLS-1)*dotGapRatio), availHeight / (ROWS + (ROWS-1)*dotGapRatio) )`
6. Hard cap from `CalendarLayout.dotSizeCapPx` (20 px) — Umr dots stay small even on QHD+.
7. Center the resulting grid horizontally in the available band (gridWidth almost certainly < availWidth at the cap, so leftover air is split evenly).

### 4.2 Aspect coverage

Existing aspect buckets cover S21+ (1080×2400, aspect 2.22 → 12% / 28% bucket), A35/A36 (similar), Galaxy A11 (720×1560, aspect 2.17 → 12% / 28% bucket), foldable cover screens (around 2.0 → 15% / 25%), tablets (~1.6 → 18% / 22%). New phones drop into the right bucket automatically without per-model code.

### 4.3 At the 20 px cap on an S21+

- 52 cols × 20 px + 51 × `0.7 × 20` = 1040 + 714 = 1754 px wide — wider than 1080 panel.
- So on every Samsung phone S21+ class and below, `dotSize` will be the constraint, not the cap. Cap is harmless.
- The cap matters on tablets / QHD+ where availHeight could allow much bigger dots but visually they get ugly.

---

## 5. Auto-switch engine (Approach B)

### 5.1 Source of truth — wall-clock formula

The current effective mode is a **pure function** of three values + the current time:

```kotlin
fun currentEffectiveMode(now: Long, s: WallpaperSettings): TopViewMode {
    val auto = s.autoSwitchSettings
    if (!auto.enabled) return s.topViewMode
    if (s.umrSettings.birthdayEpochMs == 0L) return TopViewMode.YIL  // safety
    val elapsed = now - auto.referenceMs
    val ticks = if (auto.intervalMs > 0L) elapsed / auto.intervalMs else 0L
    return if (ticks % 2L == 0L) auto.startMode
           else opposite(auto.startMode)
}
```

This is read on every `draw()` — the engine never holds a "current mode" state variable. No drift possible.

### 5.2 When auto-switch is enabled

On the `setAutoSwitchEnabled(true)` setter:

1. Write `auto_switch_reference_ms = System.currentTimeMillis()`
2. Write `auto_switch_start_mode = topViewMode` (currently picked)
3. Schedule both: a `Handler.postDelayed` for the next interval boundary (if visible), and an `AlarmManager.setExactAndAllowWhileIdle` for the next boundary.

On disable:
1. Cancel any pending Handler callback.
2. Cancel the AlarmManager intent.

### 5.3 Flip triggers

Three independent triggers, all of which result in `engine.draw()`:

- **A. Handler.postDelayed** — runs only while engine is visible. Fires at next interval boundary, draws, reschedules. Handles short intervals (5s test) cleanly.
- **B. AlarmManager** — `setExactAndAllowWhileIdle` for next boundary, `RTC_WAKEUP` so it fires even in Doze (with the OS-imposed ~9 min quota). Receiver pokes the engine to redraw if visible. For intervals ≥ 60s this fires when expected; for <60s in Doze, Android may delay it — fine, because the user isn't looking.
- **C. Visibility change** — `onVisibilityChanged(true)` already redraws; that redraw reads the wall-clock formula and gets the right mode for free. This is the "always works on wake" guarantee.

### 5.4 What "always works" actually means

The user said "switcher should always work, in background even in low power mode."

Implementation honors this in the way users actually experience the wallpaper:
- **When you wake your phone**, the mode shown is correct per the wall-clock — guaranteed by trigger C.
- **When you watch the phone for a while with auto on**, mode flips on time — guaranteed by triggers A (short intervals) and B (long intervals, with Doze caveats).
- **While the screen is off**, the wallpaper isn't rendered because nothing is visible. The mode "advances" in the formula but no pixels are drawn. This is correct behavior, not a bug — saves battery.

### 5.5 5s test interval reality check

- 5s while screen on: Handler fires every 5s → flip visible → works perfectly.
- 5s while screen off: AlarmManager won't fire every 5s — Doze quotas dominate. Mode is still correct when you wake up (per formula), but there's no "live flipping in the dark." Acceptable for a test interval.

---

## 6. Data model + persistence

### 6.1 New enum + data classes

```kotlin
enum class TopViewMode { YIL, UMR }

data class AutoSwitchSettings(
    val enabled: Boolean = false,
    val intervalMs: Long = 5_000L,
    val referenceMs: Long = 0L,
    val startMode: TopViewMode = TopViewMode.YIL,
)

data class UmrSettings(
    val birthdayEpochMs: Long = 0L,    // 0L == not set
)
```

Added to `WallpaperSettings`:

```kotlin
val topViewMode: TopViewMode = TopViewMode.YIL,
val autoSwitchSettings: AutoSwitchSettings = AutoSwitchSettings(),
val umrSettings: UmrSettings = UmrSettings(),
```

### 6.2 Pref keys (added to `LifeDotsPreferences`)

| Constant | String key | Type | Default |
|---|---|---|---|
| `KEY_TOP_VIEW_MODE` | `top_view_mode` | String "YIL"/"UMR" | "YIL" |
| `KEY_AUTO_SWITCH_ENABLED` | `auto_switch_enabled` | Boolean | false |
| `KEY_AUTO_SWITCH_INTERVAL_MS` | `auto_switch_interval_ms` | Long | 5000 |
| `KEY_AUTO_SWITCH_REFERENCE_MS` | `auto_switch_reference_ms` | Long | 0 |
| `KEY_AUTO_SWITCH_START_MODE` | `auto_switch_start_mode` | String "YIL"/"UMR" | "YIL" |
| `KEY_UMR_BIRTHDAY_MS` | `umr_birthday_ms` | Long | 0 |

### 6.3 Setters

Follow existing pattern — each writes the pref, updates `_settingsFlow.value` via `copy(...)`, calls `notifyWallpaperChanged()`. Setters added:

- `setTopViewMode(TopViewMode)`
- `setAutoSwitchEnabled(Boolean)` — also writes `referenceMs` + `startMode` on enable; cancels alarms on disable
- `setAutoSwitchIntervalMs(Long)` — also reschedules the next-boundary alarm
- `setUmrBirthday(Long)` — `0L` to clear

### 6.4 Migration v9

```kotlin
// CURRENT_MIGRATION_VERSION = 9
if (stored < 9) {
    // No destructive writes. New keys read with their class defaults.
    // (Class defaults via SharedPreferences.getString(key, "YIL") etc. do the right thing
    //  for users upgrading — they see Yil unchanged, auto-switch off.)
}
```

The migration is essentially a no-op besides bumping the version counter. We're following the lesson learned from the v4→v8 thrash: don't force-write defaults that defaults already provide.

---

## 7. Code structure / file changes

| File | Change |
|---|---|
| `preferences/LifeDotsPreferences.kt` | New enum, data classes, pref keys, setters, migration bump |
| `wallpaper/CalendarLayout.kt` | Add `UmrLayout` data class + `UmrLayoutCompute.compute(...)` factory |
| `wallpaper/LifeDotsWallpaperService.kt` | New `drawUmrView(canvas, settings)` path; `draw()` dispatches by `currentEffectiveMode(now, settings)`; Handler + AlarmManager scheduling for auto-switch |
| `wallpaper/AutoSwitchRotator.kt` | NEW. Owns the Handler/AlarmManager scheduling. Tiny class (~100 lines) keeps the engine readable. |
| `receiver/DateChangeReceiver.kt` | Add a new action `AUTO_SWITCH_TICK` that pokes the engine to redraw |
| `SettingsActivity.kt` | New top section (pill toggle, auto-switch row, interval picker); mode-aware section visibility; new Umr section with birthday picker |
| `ui/components/ModeTogglePill.kt` | NEW. Compose component for the styled Yil/Umr pill |
| `AndroidManifest.xml` | Register `AUTO_SWITCH_TICK` action on the existing `DateChangeReceiver` |

---

## 8. Edge cases

1. **Birthday in future (typo)** — `weeksLived < 0`. Render all dots empty (future). No crash.
2. **User older than 80** — `weeksLived >= 4160`. Render all dots past, no current-week tint. No crash.
3. **Birthday cleared while auto-switch ON** — engine's `currentEffectiveMode` already falls back to `YIL`. UI also clears the auto-switch state in the same operation: clearing birthday auto-disables auto-switch and shows a snackbar "Auto-switch disabled — set a birthday to re-enable."
4. **Time zone / DST change** — week boundaries computed from epoch ms, so DST doesn't shift the grid. Year boundaries for Yil already handled by existing code.
5. **User flips pill while auto is ON** — `topViewMode` is updated but doesn't affect rendering (engine reads the formula). It does affect which mode's settings are visible below.
6. **User changes interval while auto is ON** — `referenceMs` is reset to `now` so the new interval starts cleanly from the currently-rendering mode; alarms rescheduled.
7. **Device reboot** — `AlarmManager` schedules are dropped; `BOOT_COMPLETED` already wired to `DateChangeReceiver`; we add re-arming the auto-switch alarm there too.
8. **Auto-switch enabled but engine never gains visibility (e.g., always-on display only)** — Handler never fires, but `AlarmManager` does, and visibility-change covers most real-world cases.
9. **`POST_NOTIFICATIONS` denied / `KeepAliveService` not running** — auto-switch still works; the existing risk of Samsung Freecess freezing the process is unchanged by this feature.

---

## 9. Testing / verification

- **Unit-pure logic**: `currentEffectiveMode(now, settings)` and `UmrLayoutCompute.compute(...)` are pure functions — adding tests is cheap. Current project has no test suite; we add `app/src/test/java/.../AutoSwitchModeTest.kt` and `UmrLayoutTest.kt` with a handful of cases each. (Not a precondition for shipping, but valuable.)
- **Manual verification on the user's S21+** after build:
  1. Fresh install: confirm migration v9 doesn't break existing settings. Expect Yil shown, auto-switch off, all old settings intact.
  2. Toggle pill to Umr without birthday: confirm sticky red CTA banner appears, Umr section is the first one, all other Yil-only sections hidden.
  3. Set birthday to user's real DOB. Confirm grid renders with correct number of weeks-lived filled in.
  4. Enable auto-switch with 5s interval. Watch wallpaper flip every 5s.
  5. Increase interval to 1m, lock phone for 2m, wake phone — confirm mode is correct per wall-clock.
  6. Disable auto-switch, manually pick Yil — confirm wallpaper stays on Yil.
- **Battery sanity**: with auto-switch OFF, no regression in idle drain. With auto-switch ON + 1h interval, no regression beyond an extra `AlarmManager` wake every hour.

---

## 10. Non-goals (explicitly out of scope)

- Lifespan configurable per user (fixed 80, per design call).
- Goals in Umr view (hidden, per design call).
- Stats / footer numbers in Umr (just the grid, per design call).
- Year-axis labels on the side of the Umr grid (the canonical Life-in-Weeks shows decade markers; we skip for now — minimalist).
- Per-mode separate position/scale/colors (one set of shared sub-settings, per design call).
- Animation when mode flips (no fade or crossfade in v1 — instant swap on next draw).
- Notification or widget that shows weeks-lived (out of scope; this is a wallpaper-only feature).

---

## 11. Future enhancements (post-`v1.1.0`)

If users like the feature:
- Decade-marker labels on Umr grid (faint year numbers down the left edge).
- Goals rendered in Umr as tinted weeks too (with a per-goal "show in Umr" toggle).
- Lifespan slider (with a thoughtful default — average male / female life expectancy in user's country, fetched from a constant table).
- Soft crossfade animation when auto-switch flips (300ms alpha blend).
- Configurable start-day-of-week for the Umr grid (currently weeks-lived is just `floor((now - birthday)/7d)`; some users prefer week boundaries on Mondays).

---

— end of spec —
