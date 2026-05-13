# O'lyapmiz — Project Notes

A complete reference for everything in this app: preferences, architecture, migrations, build pipeline, OEM workarounds, and version history. Ported from working through Samsung One UI / Xiaomi MIUI / Honor EMUI quirks to ship a minimalist live-wallpaper calendar.

Use this doc as a starting point for the next project. Search by section heading; every concept is paired with the file/path it lives in.

---

## 1. Identity

| Field | Value |
|---|---|
| Display name | **O'lyapmiz** (Uzbek: "we are dying" — memento mori) |
| Kotlin package / `applicationId` | `com.example.lifedots` (kept across rebrand for upgrade compatibility) |
| Gradle `rootProject.name` | `Olyapmiz` |
| GitHub repo | `alijanoffuz/olyapmiz` |
| MIT licensed | see `LICENSE` |
| Forked from | [`humonious17/LifeDots`](https://github.com/humonious17/LifeDots) — preserved in git history under `upstream` remote |

---

## 2. Build configuration

### Versioning

Tag `vX.Y.Z` → `versionCode = X*10000 + Y*100 + Z`, `versionName = "X.Y.Z"`. Drives CI builds via `-PappVersionCode=...` `-PappVersionName=...`.

### `app/build.gradle.kts` highlights

```kotlin
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
val appVersionName: String = project.findProperty("appVersionName") as String? ?: "1.0-dev"

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.example.lifedots"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.lifedots"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GITHUB_OWNER", "\"alijanoffuz\"")
        buildConfigField("String", "GITHUB_REPO",  "\"olyapmiz\"")
    }
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) { /* ... */ }
        }
    }
    buildTypes {
        release {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.10.1")
    // … standard compose-bom
}
```

### Signing

Release keystore is **gitignored**, lives in `keystore/olyapmiz-release.jks` locally. For CI, base64-encoded and stored as a GitHub Secret.

| Secret name | Contents |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -i keystore/olyapmiz-release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `olyapmiz` |
| `RELEASE_KEY_PASSWORD` | key password (= keystore password in this project) |

DN of self-signed cert: `CN=O'lyapmiz, OU=alijanoffuz, O=O'lyapmiz, L=Tashkent, ST=Tashkent, C=UZ`. Validity 10000 days.

The CI workflow writes a temp `keystore.properties` at build time from these secrets.

### `.gitignore` additions

```
keystore/
keystore.properties
*.jks
*.keystore
```

---

## 3. AndroidManifest.xml permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (max SDK 32) | Background photo picker (feature kept in code, hidden from UI) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the daily refresh `AlarmManager` after reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Standard Android battery exemption (Samsung Freecess / MIUI MemoryGuard counter) |
| `POST_NOTIFICATIONS` | Android 13+ runtime permission for the foreground-service notification + updater notifications |
| `FOREGROUND_SERVICE` | Run `KeepAliveService` as foreground |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for the "specialUse" foreground service type (Android 14+ enforced) |
| `INTERNET` | In-app updater queries GitHub Releases API |
| `REQUEST_INSTALL_PACKAGES` | In-app updater hands the downloaded APK to system installer |

### Manifest entries

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
               android:resource="@xml/file_provider_paths" />
</provider>

<service
    android:name=".service.KeepAliveService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="keep_wallpaper_engine_alive" />
</service>

<service
    android:name=".wallpaper.LifeDotsWallpaperService"
    android:exported="true"
    android:permission="android.permission.BIND_WALLPAPER">
    <intent-filter><action android:name="android.service.wallpaper.WallpaperService" /></intent-filter>
    <meta-data android:name="android.service.wallpaper" android:resource="@xml/wallpaper" />
</service>

<receiver android:name=".receiver.DateChangeReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.DATE_CHANGED" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <action android:name="com.example.lifedots.DAILY_TICK" />
    </intent-filter>
</receiver>
```

### `res/xml/file_provider_paths.xml`

```xml
<paths>
    <external-files-path name="updates" path="updates/" />
</paths>
```

---

## 4. Preferences (`LifeDotsPreferences`)

`SharedPreferences` name: **`lifedots_prefs`**. Singleton via `getInstance(context)`. Exposes `StateFlow<WallpaperSettings>` plus `addWallpaperChangeListener` for the wallpaper engine to redraw on settings changes.

### Data classes (the full preference tree)

```kotlin
data class WallpaperSettings(
    val theme: ThemeOption = ThemeOption.DARK,
    val customColors: CustomColors = CustomColors(),
    val dotSize: DotSize = DotSize.MEDIUM,
    val dotShape: DotShape = DotShape.CIRCLE,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val highlightToday: Boolean = true,
    val filledDotAlpha: Float = 1.0f,
    val emptyDotAlpha: Float = 0.4f,
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
    val footerTextSettings: FooterTextSettings = FooterTextSettings(),
    val viewModeSettings: ViewModeSettings = ViewModeSettings(),
    val calendarViewSettings: CalendarViewSettings = CalendarViewSettings(),
    val goalSettings: GoalSettings = GoalSettings(),
    val dotEffectSettings: DotEffectSettings = DotEffectSettings(),
    val positionSettings: PositionSettings = PositionSettings(),
    val animationSettings: AnimationSettings = AnimationSettings(),
    val glassEffectSettings: GlassEffectSettings = GlassEffectSettings(),
    val treeEffectSettings: TreeEffectSettings = TreeEffectSettings(),
    val fluidEffectSettings: FluidEffectSettings = FluidEffectSettings(),
    val visualTheme: VisualTheme = VisualTheme.CLASSIC
)

enum class ThemeOption { LIGHT, DARK, AMOLED, CUSTOM }
data class CustomColors(
    val backgroundColor: Int = 0xFF1A1A1A.toInt(),
    val filledDotColor: Int = 0xFFE0E0E0.toInt(),
    val emptyDotColor:  Int = 0xFF3A3A3A.toInt(),
    val todayDotColor:  Int = 0xFF5BA0E9.toInt()
)

enum class ViewMode { CONTINUOUS, MONTHLY, CALENDAR }
data class ViewModeSettings(
    val mode: ViewMode = ViewMode.CALENDAR,
    val showMonthLabels: Boolean = true,
    val monthLabelColor: Int = 0xFFFFFFFF.toInt()
)

data class CalendarViewSettings(
    val columnsPerRow: Int = 3,           // only 2 (→ 2×6) and 3 (→ 3×4) exposed
    val showYearStats: Boolean = true,
    val mondayFirst: Boolean = false,
    val highlightCurrentWeek: Boolean = true,
    val currentWeekColor: Int = 0xFFFFD54F.toInt()
    // event* fields removed in v2 migration; goals replaced them
)

data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetDate: Long,
    val color: Int = 0xFFE53935.toInt()    // Material Red 600 — vivid, default
)
data class GoalSettings(
    val enabled: Boolean = true,
    val goals: List<Goal> = emptyList(),
    val position: GoalPosition = GoalPosition.BOTTOM
)

data class PositionSettings(
    val horizontalOffset: Float = 0f,   // -50..50 percent of canvas width
    val verticalOffset: Float = 18f,    // -50..50; literal — slider value = offset
    val scale: Float = 1.0f             // 0.5..1.5
)

// Effects still present in code, hidden from Settings UI
data class AnimationSettings(val enabled: Boolean = false, …)
data class GlassEffectSettings(val enabled: Boolean = false, …)
data class TreeEffectSettings(val enabled: Boolean = false, …)
data class FluidEffectSettings(val enabled: Boolean = false, …)
data class BackgroundSettings(val enabled: Boolean = false, …)

data class FooterTextSettings(
    val enabled: Boolean = false,
    val text: String = "",
    val fontSize: Float = 14f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val alignment: TextAlignment = TextAlignment.CENTER
)

data class DotEffectSettings(
    val style: DotStyle = DotStyle.FLAT,
    val glowRadius: Float = 8f,
    val outlineWidth: Float = 2f
)
```

### Setter pattern

Each setter writes to `SharedPreferences`, updates `_settingsFlow`, and calls `notifyWallpaperChanged()` which fires every registered listener. The wallpaper engine registers a listener in `onCreate` and triggers a redraw on each change.

```kotlin
fun setHighlightToday(highlight: Boolean) {
    prefs.edit().putBoolean(KEY_HIGHLIGHT_TODAY, highlight).apply()
    _settingsFlow.value = _settingsFlow.value.copy(highlightToday = highlight)
    notifyWallpaperChanged()
}
```

### Migration system

```kotlin
private const val KEY_MIGRATION_VERSION = "migration_version"
private const val CURRENT_MIGRATION_VERSION = 8

init { runMigrationsIfNeeded() }

private fun runMigrationsIfNeeded() {
    val stored = prefs.getInt(KEY_MIGRATION_VERSION, 0)
    if (stored >= CURRENT_MIGRATION_VERSION) return
    val editor = prefs.edit()
    if (stored < 2) {
        // v2: legacy calendar_event_* → Goal conversion
        // …
    }
    if (stored < 8) {
        // v8: force-write 18 to KEY_VERTICAL_OFFSET so everyone (fresh
        // install, in-place upgrade, post-v7 wipe) opens with the slider at 18%.
        editor.putFloat(KEY_VERTICAL_OFFSET, 18f)
    }
    editor.putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION).apply()
}
```

#### Migration history (lessons learned)

| Version | Intent | Outcome |
|---|---|---|
| v1 | Force-clear `view_mode` (upstream `CONTINUOUS` default → our `CALENDAR`) | Made non-destructive later — destructive resets surprise users |
| v2 | Convert legacy `calendar_event_*` → a `Goal` | ✅ Kept |
| v3 | (unused; reserved) | — |
| v4 | Subtract 19 from saved `vertical_offset` to match a renderer baseline | Reverted — partial migrations on corrupted values produce weird states |
| v5 | Force-write 18 (with literal renderer) | Reverted — semantics changed again |
| v6 | Force-write 0 (with +18 baseline in renderer) | Reverted — baselines confuse users |
| v7 | Delete `KEY_VERTICAL_OFFSET` so default takes over | Reverted by v8 |
| v8 | Force-write 18 (literal renderer, no baseline) | ✅ Current behaviour |

**Takeaway:** every iteration of "subtle re-baselining" cost a release and confused the user. **Renderer math should be literal**; let migrations write whatever value you want users to start at, then leave the slider alone forever.

### Pref keys (alphabetical, abbreviated)

`backgroundColor`, `backgroundUri`, `calendar_columns`, `calendar_event_*` (removed), `calendar_monday_first`, `calendar_stats`, `current_week_color`, `custom_*_color`, `dot_shape`, `dot_size`, `dot_style`, `empty_dot_alpha`, `filled_dot_alpha`, `footer_*`, `glass_*`, `goals_enabled`, `goals_json`, `goals_position`, `grid_density`, `highlight_today`, `horizontal_offset`, `migration_version`, `month_label_color`, `scale`, `show_month_labels`, `theme`, `tree_*`, `vertical_offset`, `view_mode`, `visual_theme`.

---

## 5. Wallpaper rendering (`LifeDotsWallpaperService`)

### Engine lifecycle

```kotlin
class LifeDotsEngine : Engine() {
    private val preferences by lazy { LifeDotsPreferences.getInstance(applicationContext) }
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var lastDrawnDay = -1

    override fun onCreate(holder: SurfaceHolder) {
        super.onCreate(holder)
        LifeDotsPreferences.addWallpaperChangeListener(settingsChangeListener)
        DateChangeReceiver.scheduleDailyAlarm(applicationContext)
        KeepAliveService.start(applicationContext)   // pins process foreground
    }

    override fun onVisibilityChanged(visible: Boolean) {
        this.visible = visible
        if (visible) { draw(); scheduleNextMidnightCheck() }
        else handler.removeCallbacks(midnightChecker)
    }
}
```

### Calendar view (the default, only mode users see)

- Aspect-aware horizontal padding (Remainders-style):
  ```
  aspect > 2.1 → 12% pad
  aspect > 2.0 → 15%
  else          → 18%
  ```
- `safeTopRatio = if (aspectRatio > 2.0f) 0.10f else 0.13f` — reserves room above the grid for the lockscreen clock.
- Dot size is back-computed so the whole grid + stats fits between `safeTop` and `statsBottomBaseline = height * 0.965f`.
- Hard cap `dotSize ≤ 20px` for the minimalist look.
- Columns: 2 (→ 2×6 layout) or 3 (→ 3×4 layout). Old 4-column saves clamped to 3.

### Position transform

```kotlin
val offsetY = canvas.height * (positionSettings.verticalOffset / 100f)
canvas.save()
canvas.translate(offsetX, offsetY)
canvas.scale(positionSettings.scale, positionSettings.scale, canvas.width/2f, canvas.height/2f)
// … month grid drawn here
canvas.restore()
// Stats drawn AFTER restore so they stay anchored to the bottom regardless of offset.
```

### Goals (the unified "wedding/birthday/anything" feature)

```kotlin
// Build map { day-of-year → Goal } at top of drawCalendarView so the layout
// budget below can see how many countdown lines to make room for.
val goalDayOfYear: Map<Int, Goal> =
    if (settings.goalSettings.enabled) {
        val cal = Calendar.getInstance()
        settings.goalSettings.goals.mapNotNull { goal ->
            cal.timeInMillis = goal.targetDate
            if (cal.get(Calendar.YEAR) == currentYear)
                cal.get(Calendar.DAY_OF_YEAR) to goal
            else null
        }.toMap()
    } else emptyMap()
val upcomingGoalCount = goalDayOfYear.count { (day, _) -> day > dayOfYear }

// Per-dot:
val goalOnThisDay = goalDayOfYear[globalDayCounter]
when {
    goalOnThisDay != null -> drawTintedDot(canvas, cx, cy, dotSize/2, goalOnThisDay.color, glow = true)
    isToday -> drawTintedDot(canvas, cx, cy, dotSize/2, currentWeekColor, glow = true)
    globalDayCounter < dayOfYear -> drawStyledDot(... DotType.FILLED ...)
    else -> drawStyledDot(... DotType.EMPTY ...)
}

// Per-goal countdown line, sorted ascending by date, stacked DOWN from year-stats:
val upcoming = goalDayOfYear.filter { it.key > dayOfYear }.toSortedMap()
upcoming.entries.forEachIndexed { i, (day, goal) ->
    val diff = day - dayOfYear
    val countText = "${diff}d to "
    val labelText = goal.title
    val baselineY = statsLine1BaselineY + (i + 1) * (statsFontSize + statsLineGap)
    // ... paint with goal.color for "Xd to ", monthLabelColor at alpha 130 for label
}
```

### Stats layout math

```
statsBottomBaseline   = height * 0.965f
statsExtraDotUnits    = 4.8f + 1.6f + N * (0.8f + 1.6f)   where N = upcomingGoalCount
maxDotSizeV           = (statsBottomBaseline - safeTop) / totalDotUnitsForFit
statsLine1BaselineY   = statsBottomBaseline - N * (statsFontSize + statsLineGap)   if N > 0
                      else statsBottomBaseline
```

Year-stats line ("Xd left · X%") draws at `statsLine1BaselineY`; goals stack downward from there.

### Daily refresh (4-layer safety net)

| Layer | Trigger | Notes |
|---|---|---|
| **A. Visibility-change redraw** | `onVisibilityChanged(true)` | Fires every screen wake. Calls `draw()` which reads `Calendar.getInstance().get(DAY_OF_YEAR)` fresh. Primary path. |
| **B. midnightChecker** | `Handler.postDelayed` to 00:00:01 | Only fires while visible. Compares `lastDrawnDay` and redraws if changed. |
| **C. ACTION_DATE_CHANGED broadcast** | System broadcast at midnight | Manifest-registered receiver toggles a pref (`setHighlightToday(currentValue)`) which fires the listener chain → engine redraws if visible. |
| **D. AlarmManager DAILY_TICK** | `setInexactRepeating(RTC_WAKEUP, nextMidnight + 30s, INTERVAL_DAY, ...)` | Doze-friendly, no permission needed. Survives Samsung's `ACTION_DATE_CHANGED` drop under Freecess. |

```kotlin
fun scheduleDailyAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, DateChangeReceiver::class.java).apply { action = ACTION_DAILY_TICK }
    val pending = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val nextMidnight = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 30); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextMidnight, AlarmManager.INTERVAL_DAY, pending)
}
```

---

## 6. Foreground service (`KeepAliveService`)

Pure existence-of-the-service trick. Holds a min-priority "Wallpaper is running" notification so Android contractually cannot freeze the process. Defeats Samsung's `FreecessController` which logs:

```
FreecessController: com.example.lifedots(state: Initial -> Frozen, Reason: Bg)
PerProcessNandswap: startNandswapProcess Frozen-ppn com.example.lifedots
```

```kotlin
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keepalive)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Wallpaper is running")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)  // 10s grace before notif appears
            .setShowWhen(false)
            .setContentIntent(/* tap → MainActivity */)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied — wallpaper still works, just no freeze protection.
            stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }
    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
        }
    }
}
```

Started from `LifeDotsEngine.onCreate(...)` → wallpaper is the active wallpaper → engine is alive → keep-alive service is alive → process is foreground → no freeze.

### Status-bar icon for the notification

Must be white-on-transparent (Android tints). Vector at `res/drawable/ic_keepalive.xml` — a 24dp skull silhouette.

---

## 7. Battery / background-usage UX (`MainActivity`)

A dismissable-when-granted card explains *why* permissions are needed and provides one-tap deep links.

```kotlin
@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$packageName")
    }
    if (!tryStart(intent, "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), "...")
}

private fun openSamsungNeverSleeping() {
    // Samsung's second layer of restriction (beyond standard Android battery
    // optimisation). No API to detect — we can only deep-link to it.
    val intent = Intent().apply {
        setClassName("com.samsung.android.lool",
                     "com.samsung.android.sm.battery.ui.notsleeping.NotSleepingActivity")
    }
    if (tryStart(intent, "SAMSUNG_NEVER_SLEEPING")) return
    // Fallback to Samsung Device Care, then app notification settings
    tryStart(Intent().apply {
        setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity")
    }, "SAMSUNG_DEVICE_CARE")
}
```

Card visibility logic:

```kotlin
var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, e ->
        if (e == Lifecycle.Event.ON_RESUME) batteryOptimized = isBatteryOptimized(context)
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
}
val isSamsung = remember { Build.MANUFACTURER.equals("samsung", ignoreCase = true) }
if (batteryOptimized) {
    KeepAlwaysRunningCard(showBatteryButton = true, showSamsungButton = isSamsung, ...)
}
```

`isBatteryOptimized`:

```kotlin
private fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}
```

### Wallpaper picker launcher

`FLAG_ACTIVITY_NEW_TASK` is **required** on Samsung One UI 15+ — without it the cross-process activity start silently fails. Multi-tier fallback handles Samsung, generic AOSP, and unbranded device fallbacks.

```kotlin
private fun openWallpaperPicker() {
    val component = ComponentName(this, LifeDotsWallpaperService::class.java)
    val primary = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(primary, "CHANGE_LIVE_WALLPAPER")) return
    val chooser = Intent("android.service.wallpaper.LIVE_WALLPAPER_CHOOSER").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(chooser, "LIVE_WALLPAPER_CHOOSER")) return
    val any = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.set_wallpaper))
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    if (tryStart(any, "SET_WALLPAPER")) return
    Toast.makeText(this, "Couldn't open wallpaper picker. Open Settings → Wallpaper → Live wallpapers and pick the app.", Toast.LENGTH_LONG).show()
}

private fun tryStart(intent: Intent, label: String): Boolean {
    val resolved = packageManager.resolveActivity(intent, 0)
    if (resolved == null) { Log.w(TAG, "$label: no resolver"); return false }
    Log.i(TAG, "$label: resolved to ${resolved.activityInfo.packageName}/${resolved.activityInfo.name}")
    return try { startActivity(intent); true } catch (e: Exception) { Log.w(TAG, "$label fail", e); false }
}
```

---

## 8. In-app updater (`updater/`)

Three small files. No 3rd-party HTTP library — `HttpURLConnection` only — keeps APK ~7 MB.

### `UpdateChecker.kt`

```kotlin
class UpdateChecker(private val context: Context) {
    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest")
        // … parses tag_name, body, html_url; finds first asset whose name endsWith ".apk"
        // tagToVersionCode("1.2.3") = 1*10000 + 2*100 + 3 = 10203
        // returns UpdateAvailable | UpToDate | NetworkError
    }
}
data class UpdateInfo(val versionName: String, val versionCode: Int, val downloadUrl: String,
                     val downloadSize: Long, val releaseNotes: String, val htmlUrl: String)
```

### `UpdateInstaller.kt`

```kotlin
class UpdateInstaller(private val context: Context) {
    // Download into external-files-path/updates/. cleanCache() wipes leftovers.
    suspend fun download(info: UpdateInfo, onProgress: (Long, Long) -> Unit): File? { … }

    // Critical: detect signature mismatch BEFORE launching the installer.
    // Same packageName + different signing certs = installer refuses with cryptic error.
    fun signatureMatchesInstalled(apk: File): SignatureCheck {
        val installedHash = signaturesHash(currentPackageSignatures())
        val apkHash       = signaturesHash(apkSignatures(apk))
        return when {
            installedHash == null || apkHash == null -> SignatureCheck.Unknown
            installedHash == apkHash -> SignatureCheck.Match
            else -> SignatureCheck.Mismatch
        }
    }

    fun launchInstaller(activity: Activity, apk: File): LaunchResult {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            // deep-link to "Install unknown apps" setting page for our pkg
            return LaunchResult.NeedsUnknownSources
        }
        if (signatureMatchesInstalled(apk) == SignatureCheck.Mismatch) return LaunchResult.SignatureMismatch
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        return LaunchResult.Started
    }

    fun launchSelfUninstall(activity: Activity) {
        activity.startActivity(Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${activity.packageName}")
        })
    }
    fun cleanCache() { /* wipe external-files-path/updates/ */ }
}
```

### `UpdateNotifier.kt`

Posts an Android notification on a `"App updates"` `IMPORTANCE_DEFAULT` channel. Tapping the notification re-opens `MainActivity` with `EXTRA_FROM_UPDATE_NOTIFICATION = true` so the onboarding screen jumps straight to "Download" state.

### Wiring in `MainActivity`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    LifeDotsPreferences.getInstance(this)   // triggers migrations on every launch
    UpdateInstaller(this).cleanCache()       // wipe stale downloaded APKs
    setContent { /* … */ }
}
```

`OnboardingScreen` keeps a `UpdateUiState` sealed interface (`Idle`, `Checking`, `UpToDate`, `Available`, `Downloading`, `Ready`, `SignatureMismatch`, `Error`). `LaunchedEffect(autoCheckOnLaunch)` runs the silent check on entry; an OutlinedButton lets the user re-trigger explicitly. The card swaps content based on state, with progress %, "Install now" button, sig-mismatch instructions + "Uninstall current version" CTA, etc.

---

## 9. GitHub Actions release pipeline (`.github/workflows/release.yml`)

```yaml
name: Release
on:
  push:
    tags: ['v[0-9]+.[0-9]+.[0-9]+']
permissions:
  contents: write
jobs:
  build-and-release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: gradle }
      - id: version
        run: |
          TAG="${GITHUB_REF#refs/tags/}"
          VERSION_NAME="${TAG#v}"
          IFS='.' read -r MAJOR MINOR PATCH <<<"$VERSION_NAME"
          VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
          echo "name=$VERSION_NAME" >> "$GITHUB_OUTPUT"
          echo "code=$VERSION_CODE" >> "$GITHUB_OUTPUT"
          echo "tag=$TAG"           >> "$GITHUB_OUTPUT"
      - name: Decode keystore
        env: { KEYSTORE_BASE64: ${{ secrets.RELEASE_KEYSTORE_BASE64 }} }
        run: mkdir -p keystore && echo "$KEYSTORE_BASE64" | base64 -d > keystore/olyapmiz-release.jks
      - name: Write keystore.properties
        env:
          STORE_PASSWORD: ${{ secrets.RELEASE_KEYSTORE_PASSWORD }}
          KEY_ALIAS:      ${{ secrets.RELEASE_KEY_ALIAS }}
          KEY_PASSWORD:   ${{ secrets.RELEASE_KEY_PASSWORD }}
        run: |
          cat > keystore.properties <<EOF
          storeFile=keystore/olyapmiz-release.jks
          storePassword=$STORE_PASSWORD
          keyAlias=$KEY_ALIAS
          keyPassword=$KEY_PASSWORD
          EOF
      - name: Build signed release APK
        run: |
          chmod +x ./gradlew
          ./gradlew :app:assembleRelease \
            -PappVersionCode=${{ steps.version.outputs.code }} \
            -PappVersionName=${{ steps.version.outputs.name }} --stacktrace
      - name: Rename APK (stable + versioned)
        run: |
          cp app/build/outputs/apk/release/app-release.apk olyapmiz.apk
          cp app/build/outputs/apk/release/app-release.apk olyapmiz-${{ steps.version.outputs.name }}.apk
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ steps.version.outputs.tag }}
          name: O'lyapmiz ${{ steps.version.outputs.name }}
          generate_release_notes: true
          fail_on_unmatched_files: true
          files: |
            olyapmiz.apk
            olyapmiz-${{ steps.version.outputs.name }}.apk
```

Two assets per release:

- `olyapmiz.apk` — **stable filename**, always the latest. README's `https://github.com/.../releases/latest/download/olyapmiz.apk` 302-redirects here.
- `olyapmiz-X.Y.Z.apk` — versioned, for historical archive.

---

## 10. Project structure

```
LifeDots/
├── .github/workflows/release.yml          # CI: signed APK on tag push
├── .gitignore                              # keystore/, keystore.properties, *.jks, *.keystore
├── README.md                               # public-facing docs (Remainders / Nepali-Year style)
├── LICENSE                                 # MIT
├── o'lyapmiz.png                           # 1254×1254 skull logo (used in README + as launcher source)
├── build.gradle.kts
├── settings.gradle.kts                     # rootProject.name = "Olyapmiz"
├── keystore/olyapmiz-release.jks           # gitignored
├── keystore.properties                     # gitignored
└── app/
    ├── build.gradle.kts                    # versionCode/Name from -P props; release signingConfig from keystore.properties
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/lifedots/
        │   ├── MainActivity.kt             # Onboarding + battery card + update card
        │   ├── SettingsActivity.kt         # Compose settings (6 sections: theme, transparency, footer, position, view mode, goals, custom colors)
        │   ├── R.kt                        # generated
        │   ├── preferences/
        │   │   └── LifeDotsPreferences.kt  # SharedPreferences singleton + StateFlow + migrations
        │   ├── receiver/
        │   │   └── DateChangeReceiver.kt   # date-changed + BOOT_COMPLETED + DAILY_TICK alarm fan-in
        │   ├── service/
        │   │   └── KeepAliveService.kt     # foreground service for Samsung Freecess
        │   ├── updater/
        │   │   ├── UpdateChecker.kt        # GitHub Releases API
        │   │   ├── UpdateInstaller.kt      # download + signature check + system installer
        │   │   └── UpdateNotifier.kt       # notification on update available
        │   ├── wallpaper/
        │   │   └── LifeDotsWallpaperService.kt  # main render loop (calendar / continuous / monthly modes)
        │   └── ui/
        │       ├── components/ (GoalEditorDialog, ColorPicker, DatePickerDialog)
        │       └── theme/
        └── res/
            ├── drawable/
            │   ├── ic_launcher_background.xml   # solid black
            │   ├── ic_launcher_foreground.xml   # bitmap @drawable/ic_launcher_logo
            │   └── ic_keepalive.xml             # 24dp white skull silhouette
            ├── drawable-xxxhdpi/ic_launcher_logo.png    # 432px logo
            ├── mipmap-{m,h,xh,xxh,xxxhdpi}/             # ic_launcher.png + ic_launcher_round.png at each density
            ├── mipmap-anydpi-v26/
            │   ├── ic_launcher.xml              # adaptive icon
            │   └── ic_launcher_round.xml
            ├── values/{strings.xml, colors.xml, themes.xml}
            └── xml/
                ├── wallpaper.xml                # service meta-data
                ├── file_provider_paths.xml      # external-files-path/updates/
                ├── backup_rules.xml
                └── data_extraction_rules.xml
```

---

## 11. Quirks & lessons (read before porting)

### Adaptive icons can't be `painterResource(R.mipmap.ic_launcher)`

On API 26+ `R.mipmap.ic_launcher` resolves to `<adaptive-icon>` XML. Compose's `painterResource` only handles VectorDrawable XML or rasterized bitmaps — calling it on adaptive icons throws `IllegalArgumentException` and crashes the app on launch. **Always reference a flat drawable** like `R.drawable.ic_launcher_logo` (a PNG).

### Samsung wallpaper resets on uninstall

Uninstalling a live-wallpaper app drops Samsung back to its default ImageWallpaper — they don't restore a "previous wallpaper." Account for this in your onboarding by always offering a "Set as Wallpaper" path. Same on Honor/EMUI.

### Samsung Freecess freezes background processes

Samsung One UI's `FreecessController` aggressively freezes processes when they go background — including wallpaper engines when the screen turns off. Foreground service with `IMPORTANCE_MIN` notification + `FOREGROUND_SERVICE_DEFERRED` is the only contractual way to opt out. Battery-optimisation exemption helps but isn't sufficient on its own. The Samsung "Never sleeping apps" toggle is a *separate* layer with no API.

### Debug-signed → release-signed = signature mismatch

Once you ship one release-signed APK from a private keystore, debug builds from any developer machine can't install over it. Detect the mismatch in your updater (`UpdateInstaller.signatureMatchesInstalled`) and surface a clear "uninstall first" path instead of letting Android show its cryptic error.

### `FLAG_ACTIVITY_NEW_TASK` on cross-process activity starts

Samsung One UI 15 silently rejects `WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER` from a non-system app without `FLAG_ACTIVITY_NEW_TASK`. Stock Android tolerates the omission; Samsung doesn't. Always add the flag for wallpaper picker / settings deep links.

### Renderer baselines are a trap

Tried 5 different schemes to make "slider 0%" mean "calendar at comfortable position." Every variant caused user confusion because the slider value no longer matched what users see ("why does 0 look like 18?"). **Final answer: slider is literal, migration writes the desired starting value.** If you ever feel tempted to add a hidden bias in the renderer, write a migration instead.

### One-time prefs migrations are powerful but destructive

`editor.putFloat(KEY, value)` in a migration overwrites whatever the user had — including their hard-won custom settings. Use only for healing genuinely-corrupted state or rebrand bookkeeping. For normal default changes, just change the data-class default — existing saves take precedence and that's usually right.

### Run-as is denied on release builds

`adb shell run-as com.example.lifedots` works only when `android:debuggable=true` (i.e., debug-signed APKs). On a release-signed build you cannot read or modify `/data/data/com.example.lifedots/shared_prefs/...` from adb without root. Plan a different mechanism if you need to set per-device preferences (in-app screen, intent extras, etc.).

### Compose Sliders + UI-test via adb

Compose Sliders don't always respond to taps at arbitrary points the way real `SeekBar`s do — they often need a drag. Map your target value to a pixel position via `bounds[x1..x2]` and tap; expect off-by-a-couple-of-percent and recalibrate (we measured ≈6px per percent on a 1080-wide slider in this project).

---

## 12. Recommended phone settings (publish in README)

### Samsung One UI

1. Settings → Battery → Background usage limits → **Never sleeping apps** → add the app.
2. Settings → Apps → [app] → Battery → **Unrestricted**.
3. When setting wallpaper: **Home and lock screens** (not just home).
4. (In-app shortcut: the "Keep wallpaper always running" card on the home screen runs both system flows for you.)

### Xiaomi / Redmi (MIUI / HyperOS)

1. Settings → Apps → Manage apps → [app] → **Autostart: ON**.
2. Settings → Apps → Manage apps → [app] → Battery saver → **No restrictions**.
3. Settings → Wallpaper → My wallpapers → Live wallpapers → pick the app.

### Honor / Huawei (EMUI / MagicOS)

The standard `ACTION_CHANGE_LIVE_WALLPAPER` preselection often fails — picker opens but to home-wallpaper view only. Fall back to the OS-level wallpaper picker (`ACTION_SET_WALLPAPER`) and have user navigate manually.

---

## 13. Version trajectory

| Tag | Headline | Detail |
|---|---|---|
| `v1.0.0` | First signed release | Release keystore + GitHub Actions + in-app updater (signature, download, install) |
| `v1.0.1` | Onboarding UX | Promoted Check-for-updates to OutlinedButton; "Keep wallpaper always running" card hides once battery exemption granted; column scroll |
| `v1.0.2` | Update conflict handling | Detect signature mismatch before launching installer; clear "uninstall first" message + button; cache-cleanup on launch |
| `v1.0.3` | Unify goals | Removed wedding-event feature; Goals render as red dots + countdown lines (the old wedding look, generalized). Settings UI: removed 10 sections (dot shape/size/style, grid density, view mode, background photo, animation, glass, tree, fluid). Migration v2: legacy event → Goal. |
| `v1.0.4` | A11 + OEM fixes | Calendar dot-math width-safe for 720px Samsung A11; Honor/Huawei picker fallback; Android 13+ notification permission flow; updater install-permission resume; restored View Mode controls (Year / Monthly / 365); preference migrations no longer destructive |
| `v1.0.5` | Calendar columns | Restricted to 2×6 / 3×4 only (dropped 4×3); legacy 4-col saves clamped |
| `v1.0.6` | Default rollback | `verticalOffset` default back to 0f (was 18) — defer "where should the calendar sit" to per-user choice |
| `v1.0.7` | Baseline attempt (rejected) | Added +19 baseline in renderer; v4 migration subtracted 19 from saved value |
| `v1.0.8` | Force 18 default | Migration v5 force-writes 18% to everyone's saved offset; renderer literal |
| `v1.0.9` | +18 baseline attempt (rejected) | Slider 0% rendered at +18% offset; v6 migration force-wrote 0 |
| `v1.0.10` | Strip everything | No baseline, no force-write; migration v7 wipes saved offset → default 0% |
| `v1.0.11` | Final: literal + 18% default | Renderer plain math (`offsetY = canvas.height * offset/100`); migration v8 force-writes 18 so every user opens at slider=18%, calendar 18% offset. **End of vertical-offset thrash.** |
| `v1.0.12` | Extract CalendarLayout module | Pulled Yil's per-device aspect/density math into a dedicated `wallpaper/CalendarLayout.kt` so the layout buckets are documented and re-usable. |
| `v1.1.0` | Umr (life-in-weeks) view + Yil/Umr auto-switch | Second top-level wallpaper mode rendering an 80×52 grid of dots — one per week of an 80-year life — alongside the existing Yil year calendar. New top section in Settings: styled `ModeTogglePill` (Yil / Umr), Auto-switch toggle, 5s / 1m / 2m / 5m / 30m / 1h interval picker. Mode-aware section visibility (Goals, View Mode, Highlight Today, Calendar columns hide in Umr; Theme, Position, Custom Colors, Footer Text shared). Birthday picker gates auto-switch (snackbar "Set your birthday first" guard). Rotation engine is a wall-clock pure function (`currentEffectiveMode(now, settings)`) read on every draw + `AutoSwitchRotator` (Handler.postDelayed visible-only + AlarmManager.setAndAllowWhileIdle for background coverage). Migration v9 is a no-op bump — class defaults cover both fresh installs and upgraders. New files: `wallpaper/AutoSwitchRotator.kt`, `ui/components/ModeTogglePill.kt`, JUnit tests for the resolver (8 tests) and `UmrLayoutCompute` (7 tests). |

---

## 14. README defaults (worth keeping in next project)

- Download button = `https://github.com/<owner>/<repo>/releases/latest/download/<asset>.apk` (stable URL via consistent asset name).
- Self-explanatory landing block: logo, name, one-line tagline, big download button, then bullet features.
- Per-OEM "recommended settings" section for sideload realities.
- Privacy paragraph: one network call (Releases API), no telemetry, all settings local.
- Credits to upstream fork + license.

---

## 15. Things explicitly avoided

- **Play Store packaging.** APK distribution via GitHub Releases only. `applicationId` stays `com.example.*` (which Play would reject), keystore is self-signed for our use only.
- **Analytics / telemetry.** Zero. No Firebase, no Crashlytics, no anon ping.
- **Third-party HTTP / JSON libraries.** `HttpURLConnection` + `org.json` already on the platform.
- **WorkManager.** `AlarmManager.setInexactRepeating` is enough for a once-a-day refresh; WorkManager would pull in extra deps.
- **DataStore.** `SharedPreferences` is fine for this scale and a singleton + `StateFlow` gives us reactive reads.
- **Hilt / DI frameworks.** Three singletons (`LifeDotsPreferences`, `UpdateChecker`, `UpdateInstaller`) — manual wiring is faster than the boilerplate.

---

## 16. To replicate this app structure in a new project

1. Copy the `app/build.gradle.kts` (with the `keystoreProps` block) and `.github/workflows/release.yml`.
2. Generate a fresh release keystore + upload to GitHub Secrets.
3. Wire `BuildConfig.GITHUB_OWNER` / `GITHUB_REPO` to your new repo.
4. Drop in `updater/{UpdateChecker,UpdateInstaller,UpdateNotifier}.kt` — rename `LifeDots/Updater` log tag.
5. Drop in `service/KeepAliveService.kt` + `res/drawable/ic_keepalive.xml`.
6. Drop in `receiver/DateChangeReceiver.kt` if your app cares about daily refreshes.
7. Set up the `MainActivity` battery-optimisation card pattern and the wallpaper-picker (if a wallpaper app) or any deep-link flows.
8. Decide your migrations from day one — bake in a `CURRENT_MIGRATION_VERSION` constant even if it's just `1`. Future-you will need it.

— end of notes —
