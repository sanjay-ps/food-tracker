package com.example.foodtracker.ui.components.serving

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Trash2
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.domain.MealTiming
import com.example.foodtracker.ui.components.TimePickerDialog
import com.example.foodtracker.ui.components.atoms.AppButton
import com.example.foodtracker.ui.components.atoms.softFill
import com.example.foodtracker.ui.components.servingParts
import com.example.foodtracker.ui.rememberAppHaptics
import com.example.foodtracker.ui.screens.today.components.icon
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.ui.theme.StatusOver
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.roundToInt

// Servings: quarter steps, ¼–5. Grams: 5 g steps by button, any 1–2000 by typing.
private const val SERVINGS_MIN = 0.25
private const val SERVINGS_MAX = 5.0
private const val SERVINGS_STEP = 0.25
private const val GRAMS_MIN = 1.0
private const val GRAMS_MAX = 2000.0
private const val GRAMS_STEP = 5.0

/** Every control in the bar is this tall (the button's height), so the rows line up. */
internal val ControlHeight = 52.dp

/**
 * The pinned bottom of the serving sheet: everything you touch to log, within
 * thumb reach, above the button that does it.
 *
 * ```
 * [ Serving: 1 piece ▾ ]  [ − 2 + ]      ← how much
 * [ Meal: Snack ▾ ]       [ Time ▾ ]     ← when
 * [          Add to diary           ]
 * ```
 *
 * What scrolls above is only reading: what's in it, and how it's made.
 * [onDelete] adds a quiet text row under the button for editing — two equally
 * weighted buttons is how an entry gets deleted by mistake.
 */
@Composable
internal fun SheetFooter(
    food: FoodEntity,
    variant: ServingVariant,
    servings: Double,
    onVariantChange: (ServingVariant) -> Unit,
    onServingsChange: (Double) -> Unit,
    meal: MealType,
    onMealChange: (MealType) -> Unit,
    showMeal: Boolean,
    eatenAt: LocalTime?,
    onEatenAtChange: (LocalTime) -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.s, bottom = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        AmountControls(food, variant, servings, onVariantChange, onServingsChange)
        if (showMeal || eatenAt != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (showMeal) MealTile(meal, onMealChange)
                if (eatenAt != null) TimeTile(eatenAt, onEatenAtChange)
            }
        }
        AppButton(text = confirmText, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        if (onDelete != null) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(deleteText, style = AppText.label)
            }
        }
    }
}

/** Which serving (a menu, with "Enter grams" last) beside how many of it. */
@Composable
private fun AmountControls(
    food: FoodEntity,
    variant: ServingVariant,
    servings: Double,
    onVariantChange: (ServingVariant) -> Unit,
    onServingsChange: (Double) -> Unit,
) {
    val isCustom = variant.isCustomGrams
    val grams = if (isCustom) variant.grams else variant.grams * servings
    var menu by remember { mutableStateOf(false) }
    // True once "Enter grams" is picked here, so the number opens for typing.
    var typeGrams by remember { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            FieldTile(
                label = "Serving",
                value = if (isCustom) "Grams" else variant.unitName(),
                icon = Lucide.Scale,
                onClick = { menu = true },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = scheme.surfaceContainerHigh) {
                food.variantsOrDefault().forEach { v ->
                    val picked = !isCustom && v.label == variant.label && v.grams == variant.grams
                    MenuLine(v.menuText(), picked) {
                        haptics.light()
                        onVariantChange(v)
                        // A new serving is a new unit, so the count starts over.
                        onServingsChange(1.0)
                        menu = false
                    }
                }
                MenuLine("Enter grams", isCustom) {
                    haptics.light()
                    // Seeded from what is on screen, so switching keeps the amount.
                    onVariantChange(customGramsVariant(grams.coerceIn(GRAMS_MIN, GRAMS_MAX)))
                    onServingsChange(1.0)
                    typeGrams = true
                    menu = false
                }
            }
        }
        // Half the row, like each tile under it, so the bar reads as an even 2 × 2 grid.
        Row(
            Modifier
                .weight(1f)
                .height(ControlHeight)
                .clip(RoundedCornerShape(Radius.md))
                .background(softFill()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepButton(Lucide.Minus, "Less", enabled = if (isCustom) variant.grams > GRAMS_MIN else servings > SERVINGS_MIN) {
                if (isCustom) onVariantChange(customGramsVariant((variant.grams - GRAMS_STEP).coerceAtLeast(GRAMS_MIN)))
                else onServingsChange((servings - SERVINGS_STEP).coerceAtLeast(SERVINGS_MIN))
            }
            Column(Modifier.widthIn(min = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (isCustom) {
                    GramsField(variant.grams, autoFocus = typeGrams) { onVariantChange(customGramsVariant(it)) }
                } else {
                    Text(quantityText(servings), style = AppText.numberM, color = scheme.onSurface, maxLines = 1)
                }
                Text(if (isCustom) "grams" else "${grams.toInt()} g", style = AppText.eyebrow, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            StepButton(Lucide.Plus, "More", enabled = if (isCustom) variant.grams < GRAMS_MAX else servings < SERVINGS_MAX) {
                if (isCustom) onVariantChange(customGramsVariant((variant.grams + GRAMS_STEP).coerceAtMost(GRAMS_MAX)))
                else onServingsChange((servings + SERVINGS_STEP).coerceAtMost(SERVINGS_MAX))
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    IconButton(onClick = { haptics.light(); onClick() }, enabled = enabled) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/**
 * The gram total as a number you type; the − and + beside it still step by 5 g.
 * Focusing it selects the whole number, so typing replaces it rather than
 * merging digits with it. [autoFocus] opens the keyboard straight away — used
 * right after "Enter grams" is picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GramsField(grams: Double, autoFocus: Boolean, onChange: (Double) -> Unit) {
    // Re-seeded whenever the value changes (typing or a step button), cursor at
    // the end; a cleared field stays empty until a digit arrives.
    var field by remember(grams.toInt()) {
        val t = grams.toInt().toString()
        mutableStateOf(TextFieldValue(t, TextRange(t.length)))
    }
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
    // Closing the keyboard (Back or Done) ends the edit, so the next tap
    // focuses afresh and selects the number again.
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }
    LaunchedEffect(focused) {
        if (focused) {
            // After the tap that focused it has placed its cursor.
            delay(SELECT_ALL_DELAY_MS)
            field = field.copy(selection = TextRange(0, field.text.length))
        }
    }
    BasicTextField(
        value = field,
        onValueChange = { typed ->
            if (typed.text.length > 4 || typed.text.any { !it.isDigit() }) return@BasicTextField
            field = typed
            typed.text.toIntOrNull()?.takeIf { it >= GRAMS_MIN }?.let { onChange(it.toDouble().coerceAtMost(GRAMS_MAX)) }
        },
        interactionSource = interaction,
        textStyle = AppText.numberM.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.width(64.dp).focusRequester(focus),
    )
}

private const val SELECT_ALL_DELAY_MS = 120L

@Composable
internal fun MenuLine(text: String, picked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(text, style = AppText.body, color = if (picked) scheme.onSurface else scheme.onSurfaceVariant) },
        trailingIcon = if (picked) {
            { Icon(Lucide.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) }
        } else null,
        onClick = onClick,
    )
}

@Composable
private fun RowScope.MealTile(meal: MealType, onMealChange: (MealType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.weight(1f)) {
        FieldTile(label = "Meal", value = meal.displayName, icon = meal.icon(), onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = scheme.surfaceContainerHigh) {
            MealType.entries.forEach { option ->
                DropdownMenuItem(
                    leadingIcon = { Icon(option.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { MenuText(option.displayName, option == meal) },
                    onClick = {
                        haptics.light()
                        onMealChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuText(text: String, picked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(text, style = AppText.body, color = if (picked) scheme.onSurface else scheme.onSurfaceVariant)
}

/** When it was eaten; opens a time picker. Late times show in orange with a moon. */
@Composable
private fun RowScope.TimeTile(time: LocalTime, onChange: (LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val late = MealTiming.isLate(time)
    FieldTile(
        label = if (late) "Time · late" else "Time",
        value = MealTiming.label(time),
        icon = if (late) Lucide.Moon else Lucide.Clock,
        tint = if (late) StatusOver else null,
        onClick = { picking = true },
        modifier = Modifier.weight(1f),
    )
    if (picking) {
        TimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            onDismiss = { picking = false },
            onConfirm = { h, m ->
                onChange(LocalTime.of(h, m))
                picking = false
            },
        )
    }
}

/** A tappable field: icon, a small label over its value, and a chevron saying it opens something. */
@Composable
internal fun FieldTile(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = {
            haptics.light()
            onClick()
        },
        modifier = modifier.fillMaxWidth().height(ControlHeight),
        shape = RoundedCornerShape(Radius.md),
        color = softFill(),
    ) {
        Row(Modifier.padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint ?: scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.s + 2.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = AppText.eyebrow, color = tint ?: scheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = AppText.titleM, color = tint ?: scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Lucide.ChevronDown, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

/** "1 piece (10 g)" → "1 piece": the serving's name; its weight shows under the count. */
private fun ServingVariant.unitName(): String = servingParts(label).first

private val HasWeight = Regex("""\d\s*(g|ml)\b""")

/** A menu line that says the weight once: "1 piece · 10 g", "100 g", "Medium · 200 g". */
internal fun ServingVariant.menuText(): String {
    val (unit, amount) = servingParts(label)
    return when {
        amount != null -> "$unit · $amount"
        HasWeight.containsMatchIn(label) -> label
        else -> "$label · ${grams.toInt()} g"
    }
}

/** 1.25 → "1¼", 0.5 → "½", 2 → "2": how people say a portion. */
internal fun quantityText(servings: Double): String {
    val whole = floor(servings).toInt()
    val frac = when (((servings - whole) * 4).roundToInt()) {
        1 -> "¼"
        2 -> "½"
        3 -> "¾"
        else -> ""
    }
    return if (whole == 0 && frac.isNotEmpty()) frac else "$whole$frac"
}
