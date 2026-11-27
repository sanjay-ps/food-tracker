package com.example.foodtracker.ui.components

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.example.foodtracker.ui.components.atoms.AppModalSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.NutrientTarget
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.db.FoodLogWithFood
import com.example.foodtracker.data.db.nutrients
import com.example.foodtracker.data.subFieldsFor
import com.example.foodtracker.ui.components.atoms.AppBadge
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.Meter
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * "Where did today's iron come from?" — the sheet behind a tap on any nutrient.
 *
 * Lifted out of `HomeScreen.kt` during the rewrite. It was
 * private to a 1 176-line screen that no longer exists, and the all-nutrients page reaches it
 * from the all-nutrients sheet as well as from Today, so it had to become a
 * component.
 *
 * Three changes from the version inside Home: the rows are rebuilt on the
 * [Meter] atom instead of a fourth hand-rolled `Box` pair; house rule 6 adds the
 * [onFindRichFoods] call to action — a shortfall you cannot act on is just bad
 * news, and the app already knows which foods are rich in it; and it is now a
 * *page* rather than a sheet of its own, so arriving from the all-nutrients
 * list does not close that list out from under the reader.
 *
 * [onBack] renders the return affordance. Null when this page was opened
 * directly and there is nothing behind it.
 */

private data class NutrientContribution(
    val name: String,
    val detail: String,
    val amount: Double,
    val shareOfTotal: Float,
)

/**
 * Host the contribution detail as a bottom sheet over whatever raised it.
 *
 * One behaviour everywhere: over Today when a named shortfall is tapped, over
 * the all-nutrients page when a cell is tapped. Because the thing underneath is
 * always a *page*, back always returns somewhere sensible and there is no
 * in-sheet back stack to maintain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutrientContributionSheet(
    field: NutrientField,
    consumed: Nutrients,
    goals: NutrientGoals,
    logs: List<FoodLogWithFood>,
    onDismiss: () -> Unit,
    onFindRichFoods: ((String) -> Unit)? = null,
) {
    AppModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        NutrientContributionContent(
            field = field,
            consumed = consumed,
            goals = goals,
            logs = logs,
            onFindRichFoods = onFindRichFoods,
        )
    }
}

@Composable
fun NutrientContributionContent(
    field: NutrientField,
    consumed: Nutrients,
    goals: NutrientGoals,
    logs: List<FoodLogWithFood>,
    onBack: (() -> Unit)? = null,
    onFindRichFoods: ((String) -> Unit)? = null,
) {
    val total = field.read(consumed)
    val goal = field.goal(goals)
    val isLimit = field.target == NutrientTarget.Limit
    val isUntargeted = field.target == NutrientTarget.Untargeted

    // Combine repeat logs of the same food into one row, then rank by
    // contribution. Remembered because it walks every log row and does nutrient
    // maths per food — it depends on the logs and the field, not on how many
    // times the sheet recomposes while it animates in.
    val contributions = remember(field, logs, total) {
        logs
            .groupBy { it.food.id }
            .mapNotNull { (_, rows) ->
                val food = rows.first().food
                val grams = rows.sumOf { it.log.grams }
                // Summed per row rather than from the food once, because two
                // entries for the same dish can have been cooked in different
                // oils — and for saturated fat or vitamin E that is the whole
                // difference between them.
                val amount = field.read(rows.fold(Nutrients.Zero) { a, r -> a + r.nutrients() })
                if (amount <= 0.0) return@mapNotNull null
                val entryNote = if (rows.size > 1) " · ${rows.size} entries" else ""
                NutrientContribution(
                    name = food.name,
                    detail = "${grams.toInt()} g$entryNote",
                    amount = amount,
                    shareOfTotal = if (total > 0) (amount / total).toFloat() else 0f,
                )
            }
            .sortedByDescending { it.amount }
    }

    run {
        // LazyColumn so long contribution lists scroll within the sheet instead
        // of overflowing off-screen. ModalBottomSheet provides the nested-scroll
        // connection, so dragging the list past its top also drags the sheet.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item("header") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Back to the all-nutrients list, in place. The sheet
                        // itself never closes, so the list is still where it
                        // was — same scroll position, same category.
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.padding(end = Spacing.xs),
                            ) {
                                Icon(
                                    Lucide.ArrowLeft,
                                    contentDescription = "Back to all nutrients",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            field.label,
                            style = AppText.titleL,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // A capped nutrient says so; a target says nothing,
                        // since "work towards this number" is the reader's
                        // default and only the exceptions need calling out.
                        if (isLimit) {
                            Spacer(Modifier.width(Spacing.s))
                            AppBadge("LIMIT")
                        }
                    }
                    val targetWord = if (isLimit) "limit" else "goal"
                    val goalSuffix =
                        if (goal > 0) {
                            " · ${((total / goal) * 100).roundToInt()}% of " +
                                "${formatValue(goal)} ${field.unit.symbol} $targetWord"
                        } else ""
                    Text(
                        "${formatValue(total)} ${field.unit.symbol} today$goalSuffix",
                        style = AppText.bodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isLimit) {
                        Text(
                            "Staying under this is the win — there is no shortfall to make up.",
                            style = AppText.bodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The absence of a number is the one thing here that looks
                    // like a bug, so it gets a sentence rather than blank space.
                    if (isUntargeted) {
                        Text(
                            "Tracked, but not scored. No current dietary guideline sets a " +
                                "daily figure for ${field.label.lowercase()} — saturated fat is " +
                                "the number to watch for LDL. The amount is here to look at, " +
                                "not to hit or avoid.",
                            style = AppText.bodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Fiber and protein break down further. This lives here
                    // rather than beside the bar on Today: the bars are the
                    // day's scoreboard, and adding rows to them makes the
                    // scoreboard longer for everyone whether or not they care.
                    // Behind a tap it costs nothing to ignore.
                    SubBreakdown(field.key, consumed, logs)
                    Spacer(Modifier.height(Spacing.inCard))
                }
            }

            if (contributions.isEmpty()) {
                item("empty") {
                    Text(
                        when {
                            isLimit ->
                                "Nothing logged today contributes ${field.label}. " +
                                    "That is exactly where you want to be."
                            // Neither a win nor a gap — there is nothing to close.
                            isUntargeted ->
                                "Nothing logged today contributes ${field.label}."
                            else ->
                                "Nothing logged today contributes ${field.label}. " +
                                    "Log foods rich in it to close the gap."
                        },
                        style = AppText.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val barColor = field.color()
                items(contributions, key = { it.name }) { c ->
                    ContributionRow(c, field.unit.symbol, barColor)
                }
            }

            // Only for a goal that is short: offering "find foods rich in
            // sodium" to someone over their sodium cap would be absurd, and
            // offering it for a goal already met is noise.
            if (onFindRichFoods != null && !isLimit && goal > 0 && total < goal) {
                item("rich") {
                    Column {
                        Spacer(Modifier.height(Spacing.inCard))
                        AppDivider()
                        AppTextButton(
                            text = "Find foods rich in ${field.label}",
                            onClick = { onFindRichFoods(field.key) },
                        )
                    }
                }
            }
        }
    }
}

/** Name / grams / amount / share% / meter. The share meter is what makes the ranking legible. */
@Composable
private fun ContributionRow(c: NutrientContribution, unit: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    c.name,
                    style = AppText.titleM,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    c.detail,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.inCard))
            Column(horizontalAlignment = Alignment.End) {
                ValueText(c.amount, unit)
                Text(
                    "${(c.shareOfTotal * 100).roundToInt()}%",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Meter(fraction = c.shareOfTotal, color = color)
    }
}

/**
 * The optional detail for Fiber and Protein — soluble/insoluble, and essential
 * amino acids with leucine called out.
 *
 * Renders nothing unless at least one food logged today carries the breakdown,
 * which is the normal state for a diary full of custom and AI-filled foods.
 * When it does render it says what share of the parent it covers, because
 * summing only the foods that happen to have data would otherwise present a
 * partial figure as a complete one.
 */
@Composable
private fun SubBreakdown(fieldKey: String, consumed: Nutrients, logs: List<FoodLogWithFood>) {
    val subs = subFieldsFor(fieldKey)
    if (subs.isEmpty()) return
    if (subs.none { it.read(consumed) > 0.0 }) return

    // What fraction of today's fiber (or protein) came from foods that have a
    // breakdown at all. Walks the day's logs, so it is remembered on them.
    val coverage = remember(fieldKey, logs) {
        var total = 0.0
        var covered = 0.0
        logs.forEach { row ->
            val n = row.food.nutrientsPer100g() * (row.log.grams / 100.0)
            val parent = if (fieldKey == "fiber") n.fiber else n.protein
            val hasDetail = if (fieldKey == "fiber") n.hasFiberSplit() else n.hasAminoAcids()
            total += parent
            if (hasDetail) covered += parent
        }
        if (total <= 0.0) 1.0 else covered / total
    }

    val parentLabel = if (fieldKey == "fiber") "fiber" else "protein"
    NutrientSubRows(
        rows = subs.map { sub -> NutrientSubRow(sub.label, "${formatValue(sub.read(consumed))} g") },
        note = if (coverage >= 0.995) null else
            // Not "published": seeded foods carry figures from composition
            // tables, but an AI-filled food's amino split may be a typical
            // figure for its family rather than a measurement of that food.
            // "Carry a breakdown" is true of both; the old wording claimed a
            // provenance the newer half of these numbers does not have.
            "From the ${(coverage * 100).roundToInt()}% of today's $parentLabel " +
                "whose foods carry a breakdown.",
    )
}
