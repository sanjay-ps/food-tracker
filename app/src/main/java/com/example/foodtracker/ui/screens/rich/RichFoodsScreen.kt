package com.example.foodtracker.ui.screens.rich

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.SlidersHorizontal
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.PlanSlot
import com.example.foodtracker.data.NutrientCategory
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.domain.RichFood
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.HapticFilterChip
import com.example.foodtracker.ui.components.EmptyState
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.FoodRow
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppOutlinedButton
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.Meter
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.components.color
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.screens.plan.PlanAddViewModel
import com.example.foodtracker.ui.screens.plan.PlanAmountSheet
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Find foods rich in one or more nutrients, ranked, with no network call and
 * no AI — it reads the food table that is already on the device.
 *
 * The ranking rationale lives on [com.example.foodtracker.domain.NutrientRanking];
 * the short version is that results are ordered by how much of the user's own
 * daily goal one serving covers, never filtered by a threshold.
 *
 * The rewrite kept almost all of this screen and changed two things:
 *
 *  - **The picker collapses.** It used to be a permanently open card holding a
 *    segmented control and up to eleven chips, which on a 412×915 phone pushed
 *    the first result below the fold — the screen spent its best real estate on
 *    the question instead of the answer. Once something is picked it folds to
 *    one line (`Vitamin B12 · Calcium — Change`).
 *  - **It accepts [preselectedNutrientKey].** The screen now has two entry
 *    points: the Log picker's ⚗ action, which arrives with nothing picked, and
 *    "Find foods rich in iron" from a nutrient's contribution sheet, which
 *    arrives already knowing the answer it wants.
 *
 * The editorial pass takes the cards off all of it. A result used to be a food
 * in a rounded box with its readouts inside; twenty of them is twenty boxes
 * down a scrolling list, which is the one shape this app is trying to stop
 * repeating. The question and the answer are now two standing heads with rules
 * under them ([Section]) and the results are rows divided by hairlines — a
 * tenth of the ink, and the page keeps a single left margin instead of
 * indenting every line 16dp into a border (house rule 1).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RichFoodsScreen(
    onBack: () -> Unit,
    preselectedNutrientKey: String? = null,
    /**
     * The meal a result should be planned for, when the plan editor sent the
     * user here. Null on every other entry point — and null is what keeps the
     * rows inert: arriving from "find foods rich in iron" is a question, and a
     * page reached by asking it has nowhere to put an answer.
     */
    planSlot: PlanSlot? = null,
    /** Called once a food has been added to the plan; the caller navigates. */
    onPlanned: () -> Unit = {},
) {
    val vm: RichFoodsViewModel = appViewModel {
        RichFoodsViewModel(it.repository, it.profileStore)
    }
    val planVm: PlanAddViewModel = appViewModel { PlanAddViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<FoodEntity?>(null) }

    // Keyed on the argument, so arriving from a contribution sheet picks that
    // nutrient once. [RichFoodsViewModel.preselect] is a no-op if anything is
    // already picked, which is what keeps a process-death restore — or a
    // return to this entry in the back stack — from re-adding a nutrient the
    // user has since cleared.
    LaunchedEffect(preselectedNutrientKey) {
        preselectedNutrientKey?.let(vm::preselect)
    }

    var category by rememberSaveable { mutableStateOf(NutrientCategory.Macros) }
    // False means "collapse as soon as there is something to show". The picker
    // opens itself whenever nothing is picked, because then there is nothing
    // else on the screen to look at.
    var pickerExpanded by rememberSaveable { mutableStateOf(false) }
    val showFullPicker = pickerExpanded || state.selectedKeys.isEmpty()

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (planSlot == null) {
                    "Rich in…"
                } else {
                    "Rich in… · ${planSlot.displayName}"
                },
                onBack = onBack,
                actions = {
                    if (state.selectedKeys.isNotEmpty()) {
                        AppTextButton(
                            text = "Clear",
                            onClick = {
                                vm.clear()
                                pickerExpanded = false
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = Spacing.screenContent,
            // No arrangement spacing: the results are a ruled list, and a gap
            // inserted between every pair of items would float each hairline
            // in the middle of 16dp of nothing. Each block owns its own air.
        ) {
            item("picker") {
                // 24dp under the question, the standard gap between two
                // sections; the answer below brings its own head and rule.
                Column(Modifier.padding(bottom = Spacing.xxl)) {
                    if (showFullPicker) {
                        NutrientPicker(
                            state = state,
                            category = category,
                            onCategory = { category = it },
                            onToggle = vm::toggle,
                            onDone = { pickerExpanded = false },
                        )
                    } else {
                        CollapsedPicker(
                            fields = state.selectedFields,
                            onChange = { pickerExpanded = true },
                        )
                    }
                }
            }

            if (state.selectedKeys.isEmpty()) {
                item("empty") {
                    EmptyState(
                        icon = Lucide.SlidersHorizontal,
                        title = "Nothing picked yet",
                        message = "Choose a nutrient above and every food on this " +
                            "phone gets ranked by how much of your daily goal one " +
                            "serving of it covers.",
                    )
                }
            } else {
                if (state.results.isEmpty()) {
                    item("noResults") {
                        NoResults(state, onDropBlocker = { vm.toggle(it.key) })
                    }
                } else {
                    item("heading") {
                        // The count goes in the head's trailing slot, where
                        // every section in the app states its total.
                        Section(
                            title = "Results",
                            modifier = Modifier.padding(bottom = Spacing.m),
                            trailing = {
                                Text(
                                    "${state.results.size} foods",
                                    style = AppText.bodyS,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        ) {
                            Text(
                                "Per serving, as a share of your daily goal",
                                style = AppText.bodyS,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Only with more than one pick: with a single
                            // nutrient "the one it does worst on" is just that
                            // nutrient, and saying so would puzzle rather than
                            // explain.
                            if (state.selectedKeys.size > 1) {
                                Text(
                                    "Ranked by the nutrient each food does worst on, so " +
                                        "nothing gets in on one strong number.",
                                    style = AppText.bodyS,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    itemsIndexed(state.results, key = { _, r -> r.food.id }) { index, result ->
                        // Rules between rows, not around them — and none above
                        // the first, which already sits under the head's rule.
                        if (index > 0) AppDivider()
                        RichFoodRow(
                            result = result,
                            fields = state.selectedFields,
                            onPick = planSlot?.let { { picked = result.food } },
                        )
                    }
                }
            }
        }
    }

    val food = picked
    if (food != null && planSlot != null) {
        PlanAmountSheet(
            food = food,
            slot = planSlot,
            onConfirm = { item -> planVm.add(item, onDone = onPlanned) },
            onDismiss = { picked = null },
        )
    }
}

/**
 * The picker, open: an axis of categories over a wrapped row of nutrient pills.
 *
 * Still the right control, and still the same three parts — what changed is
 * that it is a [Section] rather than a card: a standing head, a rule under it,
 * then the content. The "Done" affordance only appears once a pick exists:
 * without it, re-opening the picker to change a nutrient would be a one-way
 * trip, since the automatic collapse only happens on the transition out of the
 * empty state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NutrientPicker(
    state: RichFoodsState,
    category: NutrientCategory,
    onCategory: (NutrientCategory) -> Unit,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    Section(
        title = "Pick nutrients",
        trailing = {
            if (state.selectedKeys.isNotEmpty()) {
                AppTextButton(text = "Done", onClick = onDone)
            }
        },
    ) {
        ExclusiveButtonGroup(
            options = NutrientCategory.entries,
            selected = category,
            onSelect = onCategory,
            modifier = Modifier.fillMaxWidth(),
            label = { it.name },
        )
        Spacer(Modifier.height(Spacing.m))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            RichSearchableFields
                .filter { it.category == category }
                .forEach { field ->
                    HapticFilterChip(
                        selected = field.key in state.selectedKeys,
                        onClick = { onToggle(field.key) },
                        label = { Text(field.label, style = AppText.label) },
                    )
                }
        }
    }
}
/**
 * The picker, folded: what is being asked, and one way back to the question.
 *
 * One line is enough because the answer is directly underneath and repeats
 * every nutrient's name on every result row — the summary is a reminder, not a
 * reference.
 *
 * It takes the same shape as every other block on the page — `RICH IN`, a
 * rule, then what was picked — with "Change" in the head's trailing slot. The
 * picks themselves are the content, at title size: they are the subject of the
 * list underneath, not a caption on it.
 */
@Composable
private fun CollapsedPicker(fields: List<NutrientField>, onChange: () -> Unit) {
    Section(
        title = "Rich in",
        trailing = { AppTextButton(text = "Change", onClick = onChange) },
    ) {
        Text(
            fields.joinToString(" · ") { it.label },
            style = AppText.titleL,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What to say when nothing ranks, which is a dead end unless it explains
 * itself. A food scores by its weakest pick, so a single nutrient that no food
 * on file carries — omega-3, across most of this food table — drives every
 * score to zero and empties the list on its own.
 *
 * The offer is to drop that nutrient, not to loosen what the screen means. The
 * query is what is wrong here, and fixing the query leaves every remaining
 * result as trustworthy as before; quietly switching to a weaker rule would
 * fill the screen with foods that do not answer what was asked.
 */
@Composable
private fun NoResults(state: RichFoodsState, onDropBlocker: (NutrientField) -> Unit) {
    val blocker = state.blockingField
    EmptyState(
        icon = Lucide.SearchX,
        title = if (blocker != null) {
            "No food is rich in all ${state.selectedKeys.size}"
        } else {
            "Nothing ranks for that"
        },
        message = if (blocker != null) {
            "${blocker.label} is ruling everything out — few foods here carry a " +
                "figure for it, and a food is only as good as its weakest pick."
        } else {
            "No food on file carries enough of it to rank. Custom foods you add " +
                "with real figures will show up here."
        },
        action = blocker?.let {
            {
                AppOutlinedButton(
                    text = "Remove ${it.label.lowercase()}",
                    onClick = { onDropBlocker(it) },
                )
            }
        },
    )
}

/**
 * One result: the food, then one [NutrientReadout] per nutrient picked.
 *
 * There is deliberately no separate "compact" and "expanded" row. A single
 * pick renders exactly the same readout as each of five picks does — one line
 * instead of five — so the two cases are the same component at different
 * lengths rather than two designs a reader has to learn separately. It also
 * means the one-nutrient case cannot drift out of sync with the many-nutrient
 * case when either is changed.
 *
 * The readouts sit under the food rather than to its right because the right
 * edge cannot hold a label. Unlabelled numbers stacked in a corner require the
 * reader to remember which nutrient they picked first, and silently become
 * unreadable at three picks; they also squeeze the name column until long food
 * names truncate.
 */
@Composable
private fun RichFoodRow(
    result: RichFood,
    fields: List<NutrientField>,
    onPick: (() -> Unit)? = null,
) {
    // A hairline between results is what keeps two of them from bleeding
    // together — the job the card's border used to do, done by one line
    // instead of four, and without the inset that pushed every result 16dp off
    // the page's margin. The caller draws it, so the last row has no dangling
    // rule under it.
    Column(Modifier.fillMaxWidth()) {
        // Inert unless a plan sent us here — see the `planSlot` parameter.
        FoodRow(food = result.food, onClick = onPick ?: {})
        Column(
            // Aligned with the food name above: 16dp row padding + 40dp
            // category disc + 12dp gap.
            modifier = Modifier.padding(start = 68.dp, end = Spacing.gutter, bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            fields.forEachIndexed { i, field ->
                NutrientReadout(
                    field = field,
                    amount = result.amounts[i],
                    goalFraction = result.goalFractions[i],
                )
            }
        }
    }
}

/**
 * One nutrient's contribution from one serving: what it is, how much, and how
 * far that goes towards the day's goal.
 *
 * The bar is the point. The ranking is on the goal fraction, so without a
 * visual for it the order looks arbitrary next to the gram figures — 16 g of
 * fiber outranking 18 g of protein only makes sense once you can see that the
 * first fills 46% of its goal and the second 20% of its own.
 *
 * Rebuilt on the [Meter] atom, which deletes the third hand-rolled
 * `Box`-in-a-`Box` bar in the app. The meter keeps the *nutrient's* hue rather
 * than taking a status colour: this is a ranking of foods, not a report on the
 * day, and nothing here has been eaten yet.
 *
 * **The line is now label-left, number-right, meter underneath** — house rule
 * 2, and the same shape as the macro legend on Today. The old row threaded the
 * meter *between* the label and the number, which cost it twice: the label was
 * pinned to a 96dp column (so "Vitamin B12" ellipsed while "Iron" left 60dp
 * empty), and the figure — the thing being ranked on — was squeezed into 74dp
 * of right margin at caption size. Giving the bar its own full-width line pays
 * for both: the label gets the room it needs, the amount reads at number
 * weight, and the bars line up down the page so two foods can be compared by
 * length instead of by reading.
 */
@Composable
private fun NutrientReadout(field: NutrientField, amount: Double, goalFraction: Double) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                field.label,
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.s))
            ValueText(amount, field.unit.symbol)
            Spacer(Modifier.width(Spacing.s))
            Text(
                "${(goalFraction * 100).roundToInt()}%",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Meter(
            fraction = goalFraction.toFloat(),
            color = field.color(),
            height = 2.dp,
        )
    }
}
