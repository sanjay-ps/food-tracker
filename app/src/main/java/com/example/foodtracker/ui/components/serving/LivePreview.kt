package com.example.foodtracker.ui.components.serving

import com.example.foodtracker.ui.theme.Radius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.foodtracker.data.AllNutrientFields
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.ui.theme.Rich
import com.example.foodtracker.data.NutrientCategory
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.NutrientTarget
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import com.example.foodtracker.ui.components.atoms.softFill
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.StatusOver
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.math.abs

/**
 * The nutrients as they stood the instant a preparation chip was tapped.
 *
 * Captured in the chip's own click handler, where the pre-change figures are
 * still the composed ones. [token] exists so two taps in a row restart the
 * two-second window instead of the first tap's timer cancelling the second.
 */
internal data class PrepFlash(val before: Nutrients, val token: Int)

/** In the hero card already, so not repeated in the grid. */
private val HeroKeys = setOf("calories", "carbs", "protein", "fat", "fiber")

/** The limits, always first: what to keep an eye on. */
private val LimitKeys = listOf("sugar", "saturatedFat", "sodium")

/** Micronutrient tiles after the limits: the ones this food gives you most of. */
private const val MICRO_TILES = 5

/** Shown until targets load, and when a food has none of anything. */
private val FallbackMicros = listOf("iron", "calcium", "potassium", "vitaminC", "vitaminA")

/**
 * *What's in it*, as one thick bar per nutrient in two groups: the limits,
 * then the vitamins and minerals this serving gives you most of, each filled
 * to its share of the day (or of the limit).
 *
 * **The change flash is the point of this component.** Switching palmolein for
 * coconut oil moves saturated fat and vitamin E and leaves calories exactly
 * where they were — that is the oil model's whole claim, and without a signal
 * the control reads as dead. So for two seconds after a prep tap the tiles that
 * moved carry a ↑/↓ delta and their value pulses to the accent. The delta says
 * *what moved*, never whether that is good.
 *
 * @param previous the pre-tap figures, or null when nothing is being flagged.
 */
@Composable
internal fun LivePreview(
    nutrients: Nutrients,
    previous: Nutrients?,
    goals: NutrientGoals?,
    showFatDetail: Boolean,
    modifier: Modifier = Modifier,
) {
    val fields = previewFields(nutrients, goals, showFatDetail)
    val (limits, micros) = fields.partition { it.target == NutrientTarget.Limit }
    Column(modifier.fillMaxWidth()) {
        listOf("Limits" to limits, "Vitamins & minerals" to micros)
            .filter { it.second.isNotEmpty() }
            .forEachIndexed { i, (title, group) ->
                if (i > 0) Spacer(Modifier.height(Spacing.l))
                Text(title, style = AppText.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.s))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    group.forEach { field ->
                        val now = field.read(nutrients)
                        NutrientBar(
                            field = field,
                            value = now,
                            goal = goals?.let(field.goal),
                            delta = previous?.let { before -> (now - field.read(before)).takeIf { moved(it, now) } },
                        )
                    }
                }
            }
    }
}

/**
 * Whether a difference is worth flagging: an absolute floor so 0.02 g of fat
 * earns no badge, and a relative one so a 1 mg wobble on 2 740 mg does not.
 */
private fun moved(delta: Double, now: Double): Boolean =
    abs(delta) >= 0.05 && abs(delta) >= abs(now) * 0.005

/**
 * One nutrient as one thick bar: the track fills, in the nutrient's colour, to
 * its share of the day (or of the limit), and the name, amount and share sit
 * on it in a single line. A limit gone past fills orange; after a prep change
 * the moved amount stands in for the share for a moment.
 */
@Composable
private fun NutrientBar(
    field: NutrientField,
    value: Double,
    goal: Double?,
    delta: Double?,
) {
    val scheme = MaterialTheme.colorScheme
    val share = if (goal != null && goal > 0 && field.scored) value / goal else null
    val overLimit = field.target == NutrientTarget.Limit && (share ?: 0.0) > 1.0
    val hue = if (overLimit) StatusOver else hueOf(field)
    val valueColor by animateColorAsState(
        targetValue = if (delta != null) scheme.tertiary else scheme.onSurface,
        animationSpec = tween(450),
        label = "previewPulse",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(Radius.sm))
            .background(softFill()),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth((share ?: 0.0).coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .background(hue.copy(alpha = BAR_FILL_ALPHA)),
        )
        Row(Modifier.padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shortLabel(field),
                style = AppText.body,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ValueText(value = value, unit = field.unit.symbol, style = AppText.titleM, color = valueColor)
            Text(
                when {
                    delta != null -> (if (delta > 0) "↑" else "↓") + formatValue(abs(delta))
                    share == null -> "–"
                    else -> percent(share)
                },
                style = AppText.bodyS,
                color = when {
                    delta != null -> scheme.tertiary
                    overLimit -> StatusOver
                    else -> scheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(52.dp),
            )
        }
    }
}

private val BarHeight = 36.dp

/** Light enough that the line of text on the bar stays easy to read. */
private const val BAR_FILL_ALPHA = 0.24f

/** "Vitamin B2 (Riboflavin)" → "Vitamin B2". */
private fun shortLabel(field: NutrientField): String = field.label.substringBefore(" (")

private fun percent(share: Double): String {
    val p = share * 100
    return if (p > 0 && p < 1) "<1%" else "${p.roundToInt()}%"
}

/** A nutrient's colour, from the same family as its hero cell. */
@Composable
private fun hueOf(field: NutrientField): Color = when (field.key) {
    "sugar", "carbs" -> Rich.Carbs.ink
    "saturatedFat", "fat", "omega3", "cholesterol" -> Rich.Fat.ink
    "protein" -> Rich.Protein.ink
    "fiber" -> Rich.Fiber.ink
    else -> Rich.Micros.ink
}

/**
 * The grid's tiles: the three limits, then the micronutrients with the biggest
 * share of the day (vitamin E kept in when the oil can change it). Read off
 * [AllNutrientFields] so a label or unit fixed there is fixed here.
 */
private fun previewFields(n: Nutrients, goals: NutrientGoals?, showFatDetail: Boolean): List<NutrientField> {
    fun field(key: String) = AllNutrientFields.firstOrNull { it.key == key }
    val limits = LimitKeys.mapNotNull(::field)
    val pinned = if (showFatDetail) listOfNotNull(field("vitaminE")) else emptyList()
    val candidates = AllNutrientFields.filter {
        it.category != NutrientCategory.Macros && it.scored && it.key !in HeroKeys && it.key !in LimitKeys && it !in pinned
    }
    val ranked = if (goals == null) {
        FallbackMicros.mapNotNull(::field).filter { it !in pinned }
    } else {
        candidates
            .map { it to it.goal(goals).let { g -> if (g > 0) it.read(n) / g else 0.0 } }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    return limits + pinned + ranked.take(MICRO_TILES - pinned.size)
}
