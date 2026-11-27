package com.example.foodtracker.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.composables.icons.lucide.ChartNoAxesColumn
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.UserRound
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.pressable
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * The three tab destinations. Logging is an *action*, not a place, so it is
 * the round "+" button beside the bar rather than a fourth tab.
 */
enum class BarDestination(
    val label: String,
    val icon: ImageVector,
) {
    Today("Today", Lucide.House),
    Trends("Trends", Lucide.ChartNoAxesColumn),
    You("You", Lucide.UserRound),
}

/** Whether [this] destination is (or sits under) one of the three bar tabs. */
fun NavDestination?.barDestination(): BarDestination? = when {
    this == null -> null
    hierarchy.any { it.hasRoute(TodayRoute::class) } -> BarDestination.Today
    hierarchy.any { it.hasRoute(TrendsRoute::class) } -> BarDestination.Trends
    hierarchy.any { it.hasRoute(YouRoute::class) } -> BarDestination.You
    else -> null
}

private val BarHeight = 60.dp
/** The selected-tab capsule and the ink disc inside "+" share this size. */
private val CapsuleHeight = 44.dp
private val BarBottomGap = 12.dp

/**
 * How much bottom padding a tab screen's scrolling content needs so its last
 * card can scroll clear of the floating bar.
 */
@Composable
fun bottomBarClearance(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        BarHeight + BarBottomGap + Spacing.l

/**
 * A floating frosted-glass pill: the selected tab is an ink capsule with its label, the
 * others are bare icons. The label slides out as the capsule grows, so a tab
 * switch reads as one motion. The "+" sits beside it as its own round button.
 */
@Composable
fun AppBottomBar(
    current: BarDestination?,
    onSelect: (BarDestination) -> Unit,
    onAdd: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val shadowTint = Color.Black.copy(alpha = 0.12f)
    val glass = HazeStyle(
        backgroundColor = scheme.background,
        tint = HazeTint(cardColor().copy(alpha = 0.62f)),
        blurRadius = 24.dp,
        noiseFactor = 0.06f,
    )
    Row(
        modifier
            .fillMaxWidth()
            // Swallow touches in the gaps so they never reach the list
            // scrolling underneath the floating bar.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .navigationBarsPadding()
            .padding(start = Spacing.xl, end = Spacing.xl, bottom = BarBottomGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(BarHeight)
                .shadow(18.dp, RoundedCornerShape(Radius.pill), ambientColor = shadowTint, spotColor = shadowTint)
                .clip(RoundedCornerShape(Radius.pill))
                // Frosted glass: the list scrolling underneath shows through,
                // blurred and tinted with the card colour.
                .hazeEffect(state = hazeState, style = glass)
                // A hairline edge so the bar still reads on the dark canvas.
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(Radius.pill))
                .padding(horizontal = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarDestination.entries.forEach { dest ->
                // The selected tab takes a wider share so its label fits; every
                // share is fully tappable, so there are no dead gaps.
                val share by animateFloatAsState(if (dest == current) 1.7f else 1f, label = "tabShare")
                BarItem(
                    dest = dest,
                    selected = dest == current,
                    onClick = {
                        if (dest != current) haptics.light()
                        onSelect(dest)
                    },
                    modifier = Modifier.weight(share),
                )
            }
        }
        Spacer(Modifier.width(Spacing.m))
        // The same frosted 60dp glass as the bar, holding a 44dp ink disc —
        // exactly the size of the selected-tab capsule — so the two read as a set.
        Box(
            Modifier
                .size(BarHeight)
                .shadow(18.dp, CircleShape, ambientColor = shadowTint, spotColor = shadowTint)
                .clip(CircleShape)
                .hazeEffect(state = hazeState, style = glass)
                .border(1.dp, scheme.outlineVariant, CircleShape)
                .semantics { role = Role.Button; contentDescription = "Log food" }
                .pressable(scaleTo = 0.9f) { haptics.light(); onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(CapsuleHeight).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Plus, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * One tab. It owns a share of the bar — the whole slot is the tap target —
 * and when selected its capsule fills that slot.
 */
@Composable
private fun BarItem(dest: BarDestination, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "tabFill")
    val ink by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "tabInk")
    Box(
        modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = dest.label
            }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The capsule fills its slot, so it sits the same 8dp in from the bar's
        // edge on every side — concentric with the bar, never floating.
        Row(
            Modifier
                .fillMaxWidth()
                .height(CapsuleHeight)
                .clip(RoundedCornerShape(Radius.pill))
                .background(fill)
                .padding(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(dest.icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Text(
                    dest.label,
                    style = AppText.label,
                    color = ink,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        }
    }
}
