package com.example.lifedots

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.ui.theme.LifeDotsTheme
import com.example.lifedots.wallpaper.LifeDotsWallpaperService
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Touch the prefs singleton on every launch so the rebrand migration
        // runs the moment a user opens the app — not only when the wallpaper
        // engine happens to start. Critical for users upgrading from upstream
        // LifeDots where view_mode=CONTINUOUS was persisted.
        LifeDotsPreferences.getInstance(this)
        enableEdgeToEdge()
        setContent {
            LifeDotsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OnboardingScreen(
                        onSetWallpaper = { openWallpaperPicker() },
                        onOpenSettings = { openSettings() },
                        onAllowBackground = { requestIgnoreBatteryOptimizations() },
                        onOpenSamsungNeverSleeping = { openSamsungNeverSleeping() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
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

        // Primary path: ACTION_CHANGE_LIVE_WALLPAPER with the LifeDots component
        // pre-selected. NEW_TASK is required on Samsung/Android 15 for cross-process
        // wallpaper picker launches; without it the system silently rejects the start.
        val primary = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(primary, "CHANGE_LIVE_WALLPAPER")) return

        // Fallback 1: generic live-wallpaper chooser (no preselection)
        val chooser = Intent("android.service.wallpaper.LIVE_WALLPAPER_CHOOSER").apply {
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dayOfYear = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }
    val totalDays = remember { Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_YEAR) }

    // Re-check battery optimization status every time the user comes back from
    // the system settings screen, so the card disappears once they tap "Allow".
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimized = isBatteryOptimized(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isSamsung = remember { Build.MANUFACTURER.equals("samsung", ignoreCase = true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo — point at the bitmap drawable directly, NOT @mipmap/ic_launcher.
        // On API 26+ ic_launcher resolves to <adaptive-icon> XML which Compose's
        // painterResource cannot load, throwing IllegalArgumentException.
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_logo),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(180.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = stringResource(R.string.onboarding_title),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Description
        Text(
            text = stringResource(R.string.onboarding_description),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Days counter
        Text(
            text = stringResource(R.string.days_passed, dayOfYear),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.days_remaining, totalDays - dayOfYear),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // "Keep Always Running" — only shown while the app is still being killed
        // by Samsung Freecess / stock Android Doze. Once the user grants the
        // battery-optimization exemption, this card hides itself.
        if (batteryOptimized || isSamsung) {
            KeepAlwaysRunningCard(
                showBatteryButton = batteryOptimized,
                showSamsungButton = isSamsung,
                onAllowBackground = onAllowBackground,
                onOpenSamsungNeverSleeping = onOpenSamsungNeverSleeping
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Buttons
        Button(
            onClick = onSetWallpaper,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.set_wallpaper),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.open_settings),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun KeepAlwaysRunningCard(
    showBatteryButton: Boolean,
    showSamsungButton: Boolean,
    onAllowBackground: () -> Unit,
    onOpenSamsungNeverSleeping: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Keep wallpaper always running",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Samsung and Xiaomi can freeze background apps. Allow these so the calendar doesn't disappear from your lock screen.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (showBatteryButton) {
                OutlinedButton(
                    onClick = onAllowBackground,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("1. Allow background activity", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (showSamsungButton) {
                OutlinedButton(
                    onClick = onOpenSamsungNeverSleeping,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("2. Add to 'Never sleeping apps'", fontSize = 14.sp)
                }
            }
        }
    }
}

