package com.example.foodtracker.ui.screens.you

import com.composables.icons.lucide.SunMoon
import com.example.foodtracker.data.ThemeMode
import com.example.foodtracker.ui.LocalAppContainer
import com.example.foodtracker.ui.screens.you.sheets.AppearanceSheet
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.foodtracker.ui.components.atoms.staggerIn
import com.example.foodtracker.ui.navigation.bottomBarClearance
import com.example.foodtracker.ui.theme.Pastel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.CookingPot
import com.composables.icons.lucide.Vibrate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.BuildConfig
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.screens.you.sheets.AiAutofillSheet
import com.example.foodtracker.ui.screens.you.sheets.AllTargetsSheet
import com.example.foodtracker.ui.screens.you.sheets.CustomFoodsSheet
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * You — the third tab: a gradient profile card, the goal, the four headline
 * targets, and the app settings as icon rows that open a sheet (short) or a
 * page (long), each showing its current value on the right.
 */
@Composable
fun YouScreen(
    onOpenPlan: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenDataBackup: () -> Unit,
) {
    val vm: YouViewModel = appViewModel {
        YouViewModel(it.profileStore, it.reminderStore, it.backupManager, it.repository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    var showAllTargets by remember { mutableStateOf(false) }
    var showAiAutofill by remember { mutableStateOf(false) }
    var showCustomFoods by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    val container = LocalAppContainer.current
    val themeMode by container.profileStore.themeMode.collectAsStateWithLifecycle(ThemeMode.Light)
    val scope = rememberCoroutineScope()

    // Counted here rather than in the view model's init so the filesystem is
    // only read once the page is on screen — and so the count is right again
    // when the user comes back from Data & backup having made or deleted one.
    LaunchedEffect(Unit) { vm.refreshBackupCount() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("You")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = Spacing.s,
                bottom = bottomBarClearance(),
            ),
            // 24dp between sections — house rule 1. The hairline under each
            // head is what separates them; the gap only has to keep them from
            // touching.
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item("profile") {
                ProfileHeader(profile = state.profile, onEdit = onOpenEditProfile, modifier = Modifier.staggerIn(0))
            }

            // Omitted rather than rendered empty: an absent item costs no gap,
            // whereas an item that draws nothing would leave 24dp of hole.
            state.goalProgress?.let { progress ->
                item("goal") {
                    GoalSection(
                        modifier = Modifier.staggerIn(1),
                        profile = state.profile,
                        progress = progress,
                        fraction = state.goalFraction,
                    )
                }
            }

            item("targets") {
                Section(
                    title = "Daily targets",
                    modifier = Modifier.staggerIn(2),
                    // Every section states its total in the same place: four
                    // are shown, and this says how many there are in all.
                    trailing = {
                        Text(
                            "$TARGET_COUNT in all",
                            style = AppText.bodyS,
                            color = scheme.onSurfaceVariant,
                        )
                    },
                ) {
                    // Four, not thirty. These are the numbers a day is actually
                    // steered by; the other twenty-six are a tap away and were
                    // never read at a glance. Being only four is what lets them
                    // read at numberL instead of as a dense list.
                    HeadlineTarget("Calories", state.goals.calories, "kcal")
                    AppDivider()
                    HeadlineTarget("Protein", state.goals.protein, "g")
                    AppDivider()
                    HeadlineTarget("Carbs", state.goals.carbs, "g")
                    AppDivider()
                    HeadlineTarget("Water", state.goals.waterMl.toDouble(), "ml")
                    SeeAllTargetsRow(onClick = { showAllTargets = true })
                }
            }

            item("app") {
                Section("App", Modifier.staggerIn(3)) {
                    // A rule between every row, none around the set and none
                    // under the last: the head's own hairline opens the list
                    // and the next section's head closes it. A border drawn
                    // round the rows would be a second boundary doing the first
                    // one's job (house rule 1).
                    SettingRow(
                        label = "Plan",
                        icon = Lucide.ListChecks,
                        pastel = Pastel.Mint,
                        value = state.planSummary,
                        onClick = onOpenPlan,
                    )
                    AppDivider()
                    SettingRow(
                        label = "Reminders",
                        icon = Lucide.Bell,
                        pastel = Pastel.Peach,
                        value = state.reminderSummary,
                        onClick = onOpenReminders,
                    )
                    AppDivider()
                    SettingRow(
                        label = "AI autofill",
                        icon = Lucide.Sparkles,
                        pastel = Pastel.Lavender,
                        value = state.aiSummary,
                        onClick = { showAiAutofill = true },
                    )
                    AppDivider()
                    SettingRow(
                        label = "Data & backup",
                        icon = Lucide.Database,
                        pastel = Pastel.Sky,
                        value = state.backupSummary,
                        onClick = onOpenDataBackup,
                    )
                    AppDivider()
                    SettingRow(
                        label = "Privacy",
                        icon = Lucide.Shield,
                        pastel = Pastel.Teal,
                        onClick = onOpenPrivacy,
                    )
                    AppDivider()
                    SettingRow(
                        label = "Custom foods",
                        icon = Lucide.CookingPot,
                        pastel = Pastel.Rose,
                        value = state.customFoodCount.toString(),
                        onClick = { showCustomFoods = true },
                    )
                    AppDivider()
                    SettingRow(
                        label = "Appearance",
                        icon = Lucide.SunMoon,
                        pastel = Pastel.Lavender,
                        value = themeMode.displayName,
                        onClick = { showAppearance = true },
                    )
                    AppDivider()
                    SettingToggleRow(
                        label = "Haptic feedback",
                        icon = Lucide.Vibrate,
                        pastel = Pastel.Sand,
                        checked = state.hapticsEnabled,
                        onChange = vm::setHapticsEnabled,
                    )
                }
            }

            item("version") {
                // Left-aligned, not centred: a centred line under a page of
                // flush-left rules is the only thing on the page that does not
                // share its left edge, and it reads as a stray.
                Text(
                    "Foodprint ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · offline-first",
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                )
            }
        }
    }

    if (showAllTargets) {
        AllTargetsSheet(goals = state.goals, onDismiss = { showAllTargets = false })
    }

    if (showAiAutofill) {
        AiAutofillSheet(
            enabled = state.aiEnabled,
            provider = state.aiProvider,
            model = state.aiModel,
            apiKey = state.aiApiKey,
            onEnabledChange = vm::setAiEnabled,
            onProviderChange = vm::setAiProvider,
            onModelChange = vm::setAiModel,
            onApiKeyChange = vm::setAiApiKey,
            onDismiss = { showAiAutofill = false },
        )
    }

    if (showAppearance) {
        AppearanceSheet(
            mode = themeMode,
            onSelect = { scope.launch { container.profileStore.setThemeMode(it) } },
            onDismiss = { showAppearance = false },
        )
    }

    if (showCustomFoods) {
        CustomFoodsSheet(
            count = state.customFoodCount,
            message = state.message,
            onDeleteAll = vm::deleteAllCustomFoods,
            onDismiss = {
                showCustomFoods = false
                vm.consumeMessage()
            },
        )
    }
}

/** The four steering numbers, in the loud register — see [TargetEmphasis]. */
@Composable
private fun HeadlineTarget(label: String, value: Double, unit: String) {
    TargetRow(label = label, value = value, unit = unit, emphasis = TargetEmphasis.Headline)
}
