package com.example.foodtracker.reminder

import androidx.compose.runtime.Immutable

/**
 * Identifies a single reminder slot. The ordinal also serves as the alarm request code
 * and notification id, so do not reorder existing entries without thinking about
 * already-scheduled alarms on user devices.
 */
enum class ReminderKind(val title: String, val body: String, val defaultHour: Int, val defaultMinute: Int) {
    Breakfast("Breakfast", "Log your breakfast so we keep your day on track.", 8, 30),
    Lunch("Lunch", "Don't forget to log lunch — your nutrients depend on it.", 13, 0),
    Snack("Snack", "Anything you've eaten in between meals? Log it now.", 17, 0),
    Dinner("Dinner", "Log dinner before the day closes out.", 20, 30),
    Water("Drink water", "Stay hydrated — log a glass if you've had one.", 11, 0),
    WeighIn("Weigh-in", "A quick weigh-in keeps your goals calibrated.", 7, 0),
}

@Immutable
data class ReminderConfig(
    val kind: ReminderKind,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
) {
    fun timeLabel(): String {
        val h12 = ((hour + 11) % 12) + 1
        val am = hour < 12
        val mm = if (minute < 10) "0$minute" else "$minute"
        return "$h12:$mm ${if (am) "AM" else "PM"}"
    }

    companion object {
        fun default(kind: ReminderKind) = ReminderConfig(kind, enabled = false, hour = kind.defaultHour, minute = kind.defaultMinute)
    }
}
