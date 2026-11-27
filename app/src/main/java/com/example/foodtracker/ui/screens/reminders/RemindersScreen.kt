package com.example.foodtracker.ui.screens.reminders

import com.example.foodtracker.ui.components.atoms.AppSwitch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.reminder.BatteryOptHelper
import com.example.foodtracker.reminder.ReminderConfig
import com.example.foodtracker.reminder.ReminderKind
import com.example.foodtracker.reminder.rememberBatteryOptExempt
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.BatteryOptCard
import com.example.foodtracker.ui.components.TimePickerDialog
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * Six switches and two system-permission blocks, a restyle
 * and nothing more.
 *
 * **What the editorial pass changed.** The structure was already one card of
 * six divided rows rather than six cards, which was the right call; it just
 * stopped one step short. A border drawn around a list that already has rules
 * between its rows is a second boundary doing the first one's job (house rule
 * 1), so the card is gone and the list is what it always was underneath: a
 * standing head, a rule under it, and six rows divided by hairlines — the
 * [Section] every screen in the app is built from.
 *
 * **The emoji are gone too** (rule 4). Each row used to lead with a 36dp disc
 * holding 🌞 / 🍛 / 🍪 / 🌙 / 💧 / ⚖️, on the argument that a glyph makes a list
 * scannable. It does — when the rows are otherwise identical. These rows are
 * not: each one carries a time, which is the thing anyone comes to this screen
 * to find, and the six times are already six different two-digit numbers down
 * the right-hand edge. The disc was buying scanability that the data gives for
 * free, in the friendly register the app refused, and at the cost of 48dp of the
 * row's width — so the row now reads as every settings row in the app does:
 * label on the left, value on the right, its control at the end.
 *
 * Scheduling is out of scope here: `ReminderScheduler`, `ReminderReceiver`,
 * `BatteryOptHelper` and `ReminderStore` are untouched, and every callback here
 * goes to the same [RemindersViewModel] method it did before.
 */
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val vm: RemindersViewModel = appViewModel { RemindersViewModel(it.reminderStore, it.reminderScheduler) }
    val configs by vm.configs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    var editingKind by remember { mutableStateOf<ReminderKind?>(null) }
    val batteryExempt by rememberBatteryOptExempt()
    val anyReminderEnabled = configs.any { it.enabled }

    Scaffold(
        topBar = { AppTopBar(title = "Reminders", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = Spacing.screenContent,
            // 24dp between blocks. Each one is a head with a rule under it, so
            // the gap no longer has to mark where a section ends.
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            if (!notificationsGranted) {
                item("blocked") {
                    NotificationsBlocked(
                        onAllow = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            }

            item("reminders") {
                Section(
                    title = "Daily reminders",
                    trailing = {
                        Text(
                            if (anyReminderEnabled) "${configs.count { it.enabled }} on"
                            else "All off",
                            style = AppText.bodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    configs.forEachIndexed { index, config ->
                        if (index > 0) AppDivider()
                        ReminderRow(
                            config = config,
                            onToggle = { vm.setEnabled(config.kind, it) },
                            onEditTime = { editingKind = config.kind },
                        )
                    }
                }
            }

            if (anyReminderEnabled && !batteryExempt) {
                item("battery") {
                    BatteryOptCard(onOpenSettings = { BatteryOptHelper.openSettings(context) })
                }
            }

            item("doze") {
                Text(
                    "Reminders use Doze-aware alarms — they respect battery saver but may " +
                        "fire a few minutes after the set time while the device is asleep.",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editingKind?.let { kind ->
        val current = configs.firstOrNull { it.kind == kind }
        if (current != null) {
            TimePickerDialog(
                initialHour = current.hour,
                initialMinute = current.minute,
                onDismiss = { editingKind = null },
                onConfirm = { h, m ->
                    vm.setTime(kind, h, m)
                    editingKind = null
                },
            )
        }
    }
}

/**
 * One reminder: what it is, what it will say, when it fires, and whether it is
 * on.
 *
 * Label left, number right, control at the end — the same rhythm as Today's
 * water and weight rows, which is the point of having a rhythm at all. The
 * title is an eyebrow and the time is a [AppText.numberL] figure, so the six
 * rows scan as six times rather than as six sentences; the body line trails
 * the title as its caption, exactly where the caption sits on a stat row.
 *
 * **The whole row opens the picker**, rather than only the time. The time used
 * to be the one tappable thing on the line, drawn in `primary` to say so — an
 * accent spent on an affordance, which rule 3 reserves for data. A 48dp-tall
 * row that responds anywhere is both a bigger target and a convention the user
 * already knows from every settings screen they own; the switch keeps its own
 * hit area, so toggling still never opens the dialog.
 *
 * The time is the only part that goes quiet when the reminder is off: a time
 * that will not fire should not read as loudly as five that will.
 */
@Composable
private fun ReminderRow(
    config: ReminderConfig,
    onToggle: (Boolean) -> Unit,
    onEditTime: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditTime)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                config.kind.title,
                style = AppText.eyebrow,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                config.kind.body,
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            config.timeLabel(),
            style = AppText.numberL,
            color = if (config.enabled) scheme.onSurface else scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.m))
        AppSwitch(
            checked = config.enabled,
            onCheckedChange = {
                haptics.light()
                onToggle(it)
            },
        )
    }
}

/**
 * Hard stop: nothing on this page does anything until the permission is
 * granted, so it says so in the error tone and offers the grant directly.
 *
 * No container at all now — not even the outline it kept last time. It is the
 * same [Section] as the list below it, and the state rides in the head's
 * trailing slot where every section on every screen puts its summary: the one
 * word `BLOCKED`, in the error colour, which is the whole message. An
 * error-coloured word on a black canvas is louder than any box around it, and
 * the page keeps one shape from top to bottom.
 */
@Composable
private fun NotificationsBlocked(onAllow: () -> Unit) {
    Section(
        title = "Notifications",
        trailing = {
            Text(
                "Blocked",
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.error,
            )
        },
    ) {
        Text(
            "Allow notifications so we can remind you at the times you set.",
            style = AppText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))
        AppButton(text = "Allow notifications", onClick = onAllow)
    }
}
