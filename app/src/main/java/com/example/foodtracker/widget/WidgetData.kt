package com.example.foodtracker.widget

import android.content.Context
import android.util.Log
import com.example.foodtracker.BuildConfig
import com.example.foodtracker.FoodTrackerApp
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.db.nutrients
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

/**
 * Live data feed for the home-screen widgets.
 *
 * Refresh model (important — we got this wrong before):
 *
 * `update()` / `updateAll()` re-run `provideGlance` ONLY when no Glance session
 * is currently active for that widget. While a session IS alive, those calls
 * downgrade to "recompose the existing composition" and never re-enter the
 * `provideGlance` suspend body. A long-lived `collect` inside `provideContent`
 * (e.g. observing a Room flow via `produceState`) keeps the session alive
 * forever — which is exactly what made `updateAll` look like a no-op, and froze
 * the `today` we captured at session start (so the widget got stuck on the
 * previous day until a resize tore the session down).
 *
 * So widgets now read a ONE-SHOT [snapshot] inside `provideGlance` and return —
 * no long-lived collect, no lingering session. Refreshes are pushed explicitly
 * via [WidgetUpdater.updateAll] from: every food-log / activity write, app
 * foreground, and a daily worker for the midnight date rollover. Each push
 * starts a fresh session, so `provideGlance` re-runs and recomputes `today`.
 */
data class WidgetSnapshot(
    val onboarded: Boolean,
    val profile: UserProfile,
    val goals: NutrientGoals,
    val consumed: Nutrients,
) {
    val caloriesConsumed: Double get() = consumed.calories
    val caloriesLeft: Double get() = goals.calories - caloriesConsumed
}

object WidgetData {
    /**
     * One-shot read of the current [WidgetSnapshot]. Call this inside
     * `provideGlance` — `today` is computed fresh on every call, so each session
     * (started by [WidgetUpdater.updateAll]) renders the correct day.
     */
    suspend fun snapshot(context: Context): WidgetSnapshot =
        snapshotFlow(context).first()

    /**
     * Backing [Flow] that combines the underlying sources into a [WidgetSnapshot].
     * Used only via [snapshot]'s `.first()` for a one-shot read — we deliberately
     * do NOT collect this long-term inside a widget (see the file header).
     */
    fun snapshotFlow(context: Context): Flow<WidgetSnapshot> {
        val app = context.applicationContext as FoodTrackerApp
        val today = DateUtil.todayEpochDay()
        return combine(
            app.profileStore.isOnboarded,
            app.profileStore.profile,
            app.repository.observeLogForDay(today),
        ) { onboarded, profile, logs ->
            val consumed = logs.fold(Nutrients.Zero) { acc, row -> acc + row.nutrients() }
            // Debug-only: this line carries the user's intake, and there is no
            // reason for it to reach logcat on a shipped build.
            if (BuildConfig.DEBUG) Log.d(
                "WidgetData",
                "flow emit today=$today onboarded=$onboarded logs=${logs.size} " +
                    "consumed.kcal=${consumed.calories.toInt()}",
            )
            WidgetSnapshot(
                onboarded = onboarded,
                profile = profile,
                goals = GoalCalculator.goalsFor(profile),
                consumed = consumed,
            )
        }.flowOn(Dispatchers.IO)
    }
}
