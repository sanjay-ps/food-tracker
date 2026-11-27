package com.example.foodtracker.ui.components.sheets

import com.example.foodtracker.ui.components.atoms.AppSwitch
import com.example.foodtracker.ui.components.atoms.AppTextField
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.example.foodtracker.ui.components.atoms.AppModalSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.util.DateUtil
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * How a body weight is written, everywhere it is written.
 *
 * One decimal, always — `72.0 kg`, not `72 kg`. This deliberately does **not**
 * go through the atoms' `formatValue`, whose magnitude rule rounds anything
 * over 10 to a whole number. That rule is right for 2 740 mg of sodium and
 * wrong here: the ruler steps in 0.1 kg, a fortnight of progress is often a
 * single tenth, and a reading that silently drops it would make the number
 * look stuck.
 */
fun kgText(kg: Double): String = "%.1f".format(kg)

/** A change, with its sign kept: `+0.4 kg`, `−1.2 kg`. */
fun signedKgText(deltaKg: Double): String {
    // U+2212 MINUS SIGN, not a hyphen: it is the same width as the plus, so a
    // column of changes stays aligned under tabular figures.
    val sign = if (deltaKg < 0) "−" else "+"
    return "$sign${kgText(abs(deltaKg))} kg"
}

/**
 * Logging a weigh-in.
 *
 * **Why a sheet and not a page.** Decision 6 split `WeightScreen` down the
 * middle: everything you *read* (the trend, the history) went to Trends beside
 * the other charts, and everything you *do* — one number, once a morning —
 * became this. A full route for a single value meant a push, a back arrow and a
 * back-stack entry for an interaction that is over in two seconds.
 *
 * **Why a ruler.** The original was a decimal `OutlinedTextField`; the rewrite
 * downgraded that to a stepper plus a slider, and then adopted the ruler
 * from reference shot 3 instead. The argument for all three is the same and the
 * ruler wins it outright: you weigh within about a kilo of last time, so the
 * useful range is tiny and *local*. A keyboard makes you type an absolute
 * number and offers 400 kg as readily as 72.4. A slider over 20–400 kg gives
 * each tenth of a kilo about a twentieth of a pixel. A ruler shows only the
 * neighbourhood you are in, at 0.1 kg per tick, and the number you came to
 * enter is nearly always within one thumb-swipe of the one already under the
 * cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeighInSheet(onDismiss: () -> Unit) {
    val vm: WeightViewModel = appViewModel { WeightViewModel(it.repository, it.profileStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    // `skipPartiallyExpanded`, as every other sheet in the app does. Without it
    // a sheet taller than half the screen opens at the half-screen anchor and
    // its own overflow hangs off the bottom edge — the scroll inside cannot
    // recover it, because the scroll viewport *is* the content height, so the
    // last control sits under the navigation bar until you drag the sheet up.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    AppModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Log weigh-in",
                    style = AppText.titleL,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateUtil.formatHeader(DateUtil.todayEpochDay()),
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            // The value sits above its own scale, not beside a control: at
            // numberXL it is the only thing on the sheet you read while your
            // thumb is moving, and the ruler underneath is what the thumb is on.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    kgText(state.draftKg),
                    style = AppText.numberXL,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    " kg",
                    style = AppText.titleM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(Spacing.s))
            WeightRuler(valueKg = state.draftKg, onValueChange = vm::setDraftKg)

            // Context, not decoration: the only judgement to make about today's
            // number is how it compares with the last one, and that comparison
            // is otherwise two taps away in Trends.
            val last = state.latest
            if (last != null && last.dateEpochDay != DateUtil.todayEpochDay()) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "${signedKgText(state.draftKg - last.weightKg)} since " +
                        DateUtil.formatHeader(last.dateEpochDay).lowercase(),
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            AppDivider()
            Spacer(Modifier.height(Spacing.l))

            AppTextField(
                value = state.note,
                onValueChange = vm::setNote,
                label = { Text("Note (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.l))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Update profile weight",
                        style = AppText.body,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Keeps calorie & nutrient targets in sync.",
                        style = AppText.bodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppSwitch(checked = state.syncToProfile, onCheckedChange = vm::setSyncToProfile)
            }

            Spacer(Modifier.height(Spacing.l))

            AppButton(
                text = "Save weight",
                enabled = state.canSave,
                onClick = {
                    haptics.confirm()
                    vm.saveEntry {
                        // Animate the sheet out before the caller drops it, so
                        // saving looks like a dismissal rather than a disappearance.
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One 0.1 kg tick. 10dp apart puts a whole kilogram at 100dp — a short swipe. */
private val TICK_SPACING = 10.dp
private val RULER_HEIGHT = 72.dp

/** How far the scale fades into the sheet at each edge. */
private val RULER_FADE = 40.dp

/**
 * The ruler itself: ticks scrolling under a fixed cursor.
 *
 * **The value is held locally while dragging, and mirrored from [valueKg] when
 * not.** A drag can deliver several pointer deltas inside one frame, and each
 * one has to be applied to the result of the previous one. Reading [valueKg]
 * for each would read the same pre-frame value every time and quietly drop all
 * but the last step, which feels like a sticky ruler. So the drag owns the
 * number for as long as it lasts; the hoisted state catches up each frame, and
 * [LaunchedEffect] re-syncs the moment the finger lifts (or if anything else
 * changes the weight while the sheet is open).
 *
 * Sub-tick movement is kept in [residualPx] rather than discarded, so a slow
 * drag accumulates into ticks instead of going nowhere.
 *
 * The haptic fires once per tick crossed — the same bucket-crossing feel as
 * `HapticSlider`, and the only feedback that makes a continuous gesture read as
 * a discrete choice.
 */
@Composable
private fun WeightRuler(
    valueKg: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val stepPx = with(LocalDensity.current) { TICK_SPACING.toPx() }

    val local = remember { mutableDoubleStateOf(valueKg) }
    val residualPx = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(valueKg, dragging) {
        if (!dragging) local.doubleValue = valueKg
    }

    val dragState = rememberDraggableState { deltaPx ->
        residualPx.floatValue += deltaPx
        val steps = (residualPx.floatValue / stepPx).toLong()
        if (steps != 0L) {
            residualPx.floatValue -= steps * stepPx
            // Dragging right pulls the scale right, which brings *smaller*
            // numbers under the cursor — a physical dial, not a progress bar.
            val next = (local.doubleValue - steps * WEIGHT_STEP_KG)
                .coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
            if (next != local.doubleValue) {
                local.doubleValue = next
                haptics.light()
                onValueChange(next)
            }
        }
    }

    val minorColor = MaterialTheme.colorScheme.outline
    val majorColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cursorColor = MaterialTheme.colorScheme.primary
    val fadeColor = MaterialTheme.colorScheme.surfaceContainer
    val measurer = rememberTextMeasurer()
    val labelStyle = AppText.bodyS.copy(color = majorColor)
    val shown = local.doubleValue

    Box(
        modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .semantics { contentDescription = "Weight ${kgText(shown)} kilograms. Drag to change." }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    dragging = true
                    residualPx.floatValue = 0f
                },
                onDragStopped = { dragging = false },
            ),
    ) {
        Canvas(Modifier.fillMaxWidth().height(RULER_HEIGHT)) {
            val centerX = size.width / 2f
            val stepsFromZero = shown / WEIGHT_STEP_KG
            val centerTick = stepsFromZero.roundToLong()
            // Two ticks of slack past each edge so nothing pops in mid-swipe.
            val halfSpan = (centerX / stepPx).toInt() + 2

            val top = 8.dp.toPx()
            val minorLen = 12.dp.toPx()
            val halfLen = 18.dp.toPx()
            val majorLen = 26.dp.toPx()
            val labelY = top + majorLen + 6.dp.toPx()

            for (i in -halfSpan..halfSpan) {
                val tick = centerTick + i
                val kg = tick * WEIGHT_STEP_KG
                if (kg < MIN_WEIGHT_KG || kg > MAX_WEIGHT_KG) continue
                val x = centerX + ((tick - stepsFromZero) * stepPx).toFloat()
                val isMajor = tick % 10L == 0L
                val isHalf = tick % 5L == 0L
                drawLine(
                    color = if (isMajor) majorColor else minorColor,
                    start = Offset(x, top),
                    end = Offset(x, top + if (isMajor) majorLen else if (isHalf) halfLen else minorLen),
                    strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (isMajor) {
                    // Measured in the draw pass because there are only ever
                    // three or four whole kilograms on screen — the same
                    // trade-off the charts already make for their axis labels.
                    val layout = measurer.measure(
                        AnnotatedString((kg).roundToLong().toString()),
                        style = labelStyle,
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x - layout.size.width / 2f, labelY),
                    )
                }
            }

            // The cursor is drawn last and in `primary` — it is the one thing on
            // the ruler that is not data, and the only fill the rules would allow here.
            drawLine(
                color = cursorColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, top + majorLen + 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Ticks dissolve into the sheet instead of being chopped off at the
            // edge, which is what makes a flat scale read as a curved dial.
            val fade = RULER_FADE.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(fadeColor, Color.Transparent),
                    startX = 0f,
                    endX = fade,
                ),
                size = Size(fade, size.height),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, fadeColor),
                    startX = size.width - fade,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
            )
        }
    }
}
