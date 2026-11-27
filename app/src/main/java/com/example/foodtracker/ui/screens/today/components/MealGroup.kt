package com.example.foodtracker.ui.screens.today.components

import com.example.foodtracker.ui.theme.StatusOver
import com.example.foodtracker.util.DateUtil
import com.example.foodtracker.domain.MealTiming
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Cookie
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Sunrise
import com.composables.icons.lucide.Trash2
import com.example.foodtracker.ui.components.atoms.PastelIcon
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.pressable
import com.example.foodtracker.ui.theme.Pastel
import com.example.foodtracker.ui.theme.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.db.FoodLogWithFood
import com.example.foodtracker.data.db.nutrients
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.countText
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * The day's diary: one card, four meal rows, each with a pastel icon, its
 * total, and a round add button. The current meal is tagged "Now" and opens
 * by default. Entries are tap-to-edit and swipe-to-delete (undo lives in the
 * caller's snackbar).
 */

/** Entry rows sit indented under their meal so the hierarchy reads without a box. */
/** Lines an entry up under the meal name, past the 40dp icon and its gap. */
private val ENTRY_INDENT = 52.dp

@Composable
fun MealsSection(
    groups: Map<MealType, List<FoodLogWithFood>>,
    /** The meal the clock says you are in — null on any day but today. */
    activeMeal: MealType?,
    totalKcal: Double,
    onAdd: (MealType) -> Unit,
    onEditEntry: (FoodLogWithFood) -> Unit,
    onDeleteEntry: (FoodLogWithFood) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which meal is open. Defaults to the one the wall clock says you are in,
    // which on a normal visit is the one you came to look at.
    var expanded by rememberSaveable { mutableStateOf(activeMeal?.name ?: "") }
    // No enclosing card. The meals are the longest block on the screen, and a
    // border around a list that already has rules between its rows is a second
    // boundary doing the first one's job — structure is hairlines, not
    // boxes, and a box drawn around rules reads as Direction A's card stack.
    Section(
        title = "Meals",
        modifier = modifier,
        trailing = {
            Text(
                "${grouped(totalKcal)} kcal",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        MealType.entries.forEachIndexed { index, meal ->
            if (index > 0) AppDivider()
            val rows = groups[meal].orEmpty()
            MealGroup(
                meal = meal,
                rows = rows,
                isNow = meal == activeMeal,
                isExpanded = expanded == meal.name && rows.isNotEmpty(),
                onToggle = { expanded = if (expanded == meal.name) "" else meal.name },
                onAdd = { onAdd(meal) },
                onEditEntry = onEditEntry,
                onDeleteEntry = onDeleteEntry,
            )
        }
    }
}

internal fun MealType.icon(): ImageVector = when (this) {
    MealType.Breakfast -> Lucide.Sunrise
    MealType.Lunch -> Lucide.Sun
    MealType.Snack -> Lucide.Cookie
    MealType.Dinner -> Lucide.Moon
}

private fun MealType.pastel(): Pastel = when (this) {
    MealType.Breakfast -> Pastel.Peach
    MealType.Lunch -> Pastel.Mint
    MealType.Snack -> Pastel.Rose
    MealType.Dinner -> Pastel.Lavender
}

@Composable
private fun MealGroup(
    meal: MealType,
    rows: List<FoodLogWithFood>,
    isNow: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onEditEntry: (FoodLogWithFood) -> Unit,
    onDeleteEntry: (FoodLogWithFood) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val kcal = rows.sumOf { it.nutrients().calories }
    // Opaque, so the swipe-to-delete backdrop cannot show through at rest.
    val rowFill = cardColor()
    val chevron by animateFloatAsState(if (isExpanded) 180f else 0f, label = "mealChevron")
    Column(
        Modifier
            .fillMaxWidth()
            .background(rowFill)
            .animateContentSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = rows.isNotEmpty(), onClick = onToggle)
                .padding(vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PastelIcon(meal.icon(), meal.pastel())
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meal.displayName, style = AppText.titleM, color = scheme.onSurface)
                    if (rows.any { it.isLate() }) {
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            "Late",
                            style = AppText.eyebrow,
                            color = StatusOver,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(StatusOver.copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.s, vertical = 2.dp),
                        )
                    }
                    if (isNow) {
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            "Now",
                            style = AppText.eyebrow,
                            color = scheme.onTertiaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(scheme.tertiaryContainer)
                                .padding(horizontal = Spacing.s, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    if (rows.isEmpty()) "Nothing yet"
                    else "${grouped(kcal)} kcal · ${rows.size} ${if (rows.size == 1) "item" else "items"}" +
                        " · ${MealTiming.label(rows.maxOf { MealTiming.eatingMinute(it.eatenAt()) })}",
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (rows.isNotEmpty()) {
                Icon(
                    Lucide.ChevronDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).rotate(chevron),
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Box(
                Modifier
                    .size(48.dp)
                    .semantics { role = Role.Button; contentDescription = "Add to ${meal.displayName}" }
                    .pressable(scaleTo = 0.85f, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Plus, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (isExpanded) {
            rows.forEach { row ->
                MealEntryRow(
                    row = row,
                    containerColor = rowFill,
                    onEdit = { onEditEntry(row) },
                    onDelete = { onDeleteEntry(row) },
                )
            }
            Spacer(Modifier.height(Spacing.s))
        }
    }
}

/**
 * One logged food. Tap to edit, swipe either way to delete.
 *
 * The swipe is bidirectional on purpose: this row is reached one-handed with a
 * thumb, and forcing a direction means half the gestures do nothing. Undo lives
 * in the caller's snackbar, which is what makes a destructive swipe acceptable
 * at all.
 *
 * **The delete must not live in `confirmValueChange`.** That parameter is a
 * *predicate* — Compose calls it to ask whether a target state is allowed, and
 * it may call it speculatively, more than once, and while re-resolving anchors
 * on an ordinary layout change rather than only in response to a gesture. An
 * earlier version deleted from inside it and returned false; a layout change
 * was then enough to make every visible row answer the question by deleting
 * itself, which wiped the day. The predicate is now pure, and the delete runs
 * once from an effect keyed on the state actually reaching a dismissed value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealEntryRow(
    row: FoodLogWithFood,
    containerColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Keyed on the entry: these rows are emitted by a forEach, so an unkeyed
    // remember is positional and would hand row N's half-swiped state to
    // whatever row lands at index N after a delete.
    val dismissState = key(row.log.id) { rememberSwipeToDismissBoxState() }

    LaunchedEffect(dismissState.currentValue, row.log.id) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
            // Put the box back: the row's removal arrives through the database
            // flow, and an undo has to be able to restore it. Left dismissed,
            // a restored row would re-render already swiped away.
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scheme.errorContainer)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Lucide.Trash2,
                    contentDescription = null,
                    tint = scheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(containerColor)
                .clickable(onClick = onEdit)
                .padding(
                    start = ENTRY_INDENT,
                    end = Spacing.s,
                    top = Spacing.s,
                    bottom = Spacing.s,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.food.name,
                style = AppText.body,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                "${countText(row.log.servings)} ×  ·  ${grouped(row.nutrients().calories)} kcal  ·  ",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
            Text(
                MealTiming.label(row.eatenAt()),
                style = AppText.bodyS,
                color = if (row.isLate()) StatusOver else scheme.onSurfaceVariant,
            )
        }
    }
}

private fun FoodLogWithFood.eatenAt() = MealTiming.timeOf(log.loggedAtMillis, DateUtil.zoneId())

private fun FoodLogWithFood.isLate() = MealTiming.isLate(eatenAt())
