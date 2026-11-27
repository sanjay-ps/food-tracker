package com.example.foodtracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import com.example.foodtracker.ui.components.atoms.softFill
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * A pill's minimum height — see the note at the end of [HapticFilterChip]'s
 * documentation for why it is not the 48dp touch target.
 */
private val MinHeight = 40.dp

/**
 * A pill chip: ink fill with light text when selected, a soft fill otherwise.
 * Ticks a light haptic on every tap (callers include toggles where tapping the
 * selected chip turns it off).
 *
 * [MinHeight] is 40dp rather than 48dp because these sit in dense, scrolling
 * rows (serving variants, oils).
 */
@Composable
fun HapticFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Radius.pill),
) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val content = when {
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> scheme.onPrimary
        else -> scheme.onSurface
    }
    val fill by animateColorAsState(
        when {
            selected -> scheme.primary
            else -> softFill()
        },
        label = "chipFill",
    )
    Surface(
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = fill,
        contentColor = content,
        tonalElevation = Elevation.none,
        shadowElevation = Elevation.none,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = MinHeight)
                .padding(horizontal = Spacing.m),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(Spacing.s))
                }
                ProvideTextStyle(AppText.label) { label() }
                if (trailingIcon != null) {
                    Spacer(Modifier.width(Spacing.s))
                    trailingIcon()
                }
            }
        }
    }
}
