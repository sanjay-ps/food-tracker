package com.example.foodtracker.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.reminder.ReminderConfig
import com.example.foodtracker.reminder.ReminderKind
import com.example.foodtracker.reminder.ReminderScheduler
import com.example.foodtracker.reminder.ReminderStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val configs: StateFlow<List<ReminderConfig>> = store.configs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun setEnabled(kind: ReminderKind, enabled: Boolean) {
        viewModelScope.launch {
            store.update(kind) { it.copy(enabled = enabled) }
            // Use the freshly-saved snapshot, not the staled in-memory copy, so
            // schedule() sees the new state.
            val updated = store.snapshot().first { it.kind == kind }
            scheduler.apply(updated)
        }
    }

    fun setTime(kind: ReminderKind, hour: Int, minute: Int) {
        viewModelScope.launch {
            store.update(kind) { it.copy(hour = hour, minute = minute) }
            val updated = store.snapshot().first { it.kind == kind }
            if (updated.enabled) scheduler.apply(updated)
        }
    }
}
