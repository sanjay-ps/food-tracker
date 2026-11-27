package com.example.foodtracker.ui.components.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * Decimal entry.
 *
 * This was a private helper at the bottom of `AddFoodScreen`, called 34 times
 * in a single column. Promoting it is not tidying: the whole reason the
 * add-food form was ~2 500dp tall is that every one of those 34 fields was a
 * full-width `OutlinedTextField` with a floating label, and the fix — the
 * [compact] variant below — has to live wherever the field lives.
 *
 * **Filtering belongs here, not at the call site.** Every caller wants the same
 * rule (digits and one decimal point, kept as text so a half-typed "1." is not
 * destroyed mid-keystroke) and every caller used to re-type it. [onValueChange]
 * therefore receives the *filtered* string; a caller that stored the raw input
 * would be storing something the parser cannot read.
 *
 * The keyboard is [KeyboardType.Decimal] and the field is single-line, because
 * no number in this app is a paragraph.
 */

/** Digits and dots only. The parser downstream accepts nothing else. */
private fun filterNumeric(raw: String): String = raw.filter { it.isDigit() || it == '.' }

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    if (compact) {
        // The label sits *above* the box rather than floating inside it. At half
        // the screen's width a floating label has about 20 characters before it
        // ellipses, and "Vitamin B1 / Thiamin" is longer than that — the user
        // would be choosing between fields labelled "Vitamin B1 /…" and
        // "Vitamin B2 /…". Above the box the label gets the column's full width
        // and two lines if it needs them.
        Column(modifier) {
            Text(
                label,
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            NumericBox(
                value = value,
                onValueChange = onValueChange,
                unit = unit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        NumericBox(
            value = value,
            onValueChange = onValueChange,
            unit = unit,
            enabled = enabled,
            label = label,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * The box itself. Hairline outline in both states — the house rules give a text field no
 * fill, so the only thing that changes on focus is the outline's colour.
 *
 * The value renders in [AppText.numberM]: this is a number, and house rule 2's thesis is
 * that numbers are the loud thing. The unit rides along as a suffix in the
 * quiet colour so the two never get confused for one value.
 */
@Composable
private fun NumericBox(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    AppTextField(
        value = value,
        onValueChange = { onValueChange(filterNumeric(it)) },
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { Text(it, style = AppText.body) } },
        suffix = unit?.let {
            {
                Text(
                    it,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        textStyle = AppText.numberM,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/**
 * One field in a [NumberFieldGrid], as data.
 *
 * Declaring the fields as a list rather than as 34 call sites is what makes the
 * accordion counters on Add food possible at all: "9 fields · 0 filled" is
 * [filledCount] over the same list the section renders, so the header cannot
 * drift out of step with its contents the way two hand-maintained lists would.
 */
@Immutable
data class NumberFieldSpec(
    val label: String,
    val unit: String,
    val value: String,
    val onValueChange: (String) -> Unit,
)

/** How many of these the user (or the AI) has actually put a figure in. */
val List<NumberFieldSpec>.filledCount: Int
    get() = count { it.value.isNotBlank() }

/**
 * Two columns of [compact][NumberField] fields — the "two-column compact
 * number fields halve the page height".
 *
 * Rows are bottom-aligned, which is the detail that makes the grid read as a
 * grid: labels are one or two lines depending on the nutrient, and aligning on
 * the *tops* would leave the input boxes of a row at different heights. The
 * boxes are the thing the eye tracks down the page, so the boxes are what line
 * up.
 *
 * An odd field count ends on a half-width field with an empty column beside it,
 * rather than a stretched one: a lone full-width box at the bottom of a grid
 * looks like a different kind of field.
 */
@Composable
fun NumberFieldGrid(
    fields: List<NumberFieldSpec>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        fields.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { spec ->
                    NumberField(
                        label = spec.label,
                        value = spec.value,
                        onValueChange = spec.onValueChange,
                        unit = spec.unit,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
