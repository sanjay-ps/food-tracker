package com.example.foodtracker.ui.screens.addfood

import com.example.foodtracker.ui.components.OptionGrid
import com.example.foodtracker.ui.components.atoms.AppTextField
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Sparkles
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.AiLog
import com.example.foodtracker.data.FoodCategory
import com.example.foodtracker.data.NutritionBasis
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.NumberField
import com.example.foodtracker.ui.components.atoms.NumberFieldGrid
import com.example.foodtracker.ui.components.atoms.NumberFieldSpec
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.components.atoms.filledCount
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing

/**
 * Add or edit a custom food.
 *
 * The form carries 34 fields because the app tracks 29 nutrients and refuses to
 * pretend otherwise. What changed is that it no longer *shows* 34 at once.
 *
 * Three things do that work:
 *
 *  - **Accordions with a filled counter.** Minerals, vitamins and the optional
 *    detail start collapsed, each header reading `9 fields · 0 filled`. The
 *    normal path to a complete food is AI autofill, and the normal path should
 *    never open on thirty empty boxes. The counter is derived from the same
 *    list the section renders ([filledCount]), so it cannot lie.
 *  - **The sections are rules, not cards.** Each one is now an eyebrow header
 *    over a hairline, per house rule 1. A collapsed accordion inside a card was
 *    a box drawn around a single line of text — five of them stacked down the
 *    page is the Direction A anatomy this pass exists to remove, and on a form
 *    the cost is literal: the border, its corner radius and its 16dp of inner
 *    padding each pushed the fields further from the screen's one left margin,
 *    so no two labels on the page started at the same x.
 *  - **Two-column compact fields.** Half the page height for the same
 *    fields. See `NumberFieldGrid`.
 *  - **The AI action moved beside Name**, where its one input is. It used to
 *    sit as a full-width button halfway down the scroll — below the fields it
 *    exists to spare you typing.
 *
 * Out of scope here: the autofill itself, [AddFoodViewModel], the network
 * clients, and [AiProgressDialog], which is kept exactly as it was.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFoodScreen(
    onBack: () -> Unit,
    prefillBarcode: String? = null,
) {
    val vm: AddFoodViewModel = appViewModel { AddFoodViewModel(it.repository, it.profileStore) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    var showAiInstructions by rememberSaveable { mutableStateOf(false) }
    var aiInstructions by rememberSaveable { mutableStateOf("") }
    var showAiLog by rememberSaveable { mutableStateOf(false) }
    val aiLogEntries by AiLog.entries.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(prefillBarcode) {
        if (!prefillBarcode.isNullOrBlank() && state.barcode.isNullOrBlank()) {
            vm.seedBarcode(prefillBarcode)
        }
    }

    AiProgressDialog(
        step = state.aiStep,
        verifies = state.aiVerifies,
        // The brand is part of the question now, so it is part of what the
        // dialog says it is asking about.
        subject = listOf(state.name.trim(), state.brand.trim())
            .filter { it.isNotBlank() }
            .joinToString(" — "),
        providerLabel = state.aiProvider.displayName,
        modelLabel = state.aiModel.label,
        startedAt = state.aiStartedAt,
        onCancel = { haptics.light(); vm.cancelAi() },
    )

    if (showAiLog) AiDevLogSheet(onDismiss = { showAiLog = false })

    if (showAiInstructions) {
        AlertDialog(
            onDismissRequest = { showAiInstructions = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("AI food details", style = AppText.titleL) },
            text = {
                Column {
                    // The model lives here as well as in Settings, because this
                    // is where you are standing when you find out the cheap one
                    // is rate-limited or out of free-tier requests for the day.
                    // Walking to Settings to change it and walking back meant
                    // retyping nothing but remembering everything; the whole
                    // point of the retry is that the form is still filled in.
                    // The provider is named but not switchable: each one needs
                    // its own API key, so switching here would only fail
                    // differently.
                    Section(
                        title = "Model",
                        trailing = {
                            Text(
                                state.aiProvider.displayName,
                                style = AppText.bodyS,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        ExclusiveButtonGroup(
                            options = state.aiProvider.models,
                            selected = state.aiModel,
                            onSelect = { vm.setAiModel(it) },
                            modifier = Modifier.fillMaxWidth(),
                            fillWidth = false,
                            label = { it.label },
                        )
                    }

                    Spacer(Modifier.height(Spacing.xxl))
                    Section("Instructions") {
                        AppTextField(
                            value = aiInstructions,
                            onValueChange = { aiInstructions = it },
                            label = { Text("Optional") },
                            placeholder = { Text("e.g. Kerala-style, coconut-based, 1 bowl") },
                            minLines = 2,
                            maxLines = 4,
                            // Same corner as the barcode dialog's field. A text
                            // field is the one place Material's own default
                            // radius leaks through.
                            shape = RoundedCornerShape(Radius.md),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (aiLogEntries.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.s))
                            AppTextButton(
                                text = "View request log",
                                onClick = { showAiInstructions = false; showAiLog = true },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = "Ask AI",
                    onClick = {
                        showAiInstructions = false
                        haptics.light()
                        vm.autofillFromAi(aiInstructions)
                    },
                )
            },
            dismissButton = {
                AppTextButton(text = "Cancel", onClick = { showAiInstructions = false })
            },
        )
    }

    val servingGramsInt = state.servingGrams.toDoubleOrNull()?.toInt() ?: 100
    val basisSuffix = when (state.basis) {
        NutritionBasis.Per100g -> "per 100 g"
        NutritionBasis.PerServing -> "per $servingGramsInt g serving"
    }

    Scaffold(
        topBar = { AppTopBar(title = "Add custom food", onBack = onBack) },
        bottomBar = {
            // Pinned, not scrolled to. With four collapsible sections the page's
            // height now depends on what the user opened, so a save button at
            // the bottom of the scroll would be somewhere different every time.
            Column(Modifier.navigationBarsPadding()) {
                AppDivider()
                AppButton(
                    text = "Save food",
                    onClick = {
                        haptics.confirm()
                        vm.save()
                    },
                    enabled = state.name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.gutter),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = Spacing.screenContent,
            // 24dp, not 32: every block below is a standing head with a rule
            // under it (house rule 1), so the gap no longer has to mark where
            // one section ends and the next begins.
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            item("basics") {
                BasicsSection(
                    state = state,
                    servingGramsInt = servingGramsInt,
                    onAskAi = { showAiInstructions = true },
                    onOpenAiLog = { showAiLog = true },
                    aiHasLog = aiLogEntries.isNotEmpty(),
                    vm = vm,
                )
            }

            item("macros") {
                FieldSection(
                    title = "Macros",
                    basisSuffix = basisSuffix,
                    fields = macroFields(state, vm),
                    initiallyExpanded = true,
                )
            }
            item("minerals") {
                FieldSection(
                    title = "Minerals",
                    basisSuffix = basisSuffix,
                    fields = mineralFields(state, vm),
                )
            }
            item("vitamins") {
                FieldSection(
                    title = "Vitamins",
                    basisSuffix = basisSuffix,
                    fields = vitaminFields(state, vm),
                )
            }
            item("optional") {
                FieldSection(
                    title = "Optional detail",
                    basisSuffix = basisSuffix,
                    fields = optionalFields(state, vm),
                    // Kept from the previous form, and still the honest thing
                    // to say: these are sub-components of fiber and protein,
                    // and a guess here quietly corrupts a reading elsewhere.
                    note = "Leave these blank unless you have real figures. Soluble and " +
                        "insoluble should add up to fiber; essential amino acids " +
                        "run about a third of protein, and leucine a fifth of that.",
                )
            }
        }
    }
}

// ───────────────────────────── Basics ─────────────────────────────

/**
 * Name, brand, category, serving and basis — everything that is not a nutrient.
 *
 * Never collapsible. A food with no name cannot be saved, so the one section
 * the user must touch is the one section always open.
 *
 * It is four [Section]s rather than one card: `BASICS`, `CATEGORY`, `SERVING`
 * and the basis switch each get a standing head with a rule under it. The card
 * that held all four drew one border around four unrelated groups and then left
 * the groups themselves separated by nothing but a 12dp gap — the reader had a
 * boundary where they did not need one and none where they did. Rules are the
 * opposite trade, and they cost 1dp instead of 16dp of inset on every field.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicsSection(
    state: AddFoodFormState,
    servingGramsInt: Int,
    onAskAi: () -> Unit,
    onOpenAiLog: () -> Unit,
    /** Whether any request has been attempted — nothing to show before the first. */
    aiHasLog: Boolean,
    vm: AddFoodViewModel,
) {
    Column(Modifier.fillMaxWidth()) {
        Section("Basics") {
            state.barcode?.takeIf { it.isNotBlank() }?.let { code ->
                Text(
                    "Barcode: $code",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.s))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = state.name,
                    onValueChange = { v -> vm.update { it.copy(name = v) } },
                    label = { Text("Name", style = AppText.body) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (state.aiEnabled) {
                    Spacer(Modifier.width(Spacing.s))
                    // Beside the name, because the name is its entire input. As
                    // an icon rather than a labelled button: at this size the
                    // sparkle is the convention, and the full-width "✨ Ask AI to
                    // fill nutrition" button it replaces was wide enough to read
                    // as the form's primary action, which it is not.
                    IconButton(
                        onClick = onAskAi,
                        enabled = state.name.isNotBlank() && state.aiStep == AiStep.Idle,
                        modifier = Modifier.size(Sizes.iconButton),
                    ) {
                        Icon(
                            Lucide.Sparkles,
                            contentDescription = "Ask AI to fill nutrition",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            state.aiError?.let { err ->
                Spacer(Modifier.height(Spacing.xs))
                Text(err, style = AppText.bodyS, color = MaterialTheme.colorScheme.error)
                // The message says what to do about the causes it can name.
                // This is for the ones it cannot — and it opens over the form
                // rather than navigating away from it, because the next thing
                // you do after reading it is change one field and go again.
                //
                // Hidden when nothing has been attempted: a missing API key
                // fails before any request is made, so the log would open on
                // "nothing yet" and the button would be a dead end.
                if (aiHasLog) AppTextButton(text = "View request log", onClick = onOpenAiLog)
            }

            Spacer(Modifier.height(Spacing.m))
            AppTextField(
                value = state.brand,
                onValueChange = { v -> vm.update { it.copy(brand = v) } },
                label = { Text("Brand (optional)", style = AppText.body) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.aiEnabled) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Sent to the AI with the name — a named brand is asked for by " +
                        "its own label rather than the generic food.",
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("Category") {
            // A radio grid: ten short names fit two to a row without a long column.
            OptionGrid(
                options = FoodCategory.entries,
                selected = state.category,
                onSelect = { cat -> vm.update { it.copy(category = cat) } },
                label = { it.displayName },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("Serving") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.Bottom,
            ) {
                NumberField(
                    label = "Grams",
                    value = state.servingGrams,
                    onValueChange = { v -> vm.update { it.copy(servingGrams = v) } },
                    unit = "g",
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = state.servingLabel,
                    onValueChange = { v -> vm.update { it.copy(servingLabel = v) } },
                    label = { Text("Serving label", style = AppText.body) },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
            }

            if (state.servingVariants.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xxl))
                ServingVariantsRow(
                    variants = state.servingVariants,
                    onClear = { vm.update { it.copy(servingVariants = emptyList()) } },
                )
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("Values I'm entering are") {
            ExclusiveButtonGroup(
                options = listOf(NutritionBasis.Per100g, NutritionBasis.PerServing),
                selected = state.basis,
                onSelect = { b -> vm.update { it.copy(basis = b) } },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    if (it == NutritionBasis.Per100g) "Per 100 g" else "Per $servingGramsInt g serving"
                },
            )
        }
    }
}

/**
 * Read-only view of the named sizes attached to this food — the AI returns
 * them only for foods that genuinely have them, so most saves never show this.
 * Nutrients always describe the serving above; these are alternate gram
 * amounts offered at log time. Clearing falls back to the default
 * canonical + 100 g pair.
 */
@Composable
private fun ServingVariantsRow(
    variants: List<ServingVariant>,
    onClear: () -> Unit,
) {
    Section(
        title = "Serving options",
        trailing = { AppTextButton(text = "Clear", onClick = onClear) },
    ) {
        Text(
            variants.joinToString(" · ") { "${it.label} ${it.grams.toInt()} g" },
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────── Accordion ───────────────────────────

/**
 * One collapsible block of number fields.
 *
 * The header carries three facts and the collapsed state carries all three, so
 * the user can decide whether to open it without opening it: what the section
 * is, which basis its figures are in, and how much of it is already answered.
 *
 * **Head, rule, content** — house rule 1's [Section], and the hairline is what
 * makes the accordion legible without a box. Collapsed, the section is one
 * eyebrow over one rule, so four collapsed sections read as four ruled entries
 * in a contents list rather than as four empty boxes; expanded, the same rule
 * becomes the lid of the grid underneath it. The card that used to be here drew
 * a second boundary around that line and, worse, moved every field 16dp in from
 * the margin the rest of the page uses.
 *
 * The counter and the chevron ride in the head's [trailing][Section] slot,
 * which is where every section on every screen states its summary figure — so
 * `9 fields · 0 filled` lands in the same place as Today's `2 981 kcal`.
 *
 * The head is the hit target and the content is not: the grid holds 34 text
 * fields, and a tap that lands between two of them must not fold the section
 * the user is typing in.
 *
 * Expansion is `rememberSaveable`, keyed by nothing — each section remembers
 * its own state across rotation, and a section the user opened stays open while
 * they work down the form.
 */
@Composable
private fun FieldSection(
    title: String,
    basisSuffix: String,
    fields: List<NumberFieldSpec>,
    initiallyExpanded: Boolean = false,
    note: String? = null,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val haptics = rememberAppHaptics()
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )
    val filled = fields.filledCount

    Column(Modifier.fillMaxWidth()) {
        Section(
            title = title,
            modifier = Modifier
                .clickable {
                    haptics.light()
                    expanded = !expanded
                }
                // The whole head is the hit target, and an eyebrow plus a
                // chevron is 24dp tall — short of the 48dp a finger expects.
                .heightIn(min = Sizes.touchTarget),
            trailing = {
                Text(
                    // Open, the basis is what you need to know while typing.
                    // Closed, the count is: it answers "is there anything in
                    // there?" without costing a tap.
                    if (expanded) basisSuffix
                    else "${fields.size} fields · $filled filled",
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
            // Section already lays 16dp under its rule, so an expanded
            // grid starts exactly where any other section's content does.
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                if (note != null) {
                    Text(
                        note,
                        style = AppText.bodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.l))
                }
                NumberFieldGrid(fields)
            }
        }
    }
}

// ─────────────────────────── Field tables ─────────────────────────
//
// The fields as data rather than as call sites. Besides feeding the "n filled"
// counters, this is what keeps a 34-field form readable in source: the units
// line up in a column, so a wrong one (µg where the parser wants mg) is visible
// by eye rather than by test.

private fun macroFields(s: AddFoodFormState, vm: AddFoodViewModel) = listOf(
    NumberFieldSpec("Calories", "kcal", s.calories) { v -> vm.update { it.copy(calories = v) } },
    NumberFieldSpec("Carbs", "g", s.carbs) { v -> vm.update { it.copy(carbs = v) } },
    NumberFieldSpec("Sugar", "g", s.sugar) { v -> vm.update { it.copy(sugar = v) } },
    NumberFieldSpec("Fiber", "g", s.fiber) { v -> vm.update { it.copy(fiber = v) } },
    NumberFieldSpec("Protein", "g", s.protein) { v -> vm.update { it.copy(protein = v) } },
    NumberFieldSpec("Fat", "g", s.fat) { v -> vm.update { it.copy(fat = v) } },
    NumberFieldSpec("Saturated fat", "g", s.saturatedFat) { v -> vm.update { it.copy(saturatedFat = v) } },
    NumberFieldSpec("Omega-3", "g", s.omega3) { v -> vm.update { it.copy(omega3 = v) } },
    NumberFieldSpec("Cholesterol", "mg", s.cholesterol) { v -> vm.update { it.copy(cholesterol = v) } },
    NumberFieldSpec("Sodium", "mg", s.sodium) { v -> vm.update { it.copy(sodium = v) } },
)

private fun mineralFields(s: AddFoodFormState, vm: AddFoodViewModel) = listOf(
    NumberFieldSpec("Calcium", "mg", s.calcium) { v -> vm.update { it.copy(calcium = v) } },
    NumberFieldSpec("Iron", "mg", s.iron) { v -> vm.update { it.copy(iron = v) } },
    NumberFieldSpec("Magnesium", "mg", s.magnesium) { v -> vm.update { it.copy(magnesium = v) } },
    NumberFieldSpec("Potassium", "mg", s.potassium) { v -> vm.update { it.copy(potassium = v) } },
    NumberFieldSpec("Zinc", "mg", s.zinc) { v -> vm.update { it.copy(zinc = v) } },
    NumberFieldSpec("Phosphorus", "mg", s.phosphorus) { v -> vm.update { it.copy(phosphorus = v) } },
    NumberFieldSpec("Selenium", "µg", s.selenium) { v -> vm.update { it.copy(selenium = v) } },
    NumberFieldSpec("Copper", "mg", s.copper) { v -> vm.update { it.copy(copper = v) } },
    NumberFieldSpec("Manganese", "mg", s.manganese) { v -> vm.update { it.copy(manganese = v) } },
)

private fun vitaminFields(s: AddFoodFormState, vm: AddFoodViewModel) = listOf(
    NumberFieldSpec("Vitamin A", "µg", s.vitaminA) { v -> vm.update { it.copy(vitaminA = v) } },
    NumberFieldSpec("Vitamin C", "mg", s.vitaminC) { v -> vm.update { it.copy(vitaminC = v) } },
    NumberFieldSpec("Vitamin D", "µg", s.vitaminD) { v -> vm.update { it.copy(vitaminD = v) } },
    NumberFieldSpec("Vitamin E", "mg", s.vitaminE) { v -> vm.update { it.copy(vitaminE = v) } },
    NumberFieldSpec("Vitamin K", "µg", s.vitaminK) { v -> vm.update { it.copy(vitaminK = v) } },
    NumberFieldSpec("Vitamin B1 / Thiamin", "mg", s.vitaminB1) { v -> vm.update { it.copy(vitaminB1 = v) } },
    NumberFieldSpec("Vitamin B2 / Riboflavin", "mg", s.vitaminB2) { v -> vm.update { it.copy(vitaminB2 = v) } },
    NumberFieldSpec("Vitamin B3 / Niacin", "mg", s.vitaminB3) { v -> vm.update { it.copy(vitaminB3 = v) } },
    NumberFieldSpec("Vitamin B6", "mg", s.vitaminB6) { v -> vm.update { it.copy(vitaminB6 = v) } },
    NumberFieldSpec("Vitamin B12", "µg", s.vitaminB12) { v -> vm.update { it.copy(vitaminB12 = v) } },
    NumberFieldSpec("Folate", "µg", s.folate) { v -> vm.update { it.copy(folate = v) } },
)

private fun optionalFields(s: AddFoodFormState, vm: AddFoodViewModel) = listOf(
    NumberFieldSpec("Soluble fiber", "g", s.solubleFiber) { v -> vm.update { it.copy(solubleFiber = v) } },
    NumberFieldSpec("Insoluble fiber", "g", s.insolubleFiber) { v -> vm.update { it.copy(insolubleFiber = v) } },
    NumberFieldSpec("Essential amino acids", "g", s.essentialAminoAcids) { v -> vm.update { it.copy(essentialAminoAcids = v) } },
    NumberFieldSpec("of which leucine", "g", s.leucine) { v -> vm.update { it.copy(leucine = v) } },
)
