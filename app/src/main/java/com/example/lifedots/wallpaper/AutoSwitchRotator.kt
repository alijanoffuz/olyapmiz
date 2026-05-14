package com.example.lifedots.wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.lifedots.preferences.AutoSwitchSettings
import com.example.lifedots.preferences.WallpaperSettings
import com.example.lifedots.receiver.DateChangeReceiver

/**
 * Owns the timing for Yil/Umr auto-switch.
 *
 * The mode itself is a pure function of wall-clock + settings —
 * the engine reads it on every draw and never holds rotation state
 * (see `currentEffectiveMode`). This rotator only triggers redraws
 * at interval boundaries:
 *
 *  - Handler.postDelayed: fires while wallpaper is visible. Works
 *    cleanly for the 5s test interval.
 *  - AlarmManager.setExactAndAllowWhileIdle: fires at the next
 *    boundary even while screen is off (with the OS-imposed Doze
 *    quota for short intervals — fine, the user can't see
 *    while screen is off anyway).
 *
 * Lifecycle: engine.onCreate creates one and calls refresh() on
 * every settings change + every visibility change. engine.onDestroy
 * calls cancel().
 */
class AutoSwitchRotator(
    private val context: Context,
    private val onTick: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { onTick(); reschedule() }
    private var lastSettings: WallpaperSettings? = null
    private var visibleNow: Boolean = false

    /** Call when wallpaper visibility changes. */
    fun setVisible(visible: Boolean) {
        visibleNow = visible
        reschedule()
    }

    /** Call after settings change. */
    fun refresh(settings: WallpaperSettings) {
        lastSettings = settings
        reschedule()
    }

    fun cancel() {
        handler.removeCallbacks(tickRunnable)
        cancelAlarm()
    }

    private fun reschedule() {
        handler.removeCallbacks(tickRunnable)
        val s = lastSettings ?: return
        val auto = s.autoSwitchSettings
        if (!auto.enabled || auto.intervalMs <= 0L) {
            cancelAlarm()
            return
        }
        val now = System.currentTimeMillis()
        val msUntilNextBoundary = millisUntilNextBoundary(now, auto) ?: run {
            cancelAlarm()
            return
        }

        // Handler — only worth setting while visible.
        if (visibleNow) {
            handler.postDelayed(tickRunnable, msUntilNextBoundary)
        }

        // AlarmManager — best-effort wake-up at the boundary so we can redraw
        // the moment the user wakes the phone, even after long idle.
        scheduleAlarm(now + msUntilNextBoundary)
    }

    private fun scheduleAlarm(triggerAtMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = DateChangeReceiver.ACTION_AUTO_SWITCH_TICK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
        try {
            // Inexact but Doze-friendly — no SCHEDULE_EXACT_ALARM permission needed.
            // Drift is OK: for short test intervals (5s) the Handler path covers visible
            // flips; for long intervals (30m, 1h) a few minutes of drift is invisible to
            // the user. The wall-clock formula gets the mode right on the next draw
            // regardless.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        } catch (e: Exception) {
            Log.w("LifeDots", "AutoSwitchRotator: failed to schedule alarm", e)
        }
    }

    private fun cancelAlarm() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DateChangeReceiver::class.java).apply {
            action = DateChangeReceiver.ACTION_AUTO_SWITCH_TICK
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags) ?: return
        am.cancel(pi)
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 1042

        internal fun millisUntilNextBoundary(now: Long, auto: AutoSwitchSettings): Long? {
            if (!auto.enabled || auto.intervalMs <= 0L) return null

            val elapsed = now - auto.referenceMs
            if (elapsed < 0L) {
                return (-elapsed + auto.intervalMs).coerceAtLeast(1L)
            }

            val remainder = elapsed % auto.intervalMs
            return (auto.intervalMs - remainder).coerceAtLeast(1L)
        }
    }
}
