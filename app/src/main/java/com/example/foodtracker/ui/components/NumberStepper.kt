package com.example.foodtracker.ui.components

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.foundation.shape.CircleShape
import com.example.foodtracker.ui.components.atoms.softFill
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * `[−] value [+]` — the stepper row.
 *
 * Pair it with a slider, which is how every caller uses it: the slider takes
 * the coarse swing and the stepper lands the last quarter-serving, because a
 * drag cannot reliably hit 1.25 on a 412dp screen and fourteen taps to cross a
 * range is nobody's idea of a control.
 *
 * Three changes from the version this replaces. The buttons grow
 * 32→36dp, which brings the pair back above the size a thumb can hit without
 * aiming. The tonal `secondaryContainer` fill becomes an outline: on `#000000`
 * a tonal fill is the only lift on the surface and reads as elevation, which
 * the house rules ban outright. And the value is drawn by [ValueText] — the whole point of
 * that atom is that `2 740 mg` and `0.4 µg` cannot share one `"%.1f"`.
 *
 * The editorial pass finishes that job. `OutlinedIconButton`'s defaults were
 * doing the outlining, which meant a `outline`-toned circle that did not match
 * any other edge in the app; the buttons now carry the same
 * [Elevation.hairline]/`outlineVariant` edge as every card, chip and divider,
 * and the same [Radius.sm] corner, so the control reads as two hairline keys
 * flanking a number rather than as a widget from another kit. Nothing about
 * the row is tinted: the number is the loudest thing here, which is rule 2.
 *
 * Each tap still fires a light haptic, so the change is felt even when the
 * buttons sit outside the focal area.
 *
 * Three faces, one control:
 *  - the [content] overload, for a readout too compound for an atom — the
 *    serving sheet's `1.5 × dosa / 225 g` is two lines and two type styles;
 *  - the `(value, unit)` overload, which is the common numeric case;
 *  - the `String` overload, kept for the profile and onboarding forms whose
 *    values are already formatted strings by the time they get here.
 */
@Composable
fun NumberStepper(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        StepButton(
            onClick = { haptics.light(); onDecrement() },
            enabled = decrementEnabled,
            icon = Lucide.Minus,
            label = "Decrease",
        )
        Box(
            modifier = Modifier.defaultMinSize(minWidth = 96.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
        StepButton(
            onClick = { haptics.light(); onIncrement() },
            enabled = incrementEnabled,
            icon = Lucide.Plus,
            label = "Increase",
        )
    }
}

/** The numeric case: the value and its unit, formatted by [ValueText]. */
@Composable
fun NumberStepper(
    value: Double,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
) {
    NumberStepper(
        onDecrement = onDecrement,
        onIncrement = onIncrement,
        modifier = modifier,
        decrementEnabled = decrementEnabled,
        incrementEnabled = incrementEnabled,
    ) {
        ValueText(value = value, unit = unit)
    }
}

/**
 * Pre-formatted text. Still rendered in the numeric style so a stepper's value
 * looks the same wherever it is built — the callers that use this one are
 * holding strings like `"5′ 11″"` that no unit-plus-number atom can express.
 */
@Composable
fun NumberStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
) {
    NumberStepper(
        onDecrement = onDecrement,
        onIncrement = onIncrement,
        modifier = modifier,
        decrementEnabled = decrementEnabled,
        incrementEnabled = incrementEnabled,
    ) {
        Text(
            value,
            style = AppText.numberM,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * One key of the pair. Square-ish [Radius.sm] rather than Material's circle,
 * and the app's own hairline rather than `OutlinedIconButton`'s `outline`-toned
 * default — the point of a single edge colour is that every edge is it.
 */
@Composable
private fun StepButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
) {
    val scheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = softFill(),
            contentColor = scheme.onSurface,
            disabledContainerColor = softFill().copy(alpha = 0.5f),
            disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    }
}
