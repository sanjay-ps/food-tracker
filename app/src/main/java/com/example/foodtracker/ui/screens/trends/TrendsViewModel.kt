package com.example.foodtracker.ui.screens.trends

import com.example.foodtracker.domain.MealTiming
import com.example.foodtracker.domain.planWeightPath
import com.example.foodtracker.domain.paceKgPerWeek
import com.example.foodtracker.domain.goalReachDay
import com.example.foodtracker.data.Goal
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.data.AllNutrientFields
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.NutrientStatus
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.ProfileStore
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.data.db.DailyWaterTotal
import com.example.foodtracker.data.db.FoodLogWithFood
import com.example.foodtracker.data.db.WeightLogEntity
import com.example.foodtracker.data.db.nutrients
import com.example.foodtracker.data.onTrack
import com.example.foodtracker.data.statusFor
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.ui.components.DailyValue
import com.example.foodtracker.ui.components.LinePoint
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The window every card on Trends summarises.
 *
 * Four stops, labelled the way the control shows them (`7d`, not `Week`): the
 * segmented control is four cells wide on a 412dp screen, and "3 months" does
 * not fit in one. The old set went 7/30/90/180; a year replaces six months
 * because the weight card is the one people scrub back through, and a year is
 * the window a weight trend actually means something over.
 */
enum class TrendsRange(val label: String, val days: Int) {
    Week("7d", 7),
    Month("30d", 30),
    Quarter("90d", 90),
    Year("1y", 365),
}

/**
 * One column of the macros chart: a day, or — over longer ranges — the daily
 * average of a bucket of days. See [bucketSizeFor].
 */
data class MacroBar(
    val label: String,
    val carbs: Double,
    val protein: Double,
    val fat: Double,
) {
    val total: Double get() = carbs + protein + fat
}

/**
 * One row of the nutrient heatmap: a nutrient, and how each day went for it.
 *
 * [missedDays] counts only days that were actually logged — see
 * [TrendsUiState.heatmapLoggedDays] for why that distinction matters.
 */
data class NutrientTrendRow(
    val field: NutrientField,
    val statuses: List<NutrientStatus>,
    val missedDays: Int,
)

data class TrendsUiState(
    val range: TrendsRange = TrendsRange.Week,

    val calories: List<LinePoint> = emptyList(),
    val calorieAvg: Int = 0,
    val calorieGoal: Double = 0.0,

    val macroBars: List<MacroBar> = emptyList(),
    val macroAvgCarbs: Double = 0.0,
    val macroAvgProtein: Double = 0.0,
    val macroAvgFat: Double = 0.0,
    val proteinMetDays: Int = 0,
    val loggedDays: Int = 0,

    val heatmap: List<NutrientTrendRow> = emptyList(),
    val heatmapDays: Int = 0,
    val heatmapLoggedDays: Int = 0,
    val heatmapFirstLabel: String = "",
    val heatmapLastLabel: String = "",

    val weight: List<LinePoint> = emptyList(),
    val weightEntries: List<WeightLogEntity> = emptyList(),
    val weightChange: Double? = null,
    val latestWeightKg: Double? = null,
    val goalWeightKg: Double? = null,

    val water: List<LinePoint> = emptyList(),
    val waterBars: List<DailyValue> = emptyList(),
    val waterAvgMl: Int = 0,
    val waterGoalMl: Int = 0,

    // ---- Plan vs actual (time-based, so it can run past today) ----------
    val rangeStart: Long = 0,
    val today: Long = 0,
    /** How far the plan line runs past today. */
    val forecastEnd: Long = 0,
    /** Calories on each logged day — days with nothing logged are left out, not zeroed. */
    val calorieSeries: List<Pair<Long, Double>> = emptyList(),
    val weightSeries: List<Pair<Long, Double>> = emptyList(),
    /** The planned weight path from the goal and pace set in onboarding/profile. */
    val weightPlan: List<Pair<Long, Double>> = emptyList(),
    val goalReachDay: Long? = null,
    /** Planned kg/week, negative to lose. */
    val planRateKgPerWeek: Double = 0.0,
    /** kg/week your logged average implies against your burn; null until a day is logged. */
    val paceKgPerWeek: Double? = null,

    // ---- Meal timing -----------------------------------------------------
    /** Each logged day's meal times, as eating-day minutes (after midnight sorts last). */
    val timing: List<Pair<Long, List<Int>>> = emptyList(),
) {
    val timingDays: Int get() = timing.size
    /** Days whose last meal was late (see MealTiming.LATE_FROM). */
    val lateDays: Int get() = timing.count { (_, m) -> m.last() >= LATE_MINUTE }
    val avgFirstMinute: Int? get() = timing.takeIf { it.isNotEmpty() }?.map { it.second.first() }?.average()?.roundToInt()
    val avgLastMinute: Int? get() = timing.takeIf { it.isNotEmpty() }?.map { it.second.last() }?.average()?.roundToInt()
    /** Average hours between the first and last meal of a day. */
    val avgWindowHours: Double? get() = timing.takeIf { it.isNotEmpty() }
        ?.map { (it.second.last() - it.second.first()) / 60.0 }?.average()
    /** Nutrients missed on more than half of the logged days — the card's insight. */
    val chronicallyMissed: Int
        get() = heatmap.count { heatmapLoggedDays > 0 && it.missedDays * 2 > heatmapLoggedDays }
}

/**
 * Trends. Was `ProgressViewModel`.
 *
 * It gained two things in the rename. The **macro bars**, which fold the day's
 * log into carbs/protein/fat so the chart can stack them instead of running
 * four separate sparklines; and the **nutrient heatmap**, which is the only
 * genuinely new analysis in the plan and the one that justifies tracking thirty
 * micronutrients at all — a single day's B12 is noise, a month of it is a
 * finding.
 *
 * Both are folded from [FoodRepository.observeLogForRange], which already
 * existed: the whole range comes back as rows, and a day's [Nutrients] is the
 * sum of its rows' [nutrients]. No new query, no new table.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(
    private val repository: FoodRepository,
    private val profileStore: ProfileStore,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TrendsRange.Week)

    fun setRange(range: TrendsRange) {
        selectedRange.value = range
    }

    /** The weight card's history is editable in place, so it needs this one write. */
    fun deleteWeight(id: Long) {
        viewModelScope.launch { repository.deleteWeight(id) }
    }

    val state: StateFlow<TrendsUiState> = selectedRange
        .flatMapLatest { range ->
            val today = DateUtil.todayEpochDay()
            val start = today - range.days + 1
            combine(
                repository.observeLogForRange(start, today),
                repository.observeWeightsForRange(start, today),
                repository.observeWaterTotalsForRange(start, today),
                profileStore.profile,
            ) { logs, weights, water, profile ->
                buildState(range, start, today, logs, weights, water, profile)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState())

    private fun buildState(
        range: TrendsRange,
        start: Long,
        today: Long,
        logs: List<FoodLogWithFood>,
        weights: List<WeightLogEntity>,
        water: List<DailyWaterTotal>,
        profile: UserProfile,
    ): TrendsUiState {
        val days = (start..today).toList()
        // For a week the weekday reads best; longer windows need a date, or
        // repeated weekday names collapse into each other.
        val labelFor: (Long) -> String =
            if (range == TrendsRange.Week) DateUtil::shortDay else DateUtil::shortDate
        val goals = GoalCalculator.goalsFor(profile)

        // One fold, four cards. Summing a day's rows into a single [Nutrients]
        // once is what lets the calorie line, the macro stack and the heatmap
        // all come off the same pass instead of three.
        val byDay: Map<Long, Nutrients> = logs
            .groupBy { it.log.dateEpochDay }
            .mapValues { (_, rows) -> rows.fold(Nutrients.Zero) { acc, row -> acc + row.nutrients() } }

        val loggedDays = days.count { (byDay[it]?.calories ?: 0.0) > 0.0 }

        // ---- Calories --------------------------------------------------
        val calories = days.map { LinePoint(labelFor(it), byDay[it]?.calories ?: 0.0) }
        val calorieAvg = if (loggedDays > 0) {
            (days.sumOf { byDay[it]?.calories ?: 0.0 } / loggedDays).roundToInt()
        } else 0

        // ---- Macros ----------------------------------------------------
        val macroBars = bucketMacros(days, byDay, labelFor, bucketSizeFor(range))
        val proteinField = AllNutrientFields.first { it.key == "protein" }
        val proteinMetDays = days.count { day ->
            val n = byDay[day] ?: return@count false
            n.calories > 0.0 && proteinField.statusFor(n.protein, goals.protein).onTrack
        }

        // ---- Nutrient consistency --------------------------------------
        val heatmapDays = days.takeLast(HEATMAP_MAX_COLUMNS)
        val heatmap = buildHeatmap(heatmapDays, byDay, goals)

        // ---- Weight ----------------------------------------------------
        // Weight is logged sparsely, not daily — plot the weigh-ins themselves
        // so the line is measurements rather than zero-filled gaps.
        val weightSorted = weights.sortedBy { it.dateEpochDay }
        val weightPts = weightSorted.map { LinePoint(labelFor(it.dateEpochDay), it.weightKg) }
        val weightChange = if (weightSorted.size >= 2) {
            weightSorted.last().weightKg - weightSorted.first().weightKg
        } else null

        // ---- Water -----------------------------------------------------
        val waterByDay = water.associate { it.dateEpochDay to it.totalMl }
        val waterPts = days.map { LinePoint(labelFor(it), (waterByDay[it] ?: 0).toDouble()) }
        val waterAvgMl = if (days.isNotEmpty()) {
            (days.sumOf { (waterByDay[it] ?: 0).toDouble() } / days.size).roundToInt()
        } else 0
        // The 7-day card draws bars, so it needs the last seven days whatever
        // the selected range is — see TrendsScreen's water card.
        val waterBars = days.takeLast(7).map {
            DailyValue(DateUtil.shortDay(it), (waterByDay[it] ?: 0).toDouble())
        }

        // ---- Plan vs actual ----------------------------------------------
        // The plan exists from the first day of onboarding, so these cards
        // never wait for data: they draw the plan and add the days you log.
        val forecastEnd = today + maxOf(7L, range.days / 3L)
        val signedRate = when (profile.goal) {
            Goal.Lose -> -profile.weightChangeRate.kgPerWeek
            Goal.Gain -> profile.weightChangeRate.kgPerWeek
            Goal.Maintain -> 0.0
        }
        val anchor = weightSorted.firstOrNull()?.let { it.dateEpochDay to it.weightKg }
            ?: (today to profile.weightKg)
        val goalKg = profile.goalWeightKg ?: profile.weightKg.takeIf { profile.goal == Goal.Maintain }
        val weightPlan = planWeightPath(anchor.first, anchor.second, signedRate, goalKg, start, forecastEnd)
        val pace = if (loggedDays > 0) {
            paceKgPerWeek(days.sumOf { byDay[it]?.calories ?: 0.0 } / loggedDays, GoalCalculator.tdee(profile))
        } else null

        // ---- Meal timing -------------------------------------------------
        val zone = DateUtil.zoneId()
        val timing = logs.groupBy { it.log.dateEpochDay }.toSortedMap().map { (day, rows) ->
            day to rows.map { MealTiming.eatingMinute(MealTiming.timeOf(it.log.loggedAtMillis, zone)) }.sorted()
        }

        return TrendsUiState(
            timing = timing,
            rangeStart = start,
            today = today,
            forecastEnd = forecastEnd,
            calorieSeries = days.mapNotNull { d -> byDay[d]?.calories?.takeIf { it > 0.0 }?.let { d to it } },
            weightSeries = weightSorted.map { it.dateEpochDay to it.weightKg },
            weightPlan = weightPlan,
            goalReachDay = goalReachDay(anchor.first, anchor.second, signedRate, goalKg),
            planRateKgPerWeek = signedRate,
            paceKgPerWeek = pace,
            range = range,
            calories = calories,
            calorieAvg = calorieAvg,
            calorieGoal = goals.calories,
            macroBars = macroBars,
            macroAvgCarbs = macroBars.averageOf { it.carbs },
            macroAvgProtein = macroBars.averageOf { it.protein },
            macroAvgFat = macroBars.averageOf { it.fat },
            proteinMetDays = proteinMetDays,
            loggedDays = loggedDays,
            heatmap = heatmap,
            heatmapDays = heatmapDays.size,
            heatmapLoggedDays = heatmapDays.count { (byDay[it]?.calories ?: 0.0) > 0.0 },
            heatmapFirstLabel = heatmapDays.firstOrNull()?.let(DateUtil::shortDate).orEmpty(),
            heatmapLastLabel = heatmapDays.lastOrNull()?.let(DateUtil::shortDate).orEmpty(),
            weight = weightPts,
            weightEntries = weightSorted.reversed(),
            weightChange = weightChange,
            latestWeightKg = weightSorted.lastOrNull()?.weightKg,
            goalWeightKg = profile.goalWeightKg,
            water = waterPts,
            waterBars = waterBars,
            waterAvgMl = waterAvgMl,
            waterGoalMl = goals.waterMl,
        )
    }

    /**
     * Averages over bars that carry data, not over every bar.
     *
     * A fortnight away from the app should not read as "you averaged 90 g of
     * carbs"; it should read as what you ate on the days you ate.
     */
    private fun List<MacroBar>.averageOf(pick: (MacroBar) -> Double): Double {
        val withData = filter { it.total > 0.0 }
        return if (withData.isEmpty()) 0.0 else withData.sumOf(pick) / withData.size
    }

    private companion object {
        /**
         * The widest the heatmap is allowed to get.
         *
         * A cell has to stay a finger wide enough to tap and a hairline apart
         * from its neighbour; past about thirty columns on a 412dp screen it is
         * neither, and the grid stops saying anything. Longer ranges therefore
         * show the most recent thirty days and the card says so — a heatmap
         * that quietly meant "last month" while the control said "1y" would be
         * worse than one that admits its window.
         */
        const val HEATMAP_MAX_COLUMNS = 30

        /** Keep the macro chart under this many bars, whatever the range. */
        const val MACRO_MAX_BARS = 30

        /**
         * Days per column of the macro chart.
         *
         * 7d and 30d are a bar per day. 90d and 1y are not: ninety bars on a
         * 348dp card is under 4dp each, which is a texture, not a chart. Those
         * ranges average into buckets instead, so a bar stays wide enough to
         * read and the stack still shows the *composition* of a typical day —
         * which is the only thing the macros card is for.
         */
        fun bucketSizeFor(range: TrendsRange): Int =
            ((range.days + MACRO_MAX_BARS - 1) / MACRO_MAX_BARS).coerceAtLeast(1)

        fun bucketMacros(
            days: List<Long>,
            byDay: Map<Long, Nutrients>,
            labelFor: (Long) -> String,
            bucketSize: Int,
        ): List<MacroBar> = days.chunked(bucketSize).map { bucket ->
            // Per-day average inside the bucket, so a short trailing bucket is
            // the same height as a full one rather than a cliff at the edge.
            val n = bucket.size.toDouble()
            MacroBar(
                label = labelFor(bucket.last()),
                carbs = bucket.sumOf { byDay[it]?.carbs ?: 0.0 } / n,
                protein = bucket.sumOf { byDay[it]?.protein ?: 0.0 } / n,
                fat = bucket.sumOf { byDay[it]?.fat ?: 0.0 } / n,
            )
        }

        /**
         * Every nutrient but calories, scored day by day and sorted worst-first.
         *
         * Calories are left out because they have their own card above, and a
         * row that duplicates it would push a real finding below the fold.
         *
         * A day with nothing logged scores [NutrientStatus.Unmet], which draws
         * in the same `outlineVariant` an empty cell would — so a gap in the
         * log *looks* like a gap. It is excluded from [NutrientTrendRow.missedDays]
         * all the same: a day you did not log is missing data, not a missed
         * nutrient, and counting it would accuse you of a shortfall on a day
         * the app knows nothing about.
         */
        fun buildHeatmap(
            days: List<Long>,
            byDay: Map<Long, Nutrients>,
            goals: NutrientGoals,
        ): List<NutrientTrendRow> = AllNutrientFields
            // Untargeted fields have no status to plot and no miss to count, so
            // a row for one would be 30 identical cells above a hard-coded zero.
            .filter { it.key != "calories" && it.scored }
            .map { field ->
                val goal = field.goal(goals)
                var missed = 0
                val statuses = days.map { day ->
                    val n = byDay[day]
                    val status = field.statusFor(n?.let(field.read) ?: 0.0, goal)
                    val logged = (n?.calories ?: 0.0) > 0.0
                    if (logged && !status.onTrack) missed++
                    status
                }
                NutrientTrendRow(field, statuses, missed)
            }
            // Worst-first: the point of the card is the nutrient you are
            // chronically short of, and it should never need scrolling to.
            .sortedByDescending { it.missedDays }
    }
}

/** MealTiming.LATE_FROM as an eating-day minute. */
private val LATE_MINUTE = MealTiming.eatingMinute(MealTiming.LATE_FROM)
