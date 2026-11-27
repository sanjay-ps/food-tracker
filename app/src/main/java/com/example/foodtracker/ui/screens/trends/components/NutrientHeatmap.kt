package com.example.foodtracker.ui.screens.trends.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.components.atoms.StatusCell
import com.example.foodtracker.ui.screens.trends.NutrientTrendRow
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * Room for `SATURATED FAT` at `eyebrow`, and no more — the cells need the rest.
 *
 * Four dp wider than it was, because the label is uppercase now: at 11sp with
 * 1.2sp of tracking the longest nutrient name is a hair over the old 96dp and
 * would have ellipsed on every phone. The cells pay 0.3dp each for it.
 */
private val LABEL_WIDTH = 100.dp

/**
 * Grid geometry, not spacing.
 *
 * These are the size of a cell and the hairline between two of them — the same
 * kind of value as a chart's height, which house rule 5 explicitly leaves as
 * raw dp. A 4dp gap on the `Spacing` scale between 7dp cells would make the
 * gutters as wide as the data.
 */
private val CELL_GAP = 2.dp
private val MIN_CELL = 4.dp
private val MAX_CELL = 10.dp

/**
 * Nutrients down, days across.
 *
 * **This is the section the thirty-micronutrient database is for.** Every other
 * surface in the app reads a single day, and a single day of B12 is noise: you
 * ate liver on Tuesday, you did not on Wednesday, neither fact means anything.
 * A month of Tuesdays does. The grid is the only place in the app where
 * *chronic* shortfall is visible, and it is visible at a glance because it is
 * the shape of the row, not any cell in it, that carries the finding — a row
 * that is dark all the way across is a deficiency; a row with one dark cell is
 * a Tuesday.
 *
 * **Sorted worst-first, truncated to [collapsedRows].** The sort is what makes
 * it readable without interaction: whatever you are chronically missing is in
 * the top handful of rows, so the collapsed grid is already the answer and
 * "show all" is for the curious rather than for the finding.
 *
 * **Cells size themselves to the space.** The grid is nutrients × days and both
 * dimensions move (28 rows or 6; 7 columns or 30), so a fixed 10dp cell either
 * overflows at 30 days or wastes half the width at 7. The cell is computed from
 * the measured width instead and clamped to [MIN_CELL]–[MAX_CELL]; below the
 * minimum the grid stops reading as a grid, which is why the ViewModel caps the
 * window at 30 days rather than letting a year's worth try to fit. Losing the
 * enclosing card gave every cell 32dp of width back, which at 30 columns is
 * most of a dp per cell — small, and in the direction that matters.
 *
 * **A row is not a tap target.** It used to be: tapping one opened "Rich in…"
 * for that nutrient. The row is a 16sp eyebrow beside cells that clamp to 10dp,
 * so with its 4dp padding the whole thing stands about 24dp tall — half the
 * 48dp minimum, in a grid of up to 29 of them stacked with no gap, where the
 * row above and the row below are the same size and one dot apart. That is a
 * target you hit by luck. The shortcut was worth keeping, so it moved to the
 * four macro rows on Today, which are full-bleed and twice the height; the
 * card's own "Find rich foods" action reaches the same screen with its own
 * nutrient picker for everything else.
 *
 * **The drawing model is untouched by the editorial pass** — same statuses,
 * same sizing maths, same sort. What changed is type and rhythm: the row label
 * is an `eyebrow` rather than `bodyS`, which is house rule 2's label register
 * and lines the grid's left edge up with every other label on the page, and the
 * row pitch is on the `Spacing` scale instead of a hand-rolled 5dp halved to
 * 2.5 — a raw value that came straight out of the old progress screen.
 */
@Composable
fun NutrientHeatmap(
    rows: List<NutrientTrendRow>,
    firstDayLabel: String,
    lastDayLabel: String,
    expanded: Boolean,
    collapsedRows: Int = 6,
    modifier: Modifier = Modifier,
) {
    val shown = if (expanded) rows else rows.take(collapsedRows)
    val columns = rows.firstOrNull()?.statuses?.size ?: 0
    if (columns == 0) return

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth - LABEL_WIDTH
        val cell = ((available - CELL_GAP * (columns - 1)) / columns)
            .coerceIn(MIN_CELL, MAX_CELL)

        Column(Modifier.fillMaxWidth()) {
            shown.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.field.label,
                        style = AppText.eyebrow,
                        // The row's own state decides how loud its name is: a
                        // nutrient you are hitting does not need to be read.
                        color = if (row.missedDays > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(LABEL_WIDTH).padding(end = Spacing.s),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        row.statuses.forEach { status ->
                            StatusCell(status = status, size = cell)
                        }
                    }
                }
            }

            // Which way time runs, stated once at the bottom rather than as a
            // row of thirty dates nobody could read.
            if (firstDayLabel.isNotEmpty() && lastDayLabel.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                Row(Modifier.fillMaxWidth().padding(start = LABEL_WIDTH)) {
                    Text(
                        firstDayLabel,
                        style = AppText.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        lastDayLabel,
                        style = AppText.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
