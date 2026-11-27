package com.example.foodtracker.ui.components.sheets

import com.example.foodtracker.domain.MealTiming
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.foodtracker.data.CookingOil
import com.example.foodtracker.data.MealType
import com.example.foodtracker.data.OilAmount
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.StyleOption
import com.example.foodtracker.data.db.FoodLogEntity
import com.example.foodtracker.data.db.FoodLogWithFood
import com.example.foodtracker.ui.components.serving.ServingSheet
import com.example.foodtracker.ui.components.serving.isCustomGrams
import com.example.foodtracker.util.DateUtil
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Edit a logged entry.
 *
 * **This closes the biggest behavioural hole in the app.** Before it, the diary
 * row offered delete and nothing else: a wrong serving meant deleting the row
 * and logging the food again, losing the original timestamp in the process.
 *
 * It is the serving sheet, pre-filled from the row rather than from the food's
 * defaults, with the footer verb changed and a destructive action added.
 * Reusing the same sheet is the point — "change this entry" and "log this food"
 * are the same decision with a different starting value, and two UIs for one
 * decision is how the serving model drifts.
 *
 * This file is therefore almost entirely *state*: five values pulled off the
 * entity, and the one piece of arithmetic that turns them back into a row. The
 * sheet itself owns every pixel.
 *
 * **Which is why the editorial pass (docs/ui-house-rules.md) left it alone.**
 * There is nothing here to restyle: every rule the pass applies to editing an
 * entry — the sectioned middle, the monochrome variant chips, the eyebrow
 * labels over the live preview — lands through `ServingSheet`, and the right
 * outcome for this file is that it needed no diff to get them. A presentation
 * change that had to be made here as well would have meant the two callers had
 * drifted into two sheets again, which is the exact thing this file exists to
 * prevent.
 */
@Composable
fun EntryEditSheet(
    row: FoodLogWithFood,
    onDismiss: () -> Unit,
    onSave: (FoodLogEntity) -> Unit,
    onDelete: () -> Unit,
) {
    val food = row.food
    val log = row.log

    // Pre-fill from what was actually logged, not from the food's defaults.
    // The stored label is matched back to a known variant so the right pill
    // starts selected; an entry logged in custom-grams mode keeps its sentinel
    // label, which is what tells the sheet to open in that mode — the reason
    // ServingSheet treats that prefix as persisted data rather than a UI flag.
    var variant by remember(log.id) {
        val known = food.variantsOrDefault().firstOrNull { it.label == log.servingLabel }
        mutableStateOf(
            known ?: ServingVariant(
                label = log.servingLabel.ifBlank { ServingVariant.Default.label },
                grams = if (log.servings > 0) log.grams / log.servings else log.grams,
            )
        )
    }
    var servings by remember(log.id) { mutableStateOf(log.servings) }
    var meal by remember(log.id) { mutableStateOf(MealType.fromName(log.mealType)) }
    var oil by remember(log.id) { mutableStateOf(log.oil?.let { CookingOil.fromName(it) }) }
    var style by remember(log.id) {
        mutableStateOf(StyleOption.find(food.styleOptions, log.style))
    }
    var oilAmount by remember(log.id) { mutableStateOf(OilAmount.fromName(log.oilAmount)) }
    var eatenAt by remember(log.id) { mutableStateOf(MealTiming.timeOf(log.loggedAtMillis, DateUtil.zoneId())) }

    // Provenance, not decoration: the sheet is identical to the logging one, so
    // the time this entry was written is the only thing on screen saying you
    // are editing the 1:05 PM dosa rather than adding another.
    val loggedAt = remember(log.loggedAtMillis) {
        Instant.ofEpochMilli(log.loggedAtMillis)
            .atZone(DateUtil.zoneId())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    ServingSheet(
        food = food,
        meal = meal,
        variant = variant,
        servings = servings,
        onVariantChange = { variant = it },
        onServingsChange = { servings = it },
        onMealChange = { meal = it },
        onConfirm = {
            // Custom-grams mode pins servings to 1 and puts the typed gram
            // value in the variant, so one multiplication serves both modes.
            val isCustom = variant.isCustomGrams
            onSave(
                log.copy(
                    mealType = meal.name,
                    servings = if (isCustom) 1.0 else servings,
                    grams = if (isCustom) variant.grams else variant.grams * servings,
                    servingLabel = variant.label,
                    oil = oil?.name,
                    style = style?.label,
                    oilAmount = oilAmount?.name,
                    loggedAtMillis = MealTiming.toMillis(log.dateEpochDay, eatenAt, DateUtil.zoneId()),
                )
            )
            onDismiss()
        },
        onDismiss = onDismiss,
        confirmText = "Save changes",
        eatenAt = eatenAt,
        onEatenAtChange = { eatenAt = it },
        subtitle = "${MealType.fromName(log.mealType).displayName} · $loggedAt",
        oil = oil,
        onOilChange = { oil = it },
        style = style,
        onStyleChange = { style = it },
        oilAmount = oilAmount,
        onOilAmountChange = { oilAmount = it },
        onDelete = { onDelete(); onDismiss() },
    )
}
