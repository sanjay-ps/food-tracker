package com.example.foodtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

data class DailyValue(val label: String, val value: Double)

private val CHART_HEIGHT = 160.dp

/** Room under the bars for one `bodyS` line of day labels. */
private val AXIS_HEIGHT = 22.dp

/**
 * Seven bars, a three-step grid, and an optional dashed goal line.
 *
 * The goal line used to be a literal `0xFFE85D2D` — the terracotta that the
 * palette dropped, and a hue that appears nowhere else in
 * the app now. It is `primary` instead: the goal is the one thing on this
 * chart you are aiming at, and `primary` is what "at goal" means everywhere
 * else (the calorie ring, every nutrient meter). The bars keep their caller-
 * supplied data-palette hue, so accent and datum stay distinguishable.
 *
 * **The editorial pass touched type and corners, not bars.** The day labels
 * were a raw `TextStyle(fontSize = 10.sp)` and are [AppText.bodyS], the same
 * caption style the line chart and the macro stack now use; the bar corner is
 * the macro stack's 4dp cap rather than a 6dp radius that belonged to no
 * scale. Heights, the goal line and the maximum are computed exactly as
 * before.
 */
@Composable
fun WeeklyBarChart(
    bars: List<DailyValue>,
    goal: Double?,
    barColor: Color,
    unit: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    // Hoisted for the same reason as the two above — the draw lambda has no
    // `MaterialTheme`, which is how a raw hex ended up inside it.
    val goalColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            // An eyebrow, not a title: a chart that is given a heading is a
            // section, and every section in the app heads itself the same way.
            Text(
                title,
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .padding(
                    top = Spacing.s,
                    bottom = AXIS_HEIGHT,
                    start = Spacing.xs,
                    end = Spacing.xs,
                ),
        ) {
            if (bars.isEmpty()) return@Canvas
            val maxVal = (bars.maxOf { it.value }.coerceAtLeast(goal ?: 0.0)).coerceAtLeast(1.0)
            val w = size.width
            val h = size.height
            val gap = Spacing.s.toPx()
            val barWidth = (w - gap * (bars.size + 1)) / bars.size

            // grid lines: 0, mid, max
            val gridColor = outline
            for (i in 0..3) {
                val y = h - (h * i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 0.6f,
                )
            }

            // goal line
            if (goal != null && goal > 0) {
                val goalRatio = (goal / maxVal).toFloat().coerceIn(0f, 1f)
                val y = h - h * goalRatio
                drawLine(
                    color = goalColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        intervals = floatArrayOf(8f, 6f),
                    ),
                )
            }

            bars.forEachIndexed { index, bar ->
                val x = gap + index * (barWidth + gap)
                val ratio = (bar.value / maxVal).toFloat().coerceIn(0f, 1f)
                val barH = h * ratio
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, h - barH),
                    size = Size(barWidth, barH),
                    // Same cap as the macro stack, so the two bar charts on the
                    // page have one silhouette between them.
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        minOf(barWidth / 2f, 4.dp.toPx()),
                        minOf(barWidth / 2f, 4.dp.toPx()),
                    ),
                )
                // label
                val labelLayout = measurer.measure(
                    AnnotatedString(bar.label),
                    style = AppText.bodyS.copy(color = onSurfaceVariant, textAlign = TextAlign.Center),
                )
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(
                        x + barWidth / 2f - labelLayout.size.width / 2f,
                        h + Spacing.xs.toPx(),
                    ),
                )
            }
        }
    }
}
