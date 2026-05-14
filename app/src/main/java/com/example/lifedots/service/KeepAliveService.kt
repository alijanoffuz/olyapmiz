package com.example.lifedots.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lifedots.MainActivity
import com.example.lifedots.R

/**
 * Foreground service that exists solely to anchor the wallpaper engine's process
 * in foreground state. Samsung One UI's Freecess (and Doze on stock Android, and
 * MIUI MemoryGuard on Xiaomi) freezes background processes hard — when the
 * wallpaper service goes invisible at screen-off, its process loses CPU and gets
 * swapped to NAND. The foreground-service contract requires the OS to leave us
 * alone.
 *
 * The notification is intentionally min-priority and uses a low-importance channel
 * so it sits at the bottom of the notification panel under a collapsed "Silent"
 * group; Samsung still surfaces a toggle so the user can hide it entirely.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Permission, notification, or background-start denial. The wallpaper
            // still works; we just lose the freeze-protection layer.
            Log.w(TAG, "Foreground start denied, stopping service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background activity",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the live wallpaper running on Samsung and Xiaomi."
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keepalive)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Wallpaper is running")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "LifeDots"
        private const val CHANNEL_ID = "olyapmiz_keepalive"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start KeepAliveService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop KeepAliveService", e)
            }
        }
    }
}
