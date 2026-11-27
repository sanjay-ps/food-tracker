package com.example.foodtracker.ui.screens.plan

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.OilAmount
import com.example.foodtracker.data.PlanSlot
import com.example.foodtracker.data.db.PlanItemWithFood
import com.example.foodtracker.data.db.nutrients
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.ConfirmDialog
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.countText
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.screens.today.components.StatRow
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing

/**
 * The plan: what you have decided to eat, by meal.
 *
 * **It is a shortcut, and the screen is built to stay one.** There is no
 * adherence score and no "4 of 7 planned" anywhere — those would make the plan
 * a thing you can succeed or fail at, which is a different feature with a
 * different cost. What it says per meal is the same thing Today's diary says
 * per meal: how many items and how many calories they come to.
 *
 * The one figure above that is the nutrition row, which opens
 * [PlanNutritionSheet] — what the whole plan comes to, raised over the page
 * rather than printed down the middle of it. That is a description of the plan
 * and not a grade on the reader; the sheet's docstring holds the rest of that
 * argument, including why it carries no status colours.
 *
 * All four meals render even when empty, which is the one place this screen
 * differs from Today's. Here an empty Lunch is the answer to "what have I
 * planned" — it says *nothing yet, and here is where to add it*. On Today the
 * same row would be a shortcut to nowhere, so it is dropped.
 */
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    onAddFood: (PlanSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PlanViewModel = appViewModel { PlanViewModel(it.repository, it.profileStore) }
    val addVm: PlanAddViewModel = appViewModel { PlanAddViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()

    var editing by remember { mutableStateOf<PlanItemWithFood?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showNutrition by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Plan",
                onBack = onBack,
                actions = {
                    if (state.itemCount > 0) {
                        AppTextButton(text = "Clear", onClick = { confirmClear = true })
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = Spacing.screenContent,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            if (state.itemCount == 0) {
                item("intro") {
                    // Not an EmptyState: the four meal sections below are the
                    // thing to act on, and a full-height illustration block
                    // above them would push the first "Add food" off the fold.
                    Text(
                        "Put the foods you normally eat on this list, at the " +
                            "amounts you normally eat them. They will show up " +
                            "on Today, one tap from your diary.",
                        style = AppText.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Above the meals, because it is the page's summary figure and a
            // total printed under four lists is a total nobody scrolls to. One
            // row and a chevron: the breakdown itself is a sheet (see
            // PlanNutritionSheet) so this page stays a list you act on.
            if (state.itemCount > 0) {
                item("nutrition") {
                    Section("Nutrition") {
                        StatRow(
                            label = "If you eat it all",
                            value = grouped(state.totals.calories),
                            unit = "kcal",
                            caption = "Macro split and every nutrient it gives you",
                            onClick = { haptics.light(); showNutrition = true },
                        )
                    }
                }
            }

            for (slot in PlanSlot.entries) {
                val rows = state.groups[slot].orEmpty()
                item(slot.storedName) {
                    Section(
                        title = slot.displayName,
                        trailing = {
                            Text(
                                if (rows.isEmpty()) {
                                    "Nothing planned"
                                } else {
                                    "${grouped(rows.sumOf { it.nutrients().calories })} kcal · ${rows.size}"
                                },
                                style = AppText.bodyS,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) AppDivider()
                            PlanLine(row = row, onClick = { haptics.light(); editing = row })
                        }
                        if (rows.isNotEmpty()) AppDivider()
                        AddFoodRow(slot = slot, onClick = { haptics.light(); onAddFood(slot) })
                    }
                }
            }
        }
    }

    if (confirmClear) {
        // The only action on this screen with no way back. Removing one line
        // is a two-step already (open the sheet, hit the destructive row), so
        // emptying the whole plan should not be a single tap in the app bar.
        ConfirmDialog(
            title = "Clear the plan?",
            body = "Every planned food is removed. Nothing in your diary changes.",
            confirmLabel = "Clear plan",
            onConfirm = { haptics.light(); vm.clear(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }

    if (showNutrition) {
        PlanNutritionSheet(
            totals = state.totals,
            goals = state.goals,
            itemCount = state.itemCount,
            onDismiss = { showNutrition = false },
        )
    }

    editing?.let { row ->
        PlanAmountSheet(
            food = row.food,
            slot = PlanSlot.fromName(row.item.mealType),
            existing = row.item,
            onConfirm = { addVm.add(it) },
            onDismiss = { editing = null },
            onRemove = { vm.remove(row.item.id) },
        )
    }
}

/** One planned line: what, how much, and what that comes to. */
@Composable
private fun PlanLine(row: PlanItemWithFood, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.food.name,
                style = AppText.body,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                amountLabel(row),
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            "${grouped(row.nutrients().calories)} kcal",
            style = AppText.bodyS,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddFoodRow(slot: PlanSlot, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lucide.Plus,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.s))
        // An accent-coloured label, not a filled block: house rule 3 keeps the
        // accent for data and for the one primary action, and text links are
        // the listed exception.
        Text(
            // "Add food to any time" is not a sentence; the any-time group
            // takes a preposition of its own rather than a lowercased heading.
            when (slot) {
                is PlanSlot.At -> "Add food to ${slot.meal.displayName.lowercase()}"
                PlanSlot.AnyTime -> "Add food for any time"
            },
            style = AppText.label,
            color = scheme.primary,
        )
    }
}

/**
 * `2 × 1 idli (35 g)`, or `Custom · 180 g` where that is what was planned.
 *
 * The count is dropped in custom-grams mode because it is pinned to 1 there
 * and `1 × Custom · 180 g` reads as though the one were meaningful.
 */
internal fun amountLabel(row: PlanItemWithFood): String {
    val label = row.item.servingLabel
    val prep = listOfNotNull(
        row.item.style.takeIf { !it.isNullOrBlank() },
        oilTerm(row),
    ).joinToString(" · ")
    val amount = if (row.item.servings == 1.0) label else "${countText(row.item.servings)} × $label"
    return if (prep.isBlank()) amount else "$amount  ·  $prep"
}

/**
 * The oil half of the caption: what was in the pan, qualified by how much of
 * it.
 *
 * The amount has to appear here or the row contradicts itself. A dosa planned
 * with the oil set to None is priced without it — the kcal beside this string
 * already has it removed — so printing the bare fat still names a fat that is
 * not in the dish. "No oil" drops the name entirely, because at zero grams
 * *which* oil is not a fact about the food any more. "Light" keeps the name
 * and qualifies it, since a fifth of a spoon of groundnut is still groundnut.
 *
 * Normal is left unqualified rather than spelled out: it is the overwhelming
 * majority of rows and "Palmolein (normal)" would put a word on every line to
 * earn its absence on two.
 */
private fun oilTerm(row: PlanItemWithFood): String? {
    val oil = row.item.oil?.takeIf { it.isNotBlank() && row.food.usesCookingOil() }
        ?: return null
    val amount = row.item.oilAmount
        ?.takeIf { row.food.usesOilAmountAxis() }
        ?.let(OilAmount::fromName)
    return when (amount) {
        OilAmount.None -> "No oil"
        OilAmount.Light -> "$oil (light)"
        OilAmount.Normal, null -> oil
    }
}
