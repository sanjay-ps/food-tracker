package com.example.foodtracker.ui.components.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.example.foodtracker.ui.theme.AppTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * The chrome every full-width sheet in the app shares.
 *
 * It began as `YouSheet`, the chrome behind three of the six App rows, because
 * each was a drill-down from one row of one section: a read-only list, a toggle
 * with two pickers, a count with one destructive action. None of them earns a
 * back stack entry, and a page would have to invent a title bar and a back
 * arrow to get you out of somewhere you did not really go. The plan's nutrition
 * breakdown and the AI request log are the same shape of thing, so it moved
 * here rather than being copied twice under a name that lied about where it
 * belonged.
 *
 * It is not the chrome for the *compact* sheets — `ServingSheet` and the water
 * and weigh-in sheets each own a footer and a keyboard-aware layout that this
 * does not try to generalise.
 *
 * Fixed here so the three cannot drift: `surfaceContainer` fill (sheets are one
 * of the five surfaces the rules let fill), [Radius.sheet] top corners only, title in
 * [AppText.titleL] over a hairline, and the content scrolls with the navigation
 * bar inset applied — a sheet that ends under the gesture bar eats its own last
 * row.
 *
 * The rule under the title is house rule 1 arriving here. The title is a
 * standing head like any other — it just reads at [AppText.titleL] rather than
 * as an eyebrow, because a sheet has exactly one of them and it is the thing
 * that says where you are. A heading separated from what it heads by nothing
 * but a gap was the last of that pattern left in the slice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        dragHandle = { SheetHandle() },
    ) {
        CompositionLocalProvider(
            LocalCardColor provides AppTheme.extras.cardInSheet,
            LocalBehind provides MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter)
                    .navigationBarsPadding(),
            ) {
                SheetTitleRow(title = title, onClose = onDismiss)
                Spacer(Modifier.height(Spacing.m))
                content()
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

/** A short rounded grab bar, used by every sheet in place of Material's. */
@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = Spacing.m, bottom = Spacing.s)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.outline),
    )
}

/** Sheet title on the left, a round close button on the right. */
@Composable
fun SheetTitleRow(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = AppText.titleL,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(icon = Lucide.X, contentDescription = "Close", onClick = onClose)
    }
}

/**
 * `ModalBottomSheet` with the app's handle, fill and corners, and cards inside
 * it switched to [com.example.foodtracker.ui.theme.AppExtras.cardInSheet].
 * Same parameters as the Material one, so call sites swap by name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        shape = shape,
        dragHandle = { SheetHandle() },
    ) {
        CompositionLocalProvider(
            LocalCardColor provides AppTheme.extras.cardInSheet,
            LocalBehind provides MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    }
}
