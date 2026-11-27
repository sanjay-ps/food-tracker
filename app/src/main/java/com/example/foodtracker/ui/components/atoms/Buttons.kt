package com.example.foodtracker.ui.components.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Sizes

/**
 * Actions: [AppButton] (ink pill), [AppOutlinedButton] (soft pill),
 * [AppTextButton] (link with a real tap target). All flat, all sink slightly
 * when pressed. [Sizes.button] is a minimum height, never a fixed one, so
 * labels survive large font scales.
 */

/** The primary action. Filled `primary`, pill, flat. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Sizes.button).pressScale(),
        shape = RoundedCornerShape(Radius.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.none,
            pressedElevation = Elevation.none,
            focusedElevation = Elevation.none,
            hoveredElevation = Elevation.none,
        ),
    ) {
        Text(text, style = ButtonText)
    }
}

/** The secondary action: a soft grey pill, no border. */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Sizes.button).pressScale(),
        shape = RoundedCornerShape(Radius.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = softFill(),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.none,
            pressedElevation = Elevation.none,
            focusedElevation = Elevation.none,
            hoveredElevation = Elevation.none,
        ),
    ) {
        Text(text, style = ButtonText)
    }
}

private val ButtonText = AppText.button

/** Sinks a Material button slightly while pressed, without stealing its ripple. */
@Composable
private fun Modifier.pressScale(): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btnPress")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/**
 * The tertiary action — a text link that still has a real tap target.
 *
 * [destructive] paints the label `error`. It is the one sanctioned exception to
 * house rule 3's "over-limit is the only thing coloured": a confirmation whose
 * two buttons look identical is a confirmation that does not confirm anything,
 * and the irreversible option has to be the one that looks different. It stays
 * a *text* button — a filled error button would make "erase everything" the
 * loudest thing in the dialog, which is the opposite of what a destructive
 * confirm wants.
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Sizes.button),
        shape = RoundedCornerShape(Radius.pill),
        colors = if (destructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        },
    ) {
        Text(text, style = AppText.label)
    }
}

/**
 * The one spinner. Replaces the Expressive `LoadingIndicator`, which was a
 * shape-morphing animation — banned by the motion rules and, being
 * experimental, one of the APIs phase 1 had to remove anyway.
 */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeWidth = 2.dp,
    )
}

