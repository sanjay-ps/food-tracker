package com.example.foodtracker.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing
import androidx.compose.foundation.background
import com.example.foodtracker.data.FoodCategory
import com.example.foodtracker.data.db.FoodEntity
import kotlin.math.roundToInt

/**
 * One food in a list — emoji disc, name, and a brand/kcal/serving sub-line.
 *
 * The disc keeps the emoji: scanning 166 seeded foods, a
 * glyph is a faster filter than reading the first word of each name. What it
 * no longer keeps is a per-category tint. There were twelve of them —
 * `0xFFFFE0B2`, `0xFFB3E5FC`, `0xFFD1C4E9` and so on — pastels picked to sit
 * on a white card. On `#000000` they are twelve near-white blobs: the loudest
 * thing in a list whose job is to be scanned, and all of them louder than the
 * food names they belong to. They also encoded nothing a reader could use —
 * the emoji already says "fruit", so the tint was a second copy of the same
 * fact in a code nobody has learned.
 *
 * One `surfaceContainerHigh` disc replaces all twelve. It is a holder for the
 * glyph, not a signal.
 */
@Composable
fun FoodRow(
    food: FoodEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(com.example.foodtracker.ui.components.atoms.cardColor()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = food.emoji(),
                style = AppText.titleM,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                food.name,
                style = AppText.titleM,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                listOfNotNull(
                    food.brand?.takeIf { it.isNotBlank() },
                    "${food.servingCalories().roundToInt()} kcal · ${food.servingLabel}",
                ).joinToString(" · "),
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        trailing()
    }
}
