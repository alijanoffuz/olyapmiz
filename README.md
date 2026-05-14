# O'lyapmiz

**Memento Mori. Your daily reminder to live intentionally.**

A minimalist Android live wallpaper that visualizes the passing of the year as a 12-month calendar of dots — one dot per day, one row per week, twelve months at a glance. Today is highlighted. The year fills as it passes.

<p align="center">
  <img src="o'lyapmiz.png" alt="O'lyapmiz" width="180"/>
</p>

<p align="center">
  <a href="https://github.com/aiblogsuz/olyapmiz/releases/latest/download/olyapmiz.apk">
    <img src="https://img.shields.io/badge/Download_APK-yellow?style=for-the-badge&logo=android&logoColor=black&labelColor=000000&color=FFD400" alt="Download latest APK" height="36"/>
  </a>
  <br/>
  <sub>Direct download — always the latest signed release. Once installed, the app auto-checks for updates on launch.</sub>
</p>

> *O'lyapmiz* (Uzbek): "we are dying." A reminder that life is finite — and that this is what makes today worth showing up for.

---

## Features

- **Yil view** — All 12 months of the current year on your lock screen, with each day rendered as a single dot. Today is tinted; past days are filled; future days are dimmed.
- **Umr view** — A life-calendar mode with separate birth dates for you, your mother, and your father, plus dot or X-mark visualization.
- **Auto-switch** — Optional wall-clock rotation between Yil and Umr with intervals from 1 second to 1 hour.
- **Stats line** — `Xd left · X%` and optional goal/event countdowns anchored near the bottom of the screen.
- **Themes** — Light, Dark, AMOLED, or Custom in Yil mode.
- **Goal and event countdowns** — Pick important dates and labels; Yil goals and Umr events are kept separate.
- **Position, scale, and transparency controls** — Move and resize each mode independently and adjust filled/empty dot opacity.
- **Daily refresh** — Visibility redraws, date/time broadcasts, and an `AlarmManager` daily tick keep the wallpaper current. OEM battery savers can still delay background work, so the app includes a foreground keep-alive option.
- **Privacy** — No telemetry. No accounts. The only network call is the optional GitHub release check used by the in-app updater. Settings live in your phone's local `SharedPreferences`.

---

## Installation

### From a release APK

1. Grab the latest APK from [Releases](https://github.com/aiblogsuz/olyapmiz/releases/latest) (or use the Download button at the top of this page).
2. Install on your Android phone (Settings → Apps → Special access → Install unknown apps; allow your file manager).
3. Open **O'lyapmiz** → tap **Set as Wallpaper** → confirm in the system live-wallpaper preview.

After the first install, the app auto-checks GitHub Releases on every launch and changes the **Update** button when a newer APK is available.

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

The home screen includes a **Keep Running / Allow Background / Never Sleeping** button that opens the relevant Android or OEM settings. The same settings can be reached manually:

**Samsung (One UI)**
- *In-app shortcut:* tap **1. Allow background activity** → **Allow**, then **2. Add to 'Never sleeping apps'** → toggle O'lyapmiz on.
- *Manually:* Settings → Battery → Background usage limits → **Never sleeping apps** → add O'lyapmiz, AND Settings → Apps → O'lyapmiz → Battery → **Unrestricted**.
- When you tap *Set as Wallpaper*, choose **Home and lock screens** so the calendar shows in both places.

**Xiaomi / Redmi (MIUI / HyperOS)**
- *In-app shortcut:* tap **Allow background activity** → **Allow**.
- *Manually:* Settings → Apps → Manage apps → O'lyapmiz → **Autostart: ON** AND Battery saver → **No restrictions**.
- Settings → Wallpaper → My wallpapers → Live wallpapers → pick O'lyapmiz (or use the in-app *Set as Wallpaper* button).

Without these, the wallpaper still redraws when it becomes visible, but Samsung's Freecess / MIUI's MemoryGuard can freeze the wallpaper process while the screen is off or the app is backgrounded.

---

## Customization

Tap **Customize** on the home screen to open the settings screen. From there:

| Section            | What it controls |
|--------------------|------------------|
| Mode               | Yil / Umr and optional auto-switch interval |
| Life data          | Your, mother, and father birth dates for Umr |
| Theme              | Light / Dark / AMOLED / Custom |
| Transparency       | Filled and empty dot opacity |
| Calendar layout    | Yil columns per row (2×6 or 3×4) |
| Highlight today    | Toggle the marker for today's Yil dot |
| Goals / Events     | Yil goal countdowns and Umr event markers |
| Position           | Move and scale the active grid |

---

## Tech notes

- **Language** — Kotlin
- **UI** — Jetpack Compose for the onboarding + settings activities; Android Canvas API for the wallpaper rendering
- **State** — `SharedPreferences` + `StateFlow`; no network, no database
- **Min SDK** — 26 (Android 8.0); **Target SDK** — 35 (Android 15)
- **Permissions** — `RECEIVE_BOOT_COMPLETED` (to re-arm refresh alarms after reboot/update), `POST_NOTIFICATIONS` (for update and keep-alive notifications), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and foreground-service permissions (for the keep-alive path), `INTERNET` and `REQUEST_INSTALL_PACKAGES` (for the GitHub updater)
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
