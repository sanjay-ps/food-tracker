package com.example.foodtracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText

/**
 * Pick a reminder time. Reached from Reminders and from onboarding.
 *
 * An [AlertDialog] shaped like `ManualCodeDialog` in `BarcodeScreen`, which is
 * the dialog the rewrite actually finished: `surfaceContainer`, an `AppText`
 * title, and the button atoms. This one was missed and kept drawing a `Card` —
 * the container house rule 1 abolished — with Material typography and raw
 * 20/12dp gaps off the spacing scale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val haptics = rememberAppHaptics()
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Pick a time", style = AppText.titleL) },
        text = {
            // The dial itself is a fixed 256dp and does not move with the font
            // scale, but the time field above it is display type and does, so
            // at the top of the 0.85-2.0 range the picker is taller than a
            // short phone's dialog can show. House rule 7: a fixed height does
            // not shrink content, it crops it. Scrolling the slot costs nothing
            // when it fits and is the difference between reachable and not when
            // it does not — AlertDialog does not scroll this slot for us.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            AppButton(
                text = "Set",
                onClick = {
                    haptics.confirm()
                    onConfirm(pickerState.hour, pickerState.minute)
                },
            )
        },
        dismissButton = { AppTextButton(text = "Cancel", onClick = onDismiss) },
    )
}
