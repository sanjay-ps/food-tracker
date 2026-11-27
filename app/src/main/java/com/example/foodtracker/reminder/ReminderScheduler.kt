package com.example.foodtracker.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun apply(config: ReminderConfig) {
        if (config.enabled) schedule(config) else cancel(config.kind)
    }

    fun cancel(kind: ReminderKind) {
        val pi = pendingIntent(kind, mutable = false, create = false)
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /** Wipes all alarms then re-schedules every enabled config. */
    fun rescheduleAll(configs: List<ReminderConfig>) {
        ReminderKind.entries.forEach { cancel(it) }
        configs.filter { it.enabled }.forEach { schedule(it) }
    }

    /**
     * Re-arm the next firing for [config].
     *
     * `setAndAllowWhileIdle` is the documented Doze-respecting primitive for
     * "should fire close to a specific time, even when the device is idle":
     * the system batches it into the next Doze maintenance window rather than
     * deferring it indefinitely (which is what `setInexactRepeating` does).
     * That gives reminders the best reliability profile available without any
     * special permissions or Play-policy-restricted alarm-clock APIs. The
     * one-shot semantics mean we self-rearm in the receiver after each fire.
     *
     * Reliability hint: on aggressive OEM battery savers the user may still
     * need to mark the app exempt from battery optimisation — surfaced in the
     * Reminders screen via `BatteryOptHelper`.
     */
    @SuppressLint("MissingPermission")
    private fun schedule(config: ReminderConfig) {
        val triggerAt = nextTriggerMillis(config.hour, config.minute)
        val pi = pendingIntent(config.kind, mutable = false, create = true)!!
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun pendingIntent(kind: ReminderKind, mutable: Boolean, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_KIND, kind.name)
        }
        val flags = (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, kind.ordinal, intent, flags)
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // The re-arm runs from inside the fired alarm's own broadcast, so
            // "now" sits a hair either side of the trigger time. Rolling over
            // only on `<= now` lets an alarm delivered marginally early re-arm
            // for seconds later the same day and notify twice, so require the
            // next firing to be at least a minute out. The side effect - a
            // reminder set for less than a minute from now waits until
            // tomorrow - is the right call for a daily reminder anyway.
            if (timeInMillis <= now.timeInMillis + REARM_MARGIN_MS) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    companion object {
        const val CHANNEL_ID = "food_tracker_reminders"

        /** Minimum gap between "now" and the next firing. See [nextTriggerMillis]. */
        private const val REARM_MARGIN_MS = 60_000L

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Logging reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminds you to log meals, water, and weigh-ins."
            }
            manager.createNotificationChannel(channel)
        }
    }
}
