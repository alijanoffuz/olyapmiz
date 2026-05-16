package com.example.lifedots

import android.annotation.SuppressLint
import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.service.KeepAliveService
import com.example.lifedots.ui.components.rememberUxFeedback
import com.example.lifedots.ui.theme.LifeDotsTheme
import com.example.lifedots.updater.UpdateChecker
import com.example.lifedots.updater.UpdateInfo
import com.example.lifedots.updater.UpdateInstaller
import com.example.lifedots.updater.UpdateNotifier
import com.example.lifedots.wallpaper.LifeDotsWallpaperService
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Touch the prefs singleton on every launch so the rebrand migration
        // runs the moment a user opens the app — not only when the wallpaper
        // engine happens to start. Critical for users upgrading from upstream
        // LifeDots where view_mode=CONTINUOUS was persisted.
        LifeDotsPreferences.getInstance(this)
        // Wipe any leftover downloaded APKs from previous update attempts so
        // we don't accumulate them on disk.
        UpdateInstaller(this).cleanCache()
        enableEdgeToEdge()
        // The reference home screen is full-bleed with no visible system chrome.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.systemBars())
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            LifeDotsTheme {
                val installer = remember { UpdateInstaller(this) }
                OnboardingScreen(
                    onApplyHomeAndLock = { openWallpaperPicker() },
                    onApplyHomeOnlyLive = { applySnapshotToLockThen(openLive = true) },
                    onApplyLockOnly = { applySnapshotToLockThen(openLive = false) },
                    onOpenSettings = { openSettings() },
                    onAllowBackground = { requestIgnoreBatteryOptimizations() },
                    onOpenSamsungNeverSleeping = { openSamsungNeverSleeping() },
                    onInstallApk = { apk -> installer.launchInstaller(this, apk) },
                    onLaunchSelfUninstall = { installer.launchSelfUninstall(this) },
                    autoCheckOnLaunch = true,
                    triggerFromNotification = intent?.getBooleanExtra(
                        UpdateNotifier.EXTRA_FROM_UPDATE_NOTIFICATION, false
                    ) ?: false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        // Shows the system dialog "Allow [app] to ignore battery optimizations? — Allow / Deny".
        // One tap and Samsung's Freecess + stock Android Doze stop killing the wallpaper.
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        if (!tryStart(intent, "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) {
            // Fallback: open the full list, user finds the app manually
            tryStart(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                "IGNORE_BATTERY_OPTIMIZATION_SETTINGS"
            )
        }
    }

    private fun openSamsungNeverSleeping() {
        // Samsung One UI has a SECOND layer of restriction on top of standard Android
        // battery optimization: "Background usage limits → Never sleeping apps". The
        // user has to manually add the app to that list — there's no system dialog
        // that flips it. Best we can do is deep-link to the page.
        // Component path is fairly stable across One UI versions; if it ever changes,
        // tryStart falls back to opening the generic Device Care page.
        val samsungIntent = Intent().apply {
            setClassName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.notsleeping.NotSleepingActivity"
            )
        }
        if (tryStart(samsungIntent, "SAMSUNG_NEVER_SLEEPING")) return

        // Fallback 1: Samsung Device Care top-level
        val deviceCare = Intent(Intent.ACTION_MAIN).apply {
            setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity")
        }
        if (tryStart(deviceCare, "SAMSUNG_DEVICE_CARE")) return

        // Fallback 2: app details, where most OEMs expose their battery/autostart controls.
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }, "APP_DETAILS_SETTINGS")
    }

    /**
     * Renders a snapshot of the current Yil/Umr state and applies it to the
     * lock screen via WallpaperManager.setBitmap(FLAG_LOCK). If [openLive] is
     * true, also fires the live-wallpaper picker so the user can pick our
     * service for the home screen — net result: live on home, today's
     * static on lock. If false, the home wallpaper is left untouched.
     *
     * Note on Android limits: live wallpapers cannot be set to the lock
     * screen alone via any public API. A still snapshot is the closest a
     * non-system app can get. The snapshot doesn't auto-refresh — the user
     * has to re-run "Set as Wallpaper" to update.
     */
    private fun applySnapshotToLockThen(openLive: Boolean) {
        try {
            val wm = WallpaperManager.getInstance(this)
            val w = wm.desiredMinimumWidth.coerceAtLeast(resources.displayMetrics.widthPixels)
            val h = wm.desiredMinimumHeight.coerceAtLeast(resources.displayMetrics.heightPixels)
            val bitmap = com.example.lifedots.wallpaper.SnapshotRenderer.render(this, w, h)
            wm.setBitmap(bitmap, /*visibleCropHint*/ null, /*allowBackup*/ true, WallpaperManager.FLAG_LOCK)
            Toast.makeText(this, "Lock screen set", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w("LifeDots", "applySnapshotToLockThen failed", e)
            Toast.makeText(
                this,
                "Couldn't apply to lock screen: ${e.message}",
                Toast.LENGTH_LONG,
            ).show()
        }
        if (openLive) openWallpaperPicker()
    }

    private fun openWallpaperPicker() {
        val component = ComponentName(this@MainActivity, LifeDotsWallpaperService::class.java)
        Log.i("LifeDots", "openWallpaperPicker: trying CHANGE_LIVE_WALLPAPER for $component")

        if (prefersGenericLiveWallpaperChooser()) {
            val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(chooser, "OEM_LIVE_WALLPAPER_CHOOSER")) return
        }

        // Primary path: ACTION_CHANGE_LIVE_WALLPAPER with the LifeDots component
        // pre-selected. NEW_TASK is required on Samsung/Android 15 for cross-process
        // wallpaper picker launches; without it the system silently rejects the start.
        val primary = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(primary, "CHANGE_LIVE_WALLPAPER")) return

        // Fallback 1: generic live-wallpaper chooser (no preselection)
        val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(chooser, "LIVE_WALLPAPER_CHOOSER")) return

        // Fallback 2: open a wallpaper picker via the generic set-wallpaper chooser
        val anyWallpaper = Intent.createChooser(
            Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.set_wallpaper)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (tryStart(anyWallpaper, "SET_WALLPAPER")) return

        Toast.makeText(
            this,
            "Couldn't open wallpaper picker. Open Settings → Wallpaper → Live wallpapers and pick LifeDots.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun prefersGenericLiveWallpaperChooser(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("honor") ||
            brand.contains("honor") ||
            manufacturer.contains("huawei") ||
            brand.contains("huawei")
    }

    private fun tryStart(intent: Intent, label: String): Boolean {
        val resolved = packageManager.resolveActivity(intent, 0)
        if (resolved == null) {
            Log.w("LifeDots", "$label: no activity resolves intent ${intent.action}")
            return false
        }
        Log.i("LifeDots", "$label: resolved to ${resolved.activityInfo.packageName}/${resolved.activityInfo.name}")
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("LifeDots", "$label: activity not found", e)
            false
        } catch (e: SecurityException) {
            Log.w("LifeDots", "$label: security exception", e)
            false
        } catch (e: Exception) {
            Log.w("LifeDots", "$label: failed", e)
            false
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}

@Composable
fun OnboardingScreen(
    onApplyHomeAndLock: () -> Unit,
    onApplyHomeOnlyLive: () -> Unit,
    onApplyLockOnly: () -> Unit,
    onOpenSettings: () -> Unit,
    onAllowBackground: () -> Unit,
    onOpenSamsungNeverSleeping: () -> Unit,
    onInstallApk: (File) -> UpdateInstaller.LaunchResult,
    onLaunchSelfUninstall: () -> Unit,
    autoCheckOnLaunch: Boolean,
    triggerFromNotification: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var pendingInstallApk by remember { mutableStateOf<File?>(null) }
    var pendingInstallInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showApplyDialog by remember { mutableStateOf(false) }

    fun handleInstall(apk: File) {
        val info = when (val state = updateState) {
            is UpdateUiState.Ready -> state.info
            is UpdateUiState.InstallPermissionRequired -> state.info
            else -> pendingInstallInfo
        }
        when (onInstallApk(apk)) {
            UpdateInstaller.LaunchResult.Started -> {
                pendingInstallApk = null
                pendingInstallInfo = null
            }
            UpdateInstaller.LaunchResult.NeedsUnknownSources -> {
                pendingInstallApk = apk
                pendingInstallInfo = info
                if (info != null) {
                    updateState = UpdateUiState.InstallPermissionRequired(info, apk)
                }
            }
            UpdateInstaller.LaunchResult.SignatureMismatch -> {
                pendingInstallApk = null
                pendingInstallInfo = null
                if (info != null) {
                    updateState = UpdateUiState.SignatureMismatch(info)
                }
            }
            UpdateInstaller.LaunchResult.Failed -> {
                pendingInstallApk = null
                pendingInstallInfo = null
                updateState = UpdateUiState.Error("Could not open Android package installer")
            }
        }
    }

    var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
    var notificationsAllowed by remember { mutableStateOf(hasPostNotificationsPermission(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsAllowed = hasPostNotificationsPermission(context)
        if (notificationsAllowed) KeepAliveService.start(context)
    }

    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pendingInstallApk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimized = isBatteryOptimized(context)
                notificationsAllowed = hasPostNotificationsPermission(context)
                val apk = pendingInstallApk
                if (apk != null && context.packageManager.canRequestPackageInstalls()) {
                    handleInstall(apk)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun checkForUpdate(silent: Boolean) {
        if (updateState is UpdateUiState.Checking || updateState is UpdateUiState.Downloading) return
        updateState = UpdateUiState.Checking
        scope.launch {
            val checker = UpdateChecker(context)
            updateState = when (val result = checker.check()) {
                is UpdateChecker.Result.UpdateAvailable -> {
                    UpdateNotifier.notify(context, result.info)
                    UpdateUiState.Available(result.info)
                }
                is UpdateChecker.Result.UpToDate ->
                    if (silent) UpdateUiState.Idle else UpdateUiState.UpToDate(result.version)
                is UpdateChecker.Result.NetworkError ->
                    if (silent) UpdateUiState.Idle else UpdateUiState.Error(result.message)
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        pendingInstallApk = null
        pendingInstallInfo = null
        updateState = UpdateUiState.Downloading(info, 0f)
        scope.launch {
            val installer = UpdateInstaller(context)
            val apk = installer.download(info) { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                updateState = UpdateUiState.Downloading(info, progress)
            }
            if (apk != null) {
                pendingInstallApk = apk
                pendingInstallInfo = info
                updateState = UpdateUiState.Ready(info, apk)
            } else {
                updateState = UpdateUiState.Error("Download failed")
            }
        }
    }

    // Auto-check on launch (silent — only surfaces if there's actually an update),
    // and follow through immediately to download if the user opened the app from
    // an "update available" notification.
    LaunchedEffect(autoCheckOnLaunch, triggerFromNotification) {
        if (autoCheckOnLaunch) checkForUpdate(silent = true)
    }
    LaunchedEffect(updateState, triggerFromNotification) {
        if (triggerFromNotification) {
            (updateState as? UpdateUiState.Available)?.let { startDownload(it.info) }
        }
    }

    val preferences = remember { LifeDotsPreferences.getInstance(context) }
    val settings by preferences.settingsFlow.collectAsState()
    val feedback = rememberUxFeedback(settings.soundsEnabled, settings.vibrationsEnabled)

    val updateButtonText = when (val state = updateState) {
        is UpdateUiState.Checking -> "Checking"
        is UpdateUiState.Downloading -> "Downloading"
        is UpdateUiState.Available -> "Download v${state.info.versionName}"
        is UpdateUiState.Ready -> "Install Update"
        is UpdateUiState.InstallPermissionRequired -> "Allow Install"
        is UpdateUiState.SignatureMismatch -> "Uninstall Old App"
        is UpdateUiState.UpToDate -> "Up to Date"
        is UpdateUiState.Error -> "Try Again"
        else -> "Update"
    }

    fun handleUpdateButton() {
        feedback.click()
        when (val state = updateState) {
            is UpdateUiState.Available -> startDownload(state.info)
            is UpdateUiState.Ready -> handleInstall(state.apk)
            is UpdateUiState.InstallPermissionRequired -> handleInstall(state.apk)
            is UpdateUiState.SignatureMismatch -> onLaunchSelfUninstall()
            else -> checkForUpdate(silent = false)
        }
    }

    fun handleKeepRunningButton() {
        feedback.click()
        when {
            !notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            batteryOptimized -> {
                KeepAliveService.start(context)
                onAllowBackground()
            }
            isSamsungLikeDevice() -> {
                KeepAliveService.start(context)
                onOpenSamsungNeverSleeping()
            }
            else -> {
                KeepAliveService.start(context)
                onAllowBackground()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030302))
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val gold = Color(0xFFC5A266)

        Image(
            painter = painterResource(id = R.drawable.home_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        PaintingHomeScrim(
            modifier = Modifier.matchParentSize()
        )

        TitleMark(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = screenHeight * 0.555f),
            gold = gold
        )

        Column(
            modifier = Modifier
                .offset(x = screenWidth * 0.09f, y = screenHeight * 0.665f)
                .width(screenWidth * 0.82f)
                .height(screenHeight * 0.275f),
            verticalArrangement = Arrangement.spacedBy(screenHeight * 0.014f)
        ) {
            HomeActionButton(
                label = "Set as Wallpaper",
                icon = HomeIcon.Picture,
                onClick = {
                    feedback.click()
                    showApplyDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                gold = gold,
                large = true
            )
            HomeActionButton(
                label = "Customize",
                icon = HomeIcon.Palette,
                onClick = {
                    feedback.click()
                    onOpenSettings()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                gold = gold,
                large = true
            )
            HomeActionButton(
                label = updateButtonText,
                icon = HomeIcon.Download,
                onClick = { handleUpdateButton() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                gold = gold,
                large = true,
                enabled = updateState !is UpdateUiState.Checking &&
                    updateState !is UpdateUiState.Downloading
            )
            HomeActionButton(
                label = when {
                    !notificationsAllowed -> "Press to Allow Notifications"
                    batteryOptimized -> "Press to Allow Background"
                    isSamsungLikeDevice() -> "Press for Never Sleeping"
                    else -> "Press to Keep Running"
                },
                icon = HomeIcon.Settings,
                onClick = { handleKeepRunningButton() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                gold = gold,
                large = true
            )
        }

        if (showApplyDialog) {
            ApplyWallpaperDialog(
                gold = gold,
                onDismiss = { showApplyDialog = false },
                onBoth = {
                    showApplyDialog = false
                    onApplyHomeAndLock()
                },
                onHomeOnly = {
                    showApplyDialog = false
                    onApplyHomeOnlyLive()
                },
                onLockOnly = {
                    showApplyDialog = false
                    onApplyLockOnly()
                },
            )
        }
    }
}

@Composable
private fun ApplyWallpaperDialog(
    gold: Color,
    onDismiss: () -> Unit,
    onBoth: () -> Unit,
    onHomeOnly: () -> Unit,
    onLockOnly: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0A0906))
                .border(
                    BorderStroke(1.dp, gold.copy(alpha = 0.45f)),
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column {
                Text(
                    text = "Apply where?",
                    color = gold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Live wallpaper covers both screens by default. Android does not allow a live wallpaper on the lock screen alone, so Lock-only sets today’s static snapshot instead.",
                    color = Color(0xC8EDE8DE),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(modifier = Modifier.height(18.dp))
                ApplyOptionRow(
                    title = "Lock & Home screen",
                    subtitle = "Live wallpaper on both",
                    gold = gold,
                    primary = true,
                    onClick = onBoth,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ApplyOptionRow(
                    title = "Home screen only",
                    subtitle = "Live on home, today’s snapshot frozen on lock",
                    gold = gold,
                    onClick = onHomeOnly,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ApplyOptionRow(
                    title = "Lock screen only",
                    subtitle = "Today’s static snapshot – home stays untouched",
                    gold = gold,
                    onClick = onLockOnly,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        color = gold.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplyOptionRow(
    title: String,
    subtitle: String,
    gold: Color,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (primary) Color(0xFF14110B) else Color(0x14FFFFFF))
            .border(BorderStroke(1.dp, gold.copy(alpha = if (primary) 0.55f else 0.30f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column {
            Text(
                text = title,
                color = gold,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color(0xC0EDE8DE),
                fontSize = 12.sp,
            )
        }
    }
}

private enum class HomeIcon {
    Settings,
    Picture,
    Palette,
    Download
}

@Composable
private fun PaintingHomeScrim(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black.copy(alpha = 0.02f),
                    0.42f to Color.Transparent,
                    0.76f to Color.Black.copy(alpha = 0.04f),
                    1.00f to Color.Black.copy(alpha = 0.10f)
                ),
                startY = 0f,
                endY = size.height
            )
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.12f)
                ),
                startX = 0f,
                endX = size.width
            )
        )
        drawRect(Color.Black.copy(alpha = 0.01f))
    }
}

@Composable
private fun TitleMark(
    modifier: Modifier = Modifier,
    gold: Color
) {
    Box(
        modifier = modifier.height(128.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = "O’lyapmiz",
            modifier = Modifier.fillMaxWidth(),
            color = gold,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Serif,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        GoldOrnament(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 59.dp)
                .size(width = 158.dp, height = 15.dp),
            gold = gold
        )
    }
}

@Composable
private fun GoldOrnament(
    modifier: Modifier = Modifier,
    gold: Color
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val centerX = size.width / 2f
        val lineInset = size.width * 0.14f
        drawLine(
            color = gold.copy(alpha = 0.82f),
            start = Offset(0f, centerY),
            end = Offset(centerX - lineInset, centerY),
            strokeWidth = 1.3.dp.toPx()
        )
        drawLine(
            color = gold.copy(alpha = 0.82f),
            start = Offset(centerX + lineInset, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.3.dp.toPx()
        )
        val diamond = Path().apply {
            moveTo(centerX, centerY - size.height * 0.35f)
            lineTo(centerX + size.height * 0.32f, centerY)
            lineTo(centerX, centerY + size.height * 0.35f)
            lineTo(centerX - size.height * 0.32f, centerY)
            close()
        }
        drawPath(diamond, gold.copy(alpha = 0.84f), style = Stroke(width = 1.1.dp.toPx()))
        drawCircle(gold.copy(alpha = 0.84f), radius = 2.dp.toPx(), center = Offset(centerX - size.height * 0.44f, centerY))
        drawCircle(gold.copy(alpha = 0.84f), radius = 2.dp.toPx(), center = Offset(centerX + size.height * 0.44f, centerY))
        drawLine(
            color = gold.copy(alpha = 0.84f),
            start = Offset(centerX - size.height * 0.95f, centerY - size.height * 0.26f),
            end = Offset(centerX + size.height * 0.95f, centerY + size.height * 0.26f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gold.copy(alpha = 0.84f),
            start = Offset(centerX - size.height * 0.95f, centerY + size.height * 0.26f),
            end = Offset(centerX + size.height * 0.95f, centerY - size.height * 0.26f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: HomeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Color,
    large: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(0xC20A0906))
            .border(BorderStroke(1.dp, gold.copy(alpha = 0.42f)), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(if (large) 34.dp else 24.dp)) {
                drawHomeIcon(
                    icon = icon,
                    color = gold.copy(alpha = if (enabled) 0.92f else 0.48f),
                    strokeWidth = if (large) 2.3.dp.toPx() else 1.8.dp.toPx()
                )
            }
            Spacer(modifier = Modifier.width(if (large) 16.dp else 5.dp))
            Text(
                text = label,
                color = gold.copy(alpha = if (enabled) 0.96f else 0.52f),
                fontFamily = FontFamily.Serif,
                fontSize = if (large) 20.sp else 10.5.sp,
                lineHeight = if (large) 24.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = if (large) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeIcon(
    icon: HomeIcon,
    color: Color,
    strokeWidth: Float
) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    when (icon) {
        HomeIcon.Settings -> {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * 0.34f
            val inner = size.minDimension * 0.18f
            for (i in 0 until 8) {
                val angle = i * PI.toFloat() / 4f
                val start = Offset(center.x + cos(angle) * outer * 0.78f, center.y + sin(angle) * outer * 0.78f)
                val end = Offset(center.x + cos(angle) * outer * 1.08f, center.y + sin(angle) * outer * 1.08f)
                drawLine(color, start, end, strokeWidth * 0.95f, StrokeCap.Round)
            }
            drawCircle(color, radius = outer * 0.82f, center = center, style = Stroke(width = strokeWidth * 1.2f))
            drawCircle(color, radius = inner, center = center, style = Stroke(width = strokeWidth * 1.1f))
        }
        HomeIcon.Picture -> {
            val rectTopLeft = Offset(size.width * 0.18f, size.height * 0.20f)
            val rectSize = Size(size.width * 0.64f, size.height * 0.62f)
            drawRoundRect(color, rectTopLeft, rectSize, CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(width = strokeWidth))
            val mountain = Path().apply {
                moveTo(size.width * 0.24f, size.height * 0.74f)
                lineTo(size.width * 0.42f, size.height * 0.54f)
                lineTo(size.width * 0.53f, size.height * 0.65f)
                lineTo(size.width * 0.63f, size.height * 0.50f)
                lineTo(size.width * 0.78f, size.height * 0.73f)
            }
            drawPath(mountain, color, style = stroke)
            drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.34f, size.height * 0.35f))
        }
        HomeIcon.Palette -> {
            val outline = Path().apply {
                moveTo(size.width * 0.46f, size.height * 0.14f)
                cubicTo(size.width * 0.21f, size.height * 0.15f, size.width * 0.10f, size.height * 0.34f, size.width * 0.14f, size.height * 0.55f)
                cubicTo(size.width * 0.19f, size.height * 0.83f, size.width * 0.53f, size.height * 0.92f, size.width * 0.75f, size.height * 0.75f)
                cubicTo(size.width * 0.86f, size.height * 0.66f, size.width * 0.78f, size.height * 0.53f, size.width * 0.65f, size.height * 0.57f)
                cubicTo(size.width * 0.52f, size.height * 0.61f, size.width * 0.49f, size.height * 0.48f, size.width * 0.62f, size.height * 0.38f)
                cubicTo(size.width * 0.75f, size.height * 0.28f, size.width * 0.66f, size.height * 0.14f, size.width * 0.46f, size.height * 0.14f)
                close()
            }
            drawPath(outline, color, style = stroke)
            drawCircle(
                color = color,
                radius = size.minDimension * 0.12f,
                center = Offset(size.width * 0.62f, size.height * 0.66f),
                style = stroke
            )
            listOf(
                Offset(size.width * 0.34f, size.height * 0.37f),
                Offset(size.width * 0.48f, size.height * 0.29f),
                Offset(size.width * 0.27f, size.height * 0.55f)
            ).forEach {
                drawCircle(color, radius = 2.6.dp.toPx(), center = it)
            }
        }
        HomeIcon.Download -> {
            val cloud = Path().apply {
                moveTo(size.width * 0.21f, size.height * 0.60f)
                cubicTo(size.width * 0.14f, size.height * 0.55f, size.width * 0.17f, size.height * 0.42f, size.width * 0.30f, size.height * 0.42f)
                cubicTo(size.width * 0.33f, size.height * 0.26f, size.width * 0.56f, size.height * 0.23f, size.width * 0.64f, size.height * 0.39f)
                cubicTo(size.width * 0.78f, size.height * 0.38f, size.width * 0.86f, size.height * 0.50f, size.width * 0.80f, size.height * 0.61f)
            }
            drawPath(cloud, color, style = stroke)
            drawLine(color, Offset(size.width * 0.50f, size.height * 0.44f), Offset(size.width * 0.50f, size.height * 0.78f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.38f, size.height * 0.66f), Offset(size.width * 0.50f, size.height * 0.79f), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.62f, size.height * 0.66f), Offset(size.width * 0.50f, size.height * 0.79f), strokeWidth, StrokeCap.Round)
        }
    }
}

private fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun hasPostNotificationsPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateUiState
    data class InstallPermissionRequired(val info: UpdateInfo, val apk: File) : UpdateUiState
    data class SignatureMismatch(val info: UpdateInfo) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

private fun isSamsungLikeDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    return manufacturer.contains("samsung") || brand.contains("samsung")
}
