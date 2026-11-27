package com.example.foodtracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/** One line of a sub-breakdown: a component name and its formatted amount. */
data class NutrientSubRow(val label: String, val value: String)

/**
 * The quiet detail inside a nutrient's bottom sheet — soluble/insoluble under
 * Fiber, essential amino acids under Protein.
 *
 * Deliberately plain: no progress bar, no goal, no percentage of a target, no
 * tap target. These are detail you open when curious, not numbers to hit. They
 * sit behind a tap rather than beside the bar on the home screen, so the day's
 * scoreboard stays the same length for someone who never looks at them.
 *
 * **Indentation is now a standing head.** The block used to be pushed 12dp
 * right so it would read as subordinate to the total above it, which is the
 * trick a page reaches for when it has nothing else to show hierarchy with; it
 * also put these rows on a left margin no other line in the sheet shares. A
 * head with a rule under it ([Section], house rule 1) says the same thing in
 * the sheet's own vocabulary, says *what* the rows are while it is at it, and
 * lets them sit on the one margin — and each row then reads label-left /
 * number-right like every other row in the app (rule 2), with the label as an
 * eyebrow so the figure is the louder half of the line.
 *
 * [note] carries the honest caveat about coverage. A day's sub-total is only
 * summed from the foods that happen to have a breakdown, so without it the
 * number would read as a complete figure when it is usually a partial one.
 */
@Composable
fun NutrientSubRows(
    rows: List<NutrientSubRow>,
    note: String? = null,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Spacing.xxl))
        Section("Breakdown") {
            rows.forEachIndexed { index, row ->
                if (index > 0) AppDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.label,
                        style = AppText.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.value,
                        style = AppText.numberM,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (note != null) {
                AppDivider()
                Text(
                    note,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
    }
}
