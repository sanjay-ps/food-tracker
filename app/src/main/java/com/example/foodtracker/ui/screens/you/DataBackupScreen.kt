package com.example.foodtracker.ui.screens.you

import com.example.foodtracker.ui.components.OptionList
import com.example.foodtracker.ui.theme.Pastel
import com.example.foodtracker.ui.components.atoms.PastelIcon
import com.example.foodtracker.ui.components.atoms.AppButton
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Cloud
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import android.app.ActivityManager
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.backup.AnalysisRange
import com.example.foodtracker.data.backup.LocalBackup
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppOutlinedButton
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.ConfirmDialog
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data & backup, rebuilt against `docs/ui-house-rules.md`.
 *
 * Merges three sections of the old Settings wall onto a page of their own:
 * backup folder / cadence / snapshots, export & import, and the compact log
 * export for AI analysis. They belong together because they are all the same
 * question — *where does my data go and how do I get it back* — and they belong
 * off the You page because every one of them opens a file picker or replaces
 * the database, which is not something to scroll past on the way to the haptics
 * switch.
 *
 * **Nothing under this screen changed.** The rewrite put `BackupManager`,
 * `BackupPayload`, `BackupWorker` and `BackupScheduler` out of scope, and
 * [DataBackupViewModel] is the old `SettingsViewModel`'s backup half moved
 * verbatim. The restore and import confirmations are kept word for word: they
 * calls them "correctly worded and correctly scary", and they guard the two
 * actions in the app that can destroy a diary. This pass is presentation only
 * and touched none of it.
 *
 * **The three cards became three [Section]s.** House rule 1: a standing head,
 * a hairline under it, then the content, 24dp from the next head. It matters
 * most on this page, because the snapshot list used to be a list of ruled rows
 * *inside* an outlined card — a border drawn around rows that already had
 * rules, which is the exact second boundary the rule exists to delete.
 *
 * One deviation from the original sketch, which draws automatic backup as a single
 * `[●]` switch: the stored value has three states (off / daily / weekly) and a
 * switch cannot say "weekly". The segmented control stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBackupScreen(onBack: () -> Unit) {
    val vm: DataBackupViewModel = appViewModel {
        DataBackupViewModel(it.profileStore, it.backupManager, it.backupScheduler)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.consumeMessage()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importFrom(it) } }

    var analysisRange by remember { mutableStateOf(AnalysisRange.Week) }
    val analysisExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.exportAnalysisLogs(it, analysisRange) } }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { vm.setBackupFolder(it) } }

    var pendingRestore by remember { mutableStateOf<LocalBackup?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear your diary?",
            body = "Every meal, water and weight entry will be deleted. Your profile, targets, " +
                "custom foods and plan stay. This can't be undone — export first to keep a copy.",
            confirmLabel = "Clear diary",
            onConfirm = {
                confirmClear = false
                vm.clearDiary()
            },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset the app?",
            body = "Everything on this phone is erased — diary, profile, custom foods, " +
                "reminders and settings — and the app closes. Open it again to start fresh. " +
                "Backups you exported to Drive or a folder are not touched.",
            confirmLabel = "Erase everything",
            onConfirm = {
                confirmReset = false
                // Android's own "Clear storage": wipes the database, settings and
                // files, cancels alarms and jobs, and stops the app.
                context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
            },
            onDismiss = { confirmReset = false },
        )
    }

    pendingRestore?.let { backup ->
        ConfirmDialog(
            title = "Restore this backup?",
            body = "All current data will be replaced with the contents of " +
                "${backup.displayName}. This can't be undone.",
            confirmLabel = "Restore",
            onConfirm = {
                vm.restoreBackup(backup)
                pendingRestore = null
            },
            onDismiss = { pendingRestore = null },
        )
    }
    if (confirmImport) {
        ConfirmDialog(
            title = "Import a snapshot?",
            body = "Choose a JSON file you previously exported. All current data " +
                "will be replaced — there is no undo.",
            confirmLabel = "Pick file",
            onConfirm = {
                confirmImport = false
                importLauncher.launch(arrayOf("application/json"))
            },
            onDismiss = { confirmImport = false },
        )
    }

    Scaffold(
        topBar = { AppTopBar(title = "Data & backup", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.l,
                bottom = Spacing.xxxl,
            ),
            // 24dp — the hairline under each head does the separating now.
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item("drive") {
                Section("Google Drive") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PastelIcon(Lucide.Cloud, Pastel.Sky)
                        Spacer(Modifier.width(Spacing.m))
                        Text(
                            "Save a full copy — foods, diary, water, weight and profile — to " +
                                "Google Drive, or bring one back. In the picker, choose Drive " +
                                "(or any folder).",
                            style = AppText.bodyS,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.l))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        AppButton(
                            text = "Export",
                            onClick = {
                                haptics.light()
                                exportLauncher.launch(defaultExportFilename())
                            },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        )
                        AppOutlinedButton(
                            text = "Import",
                            onClick = {
                                haptics.light()
                                confirmImport = true
                            },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Import replaces everything here with the file's contents. The file is " +
                            "plain JSON you can open and read; nothing is sent anywhere but where you save it.",
                        style = AppText.bodyS,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            item("auto") {
                Section(
                    title = "Automatic backup",
                    // The section's total, in the slot every section states
                    // one in: how many snapshots are actually on disk.
                    trailing = {
                        val count = state.backups.size
                        Text(
                            when (count) {
                                0 -> "No snapshots"
                                1 -> "1 snapshot"
                                else -> "$count snapshots"
                            },
                            style = AppText.bodyS,
                            color = scheme.onSurfaceVariant,
                        )
                    },
                ) {
                    ExclusiveButtonGroup(
                        options = BackupCadence.entries,
                        selected = state.cadence,
                        onSelect = vm::setBackupCadence,
                        modifier = Modifier.fillMaxWidth(),
                        label = { it.label },
                    )

                    Spacer(Modifier.height(Spacing.xl))
                    FolderRow(
                        folderLabel = state.folderLabel,
                        onPick = {
                            haptics.light()
                            folderLauncher.launch(null)
                        },
                        onReset = vm::clearBackupFolder,
                    )

                    Spacer(Modifier.height(Spacing.m))
                    val latest = state.latest
                    Text(
                        if (latest == null) {
                            "No snapshot written yet."
                        } else {
                            "Last run ${timestamp(latest.createdAtMillis)} · " +
                                humanReadableSize(latest.sizeBytes)
                        },
                        style = AppText.bodyS,
                        color = scheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(Spacing.l))
                    AppOutlinedButton(
                        text = if (state.folderLabel != null) "Save backup now"
                        else "Save backup to Downloads",
                        onClick = {
                            haptics.light()
                            vm.saveBackupNow()
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.backups.isNotEmpty()) {
                        // A ruled list and nothing else: the count is already
                        // in the head above, so the rows need no sub-heading,
                        // no card and no closing rule.
                        Spacer(Modifier.height(Spacing.l))
                        state.backups.forEachIndexed { index, backup ->
                            if (index > 0) AppDivider()
                            BackupRow(
                                backup = backup,
                                enabled = !state.busy,
                                onRestore = { pendingRestore = backup },
                                onDelete = { vm.deleteBackup(backup) },
                            )
                        }
                    }
                }
            }

            item("analysis") {
                Section("Export logs for AI") {
                    Text(
                        "A compact JSON of just your food log — foods are de-duplicated and " +
                            "empty nutrients dropped to keep it small. Hand it to an AI to " +
                            "review your eating habits and suggest what to improve.",
                        style = AppText.bodyS,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.l))
                    OptionList(
                        options = AnalysisRange.entries,
                        selected = analysisRange,
                        onSelect = { analysisRange = it },
                        caption = { "${it.days} days of meals" },
                        label = { it.label },
                    )
                    Spacer(Modifier.height(Spacing.l))
                    AppOutlinedButton(
                        text = "Export logs (${analysisRange.label.lowercase()})",
                        onClick = {
                            haptics.light()
                            analysisExportLauncher.launch(analysisExportFilename(analysisRange))
                        },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item("delete") {
                Section("Delete data") {
                    DangerRow(
                        icon = Lucide.Eraser,
                        title = "Clear diary",
                        body = "Delete all meals, water and weight. Keeps your profile and foods.",
                        enabled = !state.busy,
                        onClick = { confirmClear = true },
                    )
                    AppDivider()
                    DangerRow(
                        icon = Lucide.RotateCcw,
                        title = "Reset app",
                        body = "Erase everything on this phone and start over.",
                        enabled = !state.busy,
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }
}

/**
 * Where snapshots land, and the two ways to change that.
 *
 * Eyebrow over the path, not `bodyS` over it: the path is the value of this
 * row and house rule 2 wants the label to be the quiet half of the pair. It is
 * the same label-over-value unit the profile stats use, at reading size rather
 * than number size because a folder name is prose.
 */
@Composable
private fun FolderRow(
    folderLabel: String?,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Folder", style = AppText.eyebrow, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                folderLabel ?: "Downloads/FoodTracker",
                style = AppText.body,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppTextButton(if (folderLabel == null) "Choose" else "Change", onPick)
        if (folderLabel != null) AppTextButton("Reset", onReset)
    }
}

/**
 * One snapshot. Restore is a text button and delete an icon, deliberately
 * asymmetric: restore is the reason you are looking at the list, and delete is
 * housekeeping that should not be as easy to hit by accident.
 */
@Composable
private fun BackupRow(
    backup: LocalBackup,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                backup.displayName,
                style = AppText.body,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${timestamp(backup.createdAtMillis)} · ${humanReadableSize(backup.sizeBytes)}",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
        }
        AppTextButton("Restore", onRestore, enabled = enabled)
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                Lucide.Trash2,
                contentDescription = "Delete backup",
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The guard on the two irreversible actions on this screen — restoring over
 * everything, and importing over everything.
 *
 * Both confirmations are destructive, so the confirm is an error-coloured text
 * button: the rewrite left this dialog entirely bare, which meant the button
 * that replaces all your data and the button that cancels were the same
 * widget in the same colour at the same weight.
 */
@Composable
private fun timestamp(millis: Long): String = DateFormat
    .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    .format(Date(millis))

private fun defaultExportFilename(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return "foodtracker-$stamp.json"
}

private fun analysisExportFilename(range: AnalysisRange): String {
    val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    return "foodtracker-log-${range.fileTag}-$stamp.json"
}

private fun humanReadableSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun DangerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PastelIcon(icon, Pastel.Rose, size = 36.dp)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppText.titleM, color = scheme.error)
            Text(body, style = AppText.bodyS, color = scheme.onSurfaceVariant)
        }
    }
}
