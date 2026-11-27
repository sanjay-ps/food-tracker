package com.example.foodtracker.ui.screens.plan

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
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
import com.example.foodtracker.data.db.PlanItemWithFood
import com.example.foodtracker.data.db.nutrients
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.countText
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing

/**
 * Today's plan, as a list to tick off.
 *
 * **A page rather than a block on Today.** It began inline, and with a whole
 * day planned that was ~10 rows mirroring the meal structure immediately above
 * the real meal structure, which pushed the diary and the nutrient summary off
 * the fold. Today now carries one row saying how much is left; this is where
 * the ticking happens. The cost is one tap to get in, which is the right trade:
 * the thing the plan removes is the *search*, not a tap.
 *
 * **It is a shortcut to logging and nothing else.** No adherence score, no
 * total against the day's targets, no progress bar. The tick says *this is
 * already in today's diary* — nothing more — and it is read off the diary
 * rather than stored, so deleting the entry on Today un-ticks it. There is no
 * un-tick control for the same reason there is no tick control: the row does
 * not own the fact it displays.
 *
 * A logged line stops being tappable. Tapping again would log a second
 * helping, which people do eat but which the *plan* has not said — its answer
 * for today has been given. A second helping goes through the ordinary add
 * button, like any other unplanned food.
 */
@Composable
fun PlanTodayScreen(
    onBack: () -> Unit,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PlanTodayViewModel = appViewModel { PlanTodayViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()

    var checking by remember { mutableStateOf<PlanItemWithFood?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Plan",
                subtitle = "Tap a food to add it to today",
                onBack = onBack,
                actions = { AppTextButton(text = "Edit", onClick = { haptics.light(); onEditPlan() }) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = Spacing.screenContent,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            for ((slot, rows) in state.groups) {
                item(slot.storedName) {
                    Section(
                        title = slot.displayName,
                        trailing = {
                            Text(
                                "${grouped(rows.sumOf { it.planned.nutrients().calories })} kcal",
                                style = AppText.bodyS,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) AppDivider()
                            PlannedLine(
                                row = row,
                                onClick = { haptics.light(); checking = row.planned },
                            )
                        }
                    }
                }
            }
        }
    }

    checking?.let { row ->
        PlanCheckSheet(
            row = row,
            onLog = { vm.logPlanned(it) },
            onDismiss = { checking = null },
        )
    }
}

@Composable
private fun PlannedLine(row: PlannedRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val logged = row.logged
    // Logged lines go quiet rather than disappearing: the plan is the same list
    // all day, and a row vanishing as you tick it would make the list reshuffle
    // under your thumb — and leave you unsure whether breakfast was logged or
    // never planned.
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !logged, onClick = onClick)
            .heightIn(min = Sizes.touchTarget)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (logged) Lucide.CircleCheck else Lucide.Circle,
            contentDescription = if (logged) "Already logged" else null,
            // The accent means "on track" and a logged line is exactly that —
            // one of the data uses house rule 3 allows. An empty ring stays
            // neutral: nothing has happened yet.
            tint = if (logged) scheme.secondary else scheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                row.planned.food.name,
                style = AppText.body,
                color = if (logged) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                plannedAmountLabel(row),
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            "${grouped(row.planned.nutrients().calories)} kcal",
            style = AppText.bodyS,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** `2 × 1 idli (35 g)`, dropping the count where it is pinned to one. */
private fun plannedAmountLabel(row: PlannedRow): String {
    val item = row.planned.item
    return if (item.servings == 1.0) item.servingLabel
    else "${countText(item.servings)} × ${item.servingLabel}"
}
