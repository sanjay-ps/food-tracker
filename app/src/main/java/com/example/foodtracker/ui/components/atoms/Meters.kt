package com.example.foodtracker.ui.components.atoms

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.foodtracker.data.NutrientStatus
import com.example.foodtracker.ui.components.color
import com.example.foodtracker.ui.theme.Elevation

/**
 * Progress primitives.
 *
 * [Meter] replaces three near-identical implementations: `NutrientBar`'s inner
 * `Box` pair, `HomeScreen.ContributionRow`'s and `RichFoodsScreen`'s
 * `NutrientReadout`. [Ring] is the arc extracted out of `MacroDonut`, which
 * drew its track as a hardcoded `0x22000000` — 13% black, invisible on a black
 * canvas.
 *
 * Both animate over 450ms and interpolate the *value*, per the motion rules,
 * which is why the numbers beside them carry `tnum`: the digits would otherwise
 * jitter for the whole animation.
 */

private const val ANIM_MS = 600

/**
 * One goal- and limit-aware linear meter.
 *
 * @param capAt when set, the fill is drawn against a cap tick at this position
 *        in the track. A [NutrientStatus.Headroom] field is passed a fraction
 *        scaled below the tick, so crossing its cap reads as crossing a line
 *        rather than as merely filling up — the fix for the complaint that a
 *        sodium reading at 40% used to look like failure.
 */
@Composable
fun Meter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    capAt: Float? = null,
    animate: Boolean = true,
) {
    val target = fraction.coerceIn(0f, 1f)
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (animate) ANIM_MS else 0, easing = FastOutSlowInEasing),
        label = "meter",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val tick = MaterialTheme.colorScheme.outline
    // Never a hairline: the softer look wants a bar you can see at a glance.
    Canvas(modifier = modifier.fillMaxWidth().height(height.coerceAtLeast(5.dp))) {
        val r = size.height / 2f
        drawRoundRect(color = track, size = size, cornerRadius = CornerRadius(r, r))
        if (value > 0f) {
            drawRoundRect(
                color = color,
                // Never thinner than it is tall: a 0.4% reading should still be
                // a visible dot, not a sliver one pixel wide.
                size = Size((size.width * value).coerceAtLeast(size.height), size.height),
                cornerRadius = CornerRadius(r, r),
            )
        }
        if (capAt != null) {
            drawRect(
                color = tick,
                topLeft = Offset(size.width * capAt, 0f),
                size = Size(Elevation.hairline.toPx(), size.height),
            )
        }
    }
}

/**
 * Arc progress. Track and arc are both themed — in the `MacroDonut` this came
 * out of, neither was.
 *
 * Starts at twelve o'clock and sweeps clockwise with a round cap, so a nearly
 * complete ring still reads as nearly complete rather than closing early.
 */
@Composable
fun Ring(
    fraction: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 140.dp,
    stroke: Dp = 14.dp,
    color: Color = MaterialTheme.colorScheme.secondary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    val value by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(ANIM_MS * 2, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val w = stroke.toPx()
            val inset = w / 2f
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
            if (value > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * A 10×10 rounded square in one of the six status colours.
 *
 * The unit of the nutrient strip on Today and of the heatmap on Trends. Being
 * this small is the point: 29 of them fit in one 40dp band, which is the only
 * way a day's whole micronutrient state fits on a screen that also has to show
 * calories and meals.
 */
@Composable
fun StatusCell(
    status: NutrientStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(status.color())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}
