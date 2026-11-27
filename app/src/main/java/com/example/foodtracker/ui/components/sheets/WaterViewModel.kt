package com.example.foodtracker.ui.components.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.data.db.WaterLogEntity
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WaterUiState(
    val entries: List<WaterLogEntity> = emptyList(),
    val totalMl: Int = 0,
    val goals: NutrientGoals = GoalCalculator.goalsFor(UserProfile()),
)

/**
 * State behind the water sheet.
 *
 * Unchanged in substance from the `WaterScreen` it was written for, with one
 * subtraction: the seven-day totals are gone. That series was the screen's
 * week chart, and the week chart now lives on Trends' water card, which reads
 * it from [com.example.foodtracker.ui.screens.trends.TrendsViewModel] over the
 * selected range. Keeping it here would have meant a second Room flow, running
 * for as long as the sheet is open, feeding a chart that is no longer on it.
 *
 * It sits in `components/sheets/` rather than under `screens/` because what it
 * backs is no longer a screen: water is a modal reachable from Today's tile and
 * from Trends' water card, and a ViewModel in a `screens/water` package would
 * be the only thing left implying a route that no longer exists.
 */
class WaterViewModel(
    private val repository: FoodRepository,
    profileStore: ProfileStore,
) : ViewModel() {

    private val today = DateUtil.todayEpochDay()

    val state: StateFlow<WaterUiState> = combine(
        repository.observeWaterForDay(today),
        repository.observeWaterTotalForDay(today),
        profileStore.profile.map { GoalCalculator.goalsFor(it) },
    ) { entries, total, goals ->
        WaterUiState(entries, total, goals)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterUiState())

    fun addWater(ml: Int) {
        viewModelScope.launch {
            repository.addWater(
                WaterLogEntity(
                    dateEpochDay = today,
                    amountMl = ml,
                    loggedAtMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    fun removeWater(id: Long) {
        viewModelScope.launch { repository.deleteWater(id) }
    }
}
