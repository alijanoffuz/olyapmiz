# Umr family overlay + X-mode visualization + dedicated settings page — design spec

**Date:** 2026-05-14
**Target version:** `v1.3.0` (minor bump — new user-facing feature)
**Status:** approved by user, ready for implementation plan

---

## 1. Overview

Extend the existing Umr ("life in weeks") view with three independent capabilities:

1. **Family overlay.** The user enters birthdays for Mom and Dad. The Umr grid (52×80) keeps its semantics — each cell is a week of *that* person's life — but on top of the user's own filled dots we draw two outline rings at Mom's and Dad's *current week*, in red and blue respectively. The user's *current year row* gets a subtle horizontal gradient band so the eye finds "where we are" instantly.
2. **Stat counters.** Three counters sit in a band above the grid, labelled **Me / Mom / Dad**, each reading `"<lived> / 4000 weeks"` with a small colour swatch. Unset parents show `"— / 4000"` greyed out.
3. **X-mode visualization toggle.** A second visualisation: replace the user's filled dots with vector crosses ("✕") and invert the alpha hierarchy so the *unlived* dots dominate visually, putting the focus on the future rather than the past.

To make room for the new Umr-only settings, the existing single-screen `SettingsActivity` is split: a top **Yil / Umr** pill dispatches between two extracted screen composables — `YilSettingsScreen` and `UmrSettingsScreen`. Yil retains every setting it has today; Umr mirrors them minus Goals, plus the new family + visualisation controls + Apple-style rolling date picker.

The wallpaper redraws daily on the existing midnight tick — no new scheduler.

---

## 2. User-facing UX

### 2.1 Settings page structure

```
SettingsActivity
└── Column
    ├── TopBar  (existing)
    ├── ModeTogglePill   "Yil" │ "Umr"        ← controls which screen below
    ├── if YIL:  YilSettingsScreen(…)         ← extracted, unchanged content
    └── if UMR:  UmrSettingsScreen(…)         ← new
```

The pill at the top dispatches between two extracted screens. Switching the pill is purely a navigation action — it does **not** change `WallpaperSettings.topViewMode` (that lives in its own row inside each screen, unchanged from today).

### 2.2 UmrSettingsScreen sections (top → bottom)

1. **Life Data** — a single `PressableCard` that summarises all three DOBs:
   ```
   ┌──────────────────────────────────────────────┐
   │  📅  Life Data                          ✎    │
   │      Me   4 October 2004                     │
   │      Dad  12 March 1972                      │
   │      Mom  17 August 1975                     │
   └──────────────────────────────────────────────┘
   ```
   Unset rows read `"Not set"` in the muted text colour. Tapping anywhere on the card opens the editor sheet.

2. **Editor sheet** (modal bottom sheet, dismissible by swipe-down or "Done"):
   - `WhoTabs` segmented control at top: `[ Me ] [ Dad ] [ Mom ]`.
   - Below it, a `WheelDatePicker` — three columns (DD · MM · YYYY) with snap-to-centre fling and gold-stroked centre selector frame.
   - "Done" persists *all three* values back to `umrSettings` in one write.

3. **Visualization** — segmented pill `[ Dots ] [ X-marks ]`. Flipping it writes both `visualMode` *and* the alpha pair to the new mode's defaults in a single `setUmrVisualMode()` call (see §3.2).

4. **Transparency** — two sliders ("Lived" + "Empty"), 0…100 %, bound to `umrSettings.livedAlpha` and `.emptyAlpha`. These are **Umr-only** — they do not affect Yil rendering.

5. **Theme** — Light / Dark / AMOLED / Custom selector, reused verbatim from Yil. Drives shared `WallpaperSettings.theme`.

6. **Background**, **Position**, **Animation**, **Glass**, **Tree**, **Fluid effects** — reused verbatim from Yil. They drive the same shared `WallpaperSettings` fields and apply to both modes.

7. ~~**Goals**~~ — **omitted by design.** Umr is a 80-year life view; goals are a yearly-cycle concept and live only in Yil.

### 2.3 YilSettingsScreen

Created by extracting the existing Yil-specific sections out of `SettingsActivity.kt`. **No content change** — same components, same layout, same shared `WallpaperSettings` fields driving everything. This is purely a code-organisation move so `SettingsActivity` does not balloon past 3,500 lines as it absorbs the new Umr surface area.

### 2.4 Wallpaper canvas — Umr mode

```
┌──────────────────────────────────────────────────┐
│   ── lock-screen clock area (existing) ──        │
│                                                  │
│   Me     │   Mom    │   Dad           ┐          │
│   1,127  │  2,634   │  2,801          │ counter  │
│   /4000  │  /4000   │  /4000          │ band     │
│   ●      │   ○      │   ○             ┘          │
│                                                  │
│  ▪ ▪ ▪ ▪ ▪ … (52 dots) … ▪ ▪ ▪ ▪ ▪ ▪              │   ← row 0 (year 1)
│  ▪ ▪ ▪ ▪ ▪                            ▪ ▪ ▪      │
│  …                                                │
│  ▬▬▬▬▬▬▬▬▬▬▬▬▬ gradient band ▬▬▬▬▬▬▬▬▬▬▬▬▬       │   ← your current year row
│   ●  ●  ●  ●  ●  ● ◌(today glow)  ◌  ◌ … ◌       │
│  …                                                │
│             ⊙ red ring (mom's current week)      │
│                          ⊙ blue ring (dad's)     │
│  …                                                │
│  ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌ ◌      │   ← row 79 (year 80)
└──────────────────────────────────────────────────┘
```

Z-order (back → front):

1. Year-row gradient band (only on the row containing your current week)
2. Existing dot grid: filled / empty / today-glow
3. Parent rings (red for Mom, blue for Dad) at their current-week cells
4. Stat counters in the reserved band above the grid

In **X-mode**, the filled dots in layer 2 become two-line vector crosses. Today-glow is unchanged.

---

## 3. Data model + migration

### 3.1 New types and fields

```kotlin
// preferences/LifeDotsPreferences.kt

enum class UmrVisualMode { DOTS, X_MARKS }

data class UmrSettings(
    val birthdayEpochMs: Long = 0L,            // existing — yours
    val momBirthdayEpochMs: Long = 0L,         // new — 0 = unset
    val dadBirthdayEpochMs: Long = 0L,         // new — 0 = unset
    val visualMode: UmrVisualMode = UmrVisualMode.DOTS,
    val livedAlpha: Float = 1.0f,              // 0..1; DOTS default 1.0, X default 0.30
    val emptyAlpha: Float = 0.6f,              // 0..1; DOTS default 0.6, X default 1.0
    val totalWeeks: Int = 4000,                // counter denominator
)
```

`WallpaperSettings.umrSettings` continues to wrap this. Yil's top-level `filledDotAlpha` / `emptyDotAlpha` are untouched and remain Yil-only.

### 3.2 Visual-mode toggle behaviour

The Umr settings page does not expose `setUmrLivedAlpha` / `setUmrEmptyAlpha` as raw sliders that the visualisation toggle ignores. Instead the toggle writes mode *and* alphas atomically:

```kotlin
fun setUmrVisualMode(mode: UmrVisualMode) {
    val (lived, empty) = when (mode) {
        UmrVisualMode.DOTS    -> 1.0f to 0.6f
        UmrVisualMode.X_MARKS -> 0.30f to 1.0f
    }
    persist(... visualMode = mode, livedAlpha = lived, emptyAlpha = empty ...)
}
```

User-driven slider adjustments after the toggle are preserved within the same mode, but **switching mode resets both sliders to that mode's defaults** — by design, per user.

### 3.3 New SharedPreferences keys

| Key | Type | Default |
|---|---|---|
| `umr_mom_birthday_ms` | long | 0 |
| `umr_dad_birthday_ms` | long | 0 |
| `umr_visual_mode` | string (`UmrVisualMode.name`) | `"DOTS"` |
| `umr_lived_alpha` | float | 1.0 |
| `umr_empty_alpha` | float | 0.6 |
| `umr_total_weeks` | int | 4000 |

### 3.4 Migration v11

Pure additive, non-destructive. Adds the six new keys with defaults if absent. No existing key is read, modified, or deleted. The migration version is bumped from 10 → 11. Existing 1.2.1-dev installs keep their birthday + Yil alpha settings untouched.

### 3.5 Setters

- `setUmrMomBirthday(epochMs: Long)`
- `setUmrDadBirthday(epochMs: Long)`
- `setUmrVisualMode(mode: UmrVisualMode)` — atomic, writes mode + both alphas
- `setUmrLivedAlpha(alpha: Float)` — slider input
- `setUmrEmptyAlpha(alpha: Float)` — slider input

---

## 4. Rendering — wallpaper additions

All changes localised to `LifeDotsWallpaperService.drawUmrView()`. No new top-level entry points.

### 4.0 Alpha source migration

The existing Umr paints (`umrFilledPaint`, `umrEmptyPaint`) currently read their alpha from the top-level `settings.filledDotAlpha` / `settings.emptyDotAlpha`. As part of this change they migrate to `settings.umrSettings.livedAlpha` / `.emptyAlpha` so that Yil and Umr can be tuned independently. Yil's draw path is untouched — it continues to read the top-level fields. The migration is a 2-line edit inside `drawUmrView()` per-paint refresh.

### 4.1 Cell index for arbitrary birthday

```kotlin
private fun weekIndexFor(birthdayMs: Long, nowMs: Long): Int {
    if (birthdayMs <= 0L || nowMs < birthdayMs) return -1
    val weeks = ((nowMs - birthdayMs) / WEEK_MS).toInt()
    return weeks.coerceAtMost(UmrLayoutCompute.ROWS * UmrLayoutCompute.COLS - 1)
}
```

`-1` means "absent / future / unset — do not draw". Out-of-range (≥ 4160) clamps to the last cell so a 90-year-old Mom still gets a ring at row 79 col 51.

### 4.2 Year-row gradient band

```kotlin
val yourCell = weekIndexFor(birthdayMs, now)
if (yourCell >= 0) {
    val yourRow = yourCell / UmrLayoutCompute.COLS
    val rowTop = layout.gridTopPx + yourRow * (layout.dotSizePx + layout.dotGapPx)
    val gradient = LinearGradient(
        gridLeftPx, 0f, gridRightPx, 0f,
        intArrayOf(TRANSPARENT, gold_at_8pct_alpha, TRANSPARENT),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    paintBand.shader = gradient
    canvas.drawRect(gridLeft, rowTop - pad, gridRight, rowTop + dotSizePx + pad, paintBand)
}
```

The band spans **all 52 cells of the current-year row** (left edge of grid to right edge), not just up to your current week — a soft horizontal "you are here" stripe. Drawn **before** the dot loop so the dots paint over it.

### 4.3 Parent outline rings

For each parent with `birthday > 0`:

```kotlin
val cell = weekIndexFor(parentBirthdayMs, now)
if (cell >= 0) {
    val (cx, cy) = layout.centerPxOf(cell)
    paintRing.color = MOM_RED or DAD_BLUE
    paintRing.strokeWidth = dotSizePx * 0.18f
    paintRing.style = Stroke
    canvas.drawCircle(cx, cy, dotSizePx * 0.62f, paintRing)
}
```

Drawn **after** the dot loop so the ring is visible on top of any filled dot underneath. Colours:

- Mom: `0xFFE53935`
- Dad: `0xFF2D75A8`

### 4.4 X-mode in the dot loop

```kotlin
for (i in 0 until totalCells) {
    when {
        i < weeksLived -> when (settings.umrSettings.visualMode) {
            UmrVisualMode.DOTS -> canvas.drawCircle(cx, cy, r, filledPaint)
            UmrVisualMode.X_MARKS -> {
                val s = r * 0.85f
                canvas.drawLine(cx - s, cy - s, cx + s, cy + s, crossPaint)
                canvas.drawLine(cx - s, cy + s, cx + s, cy - s, crossPaint)
            }
        }
        i == weeksLived -> drawTodayDotWithGlow(...)   // unchanged
        else -> canvas.drawCircle(cx, cy, r, emptyPaint)
    }
}
```

`crossPaint` is a new `Paint` instance set up in `onCreate`-equivalent of the engine:

```kotlin
private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
}
```

`crossPaint.color` and `.alpha` are refreshed per draw from `colors.filledDot` and `umrSettings.livedAlpha`, the same way `filledPaint` already is.

### 4.5 Stat counters band

`UmrLayoutCompute` reserves a fixed-ratio band above the grid:

```kotlin
private fun counterBandRatio(aspect: Float): Float = when {
    aspect > 2.1f -> 0.05f
    aspect > 2.0f -> 0.055f
    else -> 0.06f
}
```

Inside that band: 3 equal-width columns, each rendering two `drawText` calls — `"<lived> / 4000"` line one, `"weeks"` line two — with a 4 dp solid-colour disc to the left of the number. Unset parents render `"— / 4000"` at 40 % alpha. Font size is derived from band height (≈ 30 % of band).

### 4.6 Update cadence

The engine already redraws on:
- `onVisibilityChanged(true)` — every screen wake
- The minute / midnight tick used to refresh "today" dot

Counters re-read from prefs on every redraw — no new scheduler. Weekly transitions happen automatically when `(now - birthday) / WEEK_MS` rolls over.

---

## 5. Layout — UmrLayoutCompute

```kotlin
object UmrLayoutCompute {
    const val ROWS = 80
    const val COLS = 52
    // existing fields…
    var counterBandHeightPx: Float = 0f          // new — set in compute()
    var gridTopPx: Float = 0f                    // already implied — formalise
    var gridBottomPx: Float = 0f                 // already implied — formalise
}
```

`compute()` change: subtract `counterBandHeightPx` from `availHeight` before computing dot size. The dot size cap and gap ratios stay unchanged — the band steals a thin slice off the top, shortening the available grid area.

Existing `UmrLayoutComputeTest` is extended with one new case: with the same canvas dimensions as today's test, the new `compute()` returns a `gridTopPx` that is `counterBandHeightPx` lower than before.

---

## 6. New components

| File | Purpose | Approx. LOC |
|---|---|---|
| `ui/screens/YilSettingsScreen.kt` | Extracted from SettingsActivity, no content change | 1,500 (moved) |
| `ui/screens/UmrSettingsScreen.kt` | New — sections in §2.2 | 600 |
| `ui/components/WheelDatePicker.kt` | Apple-style three-column DD/MM/YYYY rolling picker | 260 |
| `ui/components/WhoTabs.kt` | Segmented `[ Me ][ Dad ][ Mom ]` | 80 |
| Updated `SettingsActivity.kt` | Becomes thin dispatcher with pill + two screens | 300 |

`SettingsActivity` net change: 2,607 → ~300 lines (after extraction).

### 6.1 WheelDatePicker contract

```kotlin
@Composable
fun WheelDatePicker(
    initialDay: Int,            // 1..31
    initialMonth: Int,          // 1..12
    initialYear: Int,           // e.g. 2004
    yearRange: IntRange = (today.year - 120)..today.year,
    onChange: (day: Int, month: Int, year: Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

#### Visual reference

Exactly matches the user-provided mockup: three roller columns (DD · MM · YYYY) on the deep-ink Umr sheet background, with the centre item lifted into a bordered amber-gold "selection chip" and adjacent rows fading to muted grey. Inline label row beneath each column reads `DD`, `MM`, `YYYY` in small caps amber.

```
 ┌──────────┐   ┌────────────┐   ┌──────────┐
 │   02     │   │  August    │   │   2002   │   ← muted, ~62 % alpha
 │   03     │   │  September │   │   2003   │   ← muted, ~80 % alpha
 │ ┌──────┐ │ : │ ┌────────┐ │ : │ ┌──────┐ │
 │ │  04  │ │   │ │October │ │   │ │ 2004 │ │   ← centre selection (bright)
 │ └──────┘ │   │ └────────┘ │   │ └──────┘ │
 │   05     │   │  November  │   │   2005   │
 │   06     │   │  December  │   │   2006   │
 └──────────┘   └────────────┘   └──────────┘
     DD             MM              YYYY
```

#### Layout

- Three column cards, each a `RoundedCornerShape(20.dp)` with a 1 dp `HairlineGold` border and a faint vertical gradient fill (`InkBlack` → `InkBlackElevated`).
- 5 visible rows per column. Row height ≈ `64.dp`; total card height ≈ `320.dp`.
- Between columns: 12 dp horizontal gap with a single `":"` glyph in muted amber, vertically centred on the selection row.
- Below the row of three cards: a `Row` of three `Text`s `"DD" / "MM" / "YYYY"` in `SmallCaps`, amber-gold at 70 % alpha, top-padded 8 dp, horizontally aligned to each column's centre.

#### Centre selection chip

- A `Box` overlay at the column's vertical centre, `RoundedCornerShape(14.dp)`, height = one row, border 1.dp `AmberGold` at 80 % alpha, with a soft inner shadow / dark fill that's slightly lighter than the column background (≈ 6 % white tint). Conveys the "lifted" capsule look in the screenshot.

#### Row styling

| Position | Text colour | Text size | Alpha | Font weight |
|---|---|---|---|---|
| centre (Δ = 0) | `AmberGold` | 28 sp | 100 % | SemiBold |
| ±1 row | `OffWhite` | 18 sp | 78 % | Normal |
| ±2 rows | `OffWhite` | 16 sp | 50 % | Normal |
| > ±2 rows | n/a | — | clipped by card edge | — |

Sizes interpolate smoothly during fling — at scroll offset 0.5 between rows, the affected rows show 23 sp / 17 sp etc. (linear interpolation of size + alpha).

#### Mechanics

- Each column is a `LazyColumn` with `flingBehavior = rememberSnapFlingBehavior(state)` so the wheel snaps to row centres.
- `state.firstVisibleItemIndex + offsetFraction` drives both the emitted value and the per-row size/alpha calculations. Recomposed via `derivedStateOf` to avoid per-frame allocations.
- Wrap-around for months (Dec → Jan in the next year) and days (when a month has fewer days) is implemented by recomputing the valid `dayCount` for the *currently-snapped* month/year combo and clamping the day column.
- Haptic tick on each row snap (light click, opt-in to `umrSettings`-level vibrate setting via `rememberUxFeedback`).

#### Validation

- Future date guard: if the picked `(day, month, year)` is strictly later than today, the editor sheet's "Done" button disables and shows a subtle inline hint `"Date can't be in the future."` below the picker.
- Minimum year: `today.year - 120` (allows 120-year-old parents — generous).
- Leap-year math: handled by `java.time.YearMonth.of(year, month).lengthOfMonth()`.

---

## 7. Edge cases + error handling

| Case | Behaviour |
|---|---|
| Mom DOB unset | Red ring absent; Mom counter shows `"— / 4000"` in 40 % alpha |
| Dad DOB unset | Blue ring absent; Dad counter shows `"— / 4000"` in 40 % alpha |
| Parent DOB in future | Treated as unset for ring; counter shows `"0 / 4000"` |
| Parent over 80 yrs (>4160 wks) | Ring clamps to last cell (row 79 col 51); counter shows the actual number, can exceed denominator (e.g. `4250 / 4000`) — accurate by design |
| All three DOBs identical | All three rings + your filled dot stack at the same cell; rings render after dots so still visible |
| Your DOB cleared while auto-switch is on | Existing `currentEffectiveMode()` already falls back to Yil — no change needed |
| Migration v11 read failure | All new keys default-read safe (`getLong(k, 0L)`, etc.) — migration is non-destructive, missing keys are harmless |
| Visualization toggle during slider drag | Last-write-wins on the prefs key; pragmatic — drag ends, mode toggle stamps the new defaults |

---

## 8. Testing + verification

### 8.1 New / extended unit tests

- `UmrLayoutComputeTest` — new assertion: with `counterBandHeightPx > 0`, the new `gridTopPx` is exactly `safeTopPx + counterBandHeightPx` and `dotSizePx` is recomputed accordingly.
- `UmrParentRingsTest` (new) — `weekIndexFor(birthday, now)` returns:
  - `-1` for `birthday = 0L`
  - `-1` for future `birthday`
  - Expected cell for fixed past birthday
  - Clamps to `ROWS*COLS - 1` for centenarian birthdays
- `UmrVisualModeDefaultsTest` (new) — calling `setUmrVisualMode(X_MARKS)` then reading `umrSettings` returns `livedAlpha = 0.30f` and `emptyAlpha = 1.0f`; switching back yields `1.0f` and `0.6f`.
- `LifeDotsPreferencesTest` — extend the existing migration tests with a v10 → v11 case: an install with only the v10 keys upgrades without losing the user's DOB or alpha settings.

### 8.2 Manual verification matrix on the S21+

| Scenario | Expected |
|---|---|
| Fresh install, no DOBs set | Yil renders today; switching to Umr shows no rings, no fills, counters show `"— / 4000"` × 3 |
| Set only your DOB | Filled dots from cell 0 to your current week; gradient band on your year row; today glow; Me counter accurate; Mom + Dad counters `"— / 4000"` |
| Set Mom 50 yrs back | Red outline ring at cell 2600 (row 50 col 0); Mom counter shows `2600 / 4000` |
| Set Dad too | Blue ring at his cell; Dad counter accurate |
| Toggle to X-mode | Lived cells become crosses; sliders read 30 % / 100 %; today glow unchanged |
| Toggle back to Dots | Crosses revert to circles; sliders read 100 % / 60 % |
| Move a slider, then toggle mode | Mode toggle resets sliders to new mode's defaults — user warned in §3.2 |
| Wait until midnight | Counters increment by 1 for any person whose week boundary just crossed; "today" dot moves |

### 8.3 No push policy

All builds local at versionCode 10201 / versionName 1.2.1-dev unless explicitly told to bump. No `gh pr create`, no `git push`. The release-build APK is installed via `adb install` for verification only.

---

## 9. Out of scope (deliberate)

- Sibling / partner DOBs. Only Mom + Dad in this pass.
- Per-mode slider persistence (switching mode resets — confirmed by user).
- A weekly-tick `WorkManager` job. The existing per-visibility + minute redraws cover all real cases since week boundaries are rare events.
- Re-skinning Yil. Yil's UI is unchanged.
- Changing the grid dimensions. Stays at 52×80.
- Reconciling the `4000` counter denominator with the `4160` grid total. Per user, the counters are an *idealised lifespan* number; the grid is the *visible canvas* — they do not need to match.
