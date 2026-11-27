package com.example.foodtracker.ui.components.serving

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.example.foodtracker.ui.theme.Radius
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Rich
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.theme.dotTexture
import com.example.foodtracker.data.NutrientGoals
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.foodtracker.ui.theme.Spacing

/** Motion token — the same 450ms every meter and ring in the app uses. */
private const val ANIM_MS = 450

/**
 * The pinned top of the serving sheet.
 *
 * Name, optional brand, optional provenance line, and the live readout. The
 * readout is the reason this is pinned rather than scrolled: it is the answer
 * to the question the whole sheet exists to ask, and while you drag the amount
 * slider it is the only thing on screen that is changing.
 *
 * The calorie figure interpolates over [ANIM_MS] rather than snapping, which is
 * what makes a variant tap read as *this is now a different amount* instead of
 * a flicker. Every numeric style in the app carries `tnum`, so the digits hold
 * their columns for the whole animation instead of jittering.
 *
 * The hero card sits on Today's textured gradient. Its figures carry the
 * nutrient hues every ring and chart uses, and each bar is that nutrient's
 * share of today's target, so a serving reads as part of the day.
 */
@Composable
internal fun SheetHeader(
    name: String,
    brand: String?,
    subtitle: String?,
    nutrients: Nutrients,
    /** Today's targets, for the share-of-day figures; null until the profile loads. */
    goals: NutrientGoals?,
    modifier: Modifier = Modifier,
    /** One line on how the dish is made, from the food catalogue. */
    made: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val kcal by animateFloatAsState(
        targetValue = nutrients.calories.toFloat(),
        animationSpec = tween(ANIM_MS),
        label = "sheetKcal",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = AppText.titleL,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!brand.isNullOrBlank()) {
                    Text(
                        brand,
                        style = AppText.bodyS,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.width(Spacing.s))
                Text(
                    subtitle,
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (!made.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(made, style = AppText.bodyS, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Spacing.m))
        // A small hero: what this serving is and how much of the day it takes,
        // on a dotted gradient that runs left to right in the proportion its
        // calories come from carbs, protein and fat.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(macroBrush(nutrients))
                .dotTexture(color = scheme.onSurface.copy(alpha = 0.10f))
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("This serving", style = AppText.eyebrow, color = scheme.onSurfaceVariant)
                    ValueText(value = kcal.toDouble(), unit = "kcal", style = AppText.numberXL)
                }
                if (goals != null && goals.calories > 0) {
                    Text(
                        "${(nutrients.calories / goals.calories * 100).roundToInt()}% of your day",
                        style = AppText.label,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(scheme.surface.copy(alpha = 0.7f))
                            .padding(horizontal = Spacing.s + 2.dp, vertical = Spacing.xs),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                MacroCell("Carbs", nutrients.carbs, goals?.carbs, Rich.Carbs.ink, Modifier.weight(1f))
                MacroCell("Protein", nutrients.protein, goals?.protein, Rich.Protein.ink, Modifier.weight(1f))
                MacroCell("Fat", nutrients.fat, goals?.fat, Rich.Fat.ink, Modifier.weight(1f))
                MacroCell("Fiber", nutrients.fiber, goals?.fiber, Rich.Fiber.ink, Modifier.weight(1f))
            }
        }
    }
}

/**
 * A horizontal blend of the macro tints, each centred on its share of the
 * calories: a mostly-carb food is mostly saffron, a fried one turns lavender.
 * One macro alone gives a flat tint; none (water, black coffee) the plain card.
 */
@Composable
private fun macroBrush(n: Nutrients): Brush {
    val parts = listOf(n.carbs * 4 to Rich.Carbs.tint, n.protein * 4 to Rich.Protein.tint, n.fat * 9 to Rich.Fat.tint)
        .filter { it.first > 0 }
    val total = parts.sumOf { it.first }
    if (parts.isEmpty() || total <= 0) return SolidColor(cardColor())
    if (parts.size == 1) return SolidColor(parts[0].second)
    var start = 0.0
    val stops = parts.map { (kcal, color) ->
        val mid = (start + kcal / 2) / total
        start += kcal
        mid.toFloat() to color
    }
    return Brush.horizontalGradient(*stops.toTypedArray())
}

/** One macro: grams in its own hue, and a bar for its share of today's target. */
@Composable
private fun MacroCell(label: String, grams: Double, goal: Double?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = AppText.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text("${formatValue(grams)} g", style = AppText.numberM, color = color, maxLines = 1)
        Spacer(Modifier.height(Spacing.xs + 2.dp))
        ShareBar(fraction = if (goal != null && goal > 0) (grams / goal).toFloat() else 0f, color = color)
    }
}

/** A thin bar on a translucent track; fills to [fraction] of the width. */
@Composable
internal fun ShareBar(
    fraction: Float,
    color: Color,
    track: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    height: Dp = 4.dp,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.pill))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(Radius.pill))
                .background(color),
        )
    }
}
