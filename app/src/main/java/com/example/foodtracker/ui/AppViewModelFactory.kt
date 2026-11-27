package com.example.foodtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.foodtracker.FoodTrackerApp
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.backup.BackupManager
import com.example.foodtracker.data.backup.BackupScheduler
import com.example.foodtracker.reminder.ReminderScheduler
import com.example.foodtracker.reminder.ReminderStore

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

data class AppContainer(
    val repository: FoodRepository,
    val profileStore: ProfileStore,
    val reminderStore: ReminderStore,
    val reminderScheduler: ReminderScheduler,
    val backupManager: BackupManager,
    val backupScheduler: BackupScheduler,
) {
    companion object {
        fun from(app: FoodTrackerApp) = AppContainer(
            repository = app.repository,
            profileStore = app.profileStore,
            reminderStore = app.reminderStore,
            reminderScheduler = app.reminderScheduler,
            backupManager = app.backupManager,
            backupScheduler = app.backupScheduler,
        )
    }
}

/**
 * Compose-friendly helper that materialises a ViewModel using the current
 * AppContainer from CompositionLocal. Pass a builder that turns the container
 * into a ViewModel instance.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    val factory = viewModelFactory {
        initializer { create(container) }
    }
    return viewModel(factory = factory)
}
