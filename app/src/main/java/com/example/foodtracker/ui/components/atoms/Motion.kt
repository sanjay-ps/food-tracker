package com.example.foodtracker.ui.components.atoms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Small motion helpers shared by every screen.
 *
 *  - [pressable]: a click that also sinks the element to 96% while held.
 *  - [staggerIn]: fade + rise on first appearance, delayed by list position.
 *  - [AnimatedCount]: an integer that counts to its new value.
 */

/** `clickable` plus a springy press-down scale. */
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleTo else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Fade in and rise 16dp, [index] × 60ms after first composition.
 *
 * Plays once per item: the flag is saveable, and lazy lists keep saveable
 * state for items scrolled out and back, so nothing re-animates on scroll.
 */
fun Modifier.staggerIn(index: Int): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index * 60L)
            launch { progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            played = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}

/** An integer that animates — and on first show counts up from zero. */
@Composable
fun AnimatedCount(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { grouped(it.toDouble()) },
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) {
        anim.animateTo(value.toFloat(), tween(700, easing = FastOutSlowInEasing))
    }
    Text(format(anim.value.roundToInt()), style = style, color = color, modifier = modifier)
}
