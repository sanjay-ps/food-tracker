package com.example.foodtracker.ui.screens.you

import com.example.foodtracker.ui.components.detail
import com.example.foodtracker.ui.components.title
import com.example.foodtracker.ui.components.OptionGrid
import com.example.foodtracker.ui.components.OptionList
import com.example.foodtracker.ui.components.atoms.AppTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.ActivityLevel
import com.example.foodtracker.data.Goal
import com.example.foodtracker.data.Sex
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.data.WeightChangeRate
import com.example.foodtracker.data.trainingMixLabel
import com.example.foodtracker.domain.Bmi
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.BmiBar
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.HapticSlider
import com.example.foodtracker.ui.components.NumberStepper
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.screens.you.sheets.AllTargetsSheet
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Edit profile & goals, rebuilt against
 * `docs/ui-house-rules.md`.
 *
 * A page that is always editing, replacing the top-bar edit/cancel/save
 * tri-state the old `ProfileScreen` carried. That screen was one layout in two
 * modes, which meant the same file described both how the profile reads and
 * how it is entered, and the top bar had to tell you which of the two you were
 * looking at. Splitting it costs one route and removes a whole axis of state.
 *
 * The LIVE targets block at the bottom is the one genuinely new thing here.
 * Before it, changing your activity level meant saving, backing out, and
 * hunting down the Daily targets card to find out what had moved — so most
 * people never found out at all.
 * [com.example.foodtracker.domain.GoalCalculator.goalsFor] is pure, so
 * recomputing the whole target set on every drag is free.
 *
 * **The four cards are gone**, for the same reason they went on You: a form is
 * already a sequence of labelled groups, and drawing a rounded outline around
 * each group states the grouping a second time while making the page a stack of
 * boxes. Each is now a [Section] — a standing head with a hairline under it,
 * 24dp from the next.
 *
 * One consequence worth naming: the sub-headings inside a section ("Sex",
 * "Rate of loss") can no longer be eyebrows, because the section head above
 * them is one and two levels of 11sp uppercase grey is a hierarchy nobody can
 * read. They sit at `body` on `onSurface`, which is also what every slider and
 * stepper row in here already used, so the page gains a hierarchy it did not
 * have when both levels looked identical.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val vm: EditProfileViewModel = appViewModel { EditProfileViewModel(it.profileStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    var showAllTargets by remember { mutableStateOf(false) }

    val draft = state.draft

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Edit profile & goals",
                onBack = onBack,
                actions = {
                    // Pinned, and always enabled: with nothing changed the view
                    // model short-circuits to the callback, so Save is never a
                    // dead button the user has to reason about.
                    AppTextButton("Save", onClick = {
                        haptics.confirm()
                        vm.save(onBack)
                    })
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.l,
                bottom = Spacing.xxxl,
            ),
            // 24dp — the hairline under each head does the separating now.
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            item("about") {
                Section("About you") {
                    AppTextField(
                        value = draft.name,
                        onValueChange = { v -> vm.updateDraft { it.copy(name = v) } },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    FieldLabel("Sex")
                    Spacer(Modifier.height(Spacing.s))
                    ExclusiveButtonGroup(
                        options = Sex.entries,
                        selected = draft.sex,
                        onSelect = { sex -> vm.updateDraft { it.copy(sex = sex) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { it.displayName },
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    SliderRow(
                        label = "Age",
                        value = draft.ageYears.toFloat(),
                        range = 10f..90f,
                        steps = 79,
                        unit = "years",
                        onChange = { vm.updateDraft { p -> p.copy(ageYears = it.toInt()) } },
                    )
                    Spacer(Modifier.height(Spacing.l))
                    SliderRow(
                        label = "Height",
                        value = draft.heightCm.toFloat(),
                        range = 120f..220f,
                        steps = 0,
                        unit = "cm",
                        onChange = { vm.updateDraft { p -> p.copy(heightCm = it.toInt()) } },
                    )
                    Spacer(Modifier.height(Spacing.l))
                    SliderRow(
                        label = "Weight",
                        value = draft.weightKg.toFloat(),
                        range = 18f..200f,
                        steps = 0,
                        unit = "kg",
                        onChange = { vm.updateDraft { p -> p.copy(weightKg = ((it * 10).toInt() / 10.0)) } },
                        valueFormatter = { "%.1f".format(it) },
                        granularity = 1f,
                        stepBy = 0.1f,
                        // The stepper nudges by 0.1 kg; the slider snaps to whole
                        // kilos. Dragging a 182dp track at 0.1 resolution is a
                        // promise of precision the finger cannot keep.
                        onSliderChange = {
                            vm.updateDraft { p -> p.copy(weightKg = it.roundToInt().toDouble()) }
                        },
                    )
                }
            }

            item("activity") {
                Section(
                    title = "Activity",
                    // The figure this section produces, live as you drag: what
                    // the body is reckoned to burn in a day before the goal
                    // adjusts it. Before the summary slot existed you had to
                    // leave the page to find out that moving a chip had moved
                    // anything at all.
                    trailing = {
                        Text(
                            "${formatValue(GoalCalculator.tdee(draft))} kcal TDEE",
                            style = AppText.bodyS,
                            color = scheme.onSurfaceVariant,
                        )
                    },
                ) {
                    OptionList(
                        options = ActivityLevel.entries,
                        selected = draft.activityLevel,
                        onSelect = { level -> vm.updateDraft { it.copy(activityLevel = level) } },
                        caption = { it.detail },
                        label = { it.title },
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    TrainingMixRow(
                        mix = draft.trainingMix,
                        enabled = draft.activityLevel != ActivityLevel.Sedentary,
                        onChange = { v -> vm.updateDraft { it.copy(trainingMix = v) } },
                    )
                }
            }

            item("goal") {
                Section(
                    title = "Goal",
                    trailing = {
                        Text(
                            goalLine(draft),
                            style = AppText.bodyS,
                            color = scheme.onSurfaceVariant,
                        )
                    },
                ) {
                    OptionList(
                        options = Goal.entries,
                        selected = draft.goal,
                        onSelect = vm::setDraftGoal,
                        caption = {
                    when (it) {
                        Goal.Lose -> "Eat a little under what you burn"
                        Goal.Maintain -> "Eat about what you burn"
                        Goal.Gain -> "Eat a little over what you burn"
                    }
                },
                        label = { it.displayName },
                    )
                    if (draft.goal != Goal.Maintain) {
                        Spacer(Modifier.height(Spacing.xl))
                        FieldLabel(
                            if (draft.goal == Goal.Lose) "Rate of loss" else "Rate of gain",
                        )
                        Spacer(Modifier.height(Spacing.s))
                        OptionGrid(
                            options = WeightChangeRate.entries,
                            selected = draft.weightChangeRate,
                            onSelect = { rate -> vm.updateDraft { it.copy(weightChangeRate = rate) } },
                            caption = { it.detail },
                            label = { it.title },
                        )
                        Spacer(Modifier.height(Spacing.xl))
                        GoalWeightSlider(draft = draft, onUpdate = vm::updateDraft)
                    }
                    Spacer(Modifier.height(Spacing.xl))
                    BmiBar(
                        currentBmi = Bmi.bmi(draft.weightKg, draft.heightCm),
                        goalBmi = draft.goalWeightKg?.let { Bmi.bmi(it, draft.heightCm) },
                    )
                }
            }

            item("targets") {
                Section(
                    title = "These targets",
                    // An accent *label*, not the filled `AppBadge` this used to
                    // be. House rule 3 bans accent-filled chrome and says to
                    // use a coloured label instead; the word is doing the same
                    // job either way, and the badge was the only filled shape
                    // left on the page.
                    trailing = {
                        Text("Live", style = AppText.eyebrow, color = scheme.tertiary)
                    },
                ) {
                    // Only the headline four, and only the saved→draft arrow
                    // where a number actually moved. The full list is a tap
                    // away and is the same renderer.
                    LiveTarget(
                        "Calories",
                        state.draftGoals.calories,
                        "kcal",
                        state.savedGoals.calories,
                    )
                    AppDivider()
                    LiveTarget(
                        "Protein",
                        state.draftGoals.protein,
                        "g",
                        state.savedGoals.protein,
                    )
                    AppDivider()
                    LiveTarget("Carbs", state.draftGoals.carbs, "g", state.savedGoals.carbs)
                    AppDivider()
                    LiveTarget(
                        "Water",
                        state.draftGoals.waterMl.toDouble(),
                        "ml",
                        state.savedGoals.waterMl.toDouble(),
                    )
                    SeeAllTargetsRow(onClick = { showAllTargets = true })
                    if (state.dirty) {
                        Text(
                            "Not saved yet — these apply when you tap Save.",
                            style = AppText.bodyS,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.m),
                        )
                    }
                }
            }
        }
    }

    if (showAllTargets) {
        AllTargetsSheet(goals = state.draftGoals, onDismiss = { showAllTargets = false })
    }
}

/** One of the four steering numbers, with its saved value when it has moved. */
@Composable
private fun LiveTarget(label: String, value: Double, unit: String, previous: Double) {
    TargetRow(
        label = label,
        value = value,
        unit = unit,
        previous = previous,
        emphasis = TargetEmphasis.Headline,
    )
}

/**
 * A heading *inside* a section.
 *
 * Deliberately not an eyebrow: the section it sits in already has one, and two
 * levels of 11sp uppercase grey is a hierarchy nobody can read. `body` on
 * `onSurface` matches the label of every slider and stepper row beside it,
 * which is what these headings actually are — the name of one control.
 */
@Composable
private fun FieldLabel(text: String) {
    Text(text, style = AppText.body, color = MaterialTheme.colorScheme.onSurface)
}

/**
 * Goal weight, bounded by the direction the user chose: losing cannot target a
 * number above today's weight, gaining cannot target one below it. The old
 * screen computed the same two ranges inline inside an `if`, which is where
 * the `coerceAtLeast(18.1f)` guard against a zero-width range went missing
 * from review.
 */
@Composable
private fun GoalWeightSlider(
    draft: UserProfile,
    onUpdate: ((UserProfile) -> UserProfile) -> Unit,
) {
    val goalWeight = draft.goalWeightKg ?: draft.weightKg
    val range = if (draft.goal == Goal.Lose) {
        18f..draft.weightKg.toFloat().coerceAtLeast(18.1f)
    } else {
        draft.weightKg.toFloat().coerceAtMost(199.9f)..200f
    }
    SliderRow(
        label = "Goal weight",
        value = goalWeight.toFloat(),
        range = range,
        steps = 0,
        unit = "kg",
        onChange = { onUpdate { p -> p.copy(goalWeightKg = ((it * 10).toInt() / 10.0)) } },
        valueFormatter = { "%.1f".format(it) },
        granularity = 1f,
        stepBy = 0.1f,
        onSliderChange = { onUpdate { p -> p.copy(goalWeightKg = it.roundToInt().toDouble()) } },
    )
}

/**
 * Optional resistance-vs-endurance slider.
 *
 * Only [com.example.foodtracker.domain.GoalCalculator.proteinPerKg] reads it,
 * and only to split the activity bump, so leaving it alone is a real choice
 * rather than an unanswered question: null sits at the midpoint and yields the
 * same targets as before the slider existed. Disabled at Sedentary, where
 * there is no training to characterise and dragging would change nothing.
 */
@Composable
private fun TrainingMixRow(
    mix: Double?,
    enabled: Boolean,
    onChange: (Double) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Training type",
                style = AppText.body,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (enabled) trainingMixLabel(mix) else "Not training",
                style = AppText.label,
                color = if (enabled) scheme.primary else muted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gym", style = AppText.bodyS, color = muted)
            HapticSlider(
                value = (mix ?: UserProfile.TRAINING_MIX_BALANCED).toFloat(),
                onValueChange = { onChange(it.toDouble().coerceIn(0.0, 1.0)) },
                valueRange = 0f..1f,
                // Four intervals: gym only / mostly gym / both / mostly
                // running / running only. Matches [trainingMixLabel].
                steps = 3,
                granularity = 0.25f,
                enabled = enabled,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.s),
            )
            Text("Running", style = AppText.bodyS, color = muted)
        }
        Text(
            if (enabled) {
                "Optional. Lifting raises your protein target more than cardio does."
            } else {
                "Pick an activity level above Sedentary to set this."
            },
            style = AppText.bodyS,
            color = muted,
        )
    }
}

/**
 * Label + stepper on one line, continuous slider under it.
 *
 * The pairing is deliberate: the slider covers the whole plausible range in one
 * gesture, the stepper lands the last unit. Neither alone is good at both, and
 * the stepper is also the only one of the two that a user with a tremor can
 * drive.
 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    onChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    granularity: Float = 1f,
    stepBy: Float = 1f,
    onSliderChange: (Float) -> Unit = onChange,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(label)
            Spacer(Modifier.weight(1f))
            NumberStepper(
                value = "${valueFormatter(value)} $unit",
                onDecrement = { onChange((value - stepBy).coerceIn(range.start, range.endInclusive)) },
                onIncrement = { onChange((value + stepBy).coerceIn(range.start, range.endInclusive)) },
                decrementEnabled = value > range.start,
                incrementEnabled = value < range.endInclusive,
            )
        }
        HapticSlider(
            value = value,
            onValueChange = onSliderChange,
            valueRange = range,
            steps = steps,
            granularity = granularity,
        )
    }
}
