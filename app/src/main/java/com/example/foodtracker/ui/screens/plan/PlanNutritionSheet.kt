package com.example.foodtracker.ui.screens.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodtracker.data.AllNutrientFields
import com.example.foodtracker.data.NutrientCategory
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppSheet
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.MacroCarbs
import com.example.foodtracker.ui.theme.MacroFat
import com.example.foodtracker.ui.theme.MacroProtein
import com.example.foodtracker.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * What the plan comes to, if you eat all of it.
 *
 * **Raised, not inline.** The plan editor is a list you act on — four meals and
 * an "add food" row under each — and thirty nutrient readings printed down the
 * middle of it would bury the thing you came to do. It is also not something
 * you read every visit: you want it while deciding what to add, which is a
 * question you ask a few times and then stop asking. A sheet is exactly that
 * shape: one tap from the page, gone when you are done, and the page keeps its
 * scroll position because it never went away.
 *
 * **It is composition, not a score.** The plan screen deliberately has no
 * adherence figure — no "4 of 7 planned", nothing you can pass or fail at — and
 * this does not smuggle one in. The distinction is what the number is *about*:
 * an adherence score grades your behaviour against the plan, where this
 * describes the plan itself. Reading it helps you decide what to add, which is
 * the same job Rich-in does from the other direction.
 *
 * **Which is why there are no status colours and no meters.** Every other
 * nutrient list in the app paints a reading green when it clears 80% of the
 * target and red when it passes a cap — honest for a whole day's diary, and
 * actively wrong here. Most plans are not a whole day: a breakfast-only plan
 * would come up twenty-odd failures against the daily targets, and the one
 * thing this screen must never do is tell you that planning breakfast is a
 * shortfall. So the target is printed as a plain reference and nothing is
 * coloured. The one exception is the donut, whose colours are the macro
 * identity used on Today's hero, and which encodes a share of the plan's own
 * calories — not of anything it is supposed to reach.
 */
@Composable
fun PlanNutritionSheet(
    totals: Nutrients,
    goals: NutrientGoals,
    itemCount: Int,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AppSheet(title = "If you eat the whole plan", onDismiss = onDismiss) {
        Section(
            title = "Energy",
            trailing = {
                Text(
                    "${itemCount} ${if (itemCount == 1) "food" else "foods"}",
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                )
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MacroDonut(totals, Modifier.size(132.dp))
                Spacer(Modifier.width(Spacing.xl))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            grouped(totals.calories),
                            style = AppText.numberXL,
                            color = scheme.onSurface,
                        )
                        Text(
                            " kcal",
                            style = AppText.bodyS,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "of ${grouped(goals.calories)} kcal a day",
                        style = AppText.bodyS,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.m))
                    MacroLegend(totals)
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section(
            title = "Everything in it",
            trailing = {
                Text(
                    "per day",
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                )
            },
        ) {
            Text(
                "Your daily target is shown beside each one as a reference. A plan that " +
                    "covers part of the day will not reach them — that is not a shortfall.",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.l))

            NutrientCategory.entries.forEach { category ->
                val fields = AllNutrientFields.filter { it.category == category }
                if (fields.isEmpty()) return@forEach
                Text(
                    category.name,
                    style = AppText.eyebrow,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
                fields.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        pair.forEach { field ->
                            PlanNutrientCell(
                                label = field.label,
                                value = field.read(totals),
                                goal = field.goal(goals),
                                unit = field.unit.symbol,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                AppDivider()
                Spacer(Modifier.height(Spacing.l))
            }
        }
    }
}

/**
 * One reading: what the plan gives, and the day's target under it.
 *
 * No meter. See the sheet's docstring — a bar has to be a fraction of
 * something, and against a daily target a half-day plan would draw twenty-nine
 * half-empty bars, which reads as a verdict on the plan rather than a
 * description of it.
 */
@Composable
private fun PlanNutrientCell(
    label: String,
    value: Double,
    goal: Double,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            label,
            style = AppText.bodyS,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatValue(value), style = AppText.numberM, color = scheme.onSurface)
            Text(
                " $unit",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            if (goal > 0) "of ${formatValue(goal)} $unit" else "no target",
            style = AppText.bodyS,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.m))
    }
}

/**
 * The plan's calories split between carbs, protein and fat.
 *
 * A ring rather than a filled pie: the hole is where the share being read sits,
 * and three arcs on one track is the same vocabulary as the meters everywhere
 * else in the app — a filled circle is the only chart shape here that would
 * have no relative anywhere on screen.
 *
 * The split is by **calories**, not grams. 40 g of fat and 40 g of carbs are the
 * same bar by weight and more than twice apart in energy, so a gram split would
 * draw a picture of the wrong thing. 4/4/9 per gram, the same arithmetic the
 * autofill prompt holds the model to.
 */
@Composable
private fun MacroDonut(totals: Nutrients, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val slices = remember(totals) { macroSlices(totals) }
    val track = scheme.surfaceContainerHighest

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val stroke = 18.dp.toPx()
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset(
                (size.width - arcSize.width) / 2,
                (size.height - arcSize.height) / 2,
            )
            // The empty track, so a plan with no macros at all is still a ring
            // rather than nothing.
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            // 1.5dp of gap between slices, in degrees, so adjacent arcs read as
            // two readings and not one bent bar. Dropped when a slice is too
            // small to survive it.
            var start = -90f
            slices.forEach { (color, fraction) ->
                val sweep = fraction * 360f
                if (sweep <= 0f) return@forEach
                val gap = if (sweep > 8f) 2f else 0f
                drawArc(
                    color = color,
                    startAngle = start + gap / 2,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        // The ring's hole states what the ring is a split of, so the chart does
        // not need a caption outside it.
        Text(
            "SPLIT",
            style = AppText.eyebrow,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MacroLegend(totals: Nutrients) {
    val scheme = MaterialTheme.colorScheme
    val entries = listOf(
        Triple("Carbs", MacroCarbs, totals.carbs),
        Triple("Protein", MacroProtein, totals.protein),
        Triple("Fat", MacroFat, totals.fat),
    )
    val kcal = macroKcal(totals)
    Column {
        entries.forEachIndexed { index, (label, color, grams) ->
            if (index > 0) Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    label,
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${formatValue(grams)} g · ${sharePercent(label, totals, kcal)}%",
                    style = AppText.bodyS,
                    color = scheme.onSurface,
                )
            }
        }
    }
}

/** kcal from the three macros, which is the denominator the ring is drawn against. */
private fun macroKcal(n: Nutrients): Double = n.carbs * 4 + n.protein * 4 + n.fat * 9

private fun macroSlices(n: Nutrients): List<Pair<Color, Float>> {
    val total = macroKcal(n)
    if (total <= 0) return emptyList()
    return listOf(
        MacroCarbs to (n.carbs * 4 / total).toFloat(),
        MacroProtein to (n.protein * 4 / total).toFloat(),
        MacroFat to (n.fat * 9 / total).toFloat(),
    )
}

private fun sharePercent(label: String, n: Nutrients, total: Double): Int {
    if (total <= 0) return 0
    val kcal = when (label) {
        "Carbs" -> n.carbs * 4
        "Protein" -> n.protein * 4
        else -> n.fat * 9
    }
    return (kcal / total * 100).roundToInt()
}
