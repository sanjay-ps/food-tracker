package com.example.foodtracker.ui.screens.you

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.backup.AnalysisRange
import com.example.foodtracker.data.backup.BackupManager
import com.example.foodtracker.data.backup.BackupScheduler
import com.example.foodtracker.data.backup.LocalBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How often the app writes a snapshot by itself.
 *
 * Lives here rather than in the view model that uses it most because it is also
 * the value onboarding offers on its optional page, and a cadence enum buried
 * in a screen's view model is how the old `settings` package ended up being
 * imported by onboarding in the first place.
 */
enum class BackupCadence(val hours: Int, val label: String) {
    Off(0, "Off"),
    Daily(24, "Daily"),
    Weekly(24 * 7, "Weekly");

    companion object {
        fun fromHours(h: Int): BackupCadence =
            entries.firstOrNull { it.hours == h } ?: Off
    }
}

data class DataBackupUiState(
    val backups: List<LocalBackup> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val folderLabel: String? = null,
    val cadence: BackupCadence = BackupCadence.Off,
) {
    /** The snapshot the "Last run" line describes, or null before the first one. */
    val latest: LocalBackup? get() = backups.maxByOrNull { it.createdAtMillis }
}

/**
 * The Data & backup page.
 *
 * This is the backup half of the old `SettingsViewModel`, moved rather than
 * rewritten: every method below is the one that shipped, because the rewrite put
 * [BackupManager], `BackupPayload`, `BackupWorker` and [BackupScheduler]
 * explicitly out of scope. Only the screen around them changed.
 *
 * It keeps the `init` listing that the old class had — and here that is the
 * right place for it, because listing snapshots is the page's whole subject
 * rather than an expensive surprise on a screen about something else.
 */
class DataBackupViewModel(
    private val profileStore: ProfileStore,
    private val backupManager: BackupManager,
    private val backupScheduler: BackupScheduler,
) : ViewModel() {

    private val _backups = MutableStateFlow<List<LocalBackup>>(emptyList())
    private val _busy = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)
    private val _folderLabel = MutableStateFlow<String?>(null)

    init {
        refreshBackups()
        refreshFolderLabel()
    }

    val state: StateFlow<DataBackupUiState> = combine(
        _backups,
        _busy,
        _message,
        _folderLabel,
        profileStore.backupIntervalHours,
    ) { backups, busy, message, label, hours ->
        DataBackupUiState(
            backups = backups,
            busy = busy,
            message = message,
            folderLabel = label,
            cadence = BackupCadence.fromHours(hours),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataBackupUiState())

    fun consumeMessage() = _message.update { null }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _busy.update { true }
            val result = backupManager.exportTo(uri)
            _busy.update { false }
            _message.update {
                result.fold(
                    onSuccess = { "Exported snapshot." },
                    onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
                )
            }
        }
    }

    /** Export just the food logs (compact, foods de-duplicated) for AI analysis. */
    fun exportAnalysisLogs(uri: Uri, range: AnalysisRange) {
        viewModelScope.launch {
            _busy.update { true }
            val result = backupManager.exportAnalysisTo(uri, range)
            _busy.update { false }
            _message.update {
                result.fold(
                    onSuccess = { count -> "Exported $count log entries (${range.label.lowercase()})." },
                    onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
                )
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _busy.update { true }
            val result = backupManager.importFrom(uri)
            _busy.update { false }
            _message.update {
                result.fold(
                    onSuccess = { "Imported snapshot — all data replaced." },
                    onFailure = { "Import failed: ${it.message ?: "unknown error"}" },
                )
            }
            refreshBackups()
        }
    }

    fun saveBackupNow() {
        viewModelScope.launch {
            _busy.update { true }
            val result = backupManager.saveLocalBackup()
            _busy.update { false }
            _message.update {
                result.fold(
                    onSuccess = { "Saved to Downloads/FoodTracker/${it.displayName}" },
                    onFailure = { "Backup failed: ${it.message ?: "unknown error"}" },
                )
            }
            refreshBackups()
        }
    }

    fun clearDiary() {
        viewModelScope.launch {
            _busy.update { true }
            val result = backupManager.clearDiary()
            _busy.update { false }
            _message.update {
                result.fold(
                    onSuccess = { "Diary cleared. Your profile and foods are untouched." },
                    onFailure = { "Couldn't clear the diary: ${it.message ?: "unknown error"}" },
                )
            }
        }
    }

    fun restoreBackup(backup: LocalBackup) {
        viewModelScope.launch {
            _busy.update { true }
            val result = backupManager.restoreFrom(backup)
            _busy.update { false }
            _message.update {
                result.fold(
                    onSuccess = { "Restored ${backup.displayName}." },
                    onFailure = { "Restore failed: ${it.message ?: "unknown error"}" },
                )
            }
        }
    }

    fun deleteBackup(backup: LocalBackup) {
        viewModelScope.launch {
            backupManager.deleteLocalBackup(backup)
            refreshBackups()
        }
    }

    private fun refreshBackups() {
        viewModelScope.launch {
            _backups.update { backupManager.listBackups() }
        }
    }

    private fun refreshFolderLabel() {
        viewModelScope.launch {
            _folderLabel.update { backupManager.currentFolderLabel() }
        }
    }

    /** User picked a folder via the SAF tree picker. */
    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            backupManager.setCustomFolder(uri)
            _message.update { "Backups will now go to your chosen folder." }
            refreshFolderLabel()
            refreshBackups()
        }
    }

    /** Drop the custom folder; revert to Downloads/FoodTracker. */
    fun clearBackupFolder() {
        viewModelScope.launch {
            backupManager.clearCustomFolder()
            _message.update { "Reverted to Downloads/FoodTracker." }
            refreshFolderLabel()
            refreshBackups()
        }
    }

    /** Apply user's recurring-backup choice. WorkManager reflects it immediately. */
    fun setBackupCadence(cadence: BackupCadence) {
        viewModelScope.launch {
            profileStore.setBackupIntervalHours(cadence.hours)
            backupScheduler.apply(cadence.hours)
            _message.update {
                if (cadence == BackupCadence.Off) "Recurring backup turned off."
                else "Recurring backup set to ${cadence.label.lowercase()}."
            }
        }
    }
}
