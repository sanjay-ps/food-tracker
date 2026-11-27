package com.example.foodtracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.foodtracker.data.NutrientCategory
import com.example.foodtracker.data.NutrientField
import com.example.foodtracker.data.NutrientStatus
import com.example.foodtracker.ui.theme.MacroBurned
import com.example.foodtracker.ui.theme.MacroCarbs
import com.example.foodtracker.ui.theme.MacroFat
import com.example.foodtracker.ui.theme.MacroFiber
import com.example.foodtracker.ui.theme.MacroProtein
import com.example.foodtracker.ui.theme.MacroWater
import com.example.foodtracker.ui.theme.StatusOver

/**
 * How a nutrient is drawn (`NutrientColor` → `NutrientVisuals`).
 *
 * Two questions, one file: *which nutrient is this* ([color]) and *how is the
 * day going for it* ([NutrientStatus.color]). The rename is because every new
 * surface needs the second one, and only the charts need the first.
 */

/**
 * The hue a nutrient is drawn in, wherever it is drawn.
 *
 * Shared rather than per-screen so protein is the same colour on Today's macro
 * legend and in a search result — a reader who has learned one has learned the
 * other. The named macros get their own hue; everything else falls back to a
 * per-category colour, which is enough to tell a mineral from a vitamin without
 * inventing thirty hues nobody could tell apart.
 */
fun NutrientField.color(): Color = when (key) {
    "calories" -> MacroBurned
    "carbs" -> MacroCarbs
    "protein" -> MacroProtein
    "fat", "saturatedFat" -> MacroFat
    "fiber" -> MacroFiber
    else -> when (category) {
        NutrientCategory.Macros -> MacroBurned
        NutrientCategory.Minerals -> MacroFiber
        NutrientCategory.Vitamins -> MacroWater
    }
}

/**
 * The status palette. Only two states are allowed to be loud, and both of them
 * mean something has gone past a cap.
 *
 * [NutrientStatus.Headroom] is `outline`, not the positive green (`secondary`): a limit under
 * its cap is unremarkable, and painting it green would make a day with no
 * sodium look like an achievement.
 */
@Composable
fun NutrientStatus.color(): Color = when (this) {
    NutrientStatus.Unmet -> MaterialTheme.colorScheme.surfaceContainerHighest
    NutrientStatus.Partial -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f)
    NutrientStatus.Met -> MaterialTheme.colorScheme.secondary
    NutrientStatus.Headroom -> MaterialTheme.colorScheme.outline
    NutrientStatus.Over -> StatusOver
    NutrientStatus.FarOver -> MaterialTheme.colorScheme.error
    // Never actually drawn: the strip filters untargeted readings out before it
    // reaches a cell. Here so the `when` stays exhaustive without an `else`,
    // which would silently swallow any status added after this one.
    NutrientStatus.Untracked -> MaterialTheme.colorScheme.outlineVariant
}

/**
 * The colour a *named* row's meter fills with.
 *
 * [NutrientStatus.Unmet] is `outlineVariant` in the strip — the same value as a
 * meter's own track, which is right for a 10px cell and wrong for a meter,
 * where it renders as an empty bar. A row that got named *because* it is short
 * still has to show how short.
 */
@Composable
fun NutrientStatus.meterColor(): Color =
    if (this == NutrientStatus.Unmet) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    else color()
