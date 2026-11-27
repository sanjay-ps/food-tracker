package com.example.foodtracker.ui.screens.trends.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.screens.trends.MacroBar
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.MacroCarbs
import com.example.foodtracker.ui.theme.MacroFat
import com.example.foodtracker.ui.theme.MacroProtein
import com.example.foodtracker.ui.theme.Spacing

private val CHART_HEIGHT = 150.dp
private val AXIS_HEIGHT = 18.dp

/**
 * Macros as a stacked column per day, adopted from reference shots 1 and 9.
 *
 * **What it replaces and why.** The macros card used to be four sparkline rows,
 * one per macro, each on its own invisible y-axis. Four rows answer "was my
 * protein higher on Tuesday than Monday" — a question nobody asks — and cannot
 * answer the one that matters, which is what a *day* looked like. Stacking puts
 * the three macros in one column, so the shape of a day is the shape of a bar
 * and a week of them is directly comparable: same axis, same baseline, same
 * width.
 *
 * **Three segments, not four.** Fiber is not in the stack even though it has a
 * goal and a colour, because fiber *is* carbohydrate — it is already inside the
 * carbs number, and stacking it beside carbs would draw the same grams twice
 * and inflate every bar. The three segments sum to something real: the grams of
 * macronutrient eaten that day.
 *
 * **Grams, not energy share.** Energy share would normalise every bar to the
 * same height, which is a different chart answering a different question
 * (composition only). Grams keep the height meaningful — a short bar is a light
 * day — and the composition is still legible as the ratio of the segments.
 *
 * Segment colours come straight from the data palette, so protein here is the
 * same coral as protein on Today's legend and in a search result.
 *
 * **The editorial pass left the drawing model alone** — same grams, same three
 * segments, same clipped silhouette, same two gridlines. What it changed is the
 * framing: the enclosing card is gone, so the bars are measured against 32dp
 * more width and a 30-bar month is that much less of a texture; the legend
 * swatch is Today's 6dp square rather than a circle; and the gaps around the
 * legend are on the `Spacing` scale.
 */
@Composable
fun MacroStackedBars(
    bars: List<MacroBar>,
    avgCarbs: Double,
    avgProtein: Double,
    avgFat: Double,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisStyle = AppText.bodyS.copy(color = axisColor)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT + AXIS_HEIGHT),
        ) {
            if (bars.isEmpty()) return@Canvas
            val chartH = size.height - AXIS_HEIGHT.toPx()
            val maxTotal = bars.maxOf { it.total }.coerceAtLeast(1.0)

            // A baseline and one midline. Structure comes from hairlines, and
            // two lines is as much grid as a bar chart needs to be read against.
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = chartH * f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            // Bars share the width evenly and keep a fixed proportion of their
            // slot, so the chart looks the same at 7 bars and at 30.
            val slot = size.width / bars.size
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(minOf(barW / 2f, 4.dp.toPx()), minOf(barW / 2f, 4.dp.toPx()))

            bars.forEachIndexed { index, bar ->
                val x = index * slot + (slot - barW) / 2f
                val totalH = (bar.total / maxTotal).toFloat() * chartH
                if (totalH <= 0f) return@forEachIndexed

                // One rounded silhouette, filled in segments. Rounding each
                // segment on its own would leave hairline gaps between them and
                // make a thin segment read as a separate floating bar; clipping
                // the whole column instead keeps one pill with three bands.
                val column = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(Offset(x, chartH - totalH), Size(barW, totalH)),
                            radius,
                        )
                    )
                }
                clipPath(column) {
                    var y = chartH
                    listOf(
                        bar.carbs to MacroCarbs,
                        bar.protein to MacroProtein,
                        bar.fat to MacroFat,
                    ).forEach { (grams, color) ->
                        val h = (grams / maxTotal).toFloat() * chartH
                        if (h > 0f) {
                            drawRect(color = color, topLeft = Offset(x, y - h), size = Size(barW, h))
                            y -= h
                        }
                    }
                }
            }

            // Only the ends are labelled. Thirty dates along a 348dp axis is a
            // smear; the two that bound the range are what the eye needs to
            // know which way time runs.
            listOf(0 to bars.first().label, bars.lastIndex to bars.last().label)
                .distinctBy { it.first }
                .forEach { (index, label) ->
                    val layout = measurer.measure(AnnotatedString(label), style = axisStyle)
                    val centre = index * slot + slot / 2f
                    val lx = (centre - layout.size.width / 2f)
                        .coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(lx, chartH + Spacing.xs.toPx()))
                }
        }

        Spacer(Modifier.height(Spacing.l))

        // The legend carries the averages, so the card says what a typical day
        // held without a second row of numbers under the chart.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            MacroLegendItem("Carbs", avgCarbs, MacroCarbs, Modifier.weight(1f))
            MacroLegendItem("Protein", avgProtein, MacroProtein, Modifier.weight(1f))
            MacroLegendItem("Fat", avgFat, MacroFat, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroLegendItem(
    label: String,
    avgGrams: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A 6dp square with a 1dp corner, which is Today's macro legend swatch
            // exactly — it was an 8dp box under Radius.sm, i.e. a circle, and a
            // round dot beside a column chart made of square-ended bars was a
            // second shape language for no reading of the data.
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                label,
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ValueText(value = avgGrams, unit = "g avg")
    }
}
