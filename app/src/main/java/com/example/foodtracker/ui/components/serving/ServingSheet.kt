package com.example.foodtracker.ui.components.serving

import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState
import com.example.foodtracker.ui.LocalAppContainer
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.data.FoodCatalogue
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.foodtracker.data.CookingOil
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.OilAmount
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.StyleOption
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Sentinel prefix on a [ServingVariant.label] marking the free-form
 * custom-grams mode. The remainder of the label is the grams value, kept
 * human-readable so the diary still renders sensibly.
 *
 * **This is load-bearing well beyond this package.** It is the one bit of state
 * that tells the log-write maths which multiplication to do: in custom mode
 * `variant.grams` *is* the gram total and `servings` is pinned to 1.0, so
 * `servings × variant.grams` stays correct in both modes and no caller needs a
 * branch. It is also persisted — `food_log.serving_label` on every entry ever
 * logged this way starts with it — so the prefix cannot change without a
 * migration, and the sheet must keep opening such an entry back in custom mode.
 */
const val CUSTOM_GRAMS_PREFIX = "Custom · "

/** True when this variant is the free-form gram amount rather than a real serving. */
val ServingVariant.isCustomGrams: Boolean get() = label.startsWith(CUSTOM_GRAMS_PREFIX)

/**
 * A custom-grams variant for [grams].
 *
 * `toInt()` rather than rounding, deliberately: this exact formatting is what
 * sits in `food_log.serving_label` for entries already in the database, and
 * re-opening one of those has to produce the same label it was written with.
 */
fun customGramsVariant(grams: Double): ServingVariant =
    ServingVariant("$CUSTOM_GRAMS_PREFIX${grams.toInt()} g", grams)

/** How long a preparation change stays flagged in the preview. */
private const val FLASH_MS = 2_000L

/**
 * The serving sheet, the densest surface in the app.
 *
 * It answers three questions about one food — *how much*, *how it's made*, and
 * *where does it go* — and it is the only place those are asked. Logging a
 * dosa, fixing yesterday's dosa and logging a scanned packet are
 * the same decision with different starting values, so they are the same sheet;
 * the barcode screen's bespoke serving editor and meal chips were ~200 lines
 * re-asking "how much" in a second dialect.
 *
 * **Why it is pinned at both ends.** It used to be one ~600dp scroll column
 * with the primary action at the very bottom, so on a short phone you scrolled
 * *past* the thing you came to tap, and the live calorie readout — the number
 * you are adjusting — could be off-screen while you dragged the slider. Header
 * and footer now stay; only the middle scrolls. The middle is given
 * `weight(1f, fill = false)` so a food with no preparation axis still produces
 * a short sheet instead of a tall one with a hole in it.
 *
 * **Why the meal picker is a footer chip** (decision 8): it arrives correct —
 * from the route that opened the picker, or the wall clock — on nearly every
 * log, and a full-width 4-way segmented control was spending ~80dp on a control
 * that is almost never touched, at the expense of the two that always are.
 *
 * **Why the preview flashes.** Tapping "Coconut" moves nothing a user can see:
 * calories and total fat are identical across oils *by design* (the oil-*amount*
 * row is the one that moves them, and it needs no flash because the header
 * figure changes under your thumb), which made the most domain-specific control
 * in the app look broken. The old answer was two
 * extra always-on rows (`showFatDetail`), which was the right instinct but
 * passive — you had to already know which rows to watch. Now the fields that
 * actually moved announce themselves for two seconds. See [LivePreview].
 *
 * **What the editorial pass changed** (docs/ui-house-rules.md): the three
 * headings became [Section]s — a standing head with a rule under it — the
 * variant and preparation chips lost their filled-accent selected state
 * (rule 3 — see `HapticFilterChip`), and the live preview's labels became
 * eyebrows over its numbers (rule 2). Nothing about the flow, the state or the
 * arithmetic moved; this sheet was already the right three questions in the
 * right order, it just asked them in the old app's voice.
 *
 * The three heads matter more here than anywhere else in the app. This is the
 * densest surface the app has — chips over a stepper over a slider over a
 * second chip row over an eight-cell grid — and before the rules there was
 * nothing but a 20dp gap telling the eye where one question ended and the next
 * began, so the whole middle read as one undifferentiated field of controls.
 *
 * [Section] is imported from `screens/today/components/` rather than copied:
 * rule 1 names that file as the one implementation, and a sheet that invents
 * its own eight-and-sixteen is how the app ended up with six cards last time.
 * The dependency points at a screen package, which is backwards, but a second
 * copy of the rule's geometry would be worse than an awkward import.
 *
 * State is hoisted in full: every caller already owns a view-model or a
 * `remember` holding these five axes, and the sheet writing to its own copy is
 * how a picker and the row it is editing drift apart.
 *
 * @param subtitle a trailing header line for callers with provenance to show —
 *        `"Lunch · 1:05 PM"` when editing an entry. Null while logging, where
 *        there is nothing true to say yet.
 * @param onDelete renders the destructive row under the footer. Null (the
 *        default) when there is nothing to delete, which is the logging case.
 * @param extraBlock an axis only one caller knows about, rendered above *How
 *        much* — either because it reprices everything below it (the barcode
 *        screen's per-100 g / per-serving basis) or because it says what the
 *        line *is* before the sheet says how much of it (the plan editor's
 *        slot). Either way it leads, and this file learns neither what a
 *        barcode is nor what a plan is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServingSheet(
    food: FoodEntity,
    meal: MealType,
    variant: ServingVariant,
    servings: Double,
    onVariantChange: (ServingVariant) -> Unit,
    onServingsChange: (Double) -> Unit,
    onMealChange: (MealType) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Add to diary",
    subtitle: String? = null,
    /** When it was eaten, a tile in the bottom bar. Null (planning) hides it. */
    eatenAt: java.time.LocalTime? = null,
    onEatenAtChange: (java.time.LocalTime) -> Unit = {},
    /**
     * The cooking fat to price this entry in. Null means "whatever this food
     * remembers", which is what every caller with no opinion should pass.
     */
    oil: CookingOil? = null,
    onOilChange: (CookingOil) -> Unit = {},
    /** Preparation option, for the few seeded foods that offer a choice. */
    style: StyleOption? = null,
    onStyleChange: (StyleOption) -> Unit = {},
    /**
     * How much of the cooking fat to price in. Null means "whatever this food
     * remembers", same as [oil]. Only read for foods on the oil-amount axis.
     */
    oilAmount: OilAmount? = null,
    onOilAmountChange: (OilAmount) -> Unit = {},
    /**
     * Whether the bottom bar offers the meal.
     *
     * False for the plan editor, which chooses between five slots rather than
     * four meals (see [com.example.foodtracker.data.PlanSlot]) and so renders
     * its own control in [extraBlock]. Two meal pickers on one sheet, one of
     * them unable to express the value the other holds, is worse than either.
     */
    showMealChip: Boolean = true,
    onDelete: (() -> Unit)? = null,
    /** Label for the destructive row. Only read when [onDelete] is non-null. */
    deleteText: String = "Remove from diary",
    extraBlock: (@Composable () -> Unit)? = null,
) {
    // Custom-grams mode is signalled by the sentinel label. In that mode
    // `variant.grams` IS the gram value the user dialled and servings is pinned
    // to 1.0, so the log-write maths (servings × variant.grams) is unchanged.
    val isCustom = variant.isCustomGrams
    val grams = if (isCustom) variant.grams else variant.grams * servings

    // Only foods with added cooking fat can be re-priced; for everything else
    // the picker is hidden and this resolves to the stored figures unchanged.
    val showsOil = food.usesCookingOil()
    val showsStyle = food.hasStyleAxis()
    val showsOilAmount = food.usesOilAmountAxis()
    val effectiveOil = oil ?: food.preselectedOil()
    val effectiveStyle = style ?: food.preselectedStyle()
    val effectiveOilAmount = oilAmount ?: food.preselectedOilAmount()
    val nutrients =
        food.nutrientsPer100g(effectiveOil, effectiveStyle, effectiveOilAmount) * (grams / 100.0)
    val made = remember(food.name) { FoodCatalogue.madeOf(food.name)?.takeIf { it.isNotBlank() } }
    // Today's targets, so the hero and tiles can say how much of the day this is.
    val profileStore = LocalAppContainer.current.profileStore
    val goals by remember(profileStore) { profileStore.profile.map(GoalCalculator::goalsFor) }
        .collectAsState(initial = null)

    // The flash baseline is captured in the chip's own click handler, where
    // `nutrients` is still the pre-change value — reconstructing "what it was"
    // from a later recomposition means racing the state change that caused it.
    var flash by remember { mutableStateOf<PrepFlash?>(null) }
    LaunchedEffect(flash?.token) {
        if (flash != null) {
            delay(FLASH_MS)
            flash = null
        }
    }
    fun flagPrepChange() {
        flash = PrepFlash(before = nutrients, token = (flash?.token ?: 0) + 1)
    }

    AppModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        // Opens compact — name, hero, a "What's in it" row and the inputs — and
        // the sheet is sized to that. Dragging up anywhere on it (or tapping the
        // row) opens the details, and the sheet grows to fit them.
        var showDetails by rememberSaveable { mutableStateOf(false) }
        val dragToOpen = with(LocalDensity.current) { DRAG_TO_OPEN.toPx() }
        // imePadding: typing grams in the bottom bar lifts it above the keyboard.
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(showDetails) {
                    if (showDetails) return@pointerInput
                    // Watches drags without consuming them, so dragging down
                    // still dismisses the sheet as usual.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var dy = 0f
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            dy += change.position.y - change.previousPosition.y
                            if (dy < -dragToOpen) {
                                showDetails = true
                                break
                            }
                        }
                    }
                }
                .navigationBarsPadding()
                .imePadding(),
        ) {
            SheetHeader(
                name = food.name,
                brand = food.brand,
                subtitle = subtitle,
                nutrients = nutrients,
                goals = goals,
                made = made,
            )
            AppDivider()

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            ) {
                if (extraBlock != null) {
                    extraBlock()
                    Spacer(Modifier.height(Spacing.xxl))
                }

                if (showDetails && (showsOil || showsStyle || showsOilAmount)) {
                    PageSection("How it's made") {
                        PreparationBlock(
                            food = food,
                            oil = effectiveOil,
                            style = effectiveStyle,
                            oilAmount = effectiveOilAmount,
                            onOilChange = { picked -> flagPrepChange(); onOilChange(picked) },
                            onStyleChange = { picked -> flagPrepChange(); onStyleChange(picked) },
                            onOilAmountChange = { picked ->
                                flagPrepChange(); onOilAmountChange(picked)
                            },
                        )
                    }
                    Spacer(Modifier.height(Spacing.xxl))
                }
                DetailsHeader(
                    expanded = showDetails,
                    trailing = "per ${grams.toInt()} g",
                    onToggle = { showDetails = !showDetails },
                )
                if (showDetails) {
                    Spacer(Modifier.height(Spacing.m))
                    LivePreview(
                        nutrients = nutrients,
                        previous = flash?.before,
                        goals = goals,
                        showFatDetail = showsOil || showsStyle,
                    )
                }
            }

            AppDivider()
            SheetFooter(
                food = food,
                variant = variant,
                servings = servings,
                onVariantChange = onVariantChange,
                onServingsChange = onServingsChange,
                meal = meal,
                onMealChange = onMealChange,
                showMeal = showMealChip,
                eatenAt = eatenAt,
                onEatenAtChange = onEatenAtChange,
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDelete = onDelete,
                deleteText = deleteText,
            )
        }
    }
}

/**
 * A titled block straight on the sheet, no card: the sheet is already a
 * surface, and cards inside it made the tiles a box in a box in a box.
 */
@Composable
private fun PageSection(
    title: String,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AppText.titleM, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            trailing?.invoke(this)
        }
        Spacer(Modifier.height(Spacing.m))
        content()
    }
}

/** How far an upward drag on the compact sheet goes before it opens the details. */
private val DRAG_TO_OPEN = 24.dp

/**
 * "What's in it", which also opens and closes the details: "Show ⌄" while
 * they are hidden, the serving weight and ⌃ once open.
 */
@Composable
private fun DetailsHeader(expanded: Boolean, trailing: String, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClickLabel = if (expanded) "Hide details" else "Show details", onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("What's in it", style = AppText.titleM, color = scheme.onSurface, modifier = Modifier.weight(1f))
        Text(if (expanded) trailing else "Show", style = AppText.bodyS, color = scheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
