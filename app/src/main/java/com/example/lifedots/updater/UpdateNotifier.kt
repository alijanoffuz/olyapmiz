package com.example.lifedots.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lifedots.MainActivity
import com.example.lifedots.R

object UpdateNotifier {

    private const val CHANNEL_ID = "olyapmiz_updates"
    private const val NOTIFICATION_ID = 2001

    fun notify(context: Context, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        // Tapping the notification opens MainActivity with a flag that triggers
        // the download prompt automatically.
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_UPDATE_NOTIFICATION, true)
        }
        val pi = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_keepalive)
            .setContentTitle("O'lyapmiz ${info.versionName} is available")
            .setContentText("Tap to install the update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.releaseNotes))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when a new version of O'lyapmiz is available."
        }
        manager.createNotificationChannel(channel)
    }

    const val EXTRA_FROM_UPDATE_NOTIFICATION = "from_update_notification"
}
