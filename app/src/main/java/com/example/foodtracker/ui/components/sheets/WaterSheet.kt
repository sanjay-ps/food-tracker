package com.example.foodtracker.ui.components.sheets

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.foodtracker.ui.components.atoms.AppModalSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppOutlinedButton
import com.example.foodtracker.ui.components.atoms.Meter
import com.example.foodtracker.ui.components.atoms.SectionHeader
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.MacroWater
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.util.DateUtil
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Logging water.
 *
 * **Why this stopped being a screen.** `WaterScreen` was a full route reached
 * from Today, and two thirds of it was history you never came for: you open
 * water to add 250 ml and get out. A push transition, a back arrow and a top
 * bar are all overhead on a one-tap action, and the back stack entry meant the
 * system back button walked you through hydration on the way out of the app.
 * The entry half is a sheet; the *chart* half moved to Trends' water card,
 * beside the other charts, where a week of totals is actually read.
 *
 * **Why the sheet owns its own `ModalBottomSheet`.** The alternative — exposing
 * the content and making every caller wrap it — spreads the container colour,
 * the 24dp top radius and the dismiss plumbing across every call site, which is
 * exactly how the app ended up with six implementations of one card. Callers
 * own only *whether* the sheet is open; they pass [onDismiss] and nothing else.
 *
 * The totals, goal and entry list all come from [WaterViewModel], unchanged
 * from the screen it replaces — only its package moved.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WaterSheet(onDismiss: () -> Unit) {
    val vm: WaterViewModel = appViewModel { WaterViewModel(it.repository, it.profileStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    // `skipPartiallyExpanded`, as every other sheet in the app does. Without it
    // a sheet taller than half the screen opens at the half-screen anchor and
    // its own overflow hangs off the bottom edge — the scroll inside cannot
    // recover it, because the scroll viewport *is* the content height, so the
    // last control sits under the navigation bar until you drag the sheet up.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberAppHaptics()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    AppModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xxl),
        ) {
            WaterHeadline(totalMl = state.totalMl, goalMl = state.goals.waterMl)

            Spacer(Modifier.height(Spacing.l))
            AppDivider()
            Spacer(Modifier.height(Spacing.l))

            // Four amounts in one row, then the bottle on its own. The four are
            // the ones a glass actually comes in; the litre is a different kind
            // of event (a bottle you refill), so it gets the full-width primary
            // slot rather than being a fifth, narrower sibling of the glasses.
            QuickAddRow(onAdd = {
                haptics.confirm()
                vm.addWater(it)
            })
            Spacer(Modifier.height(Spacing.s))
            AppButton(
                text = "+ 1 L bottle",
                onClick = {
                    haptics.confirm()
                    vm.addWater(BOTTLE_ML)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.l))
            AppDivider()
            Spacer(Modifier.height(Spacing.l))

            SectionHeader("Today")
            Spacer(Modifier.height(Spacing.s))
            if (state.entries.isEmpty()) {
                Text(
                    // Kept verbatim from the screen it replaces — it is the one empty
                    // state in the app that sounds like a person wrote it.
                    "No water logged yet. Stay hydrated 💧",
                    style = AppText.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Newest first: the row you are most likely to have mislogged is
                // the one you just logged, and it should not be at the bottom of
                // a list you have to scroll a sheet to reach.
                state.entries.asReversed().forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ValueText(
                            value = entry.amountMl.toDouble(),
                            unit = "ml",
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Instant.ofEpochMilli(entry.loggedAtMillis)
                                .atZone(DateUtil.zoneId())
                                .toLocalTime()
                                .format(timeFormatter),
                            style = AppText.bodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = {
                            haptics.light()
                            vm.removeWater(entry.id)
                        }) {
                            Icon(
                                Lucide.Trash2,
                                contentDescription = "Remove ${entry.amountMl} ml",
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The four glass-sized amounts — four across when they fit, two across when
 * they do not.
 *
 * A quarter of the gutter width holds "100 ml" comfortably at the normal font
 * scale and not at all at the 2.0 scale Android allows, where the plain Row
 * this replaces cut the labels down to "10", "20", "25", "50" — not a smaller
 * button but a wrong one. Letting [FlowRow] decide on its own was no better: it
 * fitted three by wrapping each label onto two lines, so the chips read "100"
 * over "ml".
 *
 * So the break is measured rather than guessed. No font-scale threshold is
 * hard-coded; the widest label is measured at whatever scale is live and asked
 * whether a quarter of the real width can hold it on one line. That answers
 * reduced scales too — at 0.85 the quarters only get roomier, so nothing moves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddRow(onAdd: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // Keyed on density, which is the object that carries fontScale: re-measure
    // when the system setting changes and not on every recomposition.
    val labelWidth = remember(measurer, density) {
        with(density) {
            QUICK_ADD_ML.maxOf { measurer.measure("$it ml", AppText.label).size.width }.toDp()
        }
    }
    val chrome = ButtonDefaults.ContentPadding.calculateLeftPadding(direction) +
        ButtonDefaults.ContentPadding.calculateRightPadding(direction)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val across = QUICK_ADD_ML.size
        val quarter = (maxWidth - Spacing.s * (across - 1)) / across
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            maxItemsInEachRow = if (quarter >= labelWidth + chrome) across else 2,
        ) {
            QUICK_ADD_ML.forEach { ml ->
                AppOutlinedButton(
                    text = "$ml ml",
                    onClick = { onAdd(ml) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The four glass-sized amounts. */
private val QUICK_ADD_ML = listOf(100, 200, 250, 500)

private const val BOTTLE_ML = 1000

/**
 * The number, what it is out of, and how far there is to go — the three things
 * the sheet exists to answer before you decide which button to press.
 *
 * The remainder is spelled out rather than left as arithmetic: "1 400 to go"
 * picks a button for you, "1 200 of 2 600" makes you do the subtraction.
 */
@Composable
private fun WaterHeadline(totalMl: Int, goalMl: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text("Water", style = AppText.titleL, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.xs))
        ValueText(
            value = totalMl.toDouble(),
            unit = "ml",
            style = AppText.numberL,
        )
        Spacer(Modifier.height(Spacing.xs))
        val remaining = goalMl - totalMl
        Text(
            buildString {
                append("of ${grouped(goalMl.toDouble())} ml")
                if (remaining > 0) append(" · ${grouped(remaining.toDouble())} to go")
                else append(" · goal met")
            },
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))
        Meter(
            fraction = if (goalMl > 0) totalMl.toFloat() / goalMl else 0f,
            color = MacroWater,
            height = 6.dp,
        )
    }
}
