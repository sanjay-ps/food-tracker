package com.example.foodtracker.ui.screens.today.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Drumstick
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Nut
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Wheat
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.NutrientFieldsByKey
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.onTrack
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.domain.Streak
import com.example.foodtracker.ui.components.atoms.Metric
import com.example.foodtracker.ui.components.atoms.MetricSize
import com.example.foodtracker.ui.components.atoms.PastelIcon
import com.example.foodtracker.ui.components.atoms.Ring
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.StatusCell
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.components.atoms.pressable
import com.example.foodtracker.ui.components.atoms.softFill
import com.example.foodtracker.ui.components.readingsFor
import com.example.foodtracker.ui.components.scored
import com.example.foodtracker.ui.screens.today.HEAT_WEEKS
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Pastel
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Rich
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.ui.theme.StatusOver
import com.example.foodtracker.ui.theme.dotTexture
import com.example.foodtracker.util.DateUtil
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The home screen's bento grid.
 *
 *   [ Protein (big, ring) ] [ Carbs ]
 *   [                     ] [ Fat   ]
 *   [ Fiber               ] [ Water ]
 *   [ Streak (wide)                 ]
 *   [ Weight              ] [ Nutrients score ]
 *
 * Nutrient tiles are plain cards with the nutrient's hue on the icon, label and
 * bar; the streak alone gets its gradient and dot texture.
 */

private val TileGap = Spacing.m
/** The tile's ink (deep shade of its hue), provided by [RichTile]. */
private val LocalTileInk = staticCompositionLocalOf { Color.Unspecified }

@Composable
private fun tileInk(): Color = LocalTileInk.current

/**
 * A nutrient tile: a plain card whose colour lives in the details — the
 * nutrient's hue on its icon, label and bar — so a grid of them stays calm.
 * [featured] gives the streak its gradient and dots.
 */
@Composable
fun RichTile(
    rich: Rich,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The one tile that stands out (the streak): its soft gradient plus the dot texture. */
    featured: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = rich.ink
    CompositionLocalProvider(LocalTileInk provides ink) {
        Column(
            modifier
                .clip(RoundedCornerShape(Radius.lg))
                .then(
                    if (featured) Modifier.background(rich.brush).dotTexture(color = ink.copy(alpha = 0.14f))
                    else Modifier.background(cardColor())
                )
                .semantics(mergeDescendants = true) { role = Role.Button }
                .pressable(onClick = onClick)
                .padding(14.dp),
            content = content,
        )
    }
}

/** Icon + label across the top of a rich tile, with an optional figure on the right. */
@Composable
private fun TileHead(icon: ImageVector, label: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(tileInk().copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tileInk(), modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.width(Spacing.s))
        Text(label, style = AppText.label, color = tileInk(), modifier = Modifier.weight(1f), maxLines = 1)
        if (trailing != null) Text(trailing, style = AppText.label, color = tileInk().copy(alpha = 0.75f))
    }
}

/** A thin white progress bar on a translucent track, for rich tiles. */
@Composable
private fun TileMeter(fraction: Float, modifier: Modifier = Modifier) {
    val v by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "tileMeter")
    Box(
        modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.pill)).background(tileInk().copy(alpha = 0.15f))
    ) {
        Box(Modifier.fillMaxWidth(v).height(6.dp).clip(RoundedCornerShape(Radius.pill)).background(tileInk()))
    }
}

private fun pct(value: Double, goal: Double) = if (goal > 0) (value / goal * 100).roundToInt() else 0

/** The macro and water part of the bento grid. */
@Composable
fun BentoMacros(
    consumed: Nutrients,
    goals: NutrientGoals,
    waterMl: Int,
    waterGoalMl: Int,
    onMacro: (NutrientField) -> Unit,
    onWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TileGap)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            RichTile(
                Rich.Protein,
                onClick = { onMacro(NutrientFieldsByKey.getValue("protein")) },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 236.dp),
            ) {
                TileHead(Lucide.Drumstick, "Protein")
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    Ring(
                        fraction = if (goals.protein > 0) (consumed.protein / goals.protein).toFloat() else 0f,
                        diameter = 120.dp,
                        stroke = 12.dp,
                        color = tileInk(),
                        trackColor = tileInk().copy(alpha = 0.15f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${consumed.protein.roundToInt()}", style = AppText.numberL, color = MaterialTheme.colorScheme.onSurface)
                            Text("of ${goals.protein.roundToInt()} g", style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pct(consumed.protein, goals.protein)}% of goal",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(TileGap)) {
                SmallMacroTile(Rich.Carbs, Lucide.Wheat, "Carbs", consumed.carbs, goals.carbs, "g", Modifier.weight(1f)) {
                    onMacro(NutrientFieldsByKey.getValue("carbs"))
                }
                SmallMacroTile(Rich.Fat, Lucide.Nut, "Fat", consumed.fat, goals.fat, "g", Modifier.weight(1f)) {
                    onMacro(NutrientFieldsByKey.getValue("fat"))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            SmallMacroTile(Rich.Fiber, Lucide.Leaf, "Fiber", consumed.fiber, goals.fiber, "g", Modifier.weight(1f).fillMaxHeight()) {
                onMacro(NutrientFieldsByKey.getValue("fiber"))
            }
            SmallMacroTile(
                Rich.Water, Lucide.Droplet, "Water", waterMl.toDouble(), waterGoalMl.toDouble(), "ml",
                Modifier.weight(1f).fillMaxHeight(), onClick = onWater,
            )
        }
    }
}

@Composable
private fun SmallMacroTile(
    rich: Rich,
    icon: ImageVector,
    label: String,
    value: Double,
    goal: Double,
    unit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RichTile(rich, onClick, modifier.heightIn(min = 112.dp)) {
        TileHead(icon, label, "${pct(value, goal)}%")
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(grouped(value), style = AppText.numberL, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(
                " / ${grouped(goal)} $unit",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(Spacing.s))
        TileMeter(if (goal > 0) (value / goal).toFloat() else 0f)
    }
}

/**
 * The streak, wide: the count, the best run, and this week as seven dots —
 * filled for a logged day, ringed for today.
 */
@Composable
fun StreakTile(
    streak: Streak,
    loggedDays: Set<Long>,
    loggedToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val today = DateUtil.todayEpochDay()
    val monday = today - (DateUtil.epochDayToDate(today).dayOfWeek.value - 1)
    RichTile(Rich.Streak, onClick, modifier.fillMaxWidth(), featured = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(tileInk().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Flame, contentDescription = null, tint = tileInk(), modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${streak.current}", style = AppText.numberXL, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        " day streak",
                        style = AppText.titleM,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Text(
                    when {
                        streak.current == 0 -> "Log a meal to start one"
                        !loggedToday -> "Log today to keep it going · best ${streak.best}"
                        else -> "Best ${streak.best} ${if (streak.best == 1) "day" else "days"}"
                    },
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..6) {
                val day = monday + i
                val logged = day in loggedDays
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (logged) tileInk() else tileInk().copy(alpha = 0.12f))
                            .then(if (day == today) Modifier.border(2.dp, tileInk(), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (logged) {
                            Icon(Lucide.Flame, contentDescription = null, tint = cardColor(), modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        DateUtil.epochDayToDate(day).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = AppText.eyebrow,
                        color = if (day == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Weight and the nutrient score, side by side on plain cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BentoFacts(
    weightKg: Double?,
    weightCaption: String,
    consumed: Nutrients,
    goals: NutrientGoals,
    hasLogs: Boolean,
    onWeight: () -> Unit,
    onNutrients: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val readings = remember(consumed, goals) { readingsFor(consumed, goals).scored() }
    val onTrack = readings.count { it.status.onTrack }
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        FactCard(onWeight, Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIcon(Lucide.Scale, Pastel.Teal, size = 36.dp)
                Spacer(Modifier.weight(1f))
                Icon(Lucide.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(Spacing.m))
            Metric(
                label = "Weight",
                value = weightKg?.let { "%.1f".format(it) } ?: "—",
                unit = if (weightKg != null) "kg" else "",
                size = MetricSize.Large,
            )
            Text(weightCaption, style = AppText.bodyS, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        FactCard(onNutrients, Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIcon(Lucide.Sparkles, Pastel.Lavender, size = 36.dp)
                Spacer(Modifier.weight(1f))
                Icon(Lucide.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(Spacing.m))
            Metric(
                label = "Nutrients on track",
                value = if (hasLogs) "$onTrack" else "—",
                unit = "of ${readings.size}",
                size = MetricSize.Large,
            )
            Spacer(Modifier.height(Spacing.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                readings.forEach { StatusCell(it.status, size = 9.dp) }
            }
        }
    }
}

@Composable
private fun FactCard(onClick: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.lg))
            .background(cardColor())
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pressable(onClick = onClick)
            .padding(Spacing.l),
        content = content,
    )
}

/**
 * "Your rhythm": the last [HEAT_WEEKS] weeks as a calendar heatmap, one column
 * per week. Strength is how close the day came to the goal,
 * an orange cell went over, and today wears a ring. Tap a cell to open that day.
 */
@Composable
fun RhythmHeatmap(
    start: Long,
    kcal: List<Double>,
    goal: Double,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val today = DateUtil.todayEpochDay()
    val logged = kcal.count { it > 0 }
    val onTarget = kcal.count { goal > 0 && it / goal in 0.8..1.15 }
    val empty = scheme.surfaceContainerHighest
    val ring = scheme.onSurface
    val heat = Rich.Energy.ink
    Section(
        "Your rhythm",
        modifier,
        trailing = { Text("$onTarget on target · $logged logged", style = AppText.bodyS, color = scheme.onSurfaceVariant) },
    ) {
        // Month names over the first week of each month, skipping any that
        // would collide with the previous label.
        val labels = remember(start) {
            val out = mutableListOf<Pair<Int, String>>()
            var lastMonth = -1
            for (w in 0 until HEAT_WEEKS) {
                val date = DateUtil.epochDayToDate(start + w * 7L)
                if (date.monthValue != lastMonth) {
                    if (out.isEmpty() || w - out.last().first >= 3) {
                        out += w to date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    } else if (out.last().first == 0) {
                        // A month that only clips the first column gives way.
                        out[out.lastIndex] = w to date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    }
                    lastMonth = date.monthValue
                }
            }
            out
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 18.dp).height(16.dp)) {
            val colW = maxWidth / HEAT_WEEKS
            labels.forEach { (w, name) ->
                Text(
                    name,
                    style = AppText.eyebrow,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.offset(x = colW * w),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row {
            Column(Modifier.width(18.dp).height(HeatHeight), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "", "W", "", "F", "", "S").forEach {
                    Text(it, style = AppText.eyebrow.copy(fontSize = AppText.eyebrow.fontSize * 0.85f), color = scheme.onSurfaceVariant)
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(HeatHeight)
                    .semantics { contentDescription = "$logged days logged in the last $HEAT_WEEKS weeks, $onTarget on target" }
                    .pointerInput(start) {
                        detectTapGestures { pos ->
                            val col = (pos.x / (size.width / HEAT_WEEKS.toFloat())).toInt().coerceIn(0, HEAT_WEEKS - 1)
                            val row = (pos.y / (size.height / 7f)).toInt().coerceIn(0, 6)
                            val day = start + col * 7L + row
                            if (day <= today) onDayClick(day)
                        }
                    },
            ) {
                val cw = size.width / HEAT_WEEKS
                val ch = size.height / 7f
                val gap = 3.dp.toPx()
                val cell = Size(cw - gap, ch - gap)
                val r = CornerRadius(cell.minDimension * 0.32f)
                for (i in 0 until HEAT_WEEKS * 7) {
                    val day = start + i
                    val col = i / 7
                    val row = i % 7
                    val tl = Offset(col * cw, row * ch)
                    if (day > today) {
                        // The rest of this week: present but quieter, so the grid is always whole.
                        drawRoundRect(empty, tl, cell, r, alpha = 0.45f)
                        continue
                    }
                    val v = kcal.getOrElse(i) { 0.0 }
                    val ratio = if (goal > 0) v / goal else 0.0
                    when {
                        v <= 0 -> drawRoundRect(empty, tl, cell, r)
                        ratio > 1.15 -> drawRoundRect(StatusOver, tl, cell, r)
                        else -> drawRoundRect(
                            color = heat,
                            topLeft = tl,
                            size = cell,
                            cornerRadius = r,
                            alpha = when {
                                ratio < 0.5 -> 0.28f
                                ratio < 0.8 -> 0.55f
                                else -> 0.85f
                            },
                        )
                    }
                    if (day == today) {
                        drawRoundRect(ring, tl, cell, r, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = AppText.eyebrow, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.xs))
            LegendCell(Modifier.background(empty))
            listOf(0.28f, 0.55f, 0.85f).forEach { a -> LegendCell(Modifier.background(heat.copy(alpha = a))) }
            Spacer(Modifier.width(Spacing.xs))
            Text("On target", style = AppText.eyebrow, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            LegendCell(Modifier.background(StatusOver))
            Spacer(Modifier.width(Spacing.xs))
            Text("Over", style = AppText.eyebrow, color = scheme.onSurfaceVariant)
        }
    }
}

private val HeatHeight: Dp = 132.dp

@Composable
private fun LegendCell(fill: Modifier) {
    Box(Modifier.padding(horizontal = 1.5.dp).size(11.dp).clip(RoundedCornerShape(3.dp)).then(fill))
}
