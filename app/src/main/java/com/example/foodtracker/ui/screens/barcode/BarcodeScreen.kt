package com.example.foodtracker.ui.screens.barcode

import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.atoms.AppTextField
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScanBarcode
import com.composables.icons.lucide.SearchX
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.example.foodtracker.ui.components.atoms.AppModalSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodtracker.data.BarcodeProduct
import com.example.foodtracker.data.BarcodeSource
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.NutritionBasis
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.ui.appViewModel
import com.example.foodtracker.ui.components.EmptyState
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.AppOutlinedButton
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.Spinner
import com.example.foodtracker.ui.components.serving.ServingSheet
import com.example.foodtracker.ui.components.serving.isCustomGrams
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Elevation
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import java.time.LocalTime

/**
 * Barcode scanning.
 *
 * **What changed is everything above the camera, and nothing below it.** The
 * CameraX + ML Kit pipeline and `BarcodeScannerView` are out of scope and
 * untouched. What went is the ~200 lines of bespoke panel that sat on top: a
 * serving editor with its own stepper, slider and "will log" line, a 4-way
 * "Add to:" meal row, and an `Add 180 g to Lunch` button — a second dialect for
 * a question the app already has one answer to. A scanned packet now goes into
 * the same serving sheet a dosa does, so logging it is the same three
 * gestures.
 *
 * The one thing the sheet cannot know is which published basis to trust, so
 * that control survives as the sheet's `extraBlock`: OFF and USDA disagree with
 * the printed pack often enough — especially on Indian supplements, where
 * contributors paste the only number they can see into the per-100 g slot —
 * that the user, who is holding the pack, has to be able to say.
 *
 * The camera keeps running through the lookup: the
 * progress line is a small bar at the bottom rather than a spinner over the
 * whole preview, so a mis-scan does not mean waiting for a dead screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    onBack: () -> Unit,
    onAddManually: (barcode: String) -> Unit,
) {
    val vm: BarcodeViewModel = appViewModel { BarcodeViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val context = LocalContext.current

    // A single quiet tick the moment a scan resolves — confirms the lookup
    // landed without the user having to read the screen.
    LaunchedEffect(state.resolved) {
        if (state.resolved) haptics.light()
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // Seeded from the wall clock, like every other meal pre-set in the app —
    // this screen has no route to inherit one from. Deliberately a local guess
    // rather than a call into Today's `currentMeal`: a scanner should not
    // depend on a screen it never navigates to.
    var meal by rememberSaveable { mutableStateOf(mealFromClock()) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }

    // The in-memory row the sheet prices. Rebuilt when the basis changes, since
    // that is what the basis means; remembered so it keeps one identity.
    val previewFood = remember(state.product, state.cached, state.basis) { state.previewFood() }
    // Keyed on the pack's own serving rather than on the barcode: the barcode
    // is set the instant a code is read, which is *before* the lookup returns,
    // so keying on it would seed the amount from a null product and then never
    // re-seed. Neither key moves when the basis changes, which is right — the
    // basis reprices the food, it does not change how much of it you ate.
    var variant by remember(previewFood?.servingLabel, previewFood?.servingGrams) {
        mutableStateOf(
            previewFood?.variantsOrDefault()?.firstOrNull() ?: ServingVariant.Default
        )
    }
    var servings by remember(previewFood?.servingLabel, previewFood?.servingGrams) {
        mutableStateOf(1.0)
    }

    Scaffold(
        topBar = { AppTopBar(title = "Scan barcode", onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!hasPermission) {
                PermissionPrompt(onGrant = { permLauncher.launch(Manifest.permission.CAMERA) })
            } else {
                // Kept mounted through the lookup so the preview never freezes
                // mid-scan; `onBarcode` ignores detections once a lookup is in
                // flight, so leaving it running costs nothing but photons.
                BarcodeScannerView(onBarcode = vm::onBarcode, modifier = Modifier.fillMaxSize())
                ScannerOverlay()
            }

            if (hasPermission) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Spacing.gutter),
                ) {
                    if (state.isLoading) {
                        LookupBar(barcode = state.lastBarcode)
                    } else if (!state.resolved) {
                        // Small, and at the bottom: it is the fallback for a
                        // torn or unreadable label, not the main event. The
                        // keyboard glyph that used to lead the label is gone —
                        // house rule 4 keeps emoji to the food picker's
                        // category discs, and a button is not that.
                        AppOutlinedButton(
                            text = "Enter code manually",
                            onClick = { showManualEntry = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (previewFood != null) {
        // Pulled out of the call so the lambdas get an explicit @Composable
        // type; inferring one through two nullable `let`s is a coin toss.
        val product = state.product
        val cached = state.cached
        val basis = state.basis
        val source = state.resolvedSource
        val extraBlock: (@Composable () -> Unit)? = when {
            product != null -> {
                { BasisBlock(product, basis, vm::setBasis, source) }
            }
            cached != null -> {
                { StaleCacheNote(source) }
            }
            else -> null
        }
        ServingSheet(
            food = previewFood,
            meal = meal,
            variant = variant,
            servings = servings,
            onVariantChange = { variant = it },
            onServingsChange = { servings = it },
            onMealChange = { meal = it },
            onConfirm = {
                haptics.confirm()
                val isCustom = variant.isCustomGrams
                vm.logResolved(
                    meal = meal,
                    grams = if (isCustom) variant.grams else variant.grams * servings,
                    servings = if (isCustom) 1.0 else servings,
                    servingLabel = variant.label,
                    onDone = onBack,
                )
            },
            // Dismissing is "wrong packet" far more often than "never mind",
            // so it resets to scanning rather than leaving the screen.
            onDismiss = { vm.retry() },
            extraBlock = extraBlock,
        )
    }

    if (state.notFound || state.error != null) {
        NotFoundSheet(
            barcode = state.lastBarcode,
            message = state.error,
            onDismiss = vm::retry,
            onRetry = vm::retry,
            onAddManually = { state.lastBarcode?.let(onAddManually) },
        )
    }

    if (showManualEntry) {
        ManualCodeDialog(
            onDismiss = { showManualEntry = false },
            onSubmit = { code ->
                showManualEntry = false
                vm.onBarcode(code)
            },
        )
    }
}

/** The meal a scan at this hour most likely belongs to. */
private fun mealFromClock(): MealType = when (LocalTime.now().hour) {
    in 0..10 -> MealType.Breakfast
    in 11..14 -> MealType.Lunch
    in 15..18 -> MealType.Snack
    else -> MealType.Dinner
}

/**
 * The viewfinder. The two black scrims are deliberate: they are what makes the
 * frame readable against an arbitrary kitchen counter, and they are the one
 * place in the app where a hardcoded colour is right — they sit over camera
 * output, not over a themed surface.
 */
@Composable
private fun ScannerOverlay() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth(0.7f)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(Color(0x33000000)),
            )
            Spacer(Modifier.height(Spacing.xxl))
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(Radius.pill),
            ) {
                Text(
                    "Point at a barcode",
                    color = Color.White,
                    style = AppText.body,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
        }
    }
}

/**
 * Two lines in a bar, not a spinner over the preview.
 *
 * It was a `surfaceContainerHigh` slab. A panel floating over camera output
 * does need to be opaque — that is the one thing the viewfinder scrims and this
 * bar have in common — but it does not need to be the lightest surface in the
 * app to manage it. Black fill plus the app's hairline gives the same contrast
 * against an arbitrary kitchen counter while framing the panel the way every
 * other panel in the app is framed, so arriving here from the picker does not
 * feel like arriving in a different app.
 */
@Composable
private fun LookupBar(barcode: String?) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(Elevation.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spinner(size = 18.dp)
            Spacer(Modifier.width(Spacing.m))
            Column {
                Text(
                    "Looking it up…",
                    style = AppText.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (barcode != null) {
                    Text(
                        barcode,
                        style = AppText.bodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * *Match the package* — the one axis the serving sheet cannot derive.
 *
 * Only the bases this source actually published are offered. A disabled
 * segment was never the clearer answer: the note underneath already explains
 * why one is missing, and a product with neither side is rejected before it
 * reaches this screen.
 *
 * This block renders *inside* the serving sheet, above *How much*, so it is
 * framed exactly like the sheet's own sections — eyebrow, hairline, content —
 * and the choice is made with the sheet's own chips rather than with a
 * segmented control borrowed from the settings screens. One sheet, one idiom:
 * a scanned packet's "which side of the label do I trust" should not look like
 * a different class of question from "which serving did you eat".
 */
@Composable
private fun BasisBlock(
    product: BarcodeProduct,
    basis: NutritionBasis,
    onBasisChange: (NutritionBasis) -> Unit,
    source: BarcodeSource?,
) {
    val per100g = product.perHundredG
    val perServing = product.perServing
    Section(
        "Match the package",
        // The section's summary figure, per rule 1 — which source these two
        // sides came from is the fact that decides how much to trust them, so
        // it belongs on the head's baseline rather than three lines below the
        // control it qualifies.
        trailing = {
            if (source != null) {
                Text(
                    source.display,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            "If the pack prints nutrients per serving, pick that side — the published data and the label often disagree.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))
        val available = NutritionBasis.entries.filter {
            when (it) {
                NutritionBasis.Per100g -> per100g != null
                NutritionBasis.PerServing -> perServing != null
            }
        }
        // A two-way switch, so the segmented control every other two-way choice uses.
        ExclusiveButtonGroup(
            options = available,
            selected = basis,
            onSelect = onBasisChange,
            label = { option ->
                when (option) {
                    NutritionBasis.Per100g -> "Per 100 g"
                    NutritionBasis.PerServing -> "Per ${product.servingGrams.toInt()} g"
                }
            },
        )
        val note = basisNote(product)
        if (note != null) {
            Spacer(Modifier.height(Spacing.s))
            Text(
                note.text,
                style = AppText.bodyS,
                color = if (note.warning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private data class BasisNote(val text: String, val warning: Boolean)

/**
 * The caveat this product earns, if any — kept as a function so the branch is
 * readable rather than three nested `if`s inside a layout.
 */
private fun basisNote(product: BarcodeProduct): BasisNote? {
    val per100g = product.perHundredG
    val perServing = product.perServing
    if (per100g == null && perServing != null) {
        return BasisNote(
            "Only per-serving values were published — we'll scale to 100 g for storage.",
            warning = false,
        )
    }
    if (per100g != null && perServing != null && product.servingGrams > 0 &&
        per100g.calories > 0 && perServing.calories > 0
    ) {
        val ratio = (perServing.calories * 100.0 / product.servingGrams) / per100g.calories
        if (ratio !in 0.90..1.10) {
            return BasisNote(
                "These two sides disagree on this product. Trust the printed pack and pick the one whose calorie number matches it.",
                warning = true,
            )
        }
    }
    return null
}

/** Cached hits are already per-100 g, so there is no basis to pick — only a warning to give. */
@Composable
private fun StaleCacheNote(source: BarcodeSource?) {
    Text(
        "Offline — showing the last values we saw for this barcode${source?.let { " (${it.display})" } ?: ""}. They may be out of date.",
        style = AppText.bodyS,
        color = MaterialTheme.colorScheme.error,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotFoundSheet(
    barcode: String?,
    message: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAddManually: () -> Unit,
) {
    AppModalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                icon = Lucide.SearchX,
                title = "Product not found",
                message = message
                    ?: "OpenFoodFacts and USDA both came back empty for ${barcode ?: "this code"}.",
            )
            AppButton(
                text = "Add manually with this barcode",
                onClick = onAddManually,
                enabled = barcode != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.s))
            AppOutlinedButton(
                text = "Scan another",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Typing the code is the fallback for a label the camera cannot read — a torn
 * wrapper, a curved tin, a dark kitchen. It is a dialog rather than a screen
 * because it is twelve digits and a button.
 */
@Composable
private fun ManualCodeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val trimmed = code.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Enter barcode", style = AppText.titleL) },
        text = {
            AppTextField(
                value = code,
                onValueChange = { entered -> code = entered.filter { it.isDigit() }.take(14) },
                placeholder = { Text("8901234567890") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AppButton(
                text = "Look it up",
                onClick = { onSubmit(trimmed) },
                // EAN-8 is the shortest code any of the sources index.
                enabled = trimmed.length >= 8,
            )
        },
        dismissButton = { AppTextButton(text = "Cancel", onClick = onDismiss) },
    )
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = Lucide.ScanBarcode,
            title = "Camera access needed",
            message = "Scanning reads the barcode on the device. Nothing is recorded, and only the code itself is ever sent to look the product up.",
            action = { AppButton(text = "Allow camera", onClick = onGrant) },
        )
    }
}
