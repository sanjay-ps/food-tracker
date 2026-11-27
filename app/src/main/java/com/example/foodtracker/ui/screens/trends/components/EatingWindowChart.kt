package com.example.foodtracker.ui.screens.trends.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.foodtracker.domain.MealTiming
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Rich
import com.example.foodtracker.ui.theme.StatusOver
import com.example.foodtracker.util.DateUtil

/**
 * When you eat, day by day: each column is one day, a soft bar from the first
 * meal to the last (the eating window) with a dot for every meal. The band at
 * the bottom is "late" — dots in it are orange.
 */
@Composable
fun EatingWindowChart(
    days: List<Pair<Long, List<Int>>>,
    start: Long,
    today: Long,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axis = AppText.eyebrow.copy(color = scheme.onSurfaceVariant)
    val ink = Rich.Energy.ink
    val grid = scheme.outline.copy(alpha = 0.3f)
    val late = MealTiming.eatingMinute(MealTiming.LATE_FROM)
    Column(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Meal times for ${days.size} logged days" }
    ) {
        Canvas(Modifier.fillMaxWidth().height(200.dp).padding(vertical = 4.dp)) {
            val all = days.flatMap { it.second }
            // 6 AM to midnight, stretched if anything falls outside.
            val top = minOf(6 * 60, (all.minOrNull() ?: 6 * 60) - 30).toFloat()
            val bottom = maxOf(24 * 60, (all.maxOrNull() ?: 0) + 30).toFloat()
            val labelW = 40.dp.toPx()
            val w = size.width - labelW
            val h = size.height
            fun y(m: Int) = (m - top) / (bottom - top) * h
            val span = (today - start + 1).coerceAtLeast(1)
            val slot = w / span

            // The late band.
            drawRect(StatusOver.copy(alpha = 0.08f), Offset(labelW, y(late)), Size(w, h - y(late)))
            // Hour guides with their labels.
            listOf(6 * 60 to "6 AM", 12 * 60 to "12 PM", 18 * 60 to "6 PM", 24 * 60 to "12 AM")
                .filter { it.first >= top && it.first <= bottom }
                .forEach { (m, label) ->
                    drawLine(grid, Offset(labelW, y(m)), Offset(size.width, y(m)), strokeWidth = 1f)
                    val layout = measurer.measure(AnnotatedString(label), axis)
                    drawText(layout, topLeft = Offset(0f, y(m) - layout.size.height / 2f))
                }

            val barW = (slot * 0.55f).coerceIn(1.5.dp.toPx(), 10.dp.toPx())
            val dotR = (barW * 0.55f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
            days.forEach { (day, minutes) ->
                val cx = labelW + (day - start + 0.5f) * slot
                val y0 = y(minutes.first())
                val y1 = y(minutes.last())
                drawRoundRect(
                    ink.copy(alpha = 0.18f),
                    topLeft = Offset(cx - barW / 2, y0 - dotR),
                    size = Size(barW, (y1 - y0) + 2 * dotR),
                    cornerRadius = CornerRadius(barW / 2),
                )
                minutes.forEach { m ->
                    drawCircle(if (m >= late) StatusOver else ink, radius = dotR, center = Offset(cx, y(m)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp)) {
            Text(DateUtil.shortDate(start), style = axis)
            Spacer(Modifier.weight(1f))
            Text("Today", style = axis.copy(color = scheme.onSurface))
        }
    }
}
