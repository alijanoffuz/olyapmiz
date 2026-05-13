# Yellow + Black UX Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand MainActivity + SettingsActivity around warm amber-gold (#FFB300) + near-pure black (#0A0A0A), add per-element haptic / sound / animation craft, replace the birthday date picker with inline 3-field entry, replace the interval chip row with a logarithmic slider, and add Sounds + Vibrations toggles. Wallpaper rendering and lockscreen view stay untouched.

**Architecture:** New `BrandColors` palette tokens drive only the app chrome (Home + Settings) via Material 3 theming overrides — the wallpaper renderer continues to read its own `ThemeColors` from `ThemeOption`. A `UxFeedback` Compose helper centralizes haptic + sound triggers and is gated by two new prefs (`soundsEnabled` / `vibrationsEnabled`, both default ON). The slider for auto-switch uses a log-of-seconds curve so 1-second precision is achievable at the low end and a 1-hour upper bound fits on the same control.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, `androidx.compose.ui.hapticfeedback`, `android.view.SoundEffectConstants`, JUnit 4 (already configured).

**Reference docs:** [spec](../specs/2026-05-13-yellow-black-ux-overhaul-design.md), [PROJECT_NOTES.md](../../../PROJECT_NOTES.md), [RunBeat HomeScreen](../../../../../Runner%20Ai%20App/app/src/main/java/uz/ibrohim/runbeat/ui/home/HomeScreen.kt) (aesthetic reference, not modified).

---

## File Structure

### New files
- `app/src/main/java/com/example/lifedots/ui/theme/BrandColors.kt` — palette tokens
- `app/src/main/java/com/example/lifedots/ui/components/UxFeedback.kt` — haptic + sound helper
- `app/src/main/java/com/example/lifedots/ui/components/PressableCard.kt` — Surface wrapper with press scale + haptic
- `app/src/main/java/com/example/lifedots/ui/components/BirthdayEditor.kt` — 3-field birthday entry
- `app/src/main/java/com/example/lifedots/ui/components/IntervalSlider.kt` — log-mapped 1s→1h slider
- `app/src/test/java/com/example/lifedots/ui/components/IntervalSliderMathTest.kt` — pure tests for the log mapping

### Modified files
- `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt` — soundsEnabled / vibrationsEnabled fields, keys, setters, migration v10
- `app/src/main/java/com/example/lifedots/ui/theme/Theme.kt` — wire BrandColors into MaterialTheme for chrome
- `app/src/main/java/com/example/lifedots/ui/components/ModeTogglePill.kt` — recolor from lavender/white to black/yellow
- `app/src/main/java/com/example/lifedots/MainActivity.kt` — yellow background, black buttons, brand restyle
- `app/src/main/java/com/example/lifedots/SettingsActivity.kt` — black background, yellow accents, App section with new toggles, swap in BirthdayEditor + IntervalSlider
- `PROJECT_NOTES.md` — v1.2.0 row

---

## Task 1: Brand palette + Sounds/Vibrations prefs + migration v10

**Goal:** Lay the foundation. Add the brand color tokens, the two new prefs and their setters, and the migration v10 bump. Cover the migration bump with a quick smoke check (existing tests must still pass).

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/theme/BrandColors.kt`
- Modify: `app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt`

- [ ] **Step 1.1: Create `BrandColors.kt`**

```kotlin
package com.example.lifedots.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette for the app chrome (MainActivity + SettingsActivity).
 *
 * NOTE: this is NOT for the wallpaper renderer — the wallpaper still
 * reads its own `ThemeColors` derived from `ThemeOption`/`CustomColors`
 * in `LifeDotsPreferences`. These tokens are only for Home + Settings.
 */
object BrandColors {
    val AmberGold = Color(0xFFFFB300)          // primary brand
    val AmberGoldDark = Color(0xFFE89E00)      // pressed / hover
    val AmberGoldWash = Color(0x33FFB300)      // 20% alpha tint

    val InkBlack = Color(0xFF0A0A0A)           // primary surface / button fill
    val InkBlackElevated = Color(0xFF171717)   // raised cards
    val InkBlackDeep = Color(0xFF000000)       // shadows / true black

    val DarkAmber = Color(0xFF3D2900)          // muted text on yellow
    val GoldenMuted = Color(0x99FFB300)        // muted text on black (60% alpha)
    val OffWhite = Color(0xFFF5F5F5)           // body text on dark
    val HairlineGold = Color(0x40FFB300)       // card borders / dividers (25% alpha)
}
```

- [ ] **Step 1.2: Add the two new fields to `WallpaperSettings`**

In `LifeDotsPreferences.kt`, append to the `WallpaperSettings` data class parameter list (alongside the existing `topViewMode`, `autoSwitchSettings`, `umrSettings`):

```kotlin
    val soundsEnabled: Boolean = true,
    val vibrationsEnabled: Boolean = true,
```

- [ ] **Step 1.3: Add pref keys**

In the `companion object`, alongside the other new keys (`KEY_TOP_VIEW_MODE` etc.):

```kotlin
private const val KEY_SOUNDS_ENABLED = "sounds_enabled"
private const val KEY_VIBRATIONS_ENABLED = "vibrations_enabled"
```

- [ ] **Step 1.4: Extend `loadSettings()` to read the keys**

At the end of the `WallpaperSettings(...)` constructor call, before the closing paren:

```kotlin
    soundsEnabled = prefs.getBoolean(KEY_SOUNDS_ENABLED, true),
    vibrationsEnabled = prefs.getBoolean(KEY_VIBRATIONS_ENABLED, true),
```

- [ ] **Step 1.5: Add setters**

Place near the existing setters (e.g. next to `setUmrBirthday`):

```kotlin
fun setSoundsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SOUNDS_ENABLED, enabled).apply()
    val current = _settingsFlow.value
    _settingsFlow.value = current.copy(soundsEnabled = enabled)
    notifyWallpaperChanged()
}

fun setVibrationsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_VIBRATIONS_ENABLED, enabled).apply()
    val current = _settingsFlow.value
    _settingsFlow.value = current.copy(vibrationsEnabled = enabled)
    notifyWallpaperChanged()
}
```

- [ ] **Step 1.6: Bump `CURRENT_MIGRATION_VERSION` to 10**

Change `private const val CURRENT_MIGRATION_VERSION = 9` to `= 10`. Add a v10 block in `runMigrationsIfNeeded()` before the final `editor.putInt(KEY_MIGRATION_VERSION, ...)`:

```kotlin
if (stored < 10) {
    // v10: introduces soundsEnabled + vibrationsEnabled. Both default
    // true via the data-class defaults — no destructive writes needed.
}
```

- [ ] **Step 1.7: Compile-check + run existing tests**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL on both. All existing tests (AutoSwitchModeTest, UmrLayoutComputeTest) still pass since the new fields have defaults.

- [ ] **Step 1.8: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/theme/BrandColors.kt \
        app/src/main/java/com/example/lifedots/preferences/LifeDotsPreferences.kt
git commit -m "$(cat <<'EOF'
brand: palette tokens + Sounds/Vibrations prefs + migration v10

Adds BrandColors for the new yellow+black app chrome (amber-gold
#FFB300 + near-black #0A0A0A). New soundsEnabled / vibrationsEnabled
fields on WallpaperSettings (both default true) drive the upcoming
UxFeedback helper. Migration v10 is a no-op bump — class defaults
cover both fresh installs and v9 upgraders.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `UxFeedback` helper + Theme.kt brand wiring

**Goal:** Centralize haptic + sound triggers behind a single Compose helper, and bind BrandColors into the Material 3 color scheme so all surfaces, switches, sliders, and text adopt the brand by default.

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/components/UxFeedback.kt`
- Modify: `app/src/main/java/com/example/lifedots/ui/theme/Theme.kt`

- [ ] **Step 2.1: Create `UxFeedback.kt`**

```kotlin
package com.example.lifedots.ui.components

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Centralized haptic + sound feedback for interactive elements.
 *
 * Three feedback levels:
 *  - click(): light tick (TextHandleMove) + system CLICK sound — buttons, chips, cards
 *  - confirm(): stronger tick (LongPress) + system CLICK — toggles, save button
 *  - tick(): soft tick only, no sound — slider drag (would be noisy)
 *
 * Both haptic and sound are gated by the user's settings
 * (`soundsEnabled` / `vibrationsEnabled`). Sounds also respect the
 * system Touch Sounds setting (`playSoundEffect` is a no-op if the
 * OS-level Touch Sounds is off).
 */
class UxFeedback internal constructor(
    private val haptic: HapticFeedback,
    private val playSound: (effect: Int) -> Unit,
    private val soundsEnabled: () -> Boolean,
    private val vibrationsEnabled: () -> Boolean,
) {
    fun click() {
        if (vibrationsEnabled()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (soundsEnabled()) playSound(SoundEffectConstants.CLICK)
    }

    fun confirm() {
        if (vibrationsEnabled()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (soundsEnabled()) playSound(SoundEffectConstants.CLICK)
    }

    fun tick() {
        if (vibrationsEnabled()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/**
 * Compose helper: returns a `UxFeedback` instance bound to the current
 * haptic + view + the live values of soundsEnabled / vibrationsEnabled.
 *
 * Usage:
 *   val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)
 *   Button(onClick = { feedback.click(); doThing() }) { ... }
 */
@Composable
fun rememberUxFeedback(
    soundsEnabled: Boolean,
    vibrationsEnabled: Boolean,
): UxFeedback {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(haptic, view) {
        UxFeedback(
            haptic = haptic,
            playSound = { effect -> view.playSoundEffect(effect) },
            soundsEnabled = { soundsEnabled },
            vibrationsEnabled = { vibrationsEnabled },
        )
    }.also {
        // Re-create on toggle changes so the latest values are captured.
        // (The remember key list doesn't include booleans because Compose
        // recomposes anyway when they change; the lambda captures latest
        // via the closure.)
    }
}
```

(Note: the above `also { ... }` block is illustrative — Compose's recomposition will pass new boolean values into the lambdas naturally. The lambda `{ soundsEnabled }` captures the *value* read at lambda construction, not a live reference; this means a setting change won't be reflected until the next recomposition. That's fine: every consumer reads `settings` via `collectAsState()` and will recompose immediately on a setter call. Verify by reading any existing call site to `setHighlightToday` — it triggers a settings flow update → recomposition → fresh lambda.)

- [ ] **Step 2.2: Read existing `Theme.kt` to understand the baseline**

`app/src/main/java/com/example/lifedots/ui/theme/Theme.kt` is small (60 lines). It defines `LifeDotsTheme(content: @Composable () -> Unit)` which wraps `MaterialTheme(...)`. Find which color scheme it uses (likely `darkColorScheme()` or a custom one).

- [ ] **Step 2.3: Update `Theme.kt` to use BrandColors**

Replace the body of `LifeDotsTheme` with a hand-built `darkColorScheme` that uses BrandColors:

```kotlin
package com.example.lifedots.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BrandDarkColorScheme = darkColorScheme(
    primary = BrandColors.AmberGold,
    onPrimary = BrandColors.InkBlack,
    primaryContainer = BrandColors.AmberGoldWash,
    onPrimaryContainer = BrandColors.AmberGold,

    secondary = BrandColors.AmberGold,
    onSecondary = BrandColors.InkBlack,
    secondaryContainer = BrandColors.AmberGoldWash,
    onSecondaryContainer = BrandColors.AmberGold,

    tertiary = BrandColors.AmberGold,
    onTertiary = BrandColors.InkBlack,

    background = BrandColors.InkBlack,
    onBackground = BrandColors.OffWhite,

    surface = BrandColors.InkBlackElevated,
    onSurface = BrandColors.OffWhite,
    surfaceVariant = BrandColors.InkBlackElevated,
    onSurfaceVariant = BrandColors.GoldenMuted,

    outline = BrandColors.HairlineGold,
    outlineVariant = BrandColors.HairlineGold,

    error = androidx.compose.ui.graphics.Color(0xFFE53935),
    onError = BrandColors.OffWhite,
)

@Composable
fun LifeDotsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BrandDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
```

(If the file currently uses `lightColorScheme()` or has dynamic-color logic, REPLACE that entire block — the chrome is brand-fixed, not system-themed. Keep the `Typography` import if it exists in the same `theme/` package as `Type.kt`. If `Typography` isn't defined, omit the `typography = ...` line.)

- [ ] **Step 2.4: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2.5: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/UxFeedback.kt \
        app/src/main/java/com/example/lifedots/ui/theme/Theme.kt
git commit -m "$(cat <<'EOF'
ux: UxFeedback helper + brand-bound dark color scheme

UxFeedback centralizes haptic + sound triggers behind three levels
(click / confirm / tick), both gated by the user's prefs. Theme.kt
now defines a single brand-dark color scheme using BrandColors —
primary amber-gold, background near-black, surfaces elevated black
with hairline-gold dividers. MaterialTheme consumers (switches,
sliders, buttons, text) inherit these by default.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `ModeTogglePill` recolor

**Goal:** Update the existing pill toggle from lavender + white to black + amber-gold to match the new brand.

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/ui/components/ModeTogglePill.kt`

- [ ] **Step 3.1: Update the color constants in ModeTogglePill**

The current implementation uses hardcoded `Color(0xFFEDECF1)` outer pill, `Color.White` selected chip, `Color.Black` selected text, `Color(0xFF8E8C9A)` unselected text. Replace with BrandColors-derived values:

In `Surface(... color = ...)` of `ModeTogglePill`, change:

```kotlin
color = Color(0xFFEDECF1),       // soft lavender-grey, matches reference
```

to:

```kotlin
color = BrandColors.InkBlackElevated,    // raised black pill background
```

In `PillSide`'s color logic, change:

```kotlin
val bg = if (selected) Color.White else Color.Transparent
val textColor = if (selected) Color.Black else Color(0xFF8E8C9A)
```

to:

```kotlin
val bg = if (selected) BrandColors.AmberGold else Color.Transparent
val textColor = if (selected) BrandColors.InkBlack else BrandColors.GoldenMuted
```

Add the import at the top of the file:

```kotlin
import com.example.lifedots.ui.theme.BrandColors
```

- [ ] **Step 3.2: Compile + visual preview check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. The `@Preview` will now show the black-and-gold version of the toggle.

- [ ] **Step 3.3: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/ModeTogglePill.kt
git commit -m "$(cat <<'EOF'
ui: recolor ModeTogglePill to brand black + amber-gold

Outer pill background switches from lavender (#EDECF1) to brand
elevated black (#171717). Selected chip switches from white +
black text to amber-gold (#FFB300) + ink-black text. Unselected
label muted to GoldenMuted (60% alpha gold) for hierarchy on the
dark pill.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `PressableCard` component

**Goal:** A reusable Surface wrapper that handles press-state scale animation + haptic feedback, so every interactive card in the app has the same tactile feel.

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/components/PressableCard.kt`

- [ ] **Step 4.1: Create the component**

```kotlin
package com.example.lifedots.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A Surface that scales down slightly when pressed and fires a haptic
 * via the provided UxFeedback on each click. Use this for cards,
 * tappable rows, and anything that feels card-shaped.
 *
 * Scale animation: 1.0 → 0.97 → 1.0 with a soft spring.
 * Haptic: feedback.click() on each onClick invocation.
 */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    feedback: UxFeedback,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "PressableCardScale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    feedback.click()
                    onClick()
                },
            ),
        shape = shape,
        color = if (color == Color.Unspecified) Surface.SurfaceColors.surfaceContainer else color,
        contentColor = if (contentColor == Color.Unspecified) Surface.SurfaceColors.onSurface else contentColor,
    ) {
        content()
    }
}

// Helpers so callers don't have to import Surface internals just to get defaults.
private object SurfaceColors {
    val surfaceContainer = Color(0xFF171717)
    val onSurface = Color(0xFFF5F5F5)
}
private val Surface.SurfaceColors get() = SurfaceColors
```

(Note: the `Surface.SurfaceColors` accessor at the bottom is a clean way to default to BrandColors equivalents without depending on MaterialTheme at the call site. Adjust the helper names if you find a cleaner pattern in the codebase.)

- [ ] **Step 4.2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.3: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/PressableCard.kt
git commit -m "$(cat <<'EOF'
ui: PressableCard wrapper for tactile interactive cards

Compose Surface with scale-down-on-press animation (1.0 -> 0.97 with
medium-bouncy spring) and UxFeedback.click() haptic + sound on
release. Reusable across the new BirthdayEditor and existing
section cards to give every tappable surface the same feel.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `BirthdayEditor` component

**Goal:** Three inline number fields (Day / Month / Year) with auto-advance and a Save button, replacing the modal `DatePickerDialog` flow inside the Umr section.

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/components/BirthdayEditor.kt`

- [ ] **Step 5.1: Create the component**

```kotlin
package com.example.lifedots.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifedots.ui.theme.BrandColors
import java.util.Calendar

/**
 * Inline birthday editor — three number fields + Save button.
 *
 * Validation: day 1..31 (per-month aware on Save), month 1..12,
 * year 1900..2099. Save is disabled until all fields are valid.
 * On valid Save, calls `onSave(epochMs)`; on invalid Save attempt,
 * marks the offending field with an error.
 *
 * Auto-advance: typing the 2nd digit in Day jumps to Month;
 * 2nd digit in Month jumps to Year.
 */
@Composable
fun BirthdayEditor(
    initialEpochMs: Long,
    onSave: (epochMs: Long) -> Unit,
    feedback: UxFeedback,
    modifier: Modifier = Modifier,
) {
    val initialCal = remember { Calendar.getInstance().apply { timeInMillis = initialEpochMs.coerceAtLeast(1L) } }

    var day by remember {
        mutableStateOf(if (initialEpochMs > 0L) initialCal.get(Calendar.DAY_OF_MONTH).toString() else "")
    }
    var month by remember {
        mutableStateOf(if (initialEpochMs > 0L) (initialCal.get(Calendar.MONTH) + 1).toString() else "")
    }
    var year by remember {
        mutableStateOf(if (initialEpochMs > 0L) initialCal.get(Calendar.YEAR).toString() else "")
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val dayFocus = remember { FocusRequester() }
    val monthFocus = remember { FocusRequester() }
    val yearFocus = remember { FocusRequester() }

    val parsed = remember(day, month, year) { parseAndValidate(day, month, year) }
    val isValid = parsed != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NumberCell(
                value = day,
                placeholder = "DD",
                label = "Day",
                maxLen = 2,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }.take(2)
                    day = filtered
                    if (filtered.length == 2) monthFocus.requestFocus()
                },
                focusRequester = dayFocus,
                modifier = Modifier.weight(1f),
                isError = parsed == null && day.isNotEmpty() && day.toIntOrNull()?.let { it !in 1..31 } == true,
            )
            NumberCell(
                value = month,
                placeholder = "MM",
                label = "Month",
                maxLen = 2,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }.take(2)
                    month = filtered
                    if (filtered.length == 2) yearFocus.requestFocus()
                },
                focusRequester = monthFocus,
                modifier = Modifier.weight(1f),
                isError = parsed == null && month.isNotEmpty() && month.toIntOrNull()?.let { it !in 1..12 } == true,
            )
            NumberCell(
                value = year,
                placeholder = "YYYY",
                label = "Year",
                maxLen = 4,
                onValueChange = { new ->
                    year = new.filter { it.isDigit() }.take(4)
                },
                focusRequester = yearFocus,
                modifier = Modifier.weight(1.4f),
                isError = parsed == null && year.length == 4 && year.toIntOrNull()?.let { it !in 1900..2099 } == true,
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "",
                color = androidx.compose.ui.graphics.Color(0xFFE53935),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val ms = parseAndValidate(day, month, year)
                if (ms != null) {
                    errorMessage = null
                    feedback.confirm()
                    onSave(ms)
                } else {
                    errorMessage = errorFor(day, month, year)
                }
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.AmberGold,
                contentColor = BrandColors.InkBlack,
                disabledContainerColor = BrandColors.AmberGoldWash,
                disabledContentColor = BrandColors.GoldenMuted,
            ),
        ) {
            Text("Save", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun NumberCell(
    value: String,
    placeholder: String,
    label: String,
    maxLen: Int,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, textAlign = TextAlign.Center) },
        label = { Text(label) },
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = BrandColors.OffWhite,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.focusRequester(focusRequester),
        isError = isError,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = BrandColors.InkBlackElevated,
            unfocusedContainerColor = BrandColors.InkBlackElevated,
            focusedIndicatorColor = BrandColors.AmberGold,
            unfocusedIndicatorColor = BrandColors.HairlineGold,
            errorIndicatorColor = androidx.compose.ui.graphics.Color(0xFFE53935),
            focusedLabelColor = BrandColors.AmberGold,
            unfocusedLabelColor = BrandColors.GoldenMuted,
            cursorColor = BrandColors.AmberGold,
        ),
    )
}

private fun parseAndValidate(day: String, month: String, year: String): Long? {
    val d = day.toIntOrNull() ?: return null
    val m = month.toIntOrNull() ?: return null
    val y = year.toIntOrNull() ?: return null
    if (y !in 1900..2099) return null
    if (m !in 1..12) return null
    val cal = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, y)
        set(Calendar.MONTH, m - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    if (d !in 1..maxDay) return null
    cal.set(Calendar.DAY_OF_MONTH, d)
    return cal.timeInMillis
}

private fun errorFor(day: String, month: String, year: String): String? {
    val d = day.toIntOrNull()
    val m = month.toIntOrNull()
    val y = year.toIntOrNull()
    return when {
        d == null || m == null || y == null -> "Fill all three fields"
        y !in 1900..2099 -> "Year must be 1900–2099"
        m !in 1..12 -> "Month must be 1–12"
        d !in 1..31 -> "Day must be 1–31"
        else -> {
            // Check per-month max
            val cal = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, y); set(Calendar.MONTH, m - 1); set(Calendar.DAY_OF_MONTH, 1)
            }
            "Invalid date — ${calendarMonthName(m)} $y only has ${cal.getActualMaximum(Calendar.DAY_OF_MONTH)} days"
        }
    }
}

private fun calendarMonthName(month: Int): String = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)[(month - 1).coerceIn(0, 11)]
```

- [ ] **Step 5.2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.3: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/BirthdayEditor.kt
git commit -m "$(cat <<'EOF'
ui: BirthdayEditor — three number fields + Save button

Three OutlinedTextFields (Day/Month/Year) with number keyboards
and auto-advance (Day -> Month -> Year on 2nd digit). Save button
is enabled only when all three fields parse to a real calendar
date (day per-month-aware, year 1900-2099). Invalid Save attempts
surface a specific error message. UxFeedback.confirm() haptic +
sound on successful save.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: `IntervalSlider` component + math tests

**Goal:** Logarithmic slider from 1 second to 1 hour with tick anchors at 5s / 30s / 5m / 30m / 1h and a live label that snaps to a sensible resolution. Pure math is unit-tested.

**Files:**
- Create: `app/src/main/java/com/example/lifedots/ui/components/IntervalSlider.kt`
- Create: `app/src/test/java/com/example/lifedots/ui/components/IntervalSliderMathTest.kt`

- [ ] **Step 6.1: Create `IntervalSlider.kt`**

```kotlin
package com.example.lifedots.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifedots.ui.theme.BrandColors
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * Continuous slider mapped logarithmically from 1 second to 1 hour.
 *
 * The user drags a slider in [0, 1]; the displayed/saved interval is
 * `intervalMs = 1000 * 3600^slider01` (so slider01=0 → 1s, slider01=1 → 1h).
 *
 * Resolution snap on the way out:
 *  - under 1 minute: nearest 1 second
 *  - 1 minute to 10 minutes: nearest 5 seconds
 *  - above 10 minutes: nearest 1 minute
 *
 * Tick anchors (visual reference + haptic tick crossings) sit at:
 *  5s, 30s, 5m, 30m, 1h.
 *
 * Haptic: `feedback.tick()` fires when the dragged value crosses one
 * of the tick anchors; `feedback.confirm()` on finger release.
 */
@Composable
fun IntervalSlider(
    currentMs: Long,
    onIntervalChange: (ms: Long) -> Unit,
    feedback: UxFeedback,
    modifier: Modifier = Modifier,
) {
    var slider01 by remember { mutableFloatStateOf(IntervalSliderMath.msToSlider01(currentMs)) }
    var lastTickCrossed by remember { mutableStateOf(-1) }
    val displayedMs = remember(slider01) { IntervalSliderMath.slider01ToSnappedMs(slider01) }
    val label = remember(displayedMs) { IntervalSliderMath.formatLabel(displayedMs) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.AmberGold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = slider01,
            onValueChange = { newValue ->
                slider01 = newValue
                val crossedIndex = IntervalSliderMath.tickIndexAt(newValue)
                if (crossedIndex >= 0 && crossedIndex != lastTickCrossed) {
                    feedback.tick()
                    lastTickCrossed = crossedIndex
                }
            },
            onValueChangeFinished = {
                feedback.confirm()
                onIntervalChange(displayedMs)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = BrandColors.AmberGold,
                activeTrackColor = BrandColors.AmberGold,
                inactiveTrackColor = BrandColors.InkBlackElevated,
                activeTickColor = BrandColors.InkBlack,
                inactiveTickColor = BrandColors.GoldenMuted,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("1s", "5s", "30s", "5m", "30m", "1h").forEach { tickLabel ->
                Text(
                    text = tickLabel,
                    style = MaterialTheme.typography.bodySmall.copy(color = BrandColors.GoldenMuted),
                )
            }
        }
    }
}

/**
 * Pure-function math for the IntervalSlider. Separated so it's
 * testable without Compose.
 */
object IntervalSliderMath {
    const val MIN_MS = 1_000L
    const val MAX_MS = 3_600_000L

    /** Tick anchor positions on the [0,1] slider (must match the labels in IntervalSlider). */
    val TICK_POSITIONS: List<Float> = listOf(
        0f,                            // 1s
        msToSlider01(5_000L),          // 5s
        msToSlider01(30_000L),         // 30s
        msToSlider01(300_000L),        // 5m
        msToSlider01(1_800_000L),      // 30m
        1f,                            // 1h
    )

    /** Map an interval in ms to a slider position in [0, 1]. */
    fun msToSlider01(ms: Long): Float {
        val clamped = ms.coerceIn(MIN_MS, MAX_MS).toDouble()
        val logMin = ln(MIN_MS.toDouble())
        val logMax = ln(MAX_MS.toDouble())
        return ((ln(clamped) - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)
    }

    /** Map a slider position in [0, 1] to an interval in ms (continuous, not snapped). */
    fun slider01ToMs(slider01: Float): Long {
        val logMin = ln(MIN_MS.toDouble())
        val logMax = ln(MAX_MS.toDouble())
        return Math.exp(logMin + slider01.coerceIn(0f, 1f) * (logMax - logMin)).roundToLong()
    }

    /** Map a slider position to a snapped interval (under 1 min: 1s, 1-10 min: 5s, above: 1 min). */
    fun slider01ToSnappedMs(slider01: Float): Long {
        val raw = slider01ToMs(slider01)
        return when {
            raw < 60_000L -> ((raw + 500L) / 1_000L) * 1_000L                    // nearest 1s
            raw < 600_000L -> ((raw + 2_500L) / 5_000L) * 5_000L                  // nearest 5s
            else -> ((raw + 30_000L) / 60_000L) * 60_000L                         // nearest 1m
        }.coerceIn(MIN_MS, MAX_MS)
    }

    /** Returns the index of the closest tick anchor if slider01 is within 0.015 of it; else -1. */
    fun tickIndexAt(slider01: Float, tolerance: Float = 0.015f): Int {
        var bestIdx = -1
        var bestDist = tolerance
        TICK_POSITIONS.forEachIndexed { idx, pos ->
            val d = kotlin.math.abs(pos - slider01)
            if (d <= bestDist) {
                bestDist = d
                bestIdx = idx
            }
        }
        return bestIdx
    }

    /** Human label for a snapped interval. */
    fun formatLabel(ms: Long): String {
        if (ms >= 3_600_000L) return "Every 1 hour"
        if (ms >= 60_000L) {
            val minutes = ms / 60_000L
            return if (minutes == 1L) "Every 1 minute" else "Every $minutes minutes"
        }
        val seconds = ms / 1_000L
        return if (seconds == 1L) "Every 1 second" else "Every $seconds seconds"
    }
}
```

- [ ] **Step 6.2: Create `IntervalSliderMathTest.kt`**

```kotlin
package com.example.lifedots.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalSliderMathTest {

    @Test fun `msToSlider01 returns 0 for 1 second`() {
        assertEquals(0f, IntervalSliderMath.msToSlider01(1_000L), 0.001f)
    }

    @Test fun `msToSlider01 returns 1 for 1 hour`() {
        assertEquals(1f, IntervalSliderMath.msToSlider01(3_600_000L), 0.001f)
    }

    @Test fun `slider01ToMs round-trips with msToSlider01`() {
        listOf(1_000L, 5_000L, 30_000L, 300_000L, 1_800_000L, 3_600_000L).forEach { ms ->
            val slider = IntervalSliderMath.msToSlider01(ms)
            val back = IntervalSliderMath.slider01ToMs(slider)
            val tolerance = (ms * 0.01).toLong().coerceAtLeast(1L)
            assertTrue("$ms -> $slider -> $back exceeded tolerance",
                kotlin.math.abs(back - ms) <= tolerance)
        }
    }

    @Test fun `slider01ToSnappedMs snaps to nearest 1s below 1 minute`() {
        // ~7.3s should snap to 7s
        val ms = IntervalSliderMath.slider01ToSnappedMs(IntervalSliderMath.msToSlider01(7_300L))
        assertEquals(7_000L, ms)
    }

    @Test fun `slider01ToSnappedMs snaps to nearest 5s between 1 and 10 minutes`() {
        // ~123s should snap to 125s
        val ms = IntervalSliderMath.slider01ToSnappedMs(IntervalSliderMath.msToSlider01(123_000L))
        assertEquals(125_000L, ms)
    }

    @Test fun `slider01ToSnappedMs snaps to nearest 1 minute above 10 minutes`() {
        // ~22 min 30s should snap to 23 min
        val ms = IntervalSliderMath.slider01ToSnappedMs(IntervalSliderMath.msToSlider01(1_350_000L))
        // Allow snap to either 22 or 23 min since the input is on the boundary
        assertTrue(ms == 22 * 60_000L || ms == 23 * 60_000L)
    }

    @Test fun `tickIndexAt detects each anchor`() {
        IntervalSliderMath.TICK_POSITIONS.forEachIndexed { idx, pos ->
            assertEquals("anchor $idx at $pos", idx, IntervalSliderMath.tickIndexAt(pos))
        }
    }

    @Test fun `tickIndexAt returns -1 between anchors`() {
        // Half-way between 5s (idx 1) and 30s (idx 2) — not close to either
        val mid = (IntervalSliderMath.TICK_POSITIONS[1] + IntervalSliderMath.TICK_POSITIONS[2]) / 2f
        assertEquals(-1, IntervalSliderMath.tickIndexAt(mid))
    }

    @Test fun `formatLabel pluralizes correctly`() {
        assertEquals("Every 1 second", IntervalSliderMath.formatLabel(1_000L))
        assertEquals("Every 5 seconds", IntervalSliderMath.formatLabel(5_000L))
        assertEquals("Every 1 minute", IntervalSliderMath.formatLabel(60_000L))
        assertEquals("Every 5 minutes", IntervalSliderMath.formatLabel(300_000L))
        assertEquals("Every 1 hour", IntervalSliderMath.formatLabel(3_600_000L))
    }
}
```

- [ ] **Step 6.3: Run the tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.lifedots.ui.components.IntervalSliderMathTest"`
Expected: 9 tests, 0 failures.

- [ ] **Step 6.4: Compile-check the whole app**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.5: Commit**

```bash
git add app/src/main/java/com/example/lifedots/ui/components/IntervalSlider.kt \
        app/src/test/java/com/example/lifedots/ui/components/IntervalSliderMathTest.kt
git commit -m "$(cat <<'EOF'
ui: IntervalSlider — log-mapped 1s to 1h slider with tick anchors

Continuous slider that maps the user's [0,1] drag to an exponential
interval from 1 second (slider=0) to 1 hour (slider=1). Live label
above the track shows the snapped value (1s resolution below 1 min,
5s below 10 min, 1m above). Tick anchors at 5s/30s/5m/30m/1h get
a soft haptic tick when the drag crosses them; release triggers a
confirm haptic + sound + setter call.

Covered by 9 JUnit tests for the pure log/snap/format math.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: `MainActivity` restyle — yellow background, black buttons

**Goal:** Repaint the Home screen with the brand palette. Yellow background, black logo card, black pill buttons, dark amber muted text. Hook UxFeedback into every button.

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/MainActivity.kt`

This is the largest visual change. Read MainActivity.kt first to understand the existing composable structure — it's ~779 lines.

- [ ] **Step 7.1: Read `MainActivity.kt` to map the structure**

```bash
grep -n "^@Composable\|^fun \|^private fun\|setContent\|Scaffold\|Column\|Surface" app/src/main/java/com/example/lifedots/MainActivity.kt | head -30
```

Identify:
- The main entry composable (likely `OnboardingScreen` or similar)
- Where the "Set as Wallpaper", "Customize", "Check for updates" buttons render
- Where the logo card renders
- Where the title + paragraph render
- The "Keep wallpaper always running" battery card

- [ ] **Step 7.2: Add the brand imports**

At the top of `MainActivity.kt`, add:

```kotlin
import com.example.lifedots.ui.theme.BrandColors
import com.example.lifedots.ui.components.rememberUxFeedback
```

- [ ] **Step 7.3: Wrap the screen content in a yellow Scaffold**

Find the `setContent { ... }` block. The outermost layout (likely `Scaffold` or `Surface`) should set its background to `BrandColors.AmberGold`. Example for the typical Scaffold pattern:

```kotlin
setContent {
    LifeDotsTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BrandColors.AmberGold,
        ) { innerPadding ->
            OnboardingScreen(
                modifier = Modifier.padding(innerPadding),
                preferences = preferences,
            )
        }
    }
}
```

(If MainActivity uses a different outer composable like `Surface` or a raw `Column`, apply `containerColor = BrandColors.AmberGold` (Scaffold) or `color = BrandColors.AmberGold` (Surface). The point is the root composable's background must be solid amber-gold.)

- [ ] **Step 7.4: Restyle the title + subtitle + body text**

Find the existing text composables in the main screen. Apply brand colors:

- App title "O'lyapmiz": `BrandColors.InkBlack`, 32sp, `FontWeight.SemiBold`
- Subtitle "See the year pass…": `BrandColors.DarkAmber`, 16sp, regular
- Body paragraph (the 4-sentence description): `BrandColors.DarkAmber`, 14sp
- "133 days passed" / "232 days remaining": title in `BrandColors.InkBlack` 22sp semibold, subtitle in `BrandColors.DarkAmber`

For each `Text(...)` call that needs updating, add `color = BrandColors.InkBlack` (or `DarkAmber`) and adjust `style = MaterialTheme.typography.X.copy(...)` if needed.

- [ ] **Step 7.5: Restyle the three buttons**

Find the three buttons (Set as Wallpaper / Customize / Check for updates). For the primary "Set as Wallpaper":

```kotlin
val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)

Button(
    onClick = {
        feedback.click()
        openWallpaperPicker()
    },
    modifier = Modifier.fillMaxWidth().height(56.dp),
    shape = RoundedCornerShape(16.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = BrandColors.InkBlack,
        contentColor = BrandColors.AmberGold,
    ),
    elevation = ButtonDefaults.buttonElevation(
        defaultElevation = 4.dp,
        pressedElevation = 8.dp,
    ),
) {
    Text("Set as Wallpaper",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
}
```

For the secondary buttons (Customize / Check for updates), use `OutlinedButton`:

```kotlin
OutlinedButton(
    onClick = {
        feedback.click()
        openCustomize()    // or the corresponding existing handler
    },
    modifier = Modifier.fillMaxWidth().height(56.dp),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(2.dp, BrandColors.InkBlack),
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = BrandColors.InkBlack,
    ),
) {
    Text("Customize",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
}
```

(If the existing code uses `Button` for all three, switch the two secondary ones to `OutlinedButton`. Replace the existing handler invocations with `feedback.click(); existingHandler()` so each tap triggers UxFeedback.)

- [ ] **Step 7.6: Restyle the "Keep wallpaper always running" battery card**

Find the existing `KeepAlwaysRunningCard` composable invocation. The card currently uses default Material colors; override it to:

- Background: `BrandColors.InkBlack`
- Title: `BrandColors.AmberGold`
- Body: `BrandColors.OffWhite`
- Icon: tint `BrandColors.AmberGold`
- Buttons inside the card: filled yellow on black, like the primary button above

(The exact location and signature of `KeepAlwaysRunningCard` lives in MainActivity.kt — adjust colors at the composable's definition site.)

- [ ] **Step 7.7: Update the system status bar to light icons on yellow**

In `onCreate(savedInstanceState: Bundle?)`, after `super.onCreate(savedInstanceState)` and before `setContent { ... }`, add:

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, true)
WindowCompat.getInsetsController(window, window.decorView).apply {
    isAppearanceLightStatusBars = true   // dark icons on yellow status bar
}
window.statusBarColor = android.graphics.Color.parseColor("#FFB300")
```

(Import `androidx.core.view.WindowCompat`.)

- [ ] **Step 7.8: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.9: Build the debug APK**

Run: `./gradlew :app:assembleDebug -PappVersionCode=10200 -PappVersionName=1.2.0-dev`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.10: Install on the connected S21+ and screenshot**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.lifedots/.MainActivity
adb shell screencap -p /sdcard/main.png && adb pull /sdcard/main.png /tmp/lifedots_main.png
```

Inspect `/tmp/lifedots_main.png` — confirm: yellow background, black logo card visible, dark text readable, three black buttons stacked with amber-gold text.

- [ ] **Step 7.11: Commit**

```bash
git add app/src/main/java/com/example/lifedots/MainActivity.kt
git commit -m "$(cat <<'EOF'
ui: MainActivity rebrand — yellow background + black pill buttons

Full amber-gold (#FFB300) background, light status bar icons, dark
text in InkBlack + DarkAmber for hierarchy. Primary "Set as Wallpaper"
is a filled black button with amber-gold text and a 4dp resting
elevation (pops out). Secondary "Customize" + "Check for updates"
are OutlinedButton with 2dp ink-black borders. KeepAlwaysRunning
battery card restyled to black-on-yellow with gold accents. Every
button taps through UxFeedback.click() for haptic + sound.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: `SettingsActivity` restyle + App section + swap in BirthdayEditor + IntervalSlider

**Goal:** Repaint Settings with brand palette (black background, yellow accents), wrap existing sections in cards, swap the birthday card for `BirthdayEditor`, swap `IntervalChips` for `IntervalSlider`, add a new "App" section with Sounds + Vibrations toggles.

**Files:**
- Modify: `app/src/main/java/com/example/lifedots/SettingsActivity.kt`

`SettingsActivity.kt` is ~1986 lines (latest). The big additions are localized — read first, then surgical changes.

- [ ] **Step 8.1: Add imports**

```kotlin
import com.example.lifedots.ui.theme.BrandColors
import com.example.lifedots.ui.components.BirthdayEditor
import com.example.lifedots.ui.components.IntervalSlider
import com.example.lifedots.ui.components.PressableCard
import com.example.lifedots.ui.components.rememberUxFeedback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SwitchDefaults
```

- [ ] **Step 8.2: Set Settings background to InkBlack**

In `onCreate`, find the existing `Scaffold(...)` (added in Task 6 of v1.1.0). Add `containerColor = BrandColors.InkBlack`:

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = BrandColors.InkBlack,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
) { innerPadding -> ... }
```

Also set the system status bar:

```kotlin
WindowCompat.getInsetsController(window, window.decorView).apply {
    isAppearanceLightStatusBars = false   // light icons on black
}
window.statusBarColor = android.graphics.Color.parseColor("#0A0A0A")
```

- [ ] **Step 8.3: Restyle the "O'lyapmiz Settings" header text**

Find the existing top header Text in `SettingsScreen`. Apply:

```kotlin
Text(
    text = "O'lyapmiz Settings",
    style = MaterialTheme.typography.headlineMedium.copy(
        color = BrandColors.AmberGold,
        fontWeight = FontWeight.SemiBold,
    ),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
)
```

- [ ] **Step 8.4: Wrap each existing `SettingsSection { ... }` in a brand card**

Find `SettingsSection` definition (line ~901 in the file). Update it to wrap content in a brand-styled Surface:

```kotlin
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                color = BrandColors.AmberGold,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = BrandColors.InkBlackElevated,
            border = BorderStroke(1.dp, BrandColors.HairlineGold),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}
```

This automatically restyles every existing section that calls `SettingsSection(...)`.

- [ ] **Step 8.5: Override Switch colors globally via `SwitchDefaults`**

Where `Switch(...)` is used (Auto-switch row, Highlight Today, etc.), apply the brand colors. Either via a `@Composable fun BrandSwitch(...)` wrapper, or by adding `colors = SwitchDefaults.colors(...)` at every call site.

Recommended: add a small wrapper in the same file near the existing helpers:

```kotlin
@Composable
private fun BrandSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = BrandColors.InkBlack,
            checkedTrackColor = BrandColors.AmberGold,
            checkedBorderColor = BrandColors.AmberGold,
            uncheckedThumbColor = BrandColors.GoldenMuted,
            uncheckedTrackColor = BrandColors.InkBlackElevated,
            uncheckedBorderColor = BrandColors.HairlineGold,
        ),
    )
}
```

Then replace every `Switch(` call inside SettingsActivity.kt with `BrandSwitch(`. (Typically there are 3–4 switches: Auto-switch, Highlight Today, and the two new ones in Step 8.7.)

- [ ] **Step 8.6: Replace `IntervalChips` with `IntervalSlider` inside `ModeTopSection`**

Find `IntervalChips(currentMs = ..., onPick = ...)` call site (inside `ModeTopSection`). Replace it with:

```kotlin
val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)
IntervalSlider(
    currentMs = settings.autoSwitchSettings.intervalMs,
    onIntervalChange = { ms -> preferences.setAutoSwitchIntervalMs(ms) },
    feedback = feedback,
)
```

Also DELETE the `IntervalChips` composable definition (the function below `ModeTopSection`) — it's no longer used.

- [ ] **Step 8.7: Replace `UmrSettingsSection`'s birthday card + DatePickerDialog with `BirthdayEditor`**

Find `UmrSettingsSection`. Replace its body so the birthday row has two states: collapsed (showing the formatted date) and editing (showing `BirthdayEditor`).

```kotlin
@Composable
private fun UmrSettingsSection(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
) {
    val birthdayMs = settings.umrSettings.birthdayEpochMs
    val isSet = birthdayMs > 0L
    val fmt = remember { java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault()) }
    var editing by remember { mutableStateOf(!isSet) }
    val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)

    SettingsSection(title = "Umr") {
        if (editing) {
            BirthdayEditor(
                initialEpochMs = birthdayMs,
                feedback = feedback,
                onSave = { ms ->
                    preferences.setUmrBirthday(ms)
                    editing = false
                },
            )
        } else {
            PressableCard(
                onClick = {
                    feedback.click()
                    editing = true
                },
                feedback = feedback,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isSet) BrandColors.InkBlackElevated else Color(0xFFE53935),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSet) "Birthday" else "Set your birthday",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSet) BrandColors.GoldenMuted else Color.White,
                        )
                        Text(
                            text = if (isSet) fmt.format(java.util.Date(birthdayMs))
                                   else "Required to render your life-in-weeks grid",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSet) BrandColors.OffWhite else Color.White,
                        )
                    }
                    Text(
                        text = "✎",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isSet) BrandColors.AmberGold else Color.White,
                    )
                }
            }
        }
    }
}
```

DELETE the old `DatePickerDialog` invocation block from `UmrSettingsSection` (the `if (showBirthdayDialog) { ... }` block at the bottom of the old impl). Also REMOVE the `showBirthdayDialog: Boolean` and `onShowBirthdayDialogChange: (Boolean) -> Unit` parameters from the function signature — they're no longer needed.

In the call site (inside `SettingsScreen`), remove the `showBirthdayDialog` state declaration and the `LaunchedEffect` that resets it; remove the corresponding params from the `UmrSettingsSection` call. ALSO remove the `onBirthdayNeeded` callback that was passed to `ModeTopSection` — replace its body with a snackbar pointing to the Umr section:

Update `ModeTopSection` signature to drop `onBirthdayNeeded`. Snackbar action text becomes "OK" (just dismisses) since the editor is right there in the page.

Or, if simpler: keep `onBirthdayNeeded` but make it scroll the LazyColumn to the Umr section AND call `editing = true`. The simpler choice is to just remove it entirely — when the snackbar appears with "Set your birthday first", the user can scroll down themselves.

Pick the simpler path: remove `onBirthdayNeeded`, change the snackbar action label to "OK", and have its action callback do nothing. The user can scroll to the Umr section themselves; the new BirthdayEditor is already visible (since when topViewMode is UMR, UmrSettingsSection is rendered).

- [ ] **Step 8.8: Add the new "App" section with Sounds + Vibrations toggles**

Inside `SettingsScreen`'s main scrollable content, add a new section ABOVE the existing Theme section:

```kotlin
SettingsSection(title = "App") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sounds",
                style = MaterialTheme.typography.bodyLarge.copy(color = BrandColors.OffWhite),
            )
            Text(
                text = "system click effects on tap",
                style = MaterialTheme.typography.bodySmall.copy(color = BrandColors.GoldenMuted),
            )
        }
        BrandSwitch(
            checked = settings.soundsEnabled,
            onCheckedChange = { preferences.setSoundsEnabled(it) },
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Vibrations",
                style = MaterialTheme.typography.bodyLarge.copy(color = BrandColors.OffWhite),
            )
            Text(
                text = "haptic feedback on tap",
                style = MaterialTheme.typography.bodySmall.copy(color = BrandColors.GoldenMuted),
            )
        }
        BrandSwitch(
            checked = settings.vibrationsEnabled,
            onCheckedChange = { preferences.setVibrationsEnabled(it) },
        )
    }
}
```

The App section is SHARED — visible in both Yil and Umr mode. Render it OUTSIDE the `if (settings.topViewMode == TopViewMode.YIL)` guards, between the `ModeTopSection` and the conditional Yil/Umr sections.

- [ ] **Step 8.9: Compile-check + run all tests**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL on both. All tests pass (8 from AutoSwitchModeTest, 7 from UmrLayoutComputeTest, 9 from IntervalSliderMathTest = 24 total).

- [ ] **Step 8.10: Build debug APK + install**

```bash
./gradlew :app:assembleDebug -PappVersionCode=10200 -PappVersionName=1.2.0-dev
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.lifedots/.SettingsActivity
adb shell screencap -p /sdcard/settings.png && adb pull /sdcard/settings.png /tmp/lifedots_settings.png
```

Inspect the screenshot — confirm: black background, gold "O'lyapmiz Settings" title, sections rendered as black cards with hairline-gold borders, yellow + black pill toggle, switches with gold tracks when on, App section visible at top with both toggles.

- [ ] **Step 8.11: Commit**

```bash
git add app/src/main/java/com/example/lifedots/SettingsActivity.kt
git commit -m "$(cat <<'EOF'
ui: SettingsActivity rebrand + BirthdayEditor + IntervalSlider + App section

Full near-black background. Each SettingsSection becomes a rounded
elevated-black card with a hairline-gold border. Pill toggle now
brand-colored (Task 3). All Switches route through BrandSwitch
wrapper (gold thumb-on-track when checked). The Umr birthday card
now flips between a collapsed "Birthday: 4 October 2004 ✎" row
and the new BirthdayEditor (3 inline fields + Save). The auto-
switch interval is now an IntervalSlider (1s to 1h log curve with
tick anchors at 5s/30s/5m/30m/1h). New "App" section above Theme
with Sounds + Vibrations toggles, both default ON.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: End-to-end verification on device + PROJECT_NOTES update

**Goal:** Smoke-test every user flow on the S21+, then log v1.2.0 in PROJECT_NOTES.

**Files:**
- Modify: `PROJECT_NOTES.md`

- [ ] **Step 9.1: Fresh install + migration v10 check**

Because the existing app on the phone is release-signed v1.0.14 (or later) and our debug build is debug-signed, signature mismatch will block install. Confirm with the user before uninstalling. Then:

```bash
adb uninstall com.example.lifedots
./gradlew :app:assembleDebug -PappVersionCode=10200 -PappVersionName=1.2.0-dev
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.lifedots/.MainActivity
sleep 2
adb shell run-as com.example.lifedots cat shared_prefs/lifedots_prefs.xml | grep migration_version
```

Expected: `<int name="migration_version" value="10" />`.

- [ ] **Step 9.2: Smoke-test each flow**

For each flow, capture a screenshot via:
```bash
adb shell screencap -p /sdcard/step.png && adb pull /sdcard/step.png /tmp/v12_<step>.png
```

Flows to verify:

1. **Home screen** — yellow background, black logo card, three black/outlined buttons. Tap "Customize" — should open Settings.
2. **Settings — Yil mode default** — black background, gold "O'lyapmiz Settings" title, App section visible with Sounds + Vibrations toggles ON, ModeTopSection with Yil pill active.
3. **Toggle Sounds OFF, Vibrations OFF** — confirm prefs reflect.
4. **Tap Umr pill** — switches to Umr; existing Yil-only sections hide; Umr section appears with red "Set your birthday" card.
5. **Tap the red card** — BirthdayEditor expands inline with 3 empty number fields and Save (disabled).
6. **Enter Day=4, Month=10, Year=2004** — Save button enables. Tap Save — confirm haptic (if vibrations on) and card collapses to "Birthday: 4 October 2004 ✎".
7. **Tap the ✎ row** — editor expands again, pre-filled with 4/10/2004. Tap outside — collapses.
8. **Enable Auto-switch** — IntervalSlider appears below. Drag the slider — confirm live label updates ("Every 7 seconds" / "Every 2 minutes" etc.). Confirm haptic ticks at the 5s/30s/5m/30m/1h anchors (when vibrations on).
9. **Set interval to ~5s** (drag to second anchor) — release. Confirm prefs reflect: `auto_switch_interval_ms = 5000`.
10. **Lock phone, wait** — confirm wallpaper rotates between Yil and Umr every 5s (existing behavior, still working).
11. **Flip pill back to Yil** — existing sections reappear, Auto-switch ON state preserved.
12. **Toggle Vibrations OFF, drag slider** — confirm no haptic ticks. Toggle Sounds OFF, tap buttons — confirm no sound.

- [ ] **Step 9.3: Update `PROJECT_NOTES.md` with the v1.2.0 row**

Find section 13 "Version trajectory". Below the `v1.1.0` row added previously, add:

```markdown
| `v1.2.0` | Yellow + black UX overhaul (chrome only) | MainActivity + SettingsActivity rebranded around warm amber-gold (#FFB300) + near-pure black (#0A0A0A). Home is yellow-forward with black pill buttons; Settings is black-forward with elevated-black section cards bordered in hairline-gold. New `UxFeedback` helper centralizes haptic + sound triggers, gated by two new toggles (Sounds + Vibrations, both default ON) in a new "App" section. Birthday entry replaced with `BirthdayEditor` (3 inline number fields + Save button, validates per-month days). Auto-switch interval replaced with `IntervalSlider` (logarithmic 1s→1h, tick anchors at 5s/30s/5m/30m/1h, haptic ticks as the drag crosses anchors). Wallpaper renderer and lockscreen view UNCHANGED. Migration v10 (no-op bump). New files: `BrandColors`, `UxFeedback`, `PressableCard`, `BirthdayEditor`, `IntervalSlider`. 9 new JUnit tests for the slider math. |
```

- [ ] **Step 9.4: Commit + tag**

```bash
git add PROJECT_NOTES.md
git commit -m "$(cat <<'EOF'
notes: log v1.2.0 — yellow + black UX overhaul for chrome

End-to-end smoke-tested on S21+ across all flows: Home rebrand,
Settings rebrand, BirthdayEditor save + edit roundtrip, IntervalSlider
drag with haptic anchors, Sounds + Vibrations toggle behavior,
mode-aware section visibility preserved from v1.1.0.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"

git tag v1.2.0
```

(Tag triggers GitHub Actions release workflow on push. Do not push without explicit user approval.)

---

## Notes for the implementing engineer

- **Wallpaper renderer is untouched.** Any edit to `LifeDotsWallpaperService.kt`, `CalendarLayout.kt`, `UmrLayoutCompute`, or `AutoSwitchRotator.kt` is out of scope. If you find yourself opening one, stop and check why.
- **The wallpaper still uses its own `ThemeColors` from `ThemeOption`** — the Light/Dark/AMOLED/Custom buttons still affect the wallpaper rendering, NOT the chrome. Chrome is brand-fixed.
- **`DatePickerDialog.kt` is kept in tree as dead code.** Don't delete it in this plan — a later cleanup commit can do that.
- **Status bar appearance** changes per-activity. Home has `isAppearanceLightStatusBars = true` (dark icons on yellow); Settings has it `false` (light icons on black). Both activities set their own.
- **Compose's `BorderStroke`** import lives in `androidx.compose.foundation.BorderStroke`. Don't confuse with the M3 `Outline` types.
- **Prefer adding small wrapper composables** (BrandSwitch, etc.) to repeating the same color block at every call site — keeps the file manageable.
- **The existing `SettingsActivity.kt` is ~1986 lines.** Edits are surgical: change colors at specific composables, swap two components, add one new section, restyle the section wrapper. Don't refactor the file.

---
