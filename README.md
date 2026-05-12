# O'lyapmiz

**Memento Mori. Your daily reminder to live intentionally.**

A minimalist Android live wallpaper that visualizes the passing of the year as a 12-month calendar of dots — one dot per day, one row per week, twelve months at a glance. Today is highlighted. The year fills as it passes.

<p align="center">
  <img src="o'lyapmiz.png" alt="O'lyapmiz" width="180"/>
</p>

> *O'lyapmiz* (Uzbek): "we are dying." A reminder that life is finite — and that this is what makes today worth showing up for.

---

## Features

- **Year View** — All 12 months of the current year on your lock screen, with each day rendered as a single dot. Today is tinted; past days are filled; future days are dimmed.
- **Three layouts** — Calendar (3×4 month grid, like the screenshot above), Monthly (per-month rows), or Continuous (a single 365-dot grid).
- **Stats line** — `Xd left · X%` and an optional event countdown anchored to the bottom of the screen, independent of where you place the calendar grid.
- **Themes** — Light, Dark, AMOLED, or a fully custom palette (background, filled dots, empty dots, today).
- **Dot styles** — Flat / Gradient / Outlined / Soft Glow / Neon / Embossed, in 5 sizes and 4 grid densities.
- **Optional milestone event** — Pick any date and a label (e.g. "birthday", "graduation"); the calendar will tint that week and show a countdown.
- **Position & scale controls** — Move the grid up/down/left/right; scale it. Stats stay anchored to the bottom regardless.
- **Daily auto-update** — A 4-layer safety net (visibility-change redraw, midnight handler, `ACTION_DATE_CHANGED` broadcast, and an `AlarmManager` daily tick) guarantees the dot for today is correct, even on aggressive battery-saver profiles.
- **Privacy** — No network calls. No telemetry. No accounts. Settings live in your phone's local `SharedPreferences`.

---

## Installation

### From a release APK

1. Grab the latest `app-debug.apk` from the [Releases](../../releases) page.
2. Install on your Android phone (Settings → Apps → Special access → Install unknown apps; allow your file manager).
3. Open **O'lyapmiz** → tap **Set as Wallpaper** → confirm in the system live-wallpaper preview.

### Build from source

Prerequisites: JDK 17, Android SDK 34+, a device or emulator running Android 8.0 (API 26) or higher.

```bash
git clone <your-fork-url>.git
cd LifeDots
./gradlew :app:assembleDebug         # outputs app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug          # or install directly to a connected device
```

---

## Tested on

| Device                     | OS                  | Status |
|----------------------------|---------------------|--------|
| Samsung Galaxy S21+ (SM-G996N) | One UI / Android 15 | ✅ Primary test device |

It is designed to work on **Samsung One UI** and **Xiaomi/Redmi MIUI** — the two phone families most common in Uzbekistan. The known pain points on both (aggressive battery optimization, lock-screen vs. home-screen wallpaper split) are handled in-app where possible, and documented below where they require a one-time setting on the phone.

### Recommended phone settings

These are one-time tweaks that make the daily auto-update bulletproof on Samsung and Redmi.

**Samsung (One UI)**
- Settings → Battery → Background usage limits → **Never sleeping apps** → add O'lyapmiz.
- Settings → Apps → O'lyapmiz → Battery → **Unrestricted**.
- When you tap *Set as Wallpaper*, choose **Home and lock screens** so the calendar shows in both places.

**Xiaomi / Redmi (MIUI / HyperOS)**
- Settings → Apps → Manage apps → O'lyapmiz → **Autostart: ON**.
- Settings → Apps → Manage apps → O'lyapmiz → Battery saver → **No restrictions**.
- Settings → Wallpaper → My wallpapers → Live wallpapers → pick O'lyapmiz (or use the in-app *Set as Wallpaper* button).

Without these, the wallpaper still updates **the moment you wake the phone after midnight** (via the visibility-change path), but the in-place midnight refresh may be skipped while the device is in deep doze.

---

## Customization

Tap **Customize** on the home screen to open the settings screen. From there:

| Section            | What it controls |
|--------------------|------------------|
| Theme              | Light / Dark / AMOLED / Custom |
| Dot style & size   | 6 styles × 5 sizes × 4 densities |
| View mode          | Calendar (default) / Monthly / Continuous |
| Calendar layout    | Columns per row (2 / 3 / 4), Monday-first weeks, month labels |
| Highlight today    | Toggle + color |
| Milestone event    | Enable + date + label + color |
| Position           | Move and scale the calendar grid (stats stay locked at the bottom) |
| Background photo   | Optional, with blur and opacity |
| Footer text        | Optional, with font size + alignment + color |
| Animations         | Fade / Pulse / Wave / Breathe / Ripple / Cascade |
| Glass effects      | Light Frost / Heavy Frost / Acrylic / Crystal / Ice |
| Tree growth mode   | 5 tree styles instead of dots |
| Fluid backgrounds  | Water / Lava / Mercury / Plasma / Aurora |

---

## Tech notes

- **Language** — Kotlin
- **UI** — Jetpack Compose for the onboarding + settings activities; Android Canvas API for the wallpaper rendering
- **State** — `SharedPreferences` + `StateFlow`; no network, no database
- **Min SDK** — 26 (Android 8.0); **Target SDK** — 35 (Android 15)
- **Permissions** — `READ_MEDIA_IMAGES` (for the optional background photo), `RECEIVE_BOOT_COMPLETED` (to re-arm the daily refresh alarm after a reboot)
- **License** — MIT, see [LICENSE](LICENSE)

### Project layout

```
app/src/main/
├── java/com/example/lifedots/
│   ├── MainActivity.kt              # Onboarding screen (logo + Set Wallpaper + Customize)
│   ├── SettingsActivity.kt          # Compose settings UI
│   ├── preferences/
│   │   └── LifeDotsPreferences.kt   # Singleton over SharedPreferences + StateFlow
│   ├── receiver/
│   │   └── DateChangeReceiver.kt    # ACTION_DATE_CHANGED + AlarmManager daily tick
│   ├── wallpaper/
│   │   └── LifeDotsWallpaperService.kt  # Live wallpaper engine, renders dots/grid/stats
│   └── ui/
│       ├── components/              # Reusable Compose widgets
│       └── theme/                   # Material 3 theme
├── res/
│   ├── mipmap-*/ic_launcher*.png    # App icon at every density
│   ├── drawable*/ic_launcher_*.xml  # Adaptive icon
│   └── values/strings.xml           # All user-facing copy
└── AndroidManifest.xml
```

---

## Roadmap

- Cyrillic / Uzbek localization (currently English only)
- Lock screen widget (Android 16+)
- Home-screen widget variant
- Week / decade / lifetime view modes
- Import / export configuration

---

## Contributing

Issues and PRs are welcome. If you're working on something non-trivial, open an issue first so we can talk about scope.

Areas where help would be especially useful:

- Localization (translation strings to Uzbek, Russian, etc.)
- Wallpaper picker compatibility on other OEMs (Vivo, Oppo, Realme, etc.)
- Accessibility improvements
- Bug reports with phone model + Android version + reproduction steps

---

## Credits

- Inspired by [thelifecalendar.com](https://thelifecalendar.com), the [Year View](https://remainders.vercel.app) by Remainders, and Nepali Year Progress.
- This project is a fork of [LifeDots](https://github.com/humonious17/LifeDots) by humonious17, rewritten around the 12-month calendar view and renamed.

---

## License

MIT. Do what you want with it. See [LICENSE](LICENSE) for the full text.
