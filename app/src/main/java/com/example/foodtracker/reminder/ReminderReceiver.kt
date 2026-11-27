package com.example.foodtracker.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.foodtracker.DeepLink
import com.example.foodtracker.FoodTrackerApp
import com.example.foodtracker.R
import com.example.foodtracker.data.MealType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> handleFire(context, intent)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> rearmAlarms(context)
        }
    }

    private fun rearmAlarms(context: Context) {
        // BroadcastReceiver onReceive runs on the main thread with a short timeout;
        // goAsync() keeps the process alive until we explicitly finish().
        val app = context.applicationContext as? FoodTrackerApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configs = app.reminderStore.snapshot()
                app.reminderScheduler.rescheduleAll(configs)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleFire(context: Context, intent: Intent) {
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: return
        val kind = runCatching { ReminderKind.valueOf(kindName) }.getOrNull() ?: return

        ReminderScheduler.ensureChannel(context)

        // Post the notification only if we have permission. We still re-arm for
        // tomorrow regardless — the user may grant the permission later, and we
        // don't want a permission denial to silently kill the daily schedule.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val launchIntent = launchIntentFor(context, kind)
            val contentPI = PendingIntent.getActivity(
                context,
                kind.ordinal,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                // The shade tints the small icon and the app name with this.
                // Left unset, the system substitutes its own default accent —
                // the one place the app was still showing a colour that is not
                // in its palette.
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentTitle(kind.title)
                .setContentText(kind.body)
                .setContentIntent(contentPI)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(context).notify(kind.ordinal, notification)
        }

        // Re-arm for tomorrow. We schedule one-shot alarms (setAndAllowWhileIdle)
        // so they survive Doze; the trade-off is we must reschedule after each
        // fire, otherwise the reminder fires once and never again.
        val app = context.applicationContext as? FoodTrackerApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = app.reminderStore.snapshot().firstOrNull { it.kind == kind }
                if (config != null && config.enabled) {
                    app.reminderScheduler.apply(config)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Build the deep-link intent for a tapped reminder notification.
     *
     * Meal reminders open LogFoodScreen with the meal preselected; Water and
     * Weigh-in reminders jump straight to their respective screens so the user
     * lands on the page they need to act on.
     */
    private fun launchIntentFor(context: Context, kind: ReminderKind): Intent = when (kind) {
        ReminderKind.Breakfast -> DeepLink.logMealIntent(context, MealType.Breakfast.name)
        ReminderKind.Lunch -> DeepLink.logMealIntent(context, MealType.Lunch.name)
        ReminderKind.Snack -> DeepLink.logMealIntent(context, MealType.Snack.name)
        ReminderKind.Dinner -> DeepLink.logMealIntent(context, MealType.Dinner.name)
        ReminderKind.Water -> DeepLink.openWaterIntent(context)
        ReminderKind.WeighIn -> DeepLink.openWeightIntent(context)
    }

    companion object {
        const val ACTION_FIRE = "com.example.foodtracker.action.REMINDER_FIRE"
        const val EXTRA_KIND = "kind"
    }
}
