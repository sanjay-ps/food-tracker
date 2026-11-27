package com.example.foodtracker.ui.components.atoms

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Number formatting.
 *
 * One rounding rule for the whole app. This is what kills the ~40 ad-hoc
 * `"%.1f".format(x)` call sites, which between them produced `1.0 ×` servings,
 * `2740.0 mg` of sodium and `0.0 µg` of B12 on the same screen.
 */

/** A non-breaking space. Thousands separator and unit gap both use it. */
const val NBSP = ' '

/** `2100` → `2 100`. Grouped with a non-breaking space so it never wraps. */
/** Whole number with comma grouping: `2,468`. */
fun grouped(value: Double): String = String.format(Locale.US, "%,d", value.roundToInt())

/**
 * Counts, not measurements: `1 ×`, `1.5 ×` — never `1.0 ×`.
 *
 * Separate from [formatValue] because a serving count is an integer that
 * happens to allow halves, and the magnitude rule below would print the
 * overwhelmingly common case as `1.0`.
 */
fun countText(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(value)

/**
 * Precision follows magnitude, which is the whole point: 0.4 µg of B12 needs a
 * decimal and 2 740 mg of sodium does not, and no single `%.1f` serves both.
 */
fun formatValue(value: Double): String = when {
    value >= 1000 -> grouped(value)
    value >= 10 -> value.roundToInt().toString()
    value >= 1 -> "%.1f".format(value)
    value > 0 -> "%.2f".format(value).trimEnd('0').trimEnd('.')
    else -> "0"
}

/** Value and unit on one baseline, the unit in the quieter colour and size. */
@Composable
fun ValueText(
    value: Double,
    unit: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AppText.numberM,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(formatValue(value), style = style, color = color)
        if (unit.isNotEmpty()) {
            Text(
                NBSP + unit,
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Small pill badge — `NOW`, counts, flags.
 *
 * Neutral by default. It used to fill with `primaryContainer`, which is house
 * rule 3's exact prohibition — a filled accent block used as chrome — and the
 * rewrite replaced its other call sites with accent *labels* while leaving the
 * atom's own default pointing at the thing it had removed. A badge marks a
 * category, not a reading, so the accent has nothing to say here.
 */
@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        style = AppText.eyebrow,
        color = content,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(container)
            .padding(horizontal = Spacing.s, vertical = 2.dp),
    )
}

/** How loud a [Metric] is. One [Hero] per screen; [Large] in cards; [Medium] in dense rows. */
enum class MetricSize { Hero, Large, Medium }

/**
 * The standard "label over a number" block — every figure in the app with a
 * caption above it uses this, so the same kind of number is always the same
 * size: label in [AppText.eyebrow], value in the size's number style, unit
 * trailing on the baseline in the quiet colour.
 */
@Composable
fun Metric(
    label: String?,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    size: MetricSize = MetricSize.Medium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val valueStyle = when (size) {
        MetricSize.Hero -> AppText.numberXXL
        MetricSize.Large -> AppText.numberL
        MetricSize.Medium -> AppText.numberM
    }
    val unitStyle = if (size == MetricSize.Hero) AppText.titleM else AppText.bodyS
    Column(modifier, horizontalAlignment = horizontalAlignment) {
        if (label != null) {
            Text(label, style = AppText.eyebrow, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = valueStyle, color = color, maxLines = 1)
            if (unit.isNotEmpty()) {
                Text(
                    NBSP + unit,
                    style = unitStyle,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = if (size == MetricSize.Hero) 8.dp else 2.dp),
                )
            }
        }
    }
}
