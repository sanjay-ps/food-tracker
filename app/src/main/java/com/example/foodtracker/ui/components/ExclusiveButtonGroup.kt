package com.example.foodtracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.height
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.components.atoms.softFill
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing

/**
 * Single-select segmented control: an ink pill slides between options on a
 * soft track (`Week | Month` style). Selection colours animate.
 *
 * Re-tapping the selected segment is a no-op — no haptic, no [onSelect] —
 * because several callers persist on every `onSelect`.
 *
 * [fillWidth] (default true) shares the row's width equally; set it false for
 * long labels and the row sizes to its labels and scrolls horizontally.
 * [trackColor] defaults to [com.example.foodtracker.ui.components.atoms.softFill].
 */
@Composable
fun <T> ExclusiveButtonGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    trackColor: Color = softFill(),
    label: (T) -> String,
) {
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme
    val pick: (T) -> Unit = { option ->
        if (option != selected) {
            haptics.light()
            onSelect(option)
        }
    }
    if (fillWidth) {
        // Equal segments, so the ink pill can slide between them as one shape.
        // It is drawn behind the row rather than laid out, which keeps this
        // control free of intrinsic measurement.
        val index = options.indexOf(selected).coerceAtLeast(0).toFloat()
        val at by animateFloatAsState(
            index,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segX",
        )
        val pill = colors.primary
        val gap = Spacing.xs
        Row(
            modifier
                .clip(RoundedCornerShape(Radius.pill))
                .background(trackColor)
                .padding(Spacing.xs)
                .drawBehind {
                    val g = gap.toPx()
                    val w = (size.width - g * (options.size - 1)) / options.size
                    drawRoundRect(
                        color = pill,
                        topLeft = Offset(at * (w + g), 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val ink by animateColorAsState(
                    if (isSelected) colors.onPrimary else colors.onSurfaceVariant, label = "segInk",
                )
                Segment(label(option), ink, isSelected, Modifier.weight(1f)) { pick(option) }
            }
        }
    } else {
        Row(
            modifier = modifier
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(Radius.pill))
                .background(trackColor)
                .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val fill by animateColorAsState(
                    if (isSelected) colors.primary else Color.Transparent, label = "segFill",
                )
                val ink by animateColorAsState(
                    if (isSelected) colors.onPrimary else colors.onSurfaceVariant, label = "segInk",
                )
                Segment(
                    label(option), ink, isSelected,
                    Modifier.clip(RoundedCornerShape(Radius.pill)).background(fill),
                ) { pick(option) }
            }
        }
    }
}

@Composable
private fun Segment(
    text: String,
    ink: Color,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .semantics { this.selected = isSelected; role = Role.Tab }
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        // Unconstrained lines: an equal-width segment wraps a long label
        // ("South Indian") rather than clipping it.
        Text(text = text, style = AppText.label, color = ink, textAlign = TextAlign.Center)
    }
}
