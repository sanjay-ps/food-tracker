package com.example.foodtracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Macros widget — three battery-fill chips, one per macro.
 *
 * Each chip is two-layer: a subtle (~20% alpha) macro tint over the widget
 * surface, with a stronger (~60% alpha) overlay growing left-to-right with
 * progress. Both layers carry the macro hue, so an empty chip still reads
 * as "this is the carbs chip" by color alone.
 *
 * Uses [SizeMode.Exact] so `LocalSize.current` returns the live widget
 * dimensions. Chip width is computed from that live width, so the row fills
 * edge-to-edge at every resize step. Density choices (full-vs-short label,
 * goal footer) are driven by the live size via thresholds.
 */
class MacrosWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // One-shot read; refreshes are pushed via WidgetUpdater.updateAll so
        // we don't pin the session open with a long-lived collect. See WidgetData.
        val snapshot = WidgetData.snapshot(context)
        provideContent {
            GlanceTheme {
                MacrosContent(snapshot)
            }
        }
    }

    companion object {
        /** Width threshold for showing the full macro name. */
        val FULL_LABEL_MIN_WIDTH = 200.dp
        /** Height threshold for showing the "of N" goal footer + Fiber row. */
        val FOOTER_MIN_HEIGHT = 120.dp
    }
}

class MacrosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MacrosWidget()
}

private data class MacroRow(
    val label: String,
    val short: String,
    val current: Double,
    val goal: Double,
    val base: ColorProvider,
    val fill: ColorProvider,
)

@Composable
private fun MacrosContent(snapshot: WidgetSnapshot) {
    val size = LocalSize.current
    val n = snapshot.consumed
    val g = snapshot.goals
    val rows = listOf(
        MacroRow("Carbs", "C", n.carbs, g.carbs,
            WidgetColors.MacroCarbsBase, WidgetColors.MacroCarbsFill),
        MacroRow("Protein", "P", n.protein, g.protein,
            WidgetColors.MacroProteinBase, WidgetColors.MacroProteinFill),
        MacroRow("Fat", "F", n.fat, g.fat,
            WidgetColors.MacroFatBase, WidgetColors.MacroFatFill),
    )

    val useFullLabel = size.width >= MacrosWidget.FULL_LABEL_MIN_WIDTH
    val showFooter = size.height >= MacrosWidget.FOOTER_MIN_HEIGHT
    // Tier drives padding, chip height, and font sizes — independent of the
    // label/footer decisions above.
    val isCompact = !useFullLabel
    val outerPaddingDp = if (isCompact) 8f else 12f
    val spacerDp = if (isCompact) 6f else 8f
    val chipHeightDp = when {
        showFooter -> 90f       // wide + tall — goal footer fits
        useFullLabel -> 70f     // wide + short — fits one label line + number
        else -> 50f             // narrow + short
    }
    val cornerDp = if (isCompact) 14f else 18f
    val padHDp = if (isCompact) 8f else 12f
    val padVDp = if (isCompact) 6f else 10f
    // Chip width — what's left after the outer padding and the two inter-chip
    // spacers, split three ways. Drives the battery-fill width.
    val chipWidthDp = max(0f, (size.width.value - 2f * outerPaddingDp - 2f * spacerDp) / 3f)
    val numberSp = when {
        showFooter -> 26.sp
        useFullLabel -> 22.sp
        else -> 18.sp
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Surface)
            .cornerRadius(com.example.foodtracker.R.dimen.widget_corner_radius)
            .padding(outerPaddingDp.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Spacer(GlanceModifier.width(spacerDp.dp))
                MacroChip(
                    row = row,
                    chipWidthDp = chipWidthDp,
                    chipHeightDp = chipHeightDp,
                    cornerDp = cornerDp,
                    padHDp = padHDp,
                    padVDp = padVDp,
                    useFullLabel = useFullLabel,
                    showFooter = showFooter,
                    numberSp = numberSp,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        if (showFooter) {
            Spacer(GlanceModifier.height(10.dp))
            FiberRow(
                current = n.fiber,
                goal = max(1.0, g.fiber),
                widgetWidthDp = size.width.value,
                outerPaddingDp = outerPaddingDp,
            )
        }
    }
}

@Composable
private fun MacroChip(
    row: MacroRow,
    chipWidthDp: Float,
    chipHeightDp: Float,
    cornerDp: Float,
    padHDp: Float,
    padVDp: Float,
    useFullLabel: Boolean,
    showFooter: Boolean,
    numberSp: androidx.compose.ui.unit.TextUnit,
    modifier: GlanceModifier,
) {
    // Text here is [WidgetColors.OnSurface], not OnPrimaryContainer: the chip
    // is a macro tint over the widget surface, never a primary container, and
    // the mint-era `onPrimaryContainer` only ever passed contrast by accident.
    // Against the carbs fill — the lightest of the three — it was ~3.9:1 at
    // 10sp, under AA; the plain foreground clears it on all three macros.
    val fillFraction = (row.current / max(1.0, row.goal)).coerceIn(0.0, 1.0).toFloat()
    val fillWidthDp = (chipWidthDp * fillFraction).dp

    Box(
        modifier = modifier
            .height(chipHeightDp.dp)
            // Always-on subtle macro tint — so even at 0% progress the chip
            // reads as "carbs" by color, not just by label.
            .background(row.base)
            .cornerRadius(cornerDp.dp),
    ) {
        if (fillFraction > 0f && chipWidthDp > 0f) {
            Row(modifier = GlanceModifier.fillMaxSize()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width(fillWidthDp)
                        .background(row.fill)
                        .cornerRadius(cornerDp.dp),
                ) {}
            }
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = padHDp.dp, vertical = padVDp.dp),
        ) {
            // Either "C" or "Carbs" — never both. Decided by widget WIDTH,
            // so a wide-but-short widget still shows the full name.
            Text(
                text = if (useFullLabel) row.label else row.short,
                style = TextStyle(
                    color = WidgetColors.OnSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(if (useFullLabel) 4.dp else 2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${row.current.roundToInt()}",
                    style = TextStyle(
                        color = WidgetColors.OnSurface,
                        fontSize = numberSp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(2.dp))
                Text(
                    text = "g",
                    style = TextStyle(
                        color = WidgetColors.OnSurface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp),
                )
            }
            if (showFooter) {
                Text(
                    text = "of ${row.goal.roundToInt()}",
                    style = TextStyle(
                        color = WidgetColors.OnSurface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FiberRow(
    current: Double,
    goal: Double,
    widgetWidthDp: Float,
    outerPaddingDp: Float,
) {
    val fill = (current / goal).coerceIn(0.0, 1.0).toFloat()
    val rowWidthDp = max(0f, widgetWidthDp - 2f * outerPaddingDp)
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(28.dp)
            .background(WidgetColors.PrimaryContainer)
            .cornerRadius(14.dp),
    ) {
        if (fill > 0f && rowWidthDp > 0f) {
            Row(modifier = GlanceModifier.fillMaxSize()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width((rowWidthDp * fill).dp)
                        .background(WidgetColors.PrimarySubtle)
                        .cornerRadius(14.dp),
                ) {}
            }
        }
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fiber",
                style = TextStyle(
                    color = WidgetColors.OnPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "${current.roundToInt()} / ${goal.roundToInt()} g",
                style = TextStyle(
                    color = WidgetColors.OnPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
