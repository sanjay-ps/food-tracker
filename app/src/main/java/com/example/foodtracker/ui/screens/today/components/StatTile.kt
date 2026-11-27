package com.example.foodtracker.ui.screens.today.components

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * Water and weight, rebuilt as rows rather than tiles.
 *
 * They used to be two outlined cards side by side. Two boxes holding one number
 * each is the Direction A anatomy the rewrite is supposed to be leaving: the
 * border does no work a label and a rule cannot do, and splitting the row in
 * half caps the number at a third of the screen's width.
 *
 * As a row the label sits left in an eyebrow, the number sits right at full
 * weight, and the caption trails it — the same structure as every other line on
 * the screen, which is what gives the page one rhythm instead of three.
 *
 * **Every one of these opens something, and now it says so.** A row with no
 * border, no chevron and no affordance of any kind is indistinguishable from a
 * read-only reading — which is exactly what water, weight and the plan row
 * looked like, so the sheets behind them went unfound. The chevron is the
 * cheapest honest signal: it is navigation, not decoration, it is the
 * convention on every platform, and at `onSurfaceVariant` it costs the row no
 * emphasis the number should have had.
 *
 * [caption] gets two lines before it ellipsises. One is enough for `of 2 500
 * ml`; the plan row puts the actual planned foods there, which is longer and is
 * the point of it.
 */
@Composable
fun StatRow(
    label: String,
    value: String,
    unit: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                caption,
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = AppText.numberL, color = MaterialTheme.colorScheme.onSurface)
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
        }
        // Boxed at a fixed width so the numbers in a stack of these rows still
        // end on the same x — the chevron is the same glyph every time, but the
        // icon's own 24dp box is not what centres it.
        Box(
            Modifier.width(Spacing.xxl).padding(start = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
