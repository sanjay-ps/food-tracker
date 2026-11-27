package com.example.foodtracker.ui.components.atoms

import kotlin.math.roundToInt
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.ui.theme.Sizes
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.AppTheme
import com.example.foodtracker.ui.theme.Radius

/**
 * Standard inputs. Every text box, switch and slider in the app goes through
 * these, so they look and behave the same everywhere.
 *
 *  - [AppTextField]: a soft filled box, 16dp corners, no underline; the label
 *    floats inside; an ink ring appears on focus.
 *  - [AppSearchField]: the pill search box with a clear button.
 *  - [AppSwitch]: ink track when on, no thumb icon.
 *  - [AppSlider]: an 8dp rounded track with a white, ringed thumb.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = AppText.body,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(Radius.md),
    focusRequester: FocusRequester? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val ring by animateColorAsState(
        when {
            isError -> scheme.error
            focused -> scheme.primary
            else -> Color.Transparent
        },
        label = "fieldRing",
    )
    val fill = softFill()
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .border(1.5.dp, ring, shape),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = source,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill.copy(alpha = 0.5f),
            errorContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedLabelColor = scheme.onSurfaceVariant,
            cursorColor = scheme.primary,
        ),
    )
}

/** The pill search box: magnifier, placeholder, and a clear button once typed in. */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    AppTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = AppText.body) },
        leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Lucide.X, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Radius.pill),
    )
}

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = AppTheme.extras.isDark
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = if (dark) scheme.onSurfaceVariant else Color.White,
            uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * The app slider: a thick, full-width bar that fills in ink up to the value,
 * with a small grip line at the fill's edge. Tap anywhere to jump there, or
 * drag along it. It only claims sideways drags, so a list still scrolls when
 * a finger lands on it. Prefer [com.example.foodtracker.ui.components.HapticSlider],
 * which adds a tick as the value crosses each step.
 *
 * @param steps the number of stops between the ends (0 = continuous), as in
 *   Material's slider.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    var width by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(onValueChange)
    fun valueAt(x: Float): Float {
        if (width <= 0 || span <= 0f) return value
        val raw = valueRange.start + (x / width).coerceIn(0f, 1f) * span
        if (steps <= 0) return raw
        val step = span / (steps + 1)
        return (valueRange.start + ((raw - valueRange.start) / step).roundToInt() * step)
            .coerceIn(valueRange.start, valueRange.endInclusive)
    }
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.touchTarget)
            .onSizeChanged { width = it.width }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        latest(target.coerceIn(valueRange.start, valueRange.endInclusive))
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange, steps) {
                if (enabled) detectTapGestures { latest(valueAt(it.x)) }
            }
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = enabled,
                state = rememberDraggableState { delta ->
                    dragX += delta
                    latest(valueAt(dragX))
                },
                onDragStarted = { start ->
                    dragX = start.x
                    latest(valueAt(dragX))
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(SliderBarHeight)
                .clip(shape)
                .background(softFill()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .widthIn(min = SliderBarHeight)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (enabled) scheme.primary else scheme.outline),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(end = Spacing.m)
                        .size(width = 3.dp, height = 12.dp)
                        .clip(shape)
                        .background(scheme.onPrimary.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

/** Thick enough to read as a bar, not a line. */
private val SliderBarHeight = 28.dp
