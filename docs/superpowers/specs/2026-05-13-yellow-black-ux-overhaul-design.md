# Yellow-and-Black UX Overhaul — design spec

**Date:** 2026-05-13
**Target version:** `v1.2.0`
**Status:** approved by user, ready for implementation plan

---

## 1. Overview

Rebrand the app's two non-wallpaper screens (MainActivity / SettingsActivity) around a yellow + black identity, and lift the UX per-element with haptics, micro-animations, optional sound effects, and RunBeat-inspired controls. The wallpaper renderer (Yil + Umr) and the lockscreen view itself stay untouched — this is a chrome/UI pass only.

Three concrete UX changes are bundled:

1. **Visual rebrand** — warm amber-gold (`#FFB300`) + near-pure black (`#0A0A0A`). Home is yellow-forward; Settings is black-forward (yellow accents).
2. **Per-element craft** — haptic feedback, scale-press animations, optional system click sounds, polished switches/sliders. New shared settings: **Sounds** + **Vibrations** toggles.
3. **Two specific control redesigns**:
   - Birthday entry switches from modal date picker to three inline number fields (Year / Month / Day) plus a Save button.
   - Auto-switch interval becomes a continuous logarithmic slider (1 second → 1 hour) with live label and tick anchors, replacing the 6-chip discrete picker.

---

## 2. Color palette

Single source of truth lives in `ui/theme/Color.kt` (additive — keep existing Yil renderer theme colors untouched; the new colors are app-chrome only).

| Token | Hex | Use |
|---|---|---|
| `AmberGold` | `#FFB300` | Primary brand color; Home background, Settings accents |
| `AmberGoldDark` | `#E89E00` | Pressed/hover state of yellow surfaces |
| `AmberGoldWash` | `#FFB30033` (20% alpha) | Tinted backgrounds, subtle borders on black surfaces |
| `InkBlack` | `#0A0A0A` | Settings background, Home button fills, primary text on yellow |
| `InkBlackElevated` | `#171717` | Raised cards on Settings background |
| `InkBlackDeep` | `#000000` | Drop shadows, true-black for AMOLED contrast |
| `DarkAmber` | `#3D2900` | Muted text on yellow surfaces (e.g. "Birthday" label) |
| `GoldenMuted` | `#FFB30099` (60% alpha) | Muted text on black surfaces (e.g. subtitles) |
| `OffWhite` | `#F5F5F5` | Body text on dark backgrounds where pure yellow would be too loud |
| `HairlineGold` | `#FFB30040` (25% alpha) | Card borders, thin dividers on Settings |

The four built-in `ThemeOption` values (Light/Dark/AMOLED/Custom) still drive the **wallpaper** color set. They do NOT drive the app chrome — chrome is fixed to the yellow+black brand. (Toggling theme still works for the wallpaper view; the rest of the app remains branded.)

---

## 3. Home screen (MainActivity)

### Layout (top to bottom)

```
┌───────────────────────────────────────────┐
│  [status bar — yellow background tint]   │
│                                           │
│           ┌──────────┐                    │
│           │  SKULL   │  ← black logo card │
│           │ (yellow  │     (already       │
│           │  inside) │      yellow icon   │
│           └──────────┘      on black)     │
│                                           │
│           O'lyapmiz                       │  black text, big
│      See the year pass, one dot at a time │  dark amber muted
│                                           │
│    365 dots, one filled each day. ...     │  dark amber body text
│                                           │
│           133 days passed                 │  black big number
│           232 days remaining              │  dark amber muted
│                                           │
│   ┌─────────────────────────────────┐    │
│   │       Set as Wallpaper          │    │  black pill, yellow text
│   └─────────────────────────────────┘    │
│   ┌─────────────────────────────────┐    │
│   │        Customize                │    │  outlined black, yellow text
│   └─────────────────────────────────┘    │
│   ┌─────────────────────────────────┐    │
│   │     Check for updates           │    │  outlined black, yellow text
│   └─────────────────────────────────┘    │
│                                           │
└───────────────────────────────────────────┘
```

- Background: `AmberGold` (#FFB300) — flat, no gradient.
- Status bar tinted yellow with dark icons (use `WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true`).
- Logo card: existing yellow-on-black `o'lyapmiz.png` adaptive icon at ~140dp, no card chrome around it (the icon itself is the card).
- Title: black, 32sp, FontWeight.SemiBold.
- Subtitle ("See the year pass…"): `DarkAmber` (#3D2900), 16sp, regular.
- Body paragraph: `DarkAmber`, 14sp, line-height 1.5.
- Day-count line: black, 22sp, semibold, with the muted "X days remaining" below.
- Buttons: 56dp tall, 16dp corner radius (rounded but not pill), 16dp horizontal padding.
  - Primary ("Set as Wallpaper"): filled `InkBlack`, text `AmberGold`, no border.
  - Secondary ("Customize" / "Check for updates"): transparent fill, 2dp `InkBlack` border, text `InkBlack`.
- Drop shadow under primary button only (4dp Y offset, 8dp blur, `InkBlackDeep` 30% alpha) — subtle tactile depth.
- The existing "Keep wallpaper always running" battery-card stays but restyled: black card on yellow surface, yellow icon, white text.

### Interactions

- Every button: `HapticFeedbackType.TextHandleMove` on tap + scale animation to 0.95 over 100ms with `FastOutSlowInEasing`, springs back over 150ms.
- Sound: `view.playSoundEffect(SoundEffectConstants.CLICK)` if user has Sounds toggle on (gated by a check on `LifeDotsPreferences.settings.soundsEnabled`).

---

## 4. Settings screen (SettingsActivity)

### Background + general style

- Root background: `InkBlack` (#0A0A0A).
- Top app bar (the existing "O'lyapmiz Settings" header): black background, `AmberGold` title text, 28sp SemiBold.
- Section headings ("Theme", "Transparency", "Umr", etc.): `AmberGold`, 16sp, SemiBold, 20dp top padding.
- Body text: `OffWhite` (#F5F5F5).
- Muted/helper text: `GoldenMuted`.
- Divider between sections: 1dp `HairlineGold`.

### Section containers (RunBeat-style cards)

Each `SettingsSection` becomes a rounded card:

```
┌─────────────────────────────────┐
│ ┌───────────────────────────┐   │   16dp horizontal page margin
│ │ ━━━━━━━━━━━━━━━━━━━━━━━━━ │   │   24dp corner radius
│ │ Section title (gold)      │   │   InkBlackElevated background
│ │                           │   │   1dp HairlineGold border
│ │ [setting row]             │   │   16dp internal padding
│ │ [setting row]             │   │
│ └───────────────────────────┘   │
└─────────────────────────────────┘
```

### Specific element styling

- **Mode pill toggle (Yil/Umr)**: black outer pill background, yellow active chip (was lavender + white). Same shape and behavior as today — colors swap.
- **Switches** (Material 3 `Switch`):
  - `checkedThumbColor = InkBlack`
  - `checkedTrackColor = AmberGold`
  - `uncheckedThumbColor = GoldenMuted`
  - `uncheckedTrackColor = InkBlackElevated` with `HairlineGold` border
- **Sliders** (Material 3 `Slider`):
  - `thumbColor = AmberGold`
  - `activeTrackColor = AmberGold`
  - `inactiveTrackColor = InkBlackElevated`
  - `activeTickColor = InkBlack`
  - `inactiveTickColor = GoldenMuted`
- **Theme option buttons** (the existing Light/Dark/AMOLED/Custom row): kept functional, restyled — black square cards with `HairlineGold` border, yellow icon, yellow text. Selected card gets a 2dp `AmberGold` border instead of hairline.
- **FilterChip** (used only for the existing interval picker, replaced in this overhaul): n/a (going away).
- **OutlinedTextField** (used in the new birthday editor): yellow focused border, gold-muted unfocused border, `AmberGold` cursor, `OffWhite` text.
- **Primary buttons** (e.g. "Save birthday"): filled `AmberGold`, `InkBlack` text, 56dp tall, 16dp corner radius, with `HapticFeedbackType.LongPress` haptic on confirm (a slightly stronger tick than the generic tap).

### Per-element craft

For every clickable surface in Settings:

| Interaction | Animation | Haptic | Sound |
|---|---|---|---|
| Button tap | scale 1.0 → 0.95 → 1.0, 250ms total | `TextHandleMove` | system CLICK |
| Pill toggle | active chip slides + fade, 200ms | `TextHandleMove` | system CLICK |
| Switch toggle | track color crossfade, thumb springs | `LongPress` (stronger) | system NAVIGATION_DOWN |
| Slider drag | none (continuous) | `TextHandleMove` at each tick anchor | none (would be noisy) |
| Slider release | none | `LongPress` | system CLICK |
| Card press (e.g. Birthday card) | elevation 4dp → 1dp over 80ms, returns over 120ms | `TextHandleMove` | system CLICK |
| TextField focus | border color animates 200ms | none | none |

All haptics gated by `settings.vibrationsEnabled`. All sounds gated by `settings.soundsEnabled`. Both default ON.

---

## 5. Birthday entry redesign

### Replace this:

```
┌────────────────────────────────────┐
│ Birthday                           │   ← clickable card opens modal
│ 4 October 2004                     │     DatePickerDialog
└────────────────────────────────────┘
```

### With this (inline editor inside Umr section):

**Collapsed state (birthday is set):**

```
┌────────────────────────────────────┐
│ Birthday          4 October 2004 ✎ │   ← row with pencil icon
└────────────────────────────────────┘
```

**Editing state (tap row → fields appear inline below):**

```
┌────────────────────────────────────┐
│ Birthday                           │
│                                    │
│  ┌─────┐  ┌─────┐  ┌─────┐         │
│  │ DD  │  │ MM  │  │YYYY │         │   ← 3 OutlinedTextField,
│  └─────┘  └─────┘  └─────┘         │     number keyboards
│   Day      Month    Year           │
│                                    │
│  ┌────────────────────────────┐   │
│  │           Save             │   │   ← yellow button
│  └────────────────────────────┘   │
└────────────────────────────────────┘
```

**Empty state (birthday not set):**

```
┌────────────────────────────────────┐
│ ⚠ Set your birthday  (red card)   │   ← sticky red CTA stays
│   Required to render life grid     │
└────────────────────────────────────┘
                                       ← tap to expand into editor
```

### Field behavior

- Day field: 2-digit, accepts 1–31, validates against month/year on Save.
- Month field: 2-digit, accepts 1–12.
- Year field: 4-digit, accepts 1900–2099.
- Auto-advance: typing the 2nd digit in Day jumps focus to Month; Month → Year.
- Save button:
  - Disabled (gray-yellow) if any field empty or invalid.
  - On press: validates, writes via `preferences.setUmrBirthday(epochMs)`, collapses back to "Birthday: 4 October 2004 ✎" row.
  - On invalid input: shake animation + red border on the bad field + helper text below.
- Cancel: tap outside the editor area collapses without saving.

### Pre-fill rules

- If birthday is already set: fields pre-fill with current value.
- If not set: fields are empty, placeholders show "DD" / "MM" / "YYYY".

### Edge cases

- Year before 1900 or after 2099: red border on year field + "Year must be 1900–2099".
- Invalid month/day combo (e.g. 31 February): red border on day field + "Invalid date for this month".
- All-zero input: Save stays disabled.

---

## 6. Auto-switch interval slider

### Replace this:

```
Switch every
[5s] [1m] [2m] [5m] [30m] [1h]    ← 6 FilterChips, sometimes wraps
```

### With this:

```
Switch every
       5 seconds
●────────────────────────────────────●
1s    5s   30s   5m   30m       1h
                                       ← Material3 Slider with tick anchors
```

### Behavior

- Continuous slider, mapped logarithmically from 1s → 3600s.
- Formula: `intervalMs = 1000 * exp(log(1) + slider01 * (log(3600) - log(1))) = 1000 * 3600^slider01`. Slider position 0 → 1 second; position 1 → 1 hour.
- Slider returns `slider01 ∈ [0, 1]`; convert to `intervalMs`, snap to a 1-second resolution under 1 minute / 5-second resolution 1–10 min / 1-minute resolution above.
- Live label updates above the slider as user drags: "Every 1 second" / "Every 7 seconds" / "Every 2 minutes" / "Every 1 hour".
- Tick anchors visible at the 5 mapped positions: 5s (0.279), 30s (0.495), 5m (0.694), 30m (0.876), 1h (1.000). 1s is at the left edge.
- Haptic `TextHandleMove` fires when the slider value crosses each tick anchor (a soft "click" feel as user passes the labeled stops).
- On release: `setAutoSwitchIntervalMs(intervalMs)` writes the value + resets `referenceMs`+`startMode` (same setter as today).

### Label formatting

```
< 60s  →  "Every X seconds"
60–3599s →  "Every X minutes" (rounded to nearest minute under 5m; nearest 5 minutes above)
3600s  →  "Every 1 hour"
```

---

## 7. New "Sounds" and "Vibrations" toggles

Two new shared rows added to the Settings page (visible in both Yil and Umr modes), placed inside a new "App" section that lives just above the existing Theme section.

```
┌─ App ───────────────────────────────┐
│  Sounds         system click effects [●─]│
│                                          │
│  Vibrations    haptic feedback on tap [●─]│
└──────────────────────────────────────────┘
```

- Both default ON.
- Stored as new prefs: `KEY_SOUNDS_ENABLED` (Boolean, default true), `KEY_VIBRATIONS_ENABLED` (Boolean, default true).
- New `WallpaperSettings` fields: `soundsEnabled: Boolean = true`, `vibrationsEnabled: Boolean = true`.
- New setters: `setSoundsEnabled(Boolean)`, `setVibrationsEnabled(Boolean)`.
- A central helper `UxFeedback` (top-level Compose helpers or `LocalUxFeedback`) reads these settings and exposes:
  - `fun click()` — performs haptic + sound if enabled.
  - `fun confirm()` — stronger haptic (LongPress) + sound.
  - `fun tick()` — soft haptic only (no sound; for slider drag).

All new UI elements route their interactions through `UxFeedback`.

---

## 8. Code structure / file changes

### New files

- `app/src/main/java/com/example/lifedots/ui/theme/BrandColors.kt` — palette tokens
- `app/src/main/java/com/example/lifedots/ui/components/UxFeedback.kt` — haptic + sound helper
- `app/src/main/java/com/example/lifedots/ui/components/BirthdayEditor.kt` — inline 3-field editor
- `app/src/main/java/com/example/lifedots/ui/components/IntervalSlider.kt` — log slider with ticks
- `app/src/main/java/com/example/lifedots/ui/components/PressableCard.kt` — wraps Surface with press animation + haptic

### Modified files

- `app/src/main/java/com/example/lifedots/ui/theme/Theme.kt` — bind brand colors into MaterialTheme (chrome only — wallpaper still reads its own ThemeColors)
- `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt` — new fields, keys, setters; migration v10 (no-op bump; defaults cover both)
- `app/src/main/java/com/example/lifedots/MainActivity.kt` — yellow background, black buttons, restyled cards
- `app/src/main/java/com/example/lifedots/SettingsActivity.kt` — black background, yellow accents, restyle existing sections, swap in BirthdayEditor + IntervalSlider, add App section with Sounds/Vibrations toggles
- `app/src/main/java/com/example/lifedots/ui/components/ModeTogglePill.kt` — recolor from lavender/white to black/yellow

### Out of scope (unchanged)

- `wallpaper/LifeDotsWallpaperService.kt` (renderer)
- `wallpaper/CalendarLayout.kt` + `UmrLayoutCompute`
- `wallpaper/AutoSwitchRotator.kt` (engine logic)
- `preferences/LifeDotsPreferences.kt`'s existing `ThemeOption`/`CustomColors` (still drive wallpaper rendering, not chrome)
- `ui/components/DatePickerDialog.kt` (kept in tree as dead code for now; can be deleted in a later cleanup commit)

---

## 9. Migration v10

```kotlin
if (stored < 10) {
    // v10: introduces Sounds + Vibrations toggles. No destructive
    // writes — class defaults (both true) cover both fresh installs
    // and upgraders.
}
```

`CURRENT_MIGRATION_VERSION = 10`.

---

## 10. Edge cases

1. **Status bar contrast on yellow background** — set `isAppearanceLightStatusBars = true` so system icons render dark. Test on Samsung's status bar overlay (notification icons).
2. **Soft keyboard pushing the birthday editor off screen** — wrap the editor in `imePadding()` so the Save button stays visible above the keyboard.
3. **User types `00` in a number field** — Save validation catches it (`day < 1` → invalid).
4. **User types non-numeric chars** — `keyboardType = KeyboardType.Number` prevents this; also a regex filter inside `onValueChange` strips non-digits as a backup.
5. **Slider snap drift** — at the upper end of the log range, single-second precision is meaningless. We snap to: 1-second steps below 60s, 5-second steps 60s–600s, 1-minute steps above. Live label respects the snapped value.
6. **Sound played while phone is on silent** — `playSoundEffect(CLICK)` respects the system Touch Sounds setting; if the user has Touch Sounds off in OS settings, no sound regardless of our toggle. Our toggle is additive on top.
7. **Haptic on devices without vibrator** — `HapticFeedback.performHapticFeedback` is a no-op on devices without a vibrator (rare on modern phones).
8. **Yellow-on-yellow text invisibility** — every text on yellow background uses `InkBlack` or `DarkAmber`; we don't reference yellow tokens for text on yellow surfaces.
9. **AMOLED burn-in concern** — Settings is mostly black so burn-in is fine; Home is yellow but users see it briefly between wallpaper interactions, not for hours.

---

## 11. Non-goals (explicitly out of scope)

- Per-user palette customization for the app chrome (yellow/black is fixed).
- Custom font (we keep Material 3 default typography family).
- Confetti / particle effects on Save.
- Sound effect customization (single system CLICK is enough).
- Dark/light mode toggle for the chrome itself.
- Tablet-specific layout (single phone-portrait design).
- Accessibility roles audit (pre-existing gap; can be addressed in a future a11y pass).

---

## 12. Future enhancements (post `v1.2.0`)

- Move the existing `ColorPicker` / `DatePickerDialog` components out of the tree (currently kept as dead code).
- Custom typeface (a humanist sans like Manrope or Inter would feel "premium" with this palette).
- Dynamic-color support for Android 12+ where the user's wallpaper-derived colors influence accents — but only on the wallpaper view, not the chrome.
- Settings search bar (the file is getting long; a search would help discoverability).
- Animation when flipping Yil/Umr in auto-switch on the actual wallpaper (currently instant; a 200ms crossfade would be a nice touch).

---

— end of spec —
