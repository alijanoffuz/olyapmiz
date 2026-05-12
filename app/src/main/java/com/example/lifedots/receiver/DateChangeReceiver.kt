package com.example.lifedots.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lifedots.preferences.LifeDotsPreferences
import java.util.Calendar

/**
 * Forces the wallpaper to redraw on:
 *  - System date/time changes (manifest-registered).
 *  - A daily AlarmManager tick at ~00:00:30, as a safety net for devices where
 *    ACTION_DATE_CHANGED is dropped by aggressive battery optimization.
 *  - BOOT_COMPLETED, so the alarm is rescheduled after every reboot.
 *
 * The wallpaper engine itself also redraws on every visibility change, so the
 * worst-case experience is "wallpaper updates the moment you wake the phone".
 */
class DateChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.i("LifeDots", "Boot completed — scheduling daily refresh alarm")
                scheduleDailyAlarm(context)
            }
            ACTION_DAILY_TICK,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                Log.i("LifeDots", "Date/time changed (${intent.action}) — forcing wallpaper redraw")
                try {
                    val prefs = LifeDotsPreferences.getInstance(context)
                    // Toggling forces notifyWallpaperChanged() regardless of whether
                    // highlightToday actually changed.
                    prefs.setHighlightToday(prefs.settings.highlightToday)
                } catch (e: Exception) {
                    Log.e("LifeDots", "Failed to refresh prefs on date change", e)
                }
                // Re-arm the alarm so it keeps firing daily even if the OS dropped a tick.
                scheduleDailyAlarm(context)
            }
        }
    }

    companion object {
        const val ACTION_DAILY_TICK = "com.example.lifedots.DAILY_TICK"

        fun scheduleDailyAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val intent = Intent(context, DateChangeReceiver::class.java).apply {
                action = ACTION_DAILY_TICK
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pending = PendingIntent.getBroadcast(context, 0, intent, flags)

            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 30)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            try {
                // Inexact repeating — doze-friendly and no permission needed on Android 12+.
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    nextMidnight,
                    AlarmManager.INTERVAL_DAY,
                    pending
                )
            } catch (e: Exception) {
                Log.w("LifeDots", "Failed to schedule daily refresh alarm", e)
            }
        }
    }
}
