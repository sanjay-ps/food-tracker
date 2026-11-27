package com.example.foodtracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.Spacing
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import com.example.foodtracker.MainActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Goal-left widget. The card itself is the progress bar: the
 * left portion (% consumed) is filled with the bright `primary` tint, the
 * right portion stays `primaryContainer`. Reads like a battery filling up.
 *
 * Data is read ONCE via [WidgetData.snapshot] inside `provideGlance`, then
 * rendered. We deliberately do NOT keep a long-lived collect open here: that
 * pins the Glance session alive, which downgrades every [WidgetUpdater.updateAll]
 * to a recompose of stale data instead of re-running `provideGlance`. Refreshes
 * are pushed explicitly (food/activity writes, app foreground, daily worker);
 * each push starts a fresh session and re-enters this method. See [WidgetData].
 */
class GoalLeftWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.snapshot(context)
        provideContent {
            GlanceTheme {
                GoalLeftContent(snapshot)
            }
        }
    }

    companion object {
        const val WIDTH_MEDIUM_DP = 180
        const val WIDTH_LARGE_DP = 260
    }
}

class GoalLeftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalLeftWidget()
}

@Composable
private fun EmptyGoalLeft(size: DpSize) {
    val numberSp = when {
        size.width.value >= GoalLeftWidget.WIDTH_LARGE_DP -> 48.sp
        size.width.value >= GoalLeftWidget.WIDTH_MEDIUM_DP -> 40.sp
        else -> 30.sp
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.PrimaryContainer)
            .cornerRadius(com.example.foodtracker.R.dimen.widget_corner_radius)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LEFT",
                style = TextStyle(
                    color = WidgetColors.OnPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "0",
                    style = TextStyle(
                        color = WidgetColors.OnPrimaryContainer,
                        fontSize = numberSp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(Spacing.xs))
                Text(
                    text = "kcal",
                    style = TextStyle(
                        color = WidgetColors.OnPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(bottom = Spacing.s),
                )
            }
            if (size.width.value >= GoalLeftWidget.WIDTH_MEDIUM_DP) {
                Spacer(GlanceModifier.height(Spacing.s))
                Text(
                    text = "Set up to start",
                    style = TextStyle(
                        color = WidgetColors.OnPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GoalLeftContent(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    if (!snapshot.onboarded) {
        EmptyGoalLeft(size)
        return
    }
    val goalKcal = max(1.0, snapshot.goals.calories)
    val left = snapshot.caloriesLeft.roundToInt()
    val consumedPct = (snapshot.caloriesConsumed / goalKcal).coerceIn(0.0, 1.0).toFloat()
    val isOver = left < 0
    val numberSp = when {
        size.width.value >= GoalLeftWidget.WIDTH_LARGE_DP -> 48.sp
        size.width.value >= GoalLeftWidget.WIDTH_MEDIUM_DP -> 40.sp
        else -> 30.sp
    }
    val bg = if (isOver) WidgetColors.ErrorBg else WidgetColors.PrimaryContainer
    val onBg = if (isOver) WidgetColors.OnErrorBg else WidgetColors.OnPrimaryContainer
    val fill = if (isOver) WidgetColors.ErrorSubtle else WidgetColors.PrimarySubtle

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(com.example.foodtracker.R.dimen.widget_corner_radius)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (consumedPct > 0f) {
            val fillWidth = (size.width.value * consumedPct).dp
            Row(modifier = GlanceModifier.fillMaxSize()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(fillWidth)
                        .background(fill)
                        .cornerRadius(com.example.foodtracker.R.dimen.widget_corner_radius),
                ) {}
            }
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isOver) "OVER" else "LEFT",
                style = TextStyle(
                    color = onBg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (isOver) "${-left}" else "$left",
                    style = TextStyle(
                        color = onBg,
                        fontSize = numberSp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(Spacing.xs))
                Text(
                    text = "kcal",
                    style = TextStyle(
                        color = onBg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(bottom = Spacing.s),
                )
            }
            if (size.width.value >= GoalLeftWidget.WIDTH_LARGE_DP) {
                Spacer(GlanceModifier.height(Spacing.s))
                Text(
                    text = "${snapshot.caloriesConsumed.roundToInt()} of ${goalKcal.roundToInt()}",
                    style = TextStyle(
                        color = onBg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}
