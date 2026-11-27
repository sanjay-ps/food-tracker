package com.example.foodtracker.ui.components

import com.example.foodtracker.ui.components.atoms.grouped
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.util.DateUtil

/**
 * Plan against actual, on a real time axis that can run past today.
 *
 * The [plan] is a dashed line (it exists from day one, so the chart is never
 * empty); [actual] is the solid line with dots for the days you logged. The
 * stretch after [today] is shaded — that part is forecast.
 */
@Composable
fun PlanChart(
    actual: List<Pair<Long, Double>>,
    plan: List<Pair<Long, Double>>,
    start: Long,
    end: Long,
    today: Long,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    planLabel: String = "Plan",
) {
    val scheme = MaterialTheme.colorScheme
    val planColor = scheme.onSurface.copy(alpha = 0.45f)
    val grid = scheme.outline.copy(alpha = 0.35f)
    val future = scheme.onSurface.copy(alpha = 0.04f)
    val marker = scheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val axisStyle = AppText.eyebrow.copy(color = marker)
    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$planLabel line with ${actual.size} logged ${if (actual.size == 1) "day" else "days"}, in $unit"
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(vertical = Spacing.s)) {
            val values = (actual + plan).map { it.second }
            if (values.isEmpty() || end <= start) return@Canvas
            val lo = values.min()
            val hi = values.max()
            val pad = ((hi - lo) * 0.15).coerceAtLeast(if (hi > 100) 50.0 else 0.5)
            val minV = lo - pad
            val maxV = hi + pad
            val w = size.width
            val h = size.height
            fun x(day: Long) = (day - start).toFloat() / (end - start).toFloat() * w
            fun y(v: Double) = h - ((v - minV) / (maxV - minV)).toFloat() * h

            for (i in 0..3) {
                val gy = h * i / 3f
                drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            // Top and bottom of the scale, so a flat line still says what it is.
            listOf(maxV to 0f, minV to h).forEach { (v, gy) ->
                val text = if (v >= 100) grouped(v) else "%.1f".format(v)
                val layout = measurer.measure(AnnotatedString(text), axisStyle)
                val ty = if (gy == 0f) 2.dp.toPx() else gy - layout.size.height - 2.dp.toPx()
                drawText(layout, topLeft = Offset(w - layout.size.width - 2.dp.toPx(), ty))
            }
            // The forecast zone.
            val tx = x(today)
            drawRect(future, topLeft = Offset(tx, 0f), size = Size(w - tx, h))
            drawLine(
                marker.copy(alpha = 0.5f), Offset(tx, 0f), Offset(tx, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            )

            if (plan.size >= 2) {
                val p = Path()
                plan.forEachIndexed { i, (d, v) -> if (i == 0) p.moveTo(x(d), y(v)) else p.lineTo(x(d), y(v)) }
                drawPath(
                    p, planColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }

            if (actual.isNotEmpty()) {
                val line = Path()
                actual.forEachIndexed { i, (d, v) -> if (i == 0) line.moveTo(x(d), y(v)) else line.lineTo(x(d), y(v)) }
                if (actual.size > 1) {
                    val wash = Path().apply {
                        addPath(line)
                        lineTo(x(actual.last().first), h)
                        lineTo(x(actual.first().first), h)
                        close()
                    }
                    drawPath(wash, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)))
                    drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                }
                actual.forEach { (d, v) ->
                    drawCircle(scheme.surface, radius = 5.dp.toPx(), center = Offset(x(d), y(v)))
                    drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x(d), y(v)))
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Text(DateUtil.shortDate(start), style = AppText.eyebrow, color = marker, modifier = Modifier.align(Alignment.CenterStart))
            // Under the dashed marker, wherever today falls on the axis.
            val f = ((today - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
            Text("Today", style = AppText.eyebrow, color = scheme.onSurface, modifier = Modifier.align(BiasAlignment(f * 2f - 1f, 0f)))
            Text(DateUtil.shortDate(end), style = AppText.eyebrow, color = marker, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Spacer(Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(Spacing.xs + 2.dp))
            Text("You", style = AppText.bodyS, color = marker)
            Spacer(Modifier.width(Spacing.l))
            Box(Modifier.width(14.dp).height(2.dp).background(planColor))
            Spacer(Modifier.width(Spacing.xs + 2.dp))
            Text(planLabel, style = AppText.bodyS, color = marker)
            Spacer(Modifier.width(Spacing.l))
            Box(Modifier.size(width = 14.dp, height = 10.dp).background(future))
            Spacer(Modifier.width(Spacing.xs + 2.dp))
            Text("Ahead", style = AppText.bodyS, color = marker)
        }
    }
}
