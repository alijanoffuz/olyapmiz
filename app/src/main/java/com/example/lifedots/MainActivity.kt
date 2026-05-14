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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.ui.components.rememberUxFeedback
import com.example.lifedots.ui.theme.BrandColors
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
                    onSetWallpaper = { openWallpaperPicker() },
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

        // Fallback 2: stock Android battery settings (works on Xiaomi/Redmi too)
        tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }, "APP_NOTIFICATION_SETTINGS")
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
    onSetWallpaper: () -> Unit,
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
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pendingInstallApk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
                .height(screenHeight * 0.205f),
            verticalArrangement = Arrangement.spacedBy(screenHeight * 0.014f)
        ) {
            HomeActionButton(
                label = "Set as Wallpaper",
                icon = HomeIcon.Picture,
                onClick = {
                    feedback.click()
                    onSetWallpaper()
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
        }
    }
}

private enum class HomeIcon {
    Menu,
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
private fun RoundHomeButton(
    icon: HomeIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Color
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xB0050403))
            .border(BorderStroke(1.dp, gold.copy(alpha = 0.42f)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            drawHomeIcon(icon, gold, 4.dp.toPx())
        }
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
private fun FocusParchmentCard(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE4D8BC),
                        Color(0xFFC8B58E),
                        Color(0xFFE0D0A9)
                    )
                )
            )
            .border(BorderStroke(1.dp, Color(0x885E4524)), shape)
    ) {
        ParchmentTexture(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp, top = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Today’s Focus",
                color = Color(0xFF17120A),
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Become better\nthan yesterday.",
                color = Color(0xFF0C0905),
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }
        DeskClockSketch(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(112.dp),
            ink = Color(0xFF1A130B)
        )
    }
}

@Composable
private fun ParchmentTexture(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        repeat(42) { index ->
            val x = ((index * 37) % 100) / 100f * size.width
            val y = ((index * 61) % 100) / 100f * size.height
            drawCircle(
                color = Color.White.copy(alpha = if (index % 2 == 0) 0.10f else 0.06f),
                radius = (3 + index % 8).dp.toPx(),
                center = Offset(x, y)
            )
        }
        repeat(14) { index ->
            val start = Offset(
                ((index * 53) % 100) / 100f * size.width,
                ((index * 29) % 100) / 100f * size.height
            )
            drawLine(
                color = Color(0xFF4C321A).copy(alpha = 0.12f),
                start = start,
                end = start + Offset(((index % 5) - 2) * 18.dp.toPx(), (8 + index % 9).dp.toPx()),
                strokeWidth = 0.7.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun DeskClockSketch(
    modifier: Modifier = Modifier,
    ink: Color
) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val clockCenter = Offset(size.width * 0.66f, size.height * 0.42f)
        val clockRadius = size.minDimension * 0.25f
        val paper = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.86f, size.height * 0.42f)
            lineTo(size.width * 0.92f, size.height * 0.76f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            close()
        }
        drawPath(paper, ink.copy(alpha = 0.16f))
        drawPath(paper, ink.copy(alpha = 0.42f), style = Stroke(width = 1.dp.toPx()))
        drawRoundRect(
            color = ink.copy(alpha = 0.20f),
            topLeft = Offset(size.width * 0.09f, size.height * 0.42f),
            size = Size(size.width * 0.26f, size.height * 0.18f),
            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(ink.copy(alpha = 0.86f), radius = clockRadius, center = clockCenter, style = Stroke(width = stroke))
        drawCircle(ink.copy(alpha = 0.60f), radius = clockRadius * 0.76f, center = clockCenter, style = Stroke(width = 1.dp.toPx()))
        for (i in 0 until 12) {
            val angle = (i * 30f - 90f) * PI.toFloat() / 180f
            val outer = Offset(
                clockCenter.x + cos(angle) * clockRadius * 0.88f,
                clockCenter.y + sin(angle) * clockRadius * 0.88f
            )
            val inner = Offset(
                clockCenter.x + cos(angle) * clockRadius * 0.72f,
                clockCenter.y + sin(angle) * clockRadius * 0.72f
            )
            drawLine(ink.copy(alpha = 0.58f), inner, outer, strokeWidth = 1.dp.toPx())
        }
        drawLine(ink, clockCenter, clockCenter + Offset(0f, -clockRadius * 0.52f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(ink, clockCenter, clockCenter + Offset(clockRadius * 0.40f, clockRadius * 0.25f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(ink, radius = 2.dp.toPx(), center = clockCenter)
        drawCircle(ink.copy(alpha = 0.76f), radius = clockRadius * 0.22f, center = clockCenter + Offset(0f, -clockRadius * 1.12f), style = Stroke(width = 2.dp.toPx()))
        drawLine(
            ink.copy(alpha = 0.56f),
            Offset(size.width * 0.22f, size.height * 0.69f),
            Offset(size.width * 0.86f, size.height * 0.65f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun QuoteCard(
    modifier: Modifier = Modifier,
    gold: Color
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xC9070604))
            .border(BorderStroke(1.dp, gold.copy(alpha = 0.34f)), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "“",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-12).dp),
            color = gold.copy(alpha = 0.22f),
            fontFamily = FontFamily.Serif,
            fontSize = 64.sp,
            lineHeight = 64.sp,
            fontWeight = FontWeight.Bold
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Text(
                text = "We are our choices.",
                color = gold,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "— J.P. Sartre",
                color = gold.copy(alpha = 0.90f),
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            GoldOrnament(
                modifier = Modifier.size(width = 106.dp, height = 12.dp),
                gold = gold
            )
        }
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
        HomeIcon.Menu -> {
            val startX = size.width * 0.18f
            val endX = size.width * 0.82f
            listOf(0.32f, 0.50f, 0.68f).forEach { y ->
                drawLine(color, Offset(startX, size.height * y), Offset(endX, size.height * y), strokeWidth, StrokeCap.Round)
            }
        }
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

@Composable
private fun KeepAlwaysRunningCard(
    showBatteryButton: Boolean,
    showNotificationButton: Boolean,
    showSamsungButton: Boolean,
    onAllowBackground: () -> Unit,
    onAllowNotifications: () -> Unit,
    onOpenSamsungNeverSleeping: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = BrandColors.InkBlack
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Keep wallpaper always running",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.AmberGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Samsung and Xiaomi can freeze background apps. Allow these so the calendar doesn't disappear from your lock screen.",
                fontSize = 13.sp,
                color = BrandColors.OffWhite,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (showBatteryButton) {
                OutlinedButton(
                    onClick = onAllowBackground,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandColors.AmberGold),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrandColors.AmberGold,
                    ),
                ) {
                    Text("Allow background activity", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (showNotificationButton) {
                OutlinedButton(
                    onClick = onAllowNotifications,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandColors.AmberGold),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrandColors.AmberGold,
                    ),
                ) {
                    Text("Allow notifications", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (showSamsungButton) {
                OutlinedButton(
                    onClick = onOpenSamsungNeverSleeping,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandColors.AmberGold),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrandColors.AmberGold,
                    ),
                ) {
                    Text("Add to 'Never sleeping apps'", fontSize = 14.sp)
                }
            }
        }
    }
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

@Composable
private fun UpdateCard(
    state: UpdateUiState,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onLaunchSelfUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateUiState.Idle || state is UpdateUiState.Checking) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = BrandColors.InkBlack
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (state) {
                is UpdateUiState.Available -> {
                    Text(
                        "Update available: v${state.info.versionName}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.AmberGold
                    )
                    if (state.info.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            state.info.releaseNotes.take(200),
                            fontSize = 13.sp,
                            color = BrandColors.OffWhite,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onDownload(state.info) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.AmberGold,
                            contentColor = BrandColors.InkBlack,
                        ),
                    ) {
                        Text("Download and install", fontSize = 14.sp)
                    }
                }
                is UpdateUiState.Downloading -> {
                    Text(
                        "Downloading v${state.info.versionName}…",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.AmberGold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = BrandColors.AmberGold
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            "${(state.progress * 100).toInt()}%",
                            fontSize = 14.sp,
                            color = BrandColors.OffWhite
                        )
                    }
                }
                is UpdateUiState.Ready -> {
                    Text(
                        "Ready to install v${state.info.versionName}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.AmberGold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onInstall(state.apk) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.AmberGold,
                            contentColor = BrandColors.InkBlack,
                        ),
                    ) {
                        Text("Install now", fontSize = 14.sp)
                    }
                }
                is UpdateUiState.InstallPermissionRequired -> {
                    Text(
                        "Install permission needed",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.AmberGold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Allow O'lyapmiz to install downloaded updates. The installer will continue when you return.",
                        fontSize = 13.sp,
                        color = BrandColors.OffWhite,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onInstall(state.apk) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.AmberGold,
                            contentColor = BrandColors.InkBlack,
                        ),
                    ) {
                        Text("Open install permission", fontSize = 14.sp)
                    }
                }
                is UpdateUiState.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "You're on the latest version (${state.version}).",
                            fontSize = 14.sp,
                            color = BrandColors.OffWhite,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = BrandColors.AmberGold,
                            ),
                        ) {
                            Text("OK", fontSize = 14.sp)
                        }
                    }
                }
                is UpdateUiState.SignatureMismatch -> {
                    Text(
                        "One-time uninstall needed",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.AmberGold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Your current install was signed with a different key " +
                            "(likely a debug or sideloaded build). Android won't install " +
                            "v${state.info.versionName} on top of it. Tap below to " +
                            "uninstall, then reopen the downloaded APK to install fresh — " +
                            "from then on, all future updates work in-place automatically.",
                        fontSize = 13.sp,
                        color = BrandColors.OffWhite,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onLaunchSelfUninstall,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.AmberGold,
                            contentColor = BrandColors.InkBlack,
                        ),
                    ) {
                        Text("Uninstall current version", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = BrandColors.AmberGold,
                        ),
                    ) {
                        Text("Later", fontSize = 14.sp)
                    }
                }
                is UpdateUiState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Couldn't check for updates: ${state.message}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = BrandColors.AmberGold,
                            ),
                        ) {
                            Text("OK", fontSize = 14.sp)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
