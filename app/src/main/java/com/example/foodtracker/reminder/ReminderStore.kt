package com.example.foodtracker.reminder

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_prefs")

class ReminderStore(private val context: Context) {

    private fun enabledKey(kind: ReminderKind) = booleanPreferencesKey("${kind.name}_enabled")
    private fun hourKey(kind: ReminderKind) = intPreferencesKey("${kind.name}_hour")
    private fun minuteKey(kind: ReminderKind) = intPreferencesKey("${kind.name}_minute")

    /** All reminder configs, ordered as in the enum so the UI is stable. */
    val configs: Flow<List<ReminderConfig>> = context.reminderDataStore.data.map { prefs ->
        ReminderKind.entries.map { kind ->
            ReminderConfig(
                kind = kind,
                enabled = prefs[enabledKey(kind)] == true,
                hour = prefs[hourKey(kind)] ?: kind.defaultHour,
                minute = prefs[minuteKey(kind)] ?: kind.defaultMinute,
            )
        }
    }

    suspend fun snapshot(): List<ReminderConfig> = configs.first()

    suspend fun update(kind: ReminderKind, transform: (ReminderConfig) -> ReminderConfig) {
        val current = snapshot().first { it.kind == kind }
        val next = transform(current)
        context.reminderDataStore.edit { prefs ->
            prefs[enabledKey(kind)] = next.enabled
            prefs[hourKey(kind)] = next.hour
            prefs[minuteKey(kind)] = next.minute
        }
    }
}
