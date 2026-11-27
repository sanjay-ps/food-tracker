package com.example.foodtracker.ui.screens.today.components

import com.example.foodtracker.ui.theme.Rich
import com.composables.icons.lucide.Heart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Drumstick
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Nut
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.UserRound
import com.composables.icons.lucide.Wheat
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.NutrientFieldsByKey
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.PastelIcon
import com.example.foodtracker.ui.components.atoms.RoundIconButton
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.pressable
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Pastel
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.util.DateUtil
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val MS_PER_DAY = 86_400_000L

/**
 * Greeting and day title on the left; a `‹ 27 Nov ›` day pill and the
 * profile button on the right. Tapping the date opens a calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayHeader(
    epochDay: Long,
    name: String,
    onSelectDay: (Long) -> Unit,
    onOpenYou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val today = DateUtil.todayEpochDay()
    var picking by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    } + if (name.isNotBlank()) ", ${name.trim().substringBefore(' ')}" else ""

    Row(
        modifier
            .fillMaxWidth()
            .background(scheme.background)
            .statusBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.s, top = Spacing.m, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = AppText.bodyS, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DateUtil.formatHeader(epochDay), style = AppText.headline, color = scheme.onSurface, maxLines = 1)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(cardColor()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillArrow(Lucide.ChevronLeft, "Previous day", enabled = true) { onSelectDay(epochDay - 1) }
            Text(
                DateUtil.epochDayToDate(epochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                style = AppText.label,
                color = scheme.onSurface,
                modifier = Modifier
                    .clickable(onClickLabel = "Pick a day") { picking = true }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
            )
            PillArrow(Lucide.ChevronRight, "Next day", enabled = epochDay < today) { onSelectDay(epochDay + 1) }
        }
        RoundIconButton(icon = Lucide.UserRound, contentDescription = "You", onClick = onOpenYou)
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * MS_PER_DAY,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / MS_PER_DAY <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                AppTextButton("Go", onClick = {
                    state.selectedDateMillis?.let { onSelectDay(it / MS_PER_DAY) }
                    picking = false
                })
            },
            dismissButton = { AppTextButton("Cancel", onClick = { picking = false }) },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun PillArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .semantics { role = Role.Button; contentDescription = label }
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Today's plan as one tappable card: what's left, by name. */
@Composable
fun PlanCard(
    remaining: Int,
    summary: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(cardColor())
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pressable(onClick = onClick)
            .padding(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PastelIcon(Lucide.ListChecks, Pastel.Mint)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text("Today's plan", style = AppText.titleM, color = scheme.onSurface)
            Text(caption, style = AppText.bodyS, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Spacing.m))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (remaining > 0) "$remaining" else "✓", style = AppText.numberL, color = scheme.onSurface)
            Text(if (remaining > 0) "to log" else summary, style = AppText.bodyS, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * The page's sign-off: one big, faint line about food and a small note under
 * it. Sits after everything else on Today.
 */
@Composable
fun ClosingQuote(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(top = Spacing.xxl, start = Spacing.xs)) {
        Text(
            "Balance\nbeats perfect.",
            style = AppText.quote,
            color = scheme.onSurface.copy(alpha = 0.16f),
        )
        Spacer(Modifier.height(Spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Made with", style = AppText.bodyS, color = scheme.onSurfaceVariant)
            Icon(
                Lucide.Heart,
                contentDescription = "love",
                tint = Rich.Protein.ink,
                modifier = Modifier.padding(horizontal = Spacing.xs).size(14.dp),
            )
            Text("for everyday eating", style = AppText.bodyS, color = scheme.onSurfaceVariant)
        }
    }
}
