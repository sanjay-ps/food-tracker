package com.example.foodtracker.ui.components.serving

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Sparkles
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.foodtracker.data.CookingOil
import com.example.foodtracker.data.OilAmount
import com.example.foodtracker.data.StyleOption
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * *How it's made*: each preparation choice as a tile that opens its options —
 * the same tiles as the bottom bar, in an even grid.
 *
 * **The oil amount is its own choice, not more oil options.** "Cooked in" swaps one
 * fat for another at the same weight and so never moves calories; "Oil used"
 * changes the weight and so always does. Putting them on one row would make a
 * single tap sometimes re-price the dish and sometimes not, with nothing on
 * screen saying which. It appears for one dish for now — see
 * [OilAmount.AXIS_FOOD_UUIDS].
 *
 * Oil options stay restricted to [CookingOil.optionsFor] with the dish's own
 * default leading: the list is a claim about what this dish is plausibly cooked
 * in, not a catalogue of every fat in the model.
 *
 * Both handlers persist on tap rather than on confirm — see `rememberVariant`.
 * Someone who opens a dosa, switches to ghee roast and then backs out has still
 * told us something about their kitchen.
 */
@Composable
internal fun PreparationBlock(
    food: FoodEntity,
    oil: CookingOil,
    style: StyleOption?,
    oilAmount: OilAmount,
    onOilChange: (CookingOil) -> Unit,
    onStyleChange: (StyleOption) -> Unit,
    onOilAmountChange: (OilAmount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = buildList {
        if (food.usesCookingOil()) {
            val options = CookingOil.optionsFor(food.defaultOil)
            add(Choice("Cooked in", oil.label, Lucide.Droplet, options.map { it.label }, options.indexOf(oil)) { onOilChange(options[it]) })
        }
        // The only axis that moves calories, so it stays its own choice — see [OilAmount].
        if (food.usesOilAmountAxis()) {
            val options = OilAmount.entries
            add(Choice("Oil used", oilAmount.label, Lucide.Flame, options.map { it.label }, options.indexOf(oilAmount)) { onOilAmountChange(options[it]) })
        }
        if (food.hasStyleAxis()) {
            val options = food.styleOptions
            val current = options.indexOfFirst { it.label == style?.label }.coerceAtLeast(0)
            add(Choice("Made with", options[current].label, Lucide.Sparkles, options.map { it.label }, current) { onStyleChange(options[it]) })
        }
    }
    Column(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            choices.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    // A lone choice takes the full row rather than leaving a hole.
                    row.forEach { ChoiceTile(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One preparation axis: its label, the current pick, and the picks on offer. */
private class Choice(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val options: List<String>,
    val selected: Int,
    val onPick: (Int) -> Unit,
)

@Composable
private fun ChoiceTile(choice: Choice, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box(modifier) {
        FieldTile(label = choice.label, value = choice.value, icon = choice.icon, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = scheme.surfaceContainerHigh) {
            choice.options.forEachIndexed { i, text ->
                MenuLine(text, picked = i == choice.selected) {
                    choice.onPick(i)
                    open = false
                }
            }
        }
    }
}
