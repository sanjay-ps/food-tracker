package com.example.foodtracker.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.example.foodtracker.ui.components.atoms.softFill
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing

/**
 * One-of-N as a stack of full-width rows — for choices whose labels are too
 * long for a segmented control ("Maintain weight", "Last 3 months"). Each row
 * is a soft pill; the chosen one gets an ink outline and a check, no radio
 * circles. Re-tapping the chosen row does nothing.
 */
@Composable
fun <T> OptionList(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    caption: (T) -> String? = { null },
    label: (T) -> String,
) {
    val haptics = rememberAppHaptics()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        options.forEach { option ->
            OptionRow(
                label = label(option),
                caption = caption(option),
                selected = option == selected,
                onClick = {
                    if (option != selected) {
                        haptics.light()
                        onSelect(option)
                    }
                },
            )
        }
    }
}

/**
 * One-of-N as an even grid of the same rows — for short options that come as a
 * set (rates, categories, meal slots), where one long column would waste the
 * width. Rows in a line share a height, so the grid stays even.
 */
@Composable
fun <T> OptionGrid(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    caption: (T) -> String? = { null },
    label: (T) -> String,
) {
    val haptics = rememberAppHaptics()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        options.chunked(columns).forEach { line ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                line.forEach { option ->
                    OptionRow(
                        label = label(option),
                        caption = caption(option),
                        selected = option == selected,
                        onClick = {
                            if (option != selected) {
                                haptics.light()
                                onSelect(option)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        // Top-aligned, so titles and captions line up across a row
                        // even when one cell's text runs longer.
                        alignTop = true,
                    )
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A single option row — also usable on its own inside a custom list. */
@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    alignTop: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "optRing")
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget + 4.dp)
            .clip(shape)
            .background(softFill())
            .border(1.5.dp, ring, shape)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AppText.titleM, color = scheme.onSurface)
            if (caption != null) {
                Text(caption, style = AppText.bodyS, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(visible = selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
