package com.example.foodtracker.ui.screens.today.components

import com.example.foodtracker.ui.theme.Rich
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Flame
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.ui.components.atoms.AnimatedCount
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.ui.theme.meshGradient
import com.example.foodtracker.ui.theme.dotTexture
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.roundToInt

private val DAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * The day's calories on a soft gradient card: what you've eaten as one big
 * counting number (left / goal / week average underneath), the week as seven bars (the day on screen in the accent,
 * tap any past bar to jump to it), and three figures along the bottom.
 */
@Composable
fun CalorieHero(
    consumed: Nutrients,
    goals: NutrientGoals,
    weekKcal: List<Double>,
    selectedIndex: Int,
    todayIndex: Int?,
    isToday: Boolean,
    loaded: Boolean,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    parallax: () -> Float = { 0f },
    streak: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    val left = goals.calories - consumed.calories
    val over = left < 0
    val pct = if (goals.calories > 0) (consumed.calories / goals.calories * 100).roundToInt() else 0
    val logged = weekKcal.filter { it > 0 }
    val weekAvg = if (logged.isEmpty()) 0.0 else logged.average()

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg + 4.dp))
            // Static mesh: an endless animation here redrew Today (and the
            // blurred bar over it) every frame, even with nothing changing.
            .meshGradient(animate = false, parallax = parallax)
            .dotTexture(color = scheme.onSurface.copy(alpha = 0.10f), parallax = parallax)
            .padding(20.dp)
            // Slight parallax: the colour field and dots drift slower than the
            // card as it scrolls away, and the content fades a touch.
            .graphicsLayer {
                // As the card scrolls away its content sinks at 40% of the
                // scroll, shrinks a touch and fades — while the colour field
                // and dots move at their own speeds behind it.
                val p = parallax()
                val progress = (p / size.height).coerceIn(0f, 1f)
                translationY = p * 0.4f
                scaleX = 1f - 0.08f * progress
                scaleY = 1f - 0.08f * progress
                alpha = (1f - progress * 1.4f).coerceIn(0f, 1f)
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isToday) "Eaten today" else "Eaten that day",
                style = AppText.eyebrow,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // The streak, up top where it is seen first.
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(cardColor().copy(alpha = 0.75f))
                    .padding(horizontal = Spacing.m, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Flame, contentDescription = null, tint = Rich.Streak.ink, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "$streak",
                    style = AppText.label,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = "$streak day streak" },
                )
            }
            if (loaded) {
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "$pct% of goal",
                    style = AppText.label,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(cardColor().copy(alpha = 0.75f))
                        .padding(horizontal = Spacing.m, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            if (loaded) {
                AnimatedCount(
                    value = consumed.calories.roundToInt(),
                    style = AppText.numberXXL,
                    color = scheme.onSurface,
                )
            } else {
                Text("—", style = AppText.numberXXL, color = scheme.onSurface)
            }
            Text(
                " kcal",
                style = AppText.titleM,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Spacer(Modifier.height(Spacing.l))
        WeekBars(
            values = weekKcal,
            goal = goals.calories,
            selectedIndex = selectedIndex,
            todayIndex = todayIndex,
            onDayClick = onDayClick,
        )

        Spacer(Modifier.height(Spacing.l))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(scheme.onSurface.copy(alpha = 0.08f))
        )
        Spacer(Modifier.height(Spacing.m))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // What's left now sits here; past the goal it becomes "Over" in red.
            HeroStat(
                if (over) "Over" else "Left",
                if (loaded) grouped(abs(left)) else "—",
                Modifier.weight(1f),
                color = if (over) scheme.error else scheme.onSurface,
            )
            HeroDivider()
            HeroStat("Goal", if (loaded) grouped(goals.calories) else "—", Modifier.weight(1f))
            HeroDivider()
            HeroStat("Week avg", if (weekAvg > 0) grouped(weekAvg) else "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier.padding(horizontal = Spacing.s)) {
        Text(label, style = AppText.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = AppText.numberM, color = color)
    }
}

@Composable
private fun HeroDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
    )
}

/**
 * Seven rounded bars against a dashed goal line. Bars grow from zero on first
 * show and re-grow when the week changes. Days after today draw as a stub and
 * ignore taps.
 */
@Composable
private fun WeekBars(
    values: List<Double>,
    goal: Double,
    selectedIndex: Int,
    todayIndex: Int?,
    onDayClick: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.tertiary
    val ink = scheme.onSurface.copy(alpha = 0.72f)
    val stub = scheme.onSurface.copy(alpha = 0.12f)
    val goalLine = scheme.onSurface.copy(alpha = 0.28f)
    val scale = maxOf(goal * 1.15, values.maxOrNull() ?: 0.0, 1.0)
    val lastTappable = todayIndex ?: 6

    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    Column(
        Modifier.semantics {
            contentDescription = "This week's calories: " +
                values.mapIndexed { i, v -> "${DAY_INITIALS[i]} ${v.roundToInt()}" }.joinToString()
        }
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .pointerInput(lastTappable) {
                    detectTapGestures { pos ->
                        val i = (pos.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                        if (i <= lastTappable) onDayClick(i)
                    }
                }
        ) {
            val slot = size.width / 7f
            val barW = slot * 0.46f
            val r = CornerRadius(barW / 2.4f, barW / 2.4f)
            if (goal > 0) {
                val y = size.height * (1f - (goal / scale).toFloat())
                drawLine(
                    color = goalLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2f
                val future = i > lastTappable
                val h = if (v <= 0 || future) 4.dp.toPx()
                else (size.height * (v / scale).toFloat() * grow.value).coerceAtLeast(6.dp.toPx())
                drawRoundRect(
                    color = when {
                        future || v <= 0 -> stub
                        i == selectedIndex -> accent
                        else -> ink
                    },
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = r,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DAY_INITIALS.forEachIndexed { i, d ->
                Text(
                    d,
                    style = if (i == selectedIndex) AppText.eyebrow.copy(fontWeight = FontWeight.Bold) else AppText.eyebrow,
                    color = if (i == selectedIndex) scheme.onSurface else scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
