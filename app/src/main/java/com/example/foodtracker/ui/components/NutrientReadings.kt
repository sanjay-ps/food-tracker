package com.example.foodtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodtracker.data.AllNutrientFields
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.NutrientStatus
import com.example.foodtracker.data.NutrientTarget
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.statusFor
import com.example.foodtracker.ui.components.atoms.Meter
import com.example.foodtracker.ui.components.atoms.StatusCell
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * The nutrient molecules.
 *
 * One reading rendered three ways, because 29 nutrients is too many for any
 * single presentation: a [StatusCell] when you only want to know whether today
 * was broadly fine, a [NutrientRow] when a specific one has been called out,
 * and a [NutrientCell] when you have asked to see all of them.
 *
 * [NutrientRow] replaces `NutrientBar`, which drew a third line reading
 * `"62% of goal"` directly under `"8.1 / 18 mg"` — the same fact twice — and
 * had no concept of a limit, so a sodium reading at 40% of its cap rendered
 * identically to an iron reading at 40% of its RDA.
 */

/** The tracked fields in display order, calories excluded — it has the ring. */
val StripFields: List<NutrientField> = AllNutrientFields.filterNot { it.key == "calories" }

/** One nutrient's day: what was eaten, what the target is, and which kind of target. */
data class NutrientReading(
    val field: NutrientField,
    val value: Double,
    val goal: Double,
) {
    // `this.field` throughout: inside a property accessor the bare name is
    // Kotlin's backing-field keyword, not the constructor property of the same
    // name, and it resolves to the property being defined.
    val limit: Boolean get() = this.field.target == NutrientTarget.Limit

    /** No cap, no RDA, nothing to measure against — see [NutrientTarget.Untargeted]. */
    val untargeted: Boolean get() = this.field.target == NutrientTarget.Untargeted

    /** True when this reading belongs in a tally, a strip or a heatmap. */
    val scored: Boolean get() = this.field.scored

    val fraction: Double get() = if (goal > 0) value / goal else 0.0

    // `this.field`, not `field`: inside a property accessor the bare name is
    // Kotlin's backing-field keyword, which would silently resolve to `status`
    // itself rather than to the constructor property of the same name.
    val status: NutrientStatus get() = this.field.statusFor(value, goal)
}

/** Read every tracked field off one day's totals, scored and untargeted alike. */
fun readingsFor(totals: Nutrients, goals: NutrientGoals): List<NutrientReading> =
    StripFields.map { f -> NutrientReading(f, f.read(totals), f.goal(goals)) }

/**
 * The readings a tally may count, and its denominator.
 *
 * Untargeted readings are dropped rather than counted as passes: "28 of 29"
 * with one cell that can never move is a worse answer than "28 of 28".
 */
fun List<NutrientReading>.scored(): List<NutrientReading> = filter { it.scored }

/**
 * A limit's meter is drawn against a cap tick at 80% of the track, so crossing
 * the cap reads as crossing a line rather than as a bar merely filling up.
 *
 * The 0.8 is a drawing constant, not the 80%-of-RDA threshold that decides
 * [NutrientStatus] — they are unrelated numbers that happen to share a value.
 */
private const val CAP_TICK = 0.8f

internal val NutrientReading.meterFraction: Float
    get() = if (limit) (fraction * CAP_TICK).toFloat() else fraction.toFloat()

internal val NutrientReading.capTick: Float?
    get() = if (limit) CAP_TICK else null

/**
 * The whole day's micronutrient state in one 40dp band.
 *
 * This is the core answer to "30 micronutrients is unusually data-heavy":
 * 29 ten-pixel cells fit in a single wrapped row, so the common question — *is
 * today broadly fine?* — is answered with zero reading, and only the exceptions
 * below it get names.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutrientStatusStrip(
    readings: List<NutrientReading>,
    modifier: Modifier = Modifier,
    onCellClick: (() -> Unit)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Untargeted readings are left out entirely. A cell is a verdict, and
        // an untargeted field has none — drawing a permanently neutral square
        // among 28 live ones would read as a nutrient stuck at zero.
        readings.scored().forEach { StatusCell(it.status, onClick = onCellClick) }
    }
}

/** Two lines and a 3dp meter. Full width — for the handful of named exceptions. */
@Composable
fun NutrientRow(
    r: NutrientReading,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = Spacing.inCard)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.field.label,
                style = AppText.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (r.untargeted) {
                    "${formatValue(r.value)} ${r.field.unit.symbol}"
                } else {
                    "${formatValue(r.value)} / ${formatValue(r.goal)} ${r.field.unit.symbol}"
                },
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // No meter without a target. A bar has to be a fraction of something,
        // and inventing a denominator here would smuggle back the very cap the
        // field exists to not have.
        if (!r.untargeted) {
            Spacer(Modifier.height(8.dp))
            Meter(fraction = r.meterFraction, color = r.status.meterColor(), capAt = r.capTick)
        }
    }
}

/**
 * Half-width cell: label, value, meter.
 *
 * 29 of these in a two-column grid is about two screens. 29 three-line
 * `NutrientBar`s was about six, which is why nobody ever scrolled to the
 * vitamins.
 */
@Composable
fun NutrientCell(
    r: NutrientReading,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier.then(
            if (onClick != null) Modifier.fillMaxWidth() else Modifier
        )
    ) {
        Text(
            r.field.label,
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            ValueText(r.value, r.field.unit.symbol)
            Spacer(Modifier.weight(1f))
            Text(
                if (r.untargeted) "no limit" else "/ ${formatValue(r.goal)}",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // See NutrientRow: a meter needs a denominator, and this field has none.
        // The cell is shorter than its neighbours as a result, which is correct —
        // the grid rows are laid out in pairs, not on a shared baseline.
        if (!r.untargeted) {
            Spacer(Modifier.height(8.dp))
            Meter(fraction = r.meterFraction, color = r.status.meterColor(), capAt = r.capTick)
        }
    }
}
