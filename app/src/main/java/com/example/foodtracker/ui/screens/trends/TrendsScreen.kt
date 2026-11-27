package com.example.foodtracker.ui.screens.trends

import com.example.foodtracker.ui.screens.trends.components.EatingWindowChart
import com.example.foodtracker.domain.MealTiming
import com.example.foodtracker.ui.components.PlanChart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.foodtracker.ui.components.atoms.LocalCardColor
import com.example.foodtracker.ui.components.atoms.staggerIn
import com.example.foodtracker.ui.navigation.bottomBarClearance
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.meshGradient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.db.WeightLogEntity
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.LineChart
import com.example.foodtracker.ui.components.LinePoint
import com.example.foodtracker.ui.components.WeeklyBarChart
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.components.sheets.kgText
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.screens.trends.components.ChartCard
import com.example.foodtracker.ui.screens.trends.components.ChartInsight
import com.example.foodtracker.ui.screens.trends.components.EmptyChartLine
import com.example.foodtracker.ui.screens.trends.components.InsightTone
import com.example.foodtracker.ui.screens.trends.components.MacroStackedBars
import com.example.foodtracker.ui.screens.trends.components.NutrientHeatmap
import com.example.foodtracker.ui.screens.trends.components.RangeBar
import com.example.foodtracker.ui.screens.trends.components.SectionLink
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.MacroBurned
import com.example.foodtracker.ui.theme.MacroWater
import com.example.foodtracker.ui.theme.MacroWeight
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.util.DateUtil
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Trends — the second tab. A pinned range pill, then one card per chart:
 * calories (on the soft gradient), macros, nutrient consistency, weight and
 * water. Weigh-ins and water are logged from sheets ([onOpenWeight],
 * [onOpenWater]) so all history lives here and entry lives anywhere.
 *
 * @param onOpenRichFoods opens "Rich in…", optionally preselecting one nutrient.
 */
@Composable
fun TrendsScreen(
    onOpenRichFoods: (String?) -> Unit,
    onOpenWater: () -> Unit,
    onOpenWeight: () -> Unit,
) {
    val vm: TrendsViewModel = appViewModel { TrendsViewModel(it.repository, it.profileStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()

    // Survives rotation and the bottom bar's save/restore of this destination,
    // so coming back from a sheet does not collapse what you had open.
    var showAllNutrients by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Trends")

        RangeBar(
            selected = state.range,
            onSelect = { haptics.light(); vm.setRange(it) },
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                top = Spacing.l,
                bottom = bottomBarClearance(),
                start = Spacing.gutter,
                end = Spacing.gutter,
            ),
            // 24dp, not 32: every section now carries a rule under its standing
            // head, so the whitespace no longer has to do the separating on its
            // own — house rule 1, and the same value Today uses.
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item("calories") {
                CompositionLocalProvider(LocalCardColor provides Color.Transparent) {
                ChartCard(
                    title = "Calories",
                    modifier = Modifier
                        .staggerIn(0)
                        .clip(RoundedCornerShape(Radius.lg))
                        .meshGradient(animate = false),
                    summary = caloriesSummary(state),
                    insight = paceInsight(state),
                    crossfadeKey = state.range,
                ) {
                    PlanChart(
                        actual = state.calorieSeries,
                        plan = listOf(state.rangeStart to state.calorieGoal, state.forecastEnd to state.calorieGoal),
                        start = state.rangeStart,
                        end = state.forecastEnd,
                        today = state.today,
                        color = MacroBurned,
                        unit = "kcal",
                        planLabel = "Plan ${grouped(state.calorieGoal)} kcal",
                    )
                }
            }
            }

            item("macros") {
                ChartCard(
                    modifier = Modifier.staggerIn(1),
                    title = "Macros",
                    summary = macrosSummary(state),
                    insight = macrosInsight(state),
                    empty = if (state.macroBars.any { it.total > 0.0 }) null else NEED_A_DAY,
                    crossfadeKey = state.range,
                ) {
                    MacroStackedBars(
                        bars = state.macroBars,
                        avgCarbs = state.macroAvgCarbs,
                        avgProtein = state.macroAvgProtein,
                        avgFat = state.macroAvgFat,
                    )
                }
            }

            item("nutrients") {
                ChartCard(
                    modifier = Modifier.staggerIn(2),
                    title = "Nutrient consistency",
                    summary = nutrientSummary(state),
                    insight = nutrientInsight(state),
                    empty = if (state.heatmapLoggedDays > 0) null else NEED_A_DAY,
                    actionLabel = "Find rich foods",
                    onAction = { onOpenRichFoods(null) },
                    crossfadeKey = state.range,
                    footer = if (state.heatmap.size > HEATMAP_COLLAPSED_ROWS) {
                        {
                            SectionLink(
                                text = if (showAllNutrients) "Show less"
                                else "Show all ${state.heatmap.size} nutrients",
                                onClick = {
                                    haptics.light()
                                    showAllNutrients = !showAllNutrients
                                },
                            )
                        }
                    } else null,
                ) {
                    NutrientHeatmap(
                        rows = state.heatmap,
                        firstDayLabel = state.heatmapFirstLabel,
                        lastDayLabel = state.heatmapLastLabel,
                        expanded = showAllNutrients,
                        collapsedRows = HEATMAP_COLLAPSED_ROWS,
                    )
                }
            }

            item("timing") {
                ChartCard(
                    title = "Meal timing",
                    modifier = Modifier.staggerIn(3),
                    summary = state.avgWindowHours?.let { "%.1f h eating window".format(it) },
                    insight = timingInsight(state),
                    empty = if (state.timingDays > 0) null else NEED_A_DAY_TIMING,
                    crossfadeKey = state.range,
                ) {
                    EatingWindowChart(days = state.timing, start = state.rangeStart, today = state.today)
                }
            }

            item("weight") {
                ChartCard(
                    modifier = Modifier.staggerIn(3),
                    title = "Weight",
                    summary = weightSummary(state),
                    insight = weightPlanInsight(state),
                    actionLabel = "Log a weigh-in",
                    onAction = onOpenWeight,
                    crossfadeKey = state.range,
                    footer = if (state.weightEntries.isNotEmpty()) {
                        {
                            SectionLink(
                                text = if (showHistory) "Hide history"
                                else "History (${state.weightEntries.size})",
                                onClick = {
                                    haptics.light()
                                    showHistory = !showHistory
                                },
                            )
                            // Composed only once opened — which is the whole
                            // reason the history is a disclosure rather than a
                            // list: most visits to this section want the line.
                            if (showHistory) {
                                WeightHistory(
                                    entries = state.weightEntries,
                                    onDelete = { id ->
                                        haptics.light()
                                        vm.deleteWeight(id)
                                    },
                                )
                            }
                        }
                    } else null,
                ) {
                    PlanChart(
                        actual = state.weightSeries,
                        plan = state.weightPlan,
                        start = state.rangeStart,
                        end = state.forecastEnd,
                        today = state.today,
                        color = MacroWeight,
                        unit = "kg",
                        planLabel = state.goalWeightKg?.let { "Plan to ${kgText(it)} kg" }
                            ?: state.weightPlan.firstOrNull()?.let { "Plan ${kgText(it.second)} kg" }
                            ?: "Plan",
                    )
                }
            }

            item("water") {
                ChartCard(
                    modifier = Modifier.staggerIn(4),
                    title = "Water",
                    summary = waterSummary(state),
                    insight = waterInsight(state),
                    empty = if (state.water.any { it.value > 0.0 }) null else NEED_A_DAY,
                    actionLabel = "Add water",
                    onAction = onOpenWater,
                    crossfadeKey = state.range,
                ) {
                    // A week is seven discrete days and reads as bars; ninety is
                    // a shape and reads as a line. Same data, and the chart that
                    // fits the question each range is asking.
                    if (state.range == TrendsRange.Week) {
                        WeeklyBarChart(
                            bars = state.waterBars,
                            goal = state.waterGoalMl.takeIf { it > 0 }?.toDouble(),
                            barColor = MacroWater,
                            unit = "ml",
                        )
                    } else {
                        LineChart(
                            points = state.water,
                            lineColor = MacroWater,
                            unit = "ml",
                            referenceLine = state.waterGoalMl.takeIf { it > 0 }?.toDouble(),
                            referenceLabel = state.waterGoalMl.takeIf { it > 0 }
                                ?.let { "Goal ${grouped(it.toDouble())} ml" },
                        )
                    }
                }
            }
        }
    }
}

/** Six rows is the top of the sorted list — the finding, without the appendix. */
private const val HEATMAP_COLLAPSED_ROWS = 6

/**
 * Within this many kcal of the goal, a daily average is *at* goal.
 *
 * Portion sizes are estimates and the database is a table; calling a 12 kcal
 * difference "over goal" would be reporting noise as a verdict.
 */
private const val CALORIE_DEADBAND = 25

private const val NEED_A_DAY = "Log a day in this range to see this."

/** A series only says something once it has two real points to say it with. */

/*
 * The readings, two per section.
 *
 * Each section used to hand `ChartCard` one sentence — "2 981 kcal avg · 513
 * over goal" — which it set in `body`, the quietest register on the page,
 * directly under an eyebrow and directly above a chart. The one fact the
 * section existed to deliver was therefore the least audible thing in it.
 *
 * House rule 1 gives every section a trailing slot on its head for the figure
 * its window amounts to, and house rule 2 wants the finding as a number rather
 * than as prose. So the sentence split in two: a `*Summary` that states the
 * level in the head ("2 981 kcal avg"), and a `*Insight` that states the
 * verdict loudly under the rule ("VS GOAL · +513 kcal"). Nothing is printed
 * twice, and `ChartInsight` has no field prose would fit in.
 *
 * None of them touch the ViewModel: every value below is already in
 * `TrendsUiState`, and this is a presentation pass.
 */

/** `+513`, `−120`. U+2212 MINUS, which is the width of the plus — see `signedKgText`. */
private fun signedGrouped(delta: Int): String =
    (if (delta < 0) "−" else "+") + grouped(abs(delta).toDouble())

/** `+0.4`, `−1.2`. `signedKgText`'s sign without its unit, which here is `bodyS`. */
private fun signedKg(deltaKg: Double): String =
    (if (deltaKg < 0) "−" else "+") + kgText(abs(deltaKg))

private fun caloriesSummary(state: TrendsUiState): String? =
    if (state.calorieAvg <= 0) null else "${grouped(state.calorieAvg.toDouble())} kcal avg"

private fun macrosSummary(state: TrendsUiState): String? {
    if (state.loggedDays == 0) return null
    return "${state.proteinMetDays} of ${state.loggedDays} days"
}

private fun macrosInsight(state: TrendsUiState): ChartInsight? {
    if (state.loggedDays == 0) return null
    // The head already carries the fraction; the loud line carries the rate,
    // which is the figure that survives a change of range.
    val pct = (state.proteinMetDays * 100.0 / state.loggedDays).roundToInt()
    return ChartInsight(
        label = "Protein on target",
        value = "$pct",
        unit = "%",
        caption = "of logged days",
    )
}

private fun nutrientSummary(state: TrendsUiState): String? {
    if (state.heatmapLoggedDays == 0) return null
    return "${state.chronicallyMissed} of ${state.heatmap.size} missed"
}

private fun nutrientInsight(state: TrendsUiState): ChartInsight? {
    if (state.heatmapLoggedDays == 0) return null
    return ChartInsight(
        label = "Days logged",
        value = "${state.heatmapLoggedDays}",
        unit = "of ${state.heatmapDays}",
        // Say so when the grid is narrower than the range: a heatmap that
        // silently meant "last month" while the control read "1Y" would be a
        // lie of omission, and the grid's own axis labels only name its ends.
        caption = if (state.heatmapDays < state.range.days) {
            "grid shows the last ${state.heatmapDays} days"
        } else null,
    )
}

private fun weightSummary(state: TrendsUiState): String? =
    state.latestWeightKg?.let { "${kgText(it)} kg now" }

/** Average time of the last meal, and how many nights ran late. */
private fun timingInsight(state: TrendsUiState): ChartInsight? {
    val last = state.avgLastMinute ?: return null
    val lateShare = state.lateDays.toDouble() / state.timingDays
    return ChartInsight(
        label = "Last meal, on average",
        value = MealTiming.label(last),
        caption = when (state.lateDays) {
            0 -> "no late nights"
            1 -> "1 late night"
            else -> "${state.lateDays} late nights of ${state.timingDays}"
        },
        tone = when {
            state.lateDays == 0 -> InsightTone.OnTrack
            lateShare >= 0.5 -> InsightTone.Over
            else -> InsightTone.Neutral
        },
    )
}

private const val NEED_A_DAY_TIMING = "Log a meal in this range to see when you eat."

/** Your logged pace in kg/week (from average calories vs burn) against the plan's. */
private fun paceInsight(state: TrendsUiState): ChartInsight {
    val plan = state.planRateKgPerWeek
    val pace = state.paceKgPerWeek
        ?: return ChartInsight(
            label = "Plan",
            value = grouped(state.calorieGoal),
            unit = "kcal / day",
            caption = "log meals to see your pace",
        )
    val close = abs(pace - plan) <= 0.15
    return ChartInsight(
        label = "At this pace",
        value = signedKg(pace),
        unit = "kg / week",
        caption = "plan ${signedKg(plan)} kg / week",
        tone = if (close) InsightTone.OnTrack else InsightTone.Neutral,
    )
}

/** How far the latest weigh-in is from the plan — or, before two weigh-ins, the plan's target. */
private fun weightPlanInsight(state: TrendsUiState): ChartInsight? {
    val latest = state.weightSeries.lastOrNull()
    val planAtLatest = latest?.let { (day, _) -> planValueAt(state.weightPlan, day) }
    if (latest != null && planAtLatest != null && state.weightSeries.size >= 2) {
        val diff = latest.second - planAtLatest
        return ChartInsight(
            label = "Vs plan",
            value = signedKg(diff),
            unit = "kg",
            caption = if (abs(diff) <= 0.3) "right on plan" else "plan ${kgText(planAtLatest)} kg",
            tone = if (abs(diff) <= 0.3) InsightTone.OnTrack else InsightTone.Neutral,
        )
    }
    if (state.planRateKgPerWeek == 0.0) {
        val hold = state.weightPlan.firstOrNull()?.second ?: return null
        return ChartInsight(label = "Plan", value = kgText(hold), unit = "kg", caption = "hold steady")
    }
    val goal = state.goalWeightKg ?: return null
    val reach = state.goalReachDay ?: return null
    return ChartInsight(
        label = "Plan",
        value = kgText(goal),
        unit = "kg",
        caption = "by ${DateUtil.shortDate(reach)}",
    )
}

private fun planValueAt(plan: List<Pair<Long, Double>>, day: Long): Double? {
    if (plan.size < 2) return null
    val i = plan.indexOfLast { it.first <= day }.coerceIn(0, plan.size - 2)
    val (d0, v0) = plan[i]
    val (d1, v1) = plan[i + 1]
    return if (d1 == d0) v0 else v0 + (v1 - v0) * (day - d0) / (d1 - d0).toDouble()
}

private fun waterSummary(state: TrendsUiState): String? =
    if (state.waterAvgMl <= 0) null else "${grouped(state.waterAvgMl.toDouble())} ml / day"

private fun waterInsight(state: TrendsUiState): ChartInsight? {
    if (state.waterAvgMl <= 0 || state.waterGoalMl <= 0) return null
    return ChartInsight(
        label = "Vs goal",
        value = signedGrouped(state.waterAvgMl - state.waterGoalMl),
        unit = "ml",
        caption = "goal ${grouped(state.waterGoalMl.toDouble())} ml",
        // Water has a target, not a limit: drinking past it is the good
        // outcome, so the only verdict available here is "on track".
        tone = if (state.waterAvgMl >= state.waterGoalMl) {
            InsightTone.OnTrack
        } else InsightTone.Neutral,
    )
}

/**
 * The weigh-in list, under the weight section.
 *
 * **Capped, and honest about it.** `WeightScreen` kept history out of its card
 * precisely so `LazyColumn` could render it lazily, and that reasoning still
 * holds — but it was answering a different layout. Here the list belongs to one
 * section, and a section is one lazy item: everything in it composes at once.
 * So the list is bounded at [HISTORY_LIMIT] rows, which is far more weigh-ins
 * than anyone scrolls back through inline, and the rest is a line of text
 * rather than three hundred hidden nodes. The full series is still in the chart
 * above.
 *
 * **The row reads label-left, number-right** like every other row in the app
 * now. It used to lead with the weight and trail the date underneath it, which
 * put a `numberM` figure hard against the left gutter and left the column of
 * numbers unalignable — house rule 2's one rhythm, broken in the one place on
 * the screen that is an actual list.
 */
@Composable
private fun WeightHistory(
    entries: List<WeightLogEntity>,
    onDelete: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        entries.take(HISTORY_LIMIT).forEach { entry ->
            AppDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        DateUtil.formatHeader(entry.dateEpochDay),
                        style = AppText.eyebrow,
                        color = scheme.onSurfaceVariant,
                    )
                    entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(Modifier.height(Spacing.xs))
                        Text(note, style = AppText.bodyS, color = scheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(Spacing.m))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(kgText(entry.weightKg), style = AppText.numberM, color = scheme.onSurface)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "kg",
                        style = AppText.bodyS,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
                IconButton(onClick = { onDelete(entry.id) }) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = "Delete weigh-in",
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (entries.size > HISTORY_LIMIT) {
            Spacer(Modifier.height(Spacing.s))
            EmptyChartLine("Showing the $HISTORY_LIMIT most recent of ${entries.size}.")
        }
    }
}

private const val HISTORY_LIMIT = 30
