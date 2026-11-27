package com.example.foodtracker.ui.screens.trends.components

import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.atoms.cardColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.screens.trends.TrendsRange
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing

/** The rule under the selected range. Same 24dp as `DayStrip`'s, for the same reason. */

/**
 * The window every section on Trends obeys: `7D · 30D · 90D · 1Y`, as a
 * segmented pill on a white track.
 */
@Composable
fun RangeBar(
    selected: TrendsRange,
    onSelect: (TrendsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Re-tapping the selected range is a no-op (ExclusiveButtonGroup only
    // calls back on a change), so a resting thumb never re-runs the query.
    ExclusiveButtonGroup(
        options = TrendsRange.entries,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
        trackColor = cardColor(),
        label = { it.label.uppercase() },
    )
}
