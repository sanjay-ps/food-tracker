package com.example.foodtracker.ui.screens.nutrients

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.NutrientCategory
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.NutrientStatus
import com.example.foodtracker.data.onTrack
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.EmptyState
import com.example.foodtracker.ui.components.NutrientContributionSheet
import com.example.foodtracker.ui.components.NutrientReading
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.Ring
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.components.atoms.pressable
import com.example.foodtracker.ui.components.atoms.softFill
import com.example.foodtracker.ui.components.atoms.staggerIn
import com.example.foodtracker.ui.components.meterColor
import com.example.foodtracker.ui.components.meterFraction
import com.example.foodtracker.ui.components.readingsFor
import com.example.foodtracker.ui.components.scored
import com.example.foodtracker.ui.screens.today.TodayViewModel
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Rich
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.ui.theme.StatusOver
import com.example.foodtracker.ui.theme.dotTexture
import com.example.foodtracker.ui.theme.meshGradient
import com.example.foodtracker.util.DateUtil
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class Filter(val label: String) { All("All"), Short("Short"), OnTrack("On track"), Over("Over") }

private fun NutrientReading.isOver() = status == NutrientStatus.Over || status == NutrientStatus.FarOver
private fun NutrientReading.isShort() = scored && !status.onTrack && !isOver()

/**
 * Every tracked nutrient for one day.
 *
 * A textured score card up top (how many are on track, how many short, how
 * many over), a filter, then one card per category with the nutrients as a
 * two-column grid of tiles — each with a small ring in its status colour.
 * Tapping a tile opens what made up that nutrient today.
 *
 * A page rather than a sheet so the contribution detail can be a sheet over
 * it without stacking sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllNutrientsScreen(
    epochDay: Long,
    onBack: () -> Unit,
    onOpenRichFoods: (String?) -> Unit,
) {
    val vm: TodayViewModel = appViewModel { TodayViewModel(it.repository, it.profileStore) }
    // The view model defaults to today; this page may have been opened on a
    // back-filled day, and must keep showing that day after process death.
    LaunchedEffect(epochDay) { vm.setDay(epochDay) }

    val state by vm.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<NutrientField?>(null) }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }

    val readings = remember(state.totalNutrients, state.goals) {
        readingsFor(state.totalNutrients, state.goals)
    }
    val scored = readings.scored()
    val onTrack = scored.count { it.status.onTrack }
    val short = scored.count { it.isShort() }
    val over = scored.count { it.isOver() }
    val visible = when (filter) {
        Filter.All -> readings
        Filter.Short -> readings.filter { it.isShort() }
        Filter.OnTrack -> readings.filter { it.scored && it.status.onTrack }
        Filter.Over -> readings.filter { it.isOver() }
    }
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.background,
        topBar = {
            AppTopBar(
                title = "All nutrients",
                subtitle = DateUtil.epochDayToDate(state.epochDay)
                    .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding() + Spacing.s,
                bottom = inner.calculateBottomPadding() + Spacing.xxxl,
                start = Spacing.gutter,
                end = Spacing.gutter,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item("score") {
                ScoreCard(
                    onTrack = onTrack,
                    total = scored.size,
                    short = short,
                    over = over,
                    hasLogs = state.logs.isNotEmpty(),
                    modifier = Modifier.staggerIn(0),
                )
            }
            item("filter") {
                ExclusiveButtonGroup(
                    options = Filter.entries,
                    selected = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.fillMaxWidth().staggerIn(1),
                    label = { it.label },
                )
            }
            var shown = 0
            NutrientCategory.entries.forEachIndexed { index, category ->
                val inCategory = visible.filter { it.field.category == category }
                if (inCategory.isEmpty()) return@forEachIndexed
                shown++
                val all = readings.filter { it.field.category == category && it.scored }
                item(category.name) {
                    Section(
                        category.name,
                        modifier = Modifier.staggerIn(2 + index),
                        trailing = {
                            Text(
                                "${all.count { it.status.onTrack }} of ${all.size} on track",
                                style = AppText.bodyS,
                                color = scheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            inCategory.chunked(2).forEach { pair ->
                                Row(
                                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                ) {
                                    pair.forEach { r ->
                                        NutrientTile(
                                            r = r,
                                            onClick = { detail = r.field },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        )
                                    }
                                    // A lone last tile stays half width.
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            if (shown == 0) {
                item("none") {
                    EmptyState(
                        icon = Lucide.Sparkles,
                        title = if (filter == Filter.Over) "Nothing over" else "Nothing here",
                        message = when (filter) {
                            Filter.Over -> "No limit has been crossed today."
                            Filter.Short -> "Nothing is short — every target is met or close."
                            else -> "Log a meal and your nutrients appear here."
                        },
                    )
                }
            }
        }
    }

    detail?.let { field ->
        NutrientContributionSheet(
            field = field,
            consumed = state.totalNutrients,
            goals = state.goals,
            logs = state.logs,
            onDismiss = { detail = null },
            onFindRichFoods = { key -> detail = null; onOpenRichFoods(key) },
        )
    }
}

/** The day's nutrient score on the soft hero gradient: a ring and three counts. */
@Composable
private fun ScoreCard(
    onTrack: Int,
    total: Int,
    short: Int,
    over: Int,
    hasLogs: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg + 4.dp))
            .meshGradient(animate = false)
            .dotTexture(color = scheme.onSurface.copy(alpha = 0.10f))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(
            fraction = if (total > 0 && hasLogs) onTrack / total.toFloat() else 0f,
            diameter = 104.dp,
            stroke = 10.dp,
            color = scheme.secondary,
            trackColor = scheme.onSurface.copy(alpha = 0.08f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (hasLogs) "$onTrack" else "—", style = AppText.numberL, color = scheme.onSurface)
                Text("of $total", style = AppText.bodyS, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Nutrients on track", style = AppText.titleM, color = scheme.onSurface)
            CountChip(scheme.secondary, "On track", onTrack)
            CountChip(Rich.Carbs.ink, "Short", short)
            CountChip(StatusOver, "Over a limit", over)
        }
    }
}

@Composable
private fun CountChip(dot: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Spacing.s))
        Text(label, style = AppText.bodyS, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("$count", style = AppText.label, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** One nutrient: a small status ring with the percentage, the name, and value / goal. */
@Composable
private fun NutrientTile(r: NutrientReading, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val pct = (r.fraction * 100).roundToInt()
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(softFill())
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pressable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(
            fraction = if (r.untargeted) 0f else r.meterFraction,
            diameter = 40.dp,
            stroke = 4.dp,
            color = r.status.meterColor(),
            trackColor = scheme.onSurface.copy(alpha = 0.08f),
        ) {
            Text(
                if (r.untargeted) "—" else "${pct.coerceAtMost(999)}",
                style = AppText.eyebrow,
                color = scheme.onSurface,
            )
        }
        Spacer(Modifier.width(Spacing.s + 2.dp))
        Column(Modifier.weight(1f)) {
            Text(r.field.label, style = AppText.label, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (r.untargeted) "${formatValue(r.value)} ${r.field.unit.symbol}"
                else "${formatValue(r.value)} / ${formatValue(r.goal)} ${r.field.unit.symbol}",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
