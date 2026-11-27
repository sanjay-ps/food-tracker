package com.example.foodtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

data class LinePoint(val label: String, val value: Double)

/**
 * Room under the plot for one line of axis labels.
 *
 * `bodyS` has an 18sp line box and the labels are drawn [Spacing.xs] below the
 * baseline, so this is exactly what that costs. It was a bare 22dp chosen
 * against a 10sp label that no longer exists.
 */
/** The plot itself, above [AXIS_HEIGHT]. */
private val CHART_HEIGHT = 180.dp

private val AXIS_HEIGHT = 22.dp

/**
 * A line over time, with an optional dashed goal.
 *
 * **The drawing model is deliberately unchanged**: auto-scaled bounds expanded
 * to keep the reference line in view, 10% headroom so the path never grazes an
 * edge, a three-step grid, and sparse x-labels. That was all correct; the pass asked
 * for three cosmetic changes and nothing else.
 *
 * Two of them are here:
 *
 *  - **A 12%→0% gradient under the path.** On `#000000` a 3dp stroke on an
 *    empty field has nothing to sit on, and a run of five charts down Trends
 *    reads as five floating threads. The wash gives each line a body without
 *    adding a fill: it is the line's own hue at 12% at the top of the plot,
 *    gone by the baseline, so it never competes with the stroke and never turns
 *    the card into a filled surface, which the house rules forbid.
 *  - **Dot radius 3.5dp → 3dp.** At 3.5 the dots on a 30-day series touched
 *    each other and the line read as a dotted chain.
 *
 * The third — theming the goal line — was already done: it is
 * `colorScheme.tertiary`, the one colour in the app that means "a number you
 * set yourself".
 *
 * **The editorial pass changed the labels only.** Both the x-axis labels and
 * the goal caption were a hand-rolled `TextStyle(fontSize = 10.sp)` — the one
 * piece of type in the app that was on no scale at all, two sizes below
 * anything else on the page and the reason the charts read as diagrams rather
 * than as part of the document. They are [AppText.bodyS] now, which is what
 * `MacroStackedBars` already used for its axis and what house rule 2 calls a
 * caption. Nothing about the plotted series moved.
 */
@Composable
fun LineChart(
    points: List<LinePoint>,
    lineColor: Color,
    unit: String,
    modifier: Modifier = Modifier,
    minOverride: Double? = null,
    maxOverride: Double? = null,
    /** Optional horizontal dashed reference line — e.g. goal weight. */
    referenceLine: Double? = null,
    /** Caption painted near the reference line (only when [referenceLine] is set). */
    referenceLabel: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val referenceColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .padding(
                    top = Spacing.s,
                    bottom = AXIS_HEIGHT,
                    start = Spacing.s,
                    end = Spacing.s,
                ),
        ) {
            if (points.isEmpty()) return@Canvas
            val values = points.map { it.value }
            val dataMin = values.min()
            val dataMax = values.max()
            // Expand bounds so the reference line stays in view even when the
            // user is far from their goal.
            val refExpandedMin = referenceLine?.let { minOf(dataMin, it) } ?: dataMin
            val refExpandedMax = referenceLine?.let { maxOf(dataMax, it) } ?: dataMax
            // Add 10% headroom so the line never grazes the top/bottom.
            val padding = ((refExpandedMax - refExpandedMin) * 0.1).coerceAtLeast(0.5)
            val minV = (minOverride ?: (refExpandedMin - padding))
            val maxV = (maxOverride ?: (refExpandedMax + padding)).coerceAtLeast(minV + 0.5)
            val w = size.width
            val h = size.height

            // grid lines
            for (i in 0..3) {
                val y = h - (h * i / 3f)
                drawLine(
                    color = outline,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 0.6f,
                )
            }

            val stepX = if (points.size > 1) w / (points.size - 1).toFloat() else 0f
            fun toPx(value: Double): Float {
                val ratio = ((value - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f)
                return h - h * ratio
            }

            // Goal-line overlay drawn under the data path so the line crosses
            // it visually — gives an "are we close?" read at a glance.
            if (referenceLine != null) {
                val refY = toPx(referenceLine)
                drawLine(
                    color = referenceColor,
                    start = Offset(0f, refY),
                    end = Offset(w, refY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
                if (referenceLabel != null) {
                    val layout = measurer.measure(
                        AnnotatedString(referenceLabel),
                        style = AppText.bodyS.copy(color = referenceColor),
                    )
                    // Right-align so it doesn't collide with the leftmost data point.
                    val labelX = (w - layout.size.width - Spacing.xs.toPx()).coerceAtLeast(0f)
                    // Above the line by preference, below it when there is no
                    // room. Clamping to 0 instead put the caption exactly where
                    // the line was: a goal far above everything logged puts the
                    // reference near the top of the plot, and "Goal 2 670 kcal"
                    // then rendered with its own dashes struck through it.
                    val gap = Spacing.xs.toPx()
                    val above = refY - layout.size.height - gap
                    val labelY = if (above >= 0f) {
                        above
                    } else {
                        (refY + gap).coerceAtMost((h - layout.size.height).coerceAtLeast(0f))
                    }
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(labelX, labelY),
                    )
                }
            }

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = index * stepX
                val y = toPx(point.value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // The wash goes down first so the stroke stays the sharpest thing
            // on the chart. It is the same path, closed down to the baseline.
            if (points.size > 1) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(points.lastIndex * stepX, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.12f), Color.Transparent),
                        startY = 0f,
                        endY = h,
                    ),
                )
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx()),
            )

            // dots + sparse labels
            points.forEachIndexed { index, point ->
                val x = index * stepX
                val y = toPx(point.value)
                drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(x, y))

                if (index == 0 || index == points.lastIndex || (points.size > 6 && index % (points.size / 4) == 0)) {
                    val layout = measurer.measure(
                        AnnotatedString(point.label),
                        style = AppText.bodyS.copy(color = onSurfaceVariant),
                    )
                    val labelX = (x - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(labelX, h + Spacing.xs.toPx()),
                    )
                }
            }
        }
    }
}
