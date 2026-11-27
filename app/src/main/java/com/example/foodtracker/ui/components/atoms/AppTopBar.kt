package com.example.foodtracker.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * The app's top bar, in two shapes:
 *
 *  - **Pushed page** ([onBack] set): a round back button on the left and the
 *    title centred, like a sheet header.
 *  - **Tab destination** (no [onBack]): a large left-aligned headline with an
 *    optional line under it.
 *
 * Both sit on the canvas colour and never tint on scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = scheme.background,
        scrolledContainerColor = scheme.background,
        titleContentColor = scheme.onSurface,
        navigationIconContentColor = scheme.onSurface,
        actionIconContentColor = scheme.onSurface,
    )
    if (onBack != null) {
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = AppText.titleM, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, style = AppText.bodyS, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            },
            navigationIcon = {
                RoundIconButton(
                    icon = Lucide.ChevronLeft,
                    contentDescription = "Back",
                    onClick = onBack,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            },
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors,
        )
    } else {
        TopAppBar(
            modifier = modifier,
            title = {
                Column {
                    Text(title, style = AppText.headline)
                    if (subtitle != null) {
                        Text(subtitle, style = AppText.bodyS, color = scheme.onSurfaceVariant)
                    }
                }
            },
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = colors,
        )
    }
}

/**
 * A 40dp round button on the card fill — back, close, avatar. The 48dp touch
 * target comes from the padding around the visible disc.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(cardColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
