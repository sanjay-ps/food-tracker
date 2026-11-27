package com.example.foodtracker.ui.screens.addfood

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The overlay shown while the two-pass AI autofill runs.
 *
 * **This is a rebuild, not a restyle.** What it replaces was the one screen in
 * the app that had been granted an exemption from the house rules, and it used
 * all of it: a 64dp accent-tinted disc with a pulsing, morphing glyph inside it,
 * a two-node connector tracker, a 28dp corner off the radius scale and six raw
 * gaps. The animation was the justification — but a pulse is not information.
 * It renders identically at two seconds and at ninety, which is the one
 * distinction that matters here, and the one the user kept having to discover by
 * waiting.
 *
 * So the decoration is gone and the dialog states facts, in the page vocabulary
 * every other screen uses — a standing head with a rule under it (house rule 1),
 * the reading loudest (rule 2), label left and status right down one rhythm:
 *
 *  - **What it is looking up.** The food, and the brand when there is one. A
 *    modal that does not name its subject is indistinguishable from a hung app.
 *  - **How long it has been waiting**, as the hero figure. This is the number
 *    the user is actually asking for: a `gemini-flash` first token can take
 *    forty seconds, and the difference between "slow" and "stuck" was unreadable
 *    before.
 *  - **Which model is answering** — so a rate-limited lookup is traceable to the
 *    model that was rate-limited, without going to Settings to remember which
 *    one is selected.
 *  - **A way out.** [onCancel] is new. Three attempts against a provider
 *    shedding load is up to three minutes of a modal with no dismiss, and the
 *    fix for that case — pick another model — sits behind the dialog.
 *
 * The one accent is the progress rule, which is a reading, exactly what house
 * rule 3 reserves `primary` for.
 */
@Composable
fun AiProgressDialog(
    step: AiStep,
    verifies: Boolean = true,
    subject: String = "",
    providerLabel: String = "",
    modelLabel: String = "",
    startedAt: Long = 0L,
    onCancel: () -> Unit = {},
) {
    if (step == AiStep.Idle) return
    Dialog(
        onDismissRequest = {},
        // Still not dismissible by accident — a stray tap outside would abandon
        // a request the user is paying for. Cancelling is a deliberate button.
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = Elevation.none,
        ) {
            Column(Modifier.padding(Spacing.xxl)) {
                Section(
                    title = "AI autofill",
                    trailing = {
                        Text(
                            listOf(providerLabel, modelLabel).filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = AppText.bodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    Text(
                        subject.ifBlank { "Nutrition" },
                        style = AppText.titleM,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(Spacing.xl))
                    ElapsedReading(step = step, startedAt = startedAt, verifies = verifies)

                    Spacer(Modifier.height(Spacing.xl))
                    ProgressRule(progressFor(step, verifies))

                    Spacer(Modifier.height(Spacing.l))
                    StepLine("Look up", statusOf(step, AiStep.Fetching))
                    if (verifies) {
                        AppDivider()
                        StepLine("Verify", statusOf(step, AiStep.Verifying))
                    }
                }

                Spacer(Modifier.height(Spacing.l))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    // Absent on the done beat: there is nothing left to cancel,
                    // and a button that appears for 900ms to do nothing is a
                    // button you will hit by accident.
                    if (step != AiStep.Done) {
                        AppTextButton(text = "Cancel", onClick = onCancel)
                    }
                }
            }
        }
    }
}

/**
 * Seconds since the lookup started, as the dialog's one loud figure.
 *
 * Ticks once a second — the reading is "is this stuck", and tenths would be
 * motion for its own sake, which is what this rebuild removed. Frozen on
 * [AiStep.Done] so the final line states what the lookup actually cost.
 */
@Composable
private fun ElapsedReading(step: AiStep, startedAt: Long, verifies: Boolean) {
    var seconds by remember(startedAt) { mutableLongStateOf(elapsedSeconds(startedAt)) }
    LaunchedEffect(startedAt, step) {
        while (step != AiStep.Done) {
            seconds = elapsedSeconds(startedAt)
            delay(1_000)
        }
        seconds = elapsedSeconds(startedAt)
    }
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                seconds.toString(),
                style = AppText.numberXXL,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                " s",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.m),
            )
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ai-status",
        ) { s ->
            Text(
                statusLine(s, verifies),
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A 2dp rule that fills left to right.
 *
 * Hand-rolled rather than `LinearProgressIndicator`, which in current Material 3
 * draws a track gap and a stop indicator at the end of the bar — two pieces of
 * chrome on a control whose whole job here is to be the same hairline every
 * section head sits on, only filled.
 */
@Composable
private fun ProgressRule(fraction: Float) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(450),
        label = "ai-progress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Measured rather than fractioned with fillMaxWidth(fraction), which
        // rounds to whole pixels and leaves the bar visibly short of the end at
        // 1f on some densities.
        Layout(
            content = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.primary),
                )
            },
        ) { measurables, constraints ->
            val width = (constraints.maxWidth * animated).toInt()
            val placeable = measurables.first().measure(
                constraints.copy(minWidth = width, maxWidth = width),
            )
            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
        }
    }
}

/** Label left, status right — the row rhythm from every other screen. */
@Composable
private fun StepLine(label: String, status: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            status,
            style = AppText.label,
            color = if (status == "Waiting") MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun elapsedSeconds(startedAt: Long): Long =
    if (startedAt <= 0L) 0L else ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)

private fun statusOf(step: AiStep, stage: AiStep): String = when {
    step == stage -> "Running"
    step == AiStep.Done -> "Done"
    step.ordinal > stage.ordinal -> "Done"
    else -> "Waiting"
}

private fun statusLine(step: AiStep, verifies: Boolean): String = when (step) {
    AiStep.Fetching -> "Finding a typical serving and every nutrient"
    AiStep.Verifying -> "Re-checking the serving and every value"
    AiStep.Done ->
        if (verifies) "Filled the form with the verified values"
        else "Filled the form. Verify pass skipped for this model"
    AiStep.Idle -> ""
}

private fun progressFor(step: AiStep, verifies: Boolean): Float = when (step) {
    AiStep.Fetching -> if (verifies) 0.35f else 0.65f
    AiStep.Verifying -> 0.75f
    AiStep.Done -> 1f
    AiStep.Idle -> 0f
}
