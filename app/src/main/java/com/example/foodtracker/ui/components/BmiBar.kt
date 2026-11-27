package com.example.foodtracker.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.foodtracker.domain.Bmi
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.BmiNormal
import com.example.foodtracker.ui.theme.BmiObese
import com.example.foodtracker.ui.theme.BmiOverweight
import com.example.foodtracker.ui.theme.BmiUnderweight
import com.example.foodtracker.ui.theme.Spacing
import kotlin.math.abs

/**
 * Horizontal coloured band showing BMI categories with a triangular marker
 * for the user's current BMI and (optionally) one for their goal weight's
 * BMI. Used in the onboarding Basics page and the profile screen.
 *
 * The four band colours are the `Bmi*` tokens from `theme/Color.kt` — the same
 * clinical blue/green/amber/red reading the convention expects (these are
 * medical categories, not brand colour), re-derived against black rather than
 * flipped from the Material 300/400 tones they started as, which glared on
 * `#000000`. Markers use theme tokens: `onSurface` for Now, `primary` for Goal.
 *
 * **The caption is now a row like every other row.** It used to be one
 * `labelLarge` sentence — `BMI 23.4 · Normal` — with the goal hung off the far
 * end, which made the single most interesting number on the profile screen the
 * same size as the words around it. Rule 2 says the number is the loud half, so
 * the reading splits: an eyebrow and the category name on the left, the figure
 * itself at [AppText.numberL] on the right, and the goal as the caption trailing
 * it. That is the same label-left / number-right rhythm as Today's stat rows,
 * which is the point — one rhythm down the page.
 *
 * The band, the markers and the scale labels are unchanged in substance; only
 * their gaps moved onto the `Spacing.*` scale (rule 5) and the labels onto the
 * eyebrow style, which is what the band's numeric ticks were imitating with a
 * raw `10.sp` anyway.
 */
@Composable
fun BmiBar(
    currentBmi: Double,
    goalBmi: Double? = null,
    modifier: Modifier = Modifier,
    currentLabel: String = "Now",
    goalLabel: String = "Goal",
) {
    val cat = Bmi.category(currentBmi)
    val currentMarkerFill = MaterialTheme.colorScheme.onSurface
    val goalMarkerFill = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "BMI",
                    style = AppText.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    cat.label,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%.1f".format(currentBmi),
                    style = AppText.numberL,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (goalBmi != null) {
                    val delta = goalBmi - currentBmi
                    val arrow = if (delta > 0) "↑" else if (delta < 0) "↓" else "→"
                    Text(
                        "$goalLabel $arrow ${"%.1f".format(goalBmi)}",
                        style = AppText.bodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
        ) {
            val barTop = size.height * 0.55f
            val barHeight = size.height * 0.32f

            val range = Bmi.DISPLAY_MAX - Bmi.DISPLAY_MIN
            fun fracFor(bmi: Double): Float =
                ((bmi - Bmi.DISPLAY_MIN) / range).toFloat().coerceIn(0f, 1f)

            // Coloured bands.
            val bands = listOf(
                BmiUnderweight to fracFor(Bmi.UNDERWEIGHT_MAX),
                BmiNormal      to fracFor(Bmi.NORMAL_MAX),
                BmiOverweight  to fracFor(Bmi.OVERWEIGHT_MAX),
                BmiObese       to 1f,
            )
            var prev = 0f
            bands.forEach { (color, end) ->
                drawRect(
                    color = color,
                    topLeft = Offset(prev * size.width, barTop),
                    size = Size((end - prev) * size.width, barHeight),
                )
                prev = end
            }

            fun drawMarker(bmi: Double, fill: Color) {
                val x = fracFor(bmi) * size.width
                val tipY = barTop
                val baseY = tipY - 12.dp.toPx()
                val half = 7.dp.toPx()
                val path = Path().apply {
                    moveTo(x, tipY)
                    lineTo(x - half, baseY)
                    lineTo(x + half, baseY)
                    close()
                }
                drawPath(path, fill)
            }
            drawMarker(currentBmi, currentMarkerFill)
            if (goalBmi != null && abs(goalBmi - currentBmi) > 0.05) {
                drawMarker(goalBmi, goalMarkerFill)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BandLabel("15")
            BandLabel("18.5")
            BandLabel("25")
            BandLabel("30")
            BandLabel("40")
        }
        if (goalBmi != null) {
            Spacer(Modifier.height(Spacing.m))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                LegendMarker(currentLabel, currentMarkerFill)
                LegendMarker(goalLabel, goalMarkerFill)
            }
        }
    }
}

/** A tick on the scale. Eyebrow, because that is what an 11sp tracked numeral is. */
@Composable
private fun BandLabel(text: String) {
    Text(
        text,
        style = AppText.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LegendMarker(label: String, fill: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(width = 10.dp, height = 8.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(0f, size.height)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, fill)
            }
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            label,
            style = AppText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
