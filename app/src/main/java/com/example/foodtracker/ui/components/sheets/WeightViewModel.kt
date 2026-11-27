package com.example.foodtracker.ui.components.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.data.db.WeightLogEntity
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** Below this you are not a person, above it you are not standing on a scale. */
const val MIN_WEIGHT_KG = 20.0
const val MAX_WEIGHT_KG = 400.0

/** The ruler's tick, and therefore the precision of everything downstream. */
const val WEIGHT_STEP_KG = 0.1

/**
 * State behind the weigh-in sheet.
 *
 * **The draft is a `Double`, not a `String`.** It used to be the raw text of an
 * `OutlinedTextField(keyboardType = Decimal)`, which meant the ViewModel owned a
 * character filter, a `toDoubleOrNull()` on every read and a state that could
 * be `"7."`. The rewrite replaced the keyboard with a ruler — you weigh within a kilo
 * of last time, so a numeric keypad is the wrong instrument — and a ruler can
 * only ever produce a real number in range. The parsing problem is gone, and
 * with it the filter.
 *
 * [draftKg] is deliberately **nullable in the backing flow and non-null in the
 * state**: until the user drags, there is no draft, and the number the ruler
 * should open on is "what you weighed last time" — which is only known once the
 * log flow emits. Resolving the fallback chain here, per emission, rather than
 * seeding a `MutableStateFlow` in `init`, is what makes the sheet open on the
 * right number the first frame it has data, without a one-shot effect at the
 * call site.
 */
data class WeightUiState(
    val entries: List<WeightLogEntity> = emptyList(),
    val draftKg: Double = UserProfile().weightKg,
    val note: String = "",
    val profile: UserProfile = UserProfile(),
    val syncToProfile: Boolean = true,
) {
    val latest: WeightLogEntity? = entries.maxByOrNull { it.dateEpochDay }

    /** True when [draftKg] is a weight a scale could actually have shown. */
    val canSave: Boolean = draftKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG
}

/**
 * The weigh-in half of what used to be `WeightScreen`.
 *
 * The chart and the history are **not** here any more: they moved to Trends'
 * weight card, so this ViewModel answers one question — what
 * number is being saved, and does it go to the profile too. It still reads the
 * recent log, because the only sane starting point for the ruler is the last
 * weigh-in.
 */
class WeightViewModel(
    private val repository: FoodRepository,
    private val profileStore: ProfileStore,
) : ViewModel() {

    /** Null until the user moves the ruler — see [WeightUiState.draftKg]. */
    private val _draft = MutableStateFlow<Double?>(null)
    private val _note = MutableStateFlow("")
    private val _sync = MutableStateFlow(true)

    val state: StateFlow<WeightUiState> = combine(
        // 30 days is all the sheet needs: it wants the most recent weigh-in to
        // open on, not a history. The history lives in Trends.
        repository.observeRecentWeights(30),
        profileStore.profile,
        _draft,
        _note,
        _sync,
    ) { entries, profile, draft, note, sync ->
        val last = entries.maxByOrNull { it.dateEpochDay }?.weightKg
        WeightUiState(
            entries = entries,
            draftKg = (draft ?: last ?: profile.weightKg)
                .coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG),
            note = note,
            profile = profile,
            syncToProfile = sync,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeightUiState(),
    )

    /**
     * Snapped to [WEIGHT_STEP_KG] on the way in, so the stored number is a tick
     * on the ruler rather than whatever a drag happened to land on. Doing it
     * here and not in the picker means the value the UI draws and the value the
     * database gets are the same number, and a 0.1 kg step never accumulates
     * floating-point drift across a long drag.
     */
    fun setDraftKg(kg: Double) {
        val snapped = (kg / WEIGHT_STEP_KG).roundToLong() * WEIGHT_STEP_KG
        _draft.update { snapped.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG) }
    }

    fun setNote(value: String) = _note.update { value.take(80) }

    fun setSyncToProfile(sync: Boolean) = _sync.update { sync }

    /**
     * Writes today's weigh-in and, when the switch is on, the profile with it.
     *
     * The profile write is the reason the switch exists and the reason it
     * defaults on: every calorie and nutrient target in the app is derived from
     * body weight, so a weigh-in that does not reach the profile leaves the
     * whole app aiming at a number you no longer are.
     */
    fun saveEntry(onSaved: () -> Unit = {}) {
        val kg = _draft.value ?: state.value.draftKg
        if (kg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG) return
        viewModelScope.launch {
            repository.upsertWeight(
                WeightLogEntity(
                    dateEpochDay = DateUtil.todayEpochDay(),
                    weightKg = kg,
                    note = _note.value.ifBlank { null },
                    loggedAtMillis = System.currentTimeMillis(),
                )
            )
            if (_sync.value) {
                val current = profileStore.profile.first()
                profileStore.save(current.copy(weightKg = kg), markOnboarded = false)
            }
            // The draft is cleared, not kept: next time the sheet opens it
            // should read the entry just written, which is now "last time".
            _draft.update { null }
            _note.update { "" }
            onSaved()
        }
    }
}
