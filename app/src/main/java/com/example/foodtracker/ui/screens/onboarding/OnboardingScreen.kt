package com.example.foodtracker.ui.screens.onboarding

import com.example.foodtracker.ui.components.detail
import com.example.foodtracker.ui.components.title
import com.example.foodtracker.ui.components.OptionGrid
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import com.example.foodtracker.ui.components.OptionList
import com.example.foodtracker.ui.theme.Radius
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import com.example.foodtracker.ui.components.atoms.AppSwitch
import com.example.foodtracker.ui.components.atoms.AppTextField
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Folder
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.ActivityLevel
import com.example.foodtracker.data.AiProvider
import com.example.foodtracker.data.Goal
import com.example.foodtracker.data.Sex
import com.example.foodtracker.data.WeightChangeRate
import com.example.foodtracker.domain.Bmi
import com.example.foodtracker.reminder.BatteryOptHelper
import com.example.foodtracker.reminder.ReminderConfig
import com.example.foodtracker.reminder.ReminderKind
import com.example.foodtracker.reminder.rememberBatteryOptExempt
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.BatteryOptCard
import com.example.foodtracker.ui.components.BmiBar
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.HapticSlider
import com.example.foodtracker.ui.components.NumberStepper
import com.example.foodtracker.ui.components.TimePickerDialog
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.components.atoms.ValueText
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.screens.you.BackupCadence
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * First run, five pages cut to three.
 *
 * The three that remain are the two that are *required* (there is no usable
 * calorie target without age, sex, height, weight, activity and goal) and one
 * that is entirely optional. Backups, reminders and AI used to be a page each:
 * three full swipes, every one of them skippable, every one of them also in
 * Settings. They are now three collapsed rows on one page, which removes two
 * swipes from first run without removing a single control — and makes the fact
 * that they are optional visible at a glance rather than stated once per page.
 *
 * Two things the rewrite pinned down:
 *  - **No hero button.** `ButtonHeightHero` (56dp) is gone; the CTA here is the
 *    same 48dp [AppButton] as everywhere else. A button that is bigger only on
 *    the first run teaches the wrong size for the one pressed a thousand times
 *    afterwards.
 *  - **The privacy tagline is kept word for word.** It stays on page one,
 *    where it answers the question the page provokes — someone being asked for
 *    their weight before they have seen the app deserves the answer *then*, not
 *    two swipes later.
 *
 * **Why this screen got the heaviest hand in the editorial pass.** It is the
 * first thing anyone sees, and until now it was a stack of outlined cards: the
 * privacy tagline in one, the computed targets in another, three collapsed
 * set-ups in three more. Direction B's whole character is an oversized figure
 * against small uppercase labels with rules doing the dividing, and none of
 * that survives being boxed. So: every block is a [Section] — a standing head,
 * a hairline under it, content open to the gutter; the one number the first run
 * exists to produce (the daily calorie target) is rendered at 64sp instead of
 * as the first of six equal rows; and the page indicator is the words `STEP 2
 * OF 3` rather than three dots, which says the same thing in type the rest of
 * the screen already uses.
 */
/** Two intro pages, then the three setup steps. */
private const val IntroCount = 2
private const val SetupSteps = 3
private const val PageCount = IntroCount + SetupSteps

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val vm: OnboardingViewModel = appViewModel {
        OnboardingViewModel(
            it.profileStore,
            it.backupManager,
            it.backupScheduler,
            it.reminderStore,
            it.reminderScheduler,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val pagerState = rememberPagerState(pageCount = { PageCount })
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { vm.setBackupFolder(it, decodeFolderLabel(it.toString())) }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ProgressHeader(
                page = pagerState.currentPage,
                onSkip = if (pagerState.currentPage < IntroCount) {
                    { scope.launch { pagerState.animateScrollToPage(IntroCount) } }
                } else null,
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> PrivacyPage()
                    2 -> AboutYouPage(state = state, vm = vm)
                    3 -> GoalPage(state = state, vm = vm)
                    4 -> OptionalPage(
                        state = state,
                        vm = vm,
                        onPickFolder = { folderLauncher.launch(null) },
                    )
                }
            }

            BottomNav(
                pageIndex = pagerState.currentPage,
                pageCount = PageCount,
                // The name is required: "About you" waits for one.
                continueEnabled = pagerState.currentPage != IntroCount || state.profile.name.isNotBlank(),
                onBack = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                },
                onContinue = {
                    if (pagerState.currentPage < PageCount - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        haptics.confirm()
                        vm.finish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.l),
            )
        }
    }
}

// ───────────────────────── Page 1: About you ─────────────────────

/**
 * Everything the calorie maths needs about the body, plus activity.
 *
 * Activity level moved here from the old goal page: it describes the person,
 * not the target, and moving it balances a three-page flow that would otherwise
 * be short-long-short.
 */
@Composable
private fun AboutYouPage(state: OnboardingUiState, vm: OnboardingViewModel) {
    PageScaffold(
        step = 1,
        title = "About you",
        subtitle = "A few quick details so we can calculate your targets accurately.",
    ) {
        Section("Your name") {
            AppTextField(
                value = state.profile.name,
                onValueChange = vm::setName,
                placeholder = { Text("What should we call you?", style = AppText.body) },
                supportingText = { Text("Used only to greet you. It stays on this phone.", style = AppText.bodyS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("Sex") {
            ExclusiveButtonGroup(
                options = Sex.entries,
                selected = state.profile.sex,
                onSelect = vm::setSex,
                modifier = Modifier.fillMaxWidth(),
                label = { it.displayName },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("Body") {
            StepperSliderRow(
                label = "Age",
                displayValue = "${state.profile.ageYears} yrs",
                sliderValue = state.profile.ageYears.toFloat(),
                sliderRange = 10f..90f,
                granularity = 1f,
                onSliderChange = { vm.setAge(it.toInt()) },
                onDecrement = { vm.setAge(state.profile.ageYears - 1) },
                onIncrement = { vm.setAge(state.profile.ageYears + 1) },
            )

            Spacer(Modifier.height(Spacing.s))
            StepperSliderRow(
                label = "Height",
                displayValue = "${state.profile.heightCm} cm",
                sliderValue = state.profile.heightCm.toFloat(),
                sliderRange = 120f..220f,
                granularity = 1f,
                onSliderChange = { vm.setHeight(it.toInt()) },
                onDecrement = { vm.setHeight(state.profile.heightCm - 1) },
                onIncrement = { vm.setHeight(state.profile.heightCm + 1) },
            )

            Spacer(Modifier.height(Spacing.s))
            StepperSliderRow(
                label = "Weight",
                displayValue = "%.1f kg".format(state.profile.weightKg),
                sliderValue = state.profile.weightKg.toFloat(),
                sliderRange = 18f..200f,
                granularity = 1f,
                onSliderChange = { vm.setWeight(it.roundToInt().toDouble()) },
                onDecrement = { vm.setWeight(state.profile.weightKg - 0.1) },
                onIncrement = { vm.setWeight(state.profile.weightKg + 0.1) },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("Activity level") {
            OptionList(
                options = ActivityLevel.entries,
                selected = state.profile.activityLevel,
                onSelect = vm::setActivityLevel,
                caption = { it.detail },
                label = { it.title },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        BmiBar(currentBmi = Bmi.bmi(state.profile.weightKg, state.profile.heightCm))
    }
}

// ───────────────────────── Page 2: Your goal ─────────────────────

@Composable
private fun GoalPage(state: OnboardingUiState, vm: OnboardingViewModel) {
    PageScaffold(
        step = 2,
        title = "Your goal",
        subtitle = "Where you want to land. We'll compute calorie and nutrient targets from this.",
    ) {
        Section("Goal") {
            OptionList(
                options = Goal.entries,
                selected = state.profile.goal,
                onSelect = vm::setGoal,
                caption = {
                    when (it) {
                        Goal.Lose -> "Eat a little under what you burn"
                        Goal.Maintain -> "Eat about what you burn"
                        Goal.Gain -> "Eat a little over what you burn"
                    }
                },
                label = { it.displayName },
            )
        }

        if (state.profile.goal != Goal.Maintain) {
            Spacer(Modifier.height(Spacing.xxl))
            Section(
                if (state.profile.goal == Goal.Lose) "How fast to lose?" else "How fast to gain?"
            ) {
                OptionGrid(
                    options = WeightChangeRate.entries,
                    selected = state.profile.weightChangeRate,
                    onSelect = vm::setWeightChangeRate,
                    caption = { it.detail },
                    label = { it.title },
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
            val goalWeight = state.profile.goalWeightKg ?: state.profile.weightKg
            val goalRange = if (state.profile.goal == Goal.Lose) {
                18f..state.profile.weightKg.toFloat().coerceAtLeast(18.1f)
            } else {
                state.profile.weightKg.toFloat().coerceAtMost(199.9f)..200f
            }
            // No head of its own: the row already carries one in its eyebrow,
            // and `GOAL WEIGHT` over a rule over `Goal weight 72.0 kg` would
            // be the same two words twice.
            StepperSliderRow(
                label = "Goal weight",
                displayValue = "%.1f kg".format(goalWeight),
                sliderValue = goalWeight.toFloat(),
                sliderRange = goalRange,
                granularity = 1f,
                onSliderChange = { vm.setGoalWeight(it.roundToInt().toDouble()) },
                onDecrement = { vm.setGoalWeight(goalWeight - 0.1) },
                onIncrement = { vm.setGoalWeight(goalWeight + 0.1) },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        BmiBar(
            currentBmi = Bmi.bmi(state.profile.weightKg, state.profile.heightCm),
            goalBmi = state.profile.goalWeightKg?.let { Bmi.bmi(it, state.profile.heightCm) },
        )

        Spacer(Modifier.height(Spacing.xxl))
        // The payoff for two pages of questions, so it is the last thing on the
        // page — and the calorie figure is the whole payoff, so it is rendered
        // at hero size rather than as the first of six equal rows. Everything
        // else on this page is a question; this is the answer, and rule 2 says
        // a screen's one answer is allowed to be 64sp.
        Section(
            title = "Your estimated daily targets",
            trailing = {
                Text(
                    "per day",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Text(
                "Calories",
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    grouped(state.previewGoals.calories),
                    style = AppText.numberXXL,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "kcal",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            }
            Spacer(Modifier.height(Spacing.l))
            AppDivider()
            TargetRow("Protein", state.previewGoals.protein, "g")
            AppDivider()
            TargetRow("Carbs", state.previewGoals.carbs, "g")
            AppDivider()
            TargetRow("Fat", state.previewGoals.fat, "g")
            AppDivider()
            TargetRow("Fiber", state.previewGoals.fiber, "g")
            AppDivider()
            TargetRow("Water", state.previewGoals.waterMl.toDouble(), "ml")
        }
    }
}

// ───────────────────────── Page 3: Optional ──────────────────────

/**
 * The three optional set-ups, collapsed.
 *
 * Each row states what it is and what it costs to skip; opening one reveals
 * exactly the controls its old full page had, minus the page. Nothing is
 * written unless the user turns it on — see [OnboardingViewModel.finish] — so
 * a user who swipes straight to "Get started" leaves the reminder store and
 * the AI prefs untouched on disk.
 */
@Composable
private fun OptionalPage(
    state: OnboardingUiState,
    vm: OnboardingViewModel,
    onPickFolder: () -> Unit,
) {
    PageScaffold(
        step = 3,
        title = "Optional",
        subtitle = "Set these up now or skip them — all three live in Settings too.",
        optional = true,
    ) {
        ExpandableRow(
            title = "Backups",
            summary = if (state.backupCadence == BackupCadence.Off) "Off" else state.backupCadence.label,
        ) {
            BackupsSection(
                state = state,
                onPickFolder = onPickFolder,
                onClearFolder = vm::clearBackupFolder,
                onCadence = vm::setBackupCadence,
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        val remindersOn = state.reminders.count { it.enabled }
        ExpandableRow(
            title = "Reminders",
            summary = if (remindersOn == 0) "None" else "$remindersOn on",
        ) {
            RemindersSection(
                configs = state.reminders,
                onToggle = vm::setReminderEnabled,
                onSetTime = vm::setReminderTime,
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        ExpandableRow(
            title = "AI autofill",
            summary = if (state.aiEnabled) state.aiProvider.displayName else "Off",
        ) {
            AiSection(state = state, vm = vm)
        }

        Spacer(Modifier.height(Spacing.xxl))
        Text(
            "Skip any of these and nothing is written. You can turn them on later in Settings.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One collapsed optional set-up, as a [Section] whose head is the switchable
 * part.
 *
 * The summary on the right is the whole reason this collapses safely: a head
 * reading `REMINDERS — None` tells the user what skipping it means without
 * costing them the tap that opening it would. It sits in the trailing slot,
 * which is where every section in the app states its summary — so these three
 * read as the same object as `18 OF 29 ON TRACK` on Today rather than as a
 * bespoke accordion.
 *
 * **The icon disc is gone.** A 36dp tinted circle holding a cloud, a bell and a
 * sparkle is decoration in the friendly register the app refused (rule 4 on emoji is
 * the same argument), and it was spending the accent on chrome, which rule 3
 * rules out. Three words in a row do not need three pictures to be told apart.
 *
 * Only the head is clickable: the content holds switches, a segmented control
 * and an API-key field, and a stray tap near any of them must not fold the
 * section away.
 */
@Composable
private fun ExpandableRow(
    title: String,
    summary: String,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )
    Column(Modifier.fillMaxWidth()) {
        Section(
            title = title,
            modifier = Modifier
                .clickable {
                    haptics.light()
                    expanded = !expanded
                }
                .heightIn(min = Sizes.touchTarget),
            trailing = {
                Text(
                    summary,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Lucide.ChevronDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevron),
                )
            },
        ) {
            // Section lays 16dp under its rule; the body below starts there.
        }
        AnimatedVisibility(visible = expanded) {
            Column { content() }
        }
    }
}

@Composable
private fun BackupsSection(
    state: OnboardingUiState,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onCadence: (BackupCadence) -> Unit,
) {
    val haptics = rememberAppHaptics()
    Text(
        "Snapshots are plain JSON. Pick a folder you sync (Drive, Dropbox, photos) — " +
            "or leave it on the default Downloads/FoodTracker. You can change this anytime.",
        style = AppText.bodyS,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Spacing.xxl))
    Section("Backup folder") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Lucide.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.backupFolderLabel ?: "Downloads/FoodTracker (default)",
                    style = AppText.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    if (state.backupFolderLabel == null) "Tap Choose to pick any folder"
                    else "Selected — backups will land here",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppTextButton(
                text = if (state.backupFolderLabel == null) "Choose" else "Change",
                onClick = {
                    haptics.light()
                    onPickFolder()
                },
            )
            if (state.backupFolderLabel != null) {
                AppTextButton(text = "Reset", onClick = onClearFolder)
            }
    }
    }

    Spacer(Modifier.height(Spacing.xxl))
    Section("Recurring backup") {
        Text(
            "Runs in the background only when you turn it on.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.s))
        ExclusiveButtonGroup(
            options = BackupCadence.entries,
            selected = state.backupCadence,
            onSelect = onCadence,
            modifier = Modifier.fillMaxWidth(),
            label = { it.label },
        )
    }
}

@Composable
private fun RemindersSection(
    configs: List<ReminderConfig>,
    onToggle: (ReminderKind, Boolean) -> Unit,
    onSetTime: (ReminderKind, Int, Int) -> Unit,
) {
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

    Text(
        "Turn on the ones you want — meals, water, weigh-in. Skip them all if you'd rather " +
            "log when you remember.",
        style = AppText.bodyS,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (!notificationsGranted) {
        Spacer(Modifier.height(Spacing.xxl))
        // Same shape as the Reminders screen's own warning: the state is one
        // word in the head's trailing slot, in the error colour, which on black
        // is louder than any box drawn around it.
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
                "We need notification permission to fire the reminders you set.",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            AppButton(
                text = "Allow notifications",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
    }

    Spacer(Modifier.height(Spacing.xxl))
    Section(
        title = "Daily reminders",
        trailing = {
            val on = configs.count { it.enabled }
            Text(
                if (on == 0) "All off" else "$on on",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        configs.forEachIndexed { index, config ->
            if (index > 0) AppDivider()
            ReminderRow(
                config = config,
                onToggle = { onToggle(config.kind, it) },
                onEditTime = { editingKind = config.kind },
            )
        }
    }

    val batteryExempt by rememberBatteryOptExempt()
    if (configs.any { it.enabled } && !batteryExempt) {
        Spacer(Modifier.height(Spacing.xxl))
        BatteryOptCard(onOpenSettings = { BatteryOptHelper.openSettings(context) })
    }

    editingKind?.let { kind ->
        val current = configs.firstOrNull { it.kind == kind }
        if (current != null) {
            TimePickerDialog(
                initialHour = current.hour,
                initialMinute = current.minute,
                onDismiss = { editingKind = null },
                onConfirm = { h, m ->
                    onSetTime(kind, h, m)
                    editingKind = null
                },
            )
        }
    }
}

/**
 * One reminder, identical in shape to the row on the Reminders screen: eyebrow
 * label left, the time at number weight on the right, the switch last. The two
 * lists are the same list in two places, and they used to look nothing alike —
 * this one stacked the time under the title as a small primary link, which both
 * hid the figure and spent the accent on an affordance (rule 3).
 *
 * The whole row opens the picker now, rather than a line of 13sp text.
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
        Text(
            config.kind.title,
            style = AppText.eyebrow,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
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
 * Optional, and off unless the user turns it on. Mirrors the AI section in
 * Settings rather than adding a second way to configure the same thing — the
 * point of having it here is that someone who would use it finds out it exists
 * instead of discovering it months later.
 *
 * Nothing is written unless the toggle is on; see OnboardingViewModel.finish.
 */
@Composable
private fun AiSection(state: OnboardingUiState, vm: OnboardingViewModel) {
    val haptics = rememberAppHaptics()
    Text(
        "Adding a food by hand means typing in every nutrient. An AI can fill them " +
            "from the name instead. You bring your own API key, only the food name is " +
            "sent, and you can set this up later in Settings.",
        style = AppText.bodyS,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Spacing.xxl))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Enable AI autofill",
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Needs an API key from your chosen provider.",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppSwitch(
            checked = state.aiEnabled,
            onCheckedChange = {
                haptics.light()
                vm.setAiEnabled(it)
            },
        )
    }

    if (state.aiEnabled) {
        Spacer(Modifier.height(Spacing.xxl))
        Section("Provider") {
            ExclusiveButtonGroup(
                options = AiProvider.entries,
                selected = state.aiProvider,
                onSelect = vm::setAiProvider,
                modifier = Modifier.fillMaxWidth(),
                label = { it.displayName },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("Model") {
            ExclusiveButtonGroup(
                options = state.aiProvider.models,
                selected = state.aiModel,
                onSelect = vm::setAiModel,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = false,
                label = { it.label },
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${state.aiProvider.defaultModel.label} is the cheapest and accurate enough " +
                    "for most foods — leave it selected to save money.",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("API key") {
            AppTextField(
                value = state.aiApiKey,
                onValueChange = vm::setAiApiKey,
                label = { Text("${state.aiProvider.displayName} API key", style = AppText.body) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Your key stays on this device and is kept out of cloud backup.",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val uriHandler = LocalUriHandler.current
            Text(
                "Get a ${state.aiProvider.displayName} API key at ${state.aiProvider.keyHint} ↗",
                style = AppText.bodyS,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .clickable { uriHandler.openUri(state.aiProvider.keyUrl) },
            )
        }
    }
}

// ───────────────────────── Page chrome ───────────────────────────

/**
 * Step eyebrow, title, one-line subtitle, a rule, and a scrolling body.
 *
 * Every page had its own copy of this, with its own padding and its own
 * heading style. Three pages is few enough that the duplication was survivable
 * and many enough that it had already drifted — `PageHeader` on two pages and a
 * bare `Text` on the other two.
 *
 * **The dots are now words.** `STEP 1 OF 3` is the same fact three circles were
 * encoding, in the uppercase tracked micro-label this direction is built on,
 * and it answers the question the dots only gestured at — a dot row tells you
 * *where* you are but never *how many more*, which on a first run is the thing
 * someone wants to know before they start typing. [optional] prints the page's
 * standing in the same line rather than as a filled badge beside the title:
 * rule 3 keeps the accent for data, and "this page can be skipped" is not data.
 *
 * The rule under the subtitle is the page's own head rule — the same device
 * every [Section] below it uses, at the top of the hierarchy rather than inside
 * it.
 */
@Composable
private fun PageScaffold(
    step: Int,
    title: String,
    subtitle: String,
    optional: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
    ) {
        Text(
            if (optional) "Step $step of $SetupSteps · Optional"
            else "Step $step of $SetupSteps",
            style = AppText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            title,
            style = AppText.headline,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            subtitle,
            style = AppText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        content()
        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * Back on the left, the one forward action on the right.
 *
 * The forward button is the standard 48dp [AppButton] — the rewrite deleted the 56dp
 * hero height, and with it the trailing arrow icon, which [AppButton] has no
 * slot for and which said nothing the word "Continue" did not.
 */
@Composable
private fun BottomNav(
    pageIndex: Int,
    pageCount: Int,
    continueEnabled: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLast = pageIndex == pageCount - 1
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pageIndex > 0) {
            AppTextButton(text = "Back", onClick = onBack)
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Spacer(Modifier.weight(1f))
        AppButton(
            text = when {
                isLast -> "Start tracking"
                pageIndex == 0 -> "Get started"
                pageIndex < IntroCount -> "Sounds good"
                else -> "Continue"
            },
            onClick = onContinue,
            enabled = continueEnabled,
        )
    }
}

// ───────────────────────── Shared row helpers ───────────────────

/**
 * A labelled value with a stepper, over the slider that changes it.
 *
 * The label is an eyebrow rather than body text for the same reason every other
 * label in the app is (rule 2): the stepper beside it is showing a number, and
 * two pieces of 15sp type on one line make the reader decide which of them is
 * the answer. Uppercase and small, it stops competing.
 */
@Composable
private fun StepperSliderRow(
    label: String,
    displayValue: String,
    sliderValue: Float,
    sliderRange: ClosedFloatingPointRange<Float>,
    granularity: Float,
    onSliderChange: (Float) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            NumberStepper(
                value = displayValue,
                onDecrement = onDecrement,
                onIncrement = onIncrement,
                decrementEnabled = sliderValue > sliderRange.start,
                incrementEnabled = sliderValue < sliderRange.endInclusive,
            )
        }
        HapticSlider(
            value = sliderValue,
            onValueChange = onSliderChange,
            valueRange = sliderRange,
            granularity = granularity,
        )
    }
}


/**
 * One computed target: eyebrow left, number right, like every other row in the
 * app (rule 2). The label was `body` and the number `numberM`, which is two
 * sizes of normal text arguing about which one matters; at eyebrow weight the
 * label stops competing and the figure wins without being made bigger.
 */
@Composable
private fun TargetRow(label: String, value: Double, unit: String) {
    Row(
        modifier = Modifier
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
        ValueText(value, unit)
    }
}

/** Best-effort human label for a SAF tree URI, e.g. `primary:Documents/Backups`. */
private fun decodeFolderLabel(uri: String): String? {
    val decoded = runCatching { java.net.URLDecoder.decode(uri, "UTF-8") }.getOrNull() ?: return null
    val docIdx = decoded.indexOf("/tree/")
    if (docIdx < 0) return decoded
    return decoded.substring(docIdx + "/tree/".length)
}

/** Five short bars across the top — the done ones ink — and Skip on the intro pages. */
@Composable
private fun ProgressHeader(page: Int, onSkip: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.gutter, end = Spacing.s, top = Spacing.s)
            // Same height with or without Skip, so the bar never jumps.
            .heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            repeat(PageCount) { i ->
                val fill by animateColorAsState(
                    if (i <= page) scheme.primary else scheme.surfaceContainerHighest,
                    label = "step$i",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(fill)
                )
            }
        }
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
            if (onSkip != null) AppTextButton("Skip", onSkip)
        }
    }
}
