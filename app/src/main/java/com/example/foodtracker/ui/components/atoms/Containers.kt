package com.example.foodtracker.ui.components.atoms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * Containers: [AppCard] (an untitled card), [AppDivider], and the icon discs
 * [IconCircle] / [PastelIcon]. Titled cards are [Section].
 */

/**
 * A plain card: [cardColor] fill, [Radius.lg] corners, no border, no shadow.
 * Use [Section] when the card has a title.
 *
 * Pass `padding = 0.dp` when its rows need to bleed to the edge, as a divided
 * list does; the rows then own their insets.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(cardColor())
            .padding(padding),
        content = content,
    )
}

/**
 * Eyebrow caption with an optional trailing slot.
 *
 * Replaces `SectionCard`/`FormSection`'s built-in `titleMedium` heading and
 * `FormLabel`. Splitting the heading out of the card is deliberate: a card with
 * a mandatory title cannot be used for the many blocks that do not want one,
 * which is exactly how the app ended up with six cards.
 *
 * [title] is uppercased here rather than at the call site, so the one place
 * that decides what an eyebrow looks like is this function.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = AppText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Hairline. The `outlineVariant.copy(alpha = 0.35f)` divider appeared in four files. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Elevation.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * Tinted disc behind an icon or an emoji. Four copies today — `EmptyState`'s
 * 72dp disc, `FoodRow`'s category bubble, `AddCustomFoodRow`'s and the profile
 * `AvatarEmoji` — at four sizes and three fills.
 *
 * The default fill is `surfaceContainerHigh` rather than a tint of the content:
 * on `#000000` a 12%-alpha accent disc is indistinguishable from the canvas,
 * which is what made `FoodRow`'s twelve pastel bubbles vanish.
 */
@Composable
fun IconCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** [IconCircle] around a vector icon, which is the common case. */
@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.45f,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconCircle(modifier = modifier, size = size, container = container) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A Lucide icon on a pastel disc — meal rows, settings rows, stat cards. */
@Composable
fun PastelIcon(
    icon: ImageVector,
    pastel: com.example.foodtracker.ui.theme.Pastel,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    IconCircle(modifier = modifier, size = size, container = pastel.fill) {
        Icon(icon, contentDescription = null, tint = pastel.ink, modifier = Modifier.size(size * 0.48f))
    }
}
