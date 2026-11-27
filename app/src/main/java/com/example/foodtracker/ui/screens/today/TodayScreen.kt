package com.example.foodtracker.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.db.FoodLogWithFood
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.NutrientContributionSheet
import com.example.foodtracker.ui.components.atoms.staggerIn
import com.example.foodtracker.ui.components.sheets.EntryEditSheet
import com.example.foodtracker.ui.components.sheets.WaterSheet
import com.example.foodtracker.ui.components.sheets.WeighInSheet
import com.example.foodtracker.ui.navigation.bottomBarClearance
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.screens.today.components.CalorieHero
import com.example.foodtracker.ui.screens.today.components.BentoFacts
import com.example.foodtracker.ui.screens.today.components.ClosingQuote
import com.example.foodtracker.ui.screens.today.components.BentoMacros
import com.example.foodtracker.ui.screens.today.components.RhythmHeatmap
import com.example.foodtracker.ui.screens.today.components.StreakTile
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.foodtracker.ui.screens.today.components.MealsSection
import com.example.foodtracker.ui.screens.today.components.PlanCard
import com.example.foodtracker.ui.screens.today.components.TodayHeader
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.launch

/** A modal this screen can be asked to open on arrival — deep links land here. */
enum class TodaySheet { Water, Weight }

/**
 * Today — the home tab.
 *
 * Top to bottom: greeting + day pill, the calorie hero (textured gradient
 * with the week's bars and a slight scroll parallax), then **meals** — what
 * you act on most, so it sits above the fold — then the bento grid (macros,
 * water, streak, weight, nutrient score), the plan (when set), "Your rhythm"
 * (an 18-week heatmap) and the closing quote. Each card rises in on first show.
 *
 * Logging lives on the floating "+" beside the tab bar, so there is no FAB
 * here; each meal row also has its own add button.
 *
 * **No loading state, by design.** `TodayViewModel.state` has a non-null
 * initial value; figures show em-dashes until `loaded` flips, then count up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    /** Opens the food picker for a meal on the day on screen (so back-filling a past day works). */
    onAddFoodToMeal: (MealType, Long) -> Unit,
    onOpenYou: () -> Unit,
    onOpenRichFoods: (String?) -> Unit,
    onOpenAllNutrients: (Long) -> Unit,
    onOpenPlan: () -> Unit,
    openSheet: TodaySheet? = null,
    onSheetConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: TodayViewModel = appViewModel { TodayViewModel(it.repository, it.profileStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Scroll offset of the hero while it is the first item; read at draw time.
    val heroParallax = {
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
    }
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    var detailField by remember { mutableStateOf<NutrientField?>(null) }
    var editing by remember { mutableStateOf<FoodLogWithFood?>(null) }
    var sheet by remember { mutableStateOf<TodaySheet?>(null) }

    // A deep link (widget or reminder tap) asks for a sheet rather than a
    // route. Consumed on arrival so a configuration change does not reopen it.
    LaunchedEffect(openSheet) {
        if (openSheet != null) {
            sheet = openSheet
            onSheetConsumed()
        }
    }

    val scheme = MaterialTheme.colorScheme
    val clearance = bottomBarClearance()
    val weekStart = weekStartOf(state.epochDay)
    val today = DateUtil.todayEpochDay()
    val selectDay: (Long) -> Unit = { haptics.light(); vm.setDay(it) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.background,
        topBar = {
            TodayHeader(
                epochDay = state.epochDay,
                name = state.profile.name,
                onSelectDay = selectDay,
                onOpenYou = onOpenYou,
            )
        },
        snackbarHost = {
            SnackbarHost(snackbars, Modifier.padding(bottom = clearance - Spacing.l)) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(Radius.md),
                    containerColor = scheme.inverseSurface,
                    contentColor = scheme.inverseOnSurface,
                    actionColor = scheme.tertiary,
                )
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + Spacing.s,
                bottom = clearance,
                start = Spacing.gutter,
                end = Spacing.gutter,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item("hero") {
                CalorieHero(
                    consumed = state.totalNutrients,
                    goals = state.goals,
                    weekKcal = state.weekKcal,
                    selectedIndex = (state.epochDay - weekStart).toInt(),
                    todayIndex = (today - weekStart).toInt().takeIf { it in 0..6 },
                    isToday = state.isToday,
                    loaded = state.loaded,
                    onDayClick = { i -> selectDay(weekStart + i) },
                    modifier = Modifier.staggerIn(0),
                    parallax = heroParallax,
                    streak = history.streak.current,
                )
            }
            item("meals") {
                MealsSection(
                    groups = state.mealGroups,
                    activeMeal = if (state.isToday) currentMeal() else null,
                    totalKcal = state.totalNutrients.calories,
                    onAdd = { haptics.light(); onAddFoodToMeal(it, state.epochDay) },
                    onEditEntry = { editing = it },
                    onDeleteEntry = { row ->
                        haptics.light()
                        vm.deleteWithUndo(row.log)
                        scope.launch {
                            val result = snackbars.showSnackbar(
                                message = "${row.food.name} deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
                            else vm.clearUndo()
                        }
                    },
                    modifier = Modifier.staggerIn(4),
                )
            }
            item("bento") {
                BentoMacros(
                    consumed = state.totalNutrients,
                    goals = state.goals,
                    waterMl = state.waterMl,
                    waterGoalMl = state.goals.waterMl,
                    onMacro = { field -> haptics.light(); detailField = field },
                    onWater = { haptics.light(); sheet = TodaySheet.Water },
                    modifier = Modifier.staggerIn(1),
                )
            }
            item("streak") {
                StreakTile(
                    streak = history.streak,
                    loggedDays = history.loggedDays,
                    loggedToday = today in history.loggedDays,
                    modifier = Modifier.staggerIn(2),
                    onClick = { haptics.light(); selectDay(today) },
                )
            }
            item("facts") {
                BentoFacts(
                    weightKg = state.latestWeight?.weightKg,
                    weightCaption = state.latestWeight?.let { agoLabel(it.dateEpochDay) } ?: "Tap to log",
                    consumed = state.totalNutrients,
                    goals = state.goals,
                    hasLogs = state.logs.isNotEmpty(),
                    onWeight = { haptics.light(); sheet = TodaySheet.Weight },
                    onNutrients = { onOpenAllNutrients(state.epochDay) },
                    modifier = Modifier.staggerIn(3),
                )
            }
            // Absent entirely when no plan is set, so Today is unchanged for
            // anyone who skips the feature.
            if (state.hasPlan) {
                item("plan") {
                    PlanCard(
                        remaining = state.planRemaining,
                        summary = state.planSummary,
                        caption = planCaption(state.planNextFoods),
                        onClick = { haptics.light(); onOpenPlan() },
                        modifier = Modifier.staggerIn(3),
                    )
                }
            }
            item("rhythm") {
                RhythmHeatmap(
                    start = history.heatStart,
                    kcal = history.heatKcal,
                    goal = history.goalKcal,
                    onDayClick = selectDay,
                    modifier = Modifier.staggerIn(6),
                )
            }
            item("quote") { ClosingQuote() }
        }
    }

    detailField?.let { field ->
        NutrientContributionSheet(
            field = field,
            consumed = state.totalNutrients,
            goals = state.goals,
            logs = state.logs,
            onDismiss = { detailField = null },
            onFindRichFoods = { key -> detailField = null; onOpenRichFoods(key) },
        )
    }

    editing?.let { row ->
        EntryEditSheet(
            row = row,
            onDismiss = { editing = null },
            onSave = { vm.editLog(it) },
            onDelete = { vm.removeLog(row.log.id) },
        )
    }

    when (sheet) {
        TodaySheet.Water -> WaterSheet(onDismiss = { sheet = null })
        TodaySheet.Weight -> WeighInSheet(onDismiss = { sheet = null })
        null -> Unit
    }
}

/** The foods still to log, named — up to three, then a count. */
private fun planCaption(nextFoods: List<String>): String {
    if (nextFoods.isEmpty()) return "Everything planned is logged"
    val shown = nextFoods.take(3)
    val rest = nextFoods.size - shown.size
    return shown.joinToString(" · ") + if (rest > 0) " · +$rest more" else ""
}

/** The meal the wall clock says you are in. See [MealType.currentForTime]. */
internal fun currentMeal(): MealType = MealType.currentForTime()

private fun agoLabel(epochDay: Long): String {
    val days = DateUtil.todayEpochDay() - epochDay
    return when {
        days <= 0L -> "Logged today"
        days == 1L -> "Yesterday"
        else -> "$days days ago"
    }
}
