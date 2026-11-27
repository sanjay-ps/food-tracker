package com.example.foodtracker.ui.screens.you.sheets

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.foodtracker.ui.components.atoms.AppSheet
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.screens.you.StatBlock
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Spacing

/**
 * Custom foods: a count and one destructive action.
 *
 * The count is the point. Deleting every food you added by hand is irreversible
 * in one of its two forms, and the old Settings screen offered the button with
 * no indication of how much it would take — a user with three custom foods and
 * a user with ninety got the same red button and the same dialog.
 *
 * So the count is now the loudest thing in the sheet. It was `ValueText` at
 * `numberM`, which made the one number this sheet exists to show the same size
 * as the sentence under it; as a [StatBlock] it is an eyebrow over a `numberL`
 * figure — house rule 2, and the same unit the profile stats are built from.
 *
 * [message] is shown in place of a snackbar because You has no `Scaffold` of
 * its own (it is a bottom-bar destination; the host owns the bars), and a
 * confirmation that appears where the action was is better than one that
 * appears at the bottom of a screen the user may already have left.
 */
@Composable
fun CustomFoodsSheet(
    count: Int,
    message: String?,
    onDeleteAll: (cascadeLogs: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var confirming by remember { mutableStateOf(false) }

    AppSheet(title = "Custom foods", onDismiss = onDismiss) {
        StatBlock(
            label = "Saved",
            value = count.toDouble(),
            unit = if (count == 1) "food" else "foods",
        )
        Spacer(Modifier.height(Spacing.m))
        Text(
            "Foods you added by hand or saved from a barcode scan. The 166 seeded " +
                "foods are not counted here and are never touched.",
            style = AppText.bodyS,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        OutlinedButton(
            onClick = {
                haptics.light()
                confirming = true
            },
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
            border = BorderStroke(Elevation.hairline, scheme.error),
        ) {
            Icon(Lucide.Trash2, contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
            Text("Delete all custom foods", style = AppText.label)
        }
        if (message != null) {
            Spacer(Modifier.height(Spacing.m))
            Text(message, style = AppText.bodyS, color = scheme.secondary)
        }
    }

    if (confirming) {
        DeleteAllCustomFoodsDialog(
            onKeepHistory = {
                onDeleteAll(false)
                confirming = false
            },
            onCascade = {
                onDeleteAll(true)
                confirming = false
            },
            onDismiss = { confirming = false },
        )
    }
}

/**
 * Kept verbatim from the Settings screen it came from. The note that the
 * destructive confirms are "correctly worded and correctly scary" applies here
 * too: the two options are genuinely different outcomes, and the dialog is the
 * only place that explains which one changes historical daily totals.
 */
@Composable
private fun DeleteAllCustomFoodsDialog(
    onKeepHistory: () -> Unit,
    onCascade: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Delete all custom foods?", style = AppText.titleL) },
        text = {
            Column {
                Text(
                    "This removes every food you added by hand or saved from a barcode " +
                        "scan. Seeded foods stay untouched. Choose how to handle log history:",
                    style = AppText.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.m))
                Text(
                    "• Keep history — past diary entries still show the food name and " +
                        "nutrients, but it's gone from the picker.",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "• Delete with history — wipes every past log entry that used a custom " +
                        "food. Daily totals will change.",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { AppTextButton(text = "Keep history", onClick = onKeepHistory) },
        dismissButton = {
            Row {
                AppTextButton(
                    text = "Delete with history",
                    onClick = onCascade,
                    destructive = true,
                )
                AppTextButton(text = "Cancel", onClick = onDismiss)
            }
        },
    )
}
