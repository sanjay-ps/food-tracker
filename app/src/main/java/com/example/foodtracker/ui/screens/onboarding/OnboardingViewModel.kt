package com.example.foodtracker.ui.screens.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.ActivityLevel
import com.example.foodtracker.data.AiModel
import com.example.foodtracker.data.AiProvider
import com.example.foodtracker.data.Goal
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.Sex
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.data.WeightChangeRate
import com.example.foodtracker.data.backup.BackupManager
import com.example.foodtracker.data.backup.BackupScheduler
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.reminder.ReminderConfig
import com.example.foodtracker.reminder.ReminderKind
import com.example.foodtracker.reminder.ReminderScheduler
import com.example.foodtracker.reminder.ReminderStore
import com.example.foodtracker.ui.screens.you.BackupCadence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val profile: UserProfile = UserProfile(),
    val previewGoals: NutrientGoals = GoalCalculator.goalsFor(UserProfile()),
    /** Tree URI the user picked; null means keep the Downloads/FoodTracker default. */
    val backupFolderUri: Uri? = null,
    val backupFolderLabel: String? = null,
    val backupCadence: BackupCadence = BackupCadence.Off,
    val reminders: List<ReminderConfig> = ReminderKind.entries.map { ReminderConfig.default(it) },
    val aiEnabled: Boolean = false,
    val aiProvider: AiProvider = AiProvider.Gemini,
    val aiApiKey: String = "",
    val aiModel: AiModel = AiProvider.Gemini.defaultModel,
    val saved: Boolean = false,
)

class OnboardingViewModel(
    private val profileStore: ProfileStore,
    private val backupManager: BackupManager,
    private val backupScheduler: BackupScheduler,
    private val reminderStore: ReminderStore,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private fun mutate(block: (UserProfile) -> UserProfile) {
        _state.update {
            val updated = block(it.profile)
            it.copy(profile = updated, previewGoals = GoalCalculator.goalsFor(updated))
        }
    }

    fun setName(name: String) = mutate { it.copy(name = name) }
    fun setAge(age: Int) = mutate { it.copy(ageYears = age.coerceIn(10, 100)) }
    fun setSex(sex: Sex) = mutate { it.copy(sex = sex) }
    fun setHeight(cm: Int) = mutate { it.copy(heightCm = cm.coerceIn(100, 230)) }
    fun setWeight(kg: Double) = mutate { it.copy(weightKg = kg.coerceIn(30.0, 250.0)) }
    fun setActivityLevel(level: ActivityLevel) = mutate { it.copy(activityLevel = level) }
    fun setGoal(goal: Goal) = mutate { p ->
        // Clear the goal weight when switching to Maintain so we don't carry
        // a stale target around; default it to a sensible nudge when entering
        // Lose/Gain for the first time.
        val nextGoalWeight = when (goal) {
            Goal.Maintain -> null
            Goal.Lose -> p.goalWeightKg ?: (p.weightKg - 5.0).coerceAtLeast(30.0)
            Goal.Gain -> p.goalWeightKg ?: (p.weightKg + 5.0).coerceAtMost(250.0)
        }
        p.copy(goal = goal, goalWeightKg = nextGoalWeight)
    }
    fun setWeightChangeRate(rate: WeightChangeRate) = mutate { it.copy(weightChangeRate = rate) }
    fun setGoalWeight(kg: Double) = mutate { it.copy(goalWeightKg = kg.coerceIn(30.0, 250.0)) }

    // ─── Backup page ──────────────────────────────────────────────────────

    /** Caller invoked the SAF folder picker; persist the choice on finish. */
    fun setBackupFolder(uri: Uri, label: String?) {
        _state.update { it.copy(backupFolderUri = uri, backupFolderLabel = label) }
    }

    fun clearBackupFolder() {
        _state.update { it.copy(backupFolderUri = null, backupFolderLabel = null) }
    }

    fun setBackupCadence(cadence: BackupCadence) {
        _state.update { it.copy(backupCadence = cadence) }
    }

    // ─── Reminders page ───────────────────────────────────────────────────

    fun setReminderEnabled(kind: ReminderKind, enabled: Boolean) {
        _state.update { st ->
            st.copy(reminders = st.reminders.map { if (it.kind == kind) it.copy(enabled = enabled) else it })
        }
    }

    fun setReminderTime(kind: ReminderKind, hour: Int, minute: Int) {
        _state.update { st ->
            st.copy(reminders = st.reminders.map {
                if (it.kind == kind) it.copy(hour = hour, minute = minute) else it
            })
        }
    }

    // ─── AI autofill page ─────────────────────────────────────────────────

    fun setAiEnabled(enabled: Boolean) = _state.update { it.copy(aiEnabled = enabled) }

    /** Switching provider resets the model — model ids are provider-specific. */
    fun setAiProvider(provider: AiProvider) = _state.update {
        it.copy(aiProvider = provider, aiModel = provider.defaultModel)
    }

    fun setAiApiKey(key: String) = _state.update { it.copy(aiApiKey = key) }

    fun setAiModel(model: AiModel) = _state.update { it.copy(aiModel = model) }

    /**
     * Persists every page's state in one shot. Reminders and backup settings are
     * written FIRST, then the profile (with markOnboarded = true) is written last.
     *
     * Why the order matters: writing `onboarded = true` flips AppNav's NavHost from
     * the onboarding graph to the main graph, which destroys this ViewModel and
     * cancels [viewModelScope]. Any DataStore writes still in-flight at that point
     * are dropped — that's why an earlier version of this method lost the user's
     * reminder choices.
     */
    fun finish() {
        viewModelScope.launch {
            val st = _state.value

            st.backupFolderUri?.let { uri ->
                runCatching { backupManager.setCustomFolder(uri) }
            }
            runCatching {
                profileStore.setBackupIntervalHours(st.backupCadence.hours)
                backupScheduler.apply(st.backupCadence.hours)
            }

            runCatching {
                st.reminders.forEach { config ->
                    reminderStore.update(config.kind) { it.copy(
                        enabled = config.enabled,
                        hour = config.hour,
                        minute = config.minute,
                    ) }
                }
                reminderScheduler.rescheduleAll(reminderStore.snapshot())
            }

            // Only written when the user actually switched it on, so skipping
            // the page leaves the ai_prefs store untouched and no key on disk.
            if (st.aiEnabled) {
                runCatching {
                    profileStore.setAiEnabled(true)
                    profileStore.setAiProvider(st.aiProvider)
                    profileStore.setAiModel(st.aiModel.id)
                    profileStore.setAiApiKey(st.aiApiKey)
                }
            }

            profileStore.save(st.profile, markOnboarded = true)
            _state.update { it.copy(saved = true) }
        }
    }
}
