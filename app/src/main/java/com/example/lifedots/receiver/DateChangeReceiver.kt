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
                // Re-arm auto-switch by poking prefs so the engine reschedules its alarm.
                pokeAutoSwitchRedraw(context)
            }
            ACTION_DAILY_TICK,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                Log.i("LifeDots", "Date/time changed (${intent.action}) — forcing wallpaper redraw")
                pokeAutoSwitchRedraw(context)
                scheduleDailyAlarm(context)
            }
            ACTION_AUTO_SWITCH_TICK -> {
                Log.i("LifeDots", "Auto-switch tick — forcing wallpaper redraw")
                pokeAutoSwitchRedraw(context)
            }
        }
    }

    /**
     * Force a wallpaper redraw and let the engine reschedule its next
     * alarm — by toggling a no-op preference, we leverage the existing
     * notifyWallpaperChanged() listener chain.
     */
    private fun pokeAutoSwitchRedraw(context: Context) {
        try {
            val prefs = LifeDotsPreferences.getInstance(context)
            prefs.setHighlightToday(prefs.settings.highlightToday)
        } catch (e: Exception) {
            Log.e("LifeDots", "Failed to poke prefs on auto-switch tick", e)
        }
    }

    companion object {
        const val ACTION_DAILY_TICK = "com.example.lifedots.DAILY_TICK"
        const val ACTION_AUTO_SWITCH_TICK = "com.example.lifedots.AUTO_SWITCH_TICK"

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
