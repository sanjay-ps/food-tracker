package com.example.foodtracker.ui.screens.you.sheets

import com.example.foodtracker.ui.components.atoms.AppSwitch
import com.example.foodtracker.ui.components.atoms.AppTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.example.foodtracker.data.AiModel
import com.example.foodtracker.data.AiProvider
import com.example.foodtracker.ui.components.ExclusiveButtonGroup
import com.example.foodtracker.ui.components.atoms.AppSheet
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * Optional AI nutrition autofill.
 *
 * A toggle, a provider picker, a model picker and a key field: four controls
 * that used to sit inline in a 662-line Settings wall, where the key field was
 * visible to anyone who scrolled past it on the way to the backup list.
 *
 * **The API key.** It is the user's own key, billed to the user, and the app
 * never has one of its own. Three things about it are deliberate and must stay:
 * it is rendered through [PasswordVisualTransformation] so it is masked on
 * screen exactly as it was before this page was rewritten; it is written
 * straight through to
 * [com.example.foodtracker.data.ProfileStore]'s separate AI DataStore on every
 * keystroke, so there is no draft copy to leak and nothing the user can think
 * they saved but did not; and it is never logged, never put in a crash
 * breadcrumb, and never read by anything but
 * [com.example.foodtracker.data.AiNutritionApi]. The "stays on this device"
 * line under the field is a promise the code has to keep. The editorial pass
 * moved type and spacing around it and changed none of that.
 *
 * The sheet is three [Section]s under the enable row — standing head, hairline,
 * content, 24dp to the next — which is the same structure as the page it opens
 * from. It used to be four control groups separated by 16dp of nothing, with
 * the key field arriving under a heading that looked exactly like the two
 * pickers' headings above it.
 */
@Composable
fun AiAutofillSheet(
    enabled: Boolean,
    provider: AiProvider,
    model: AiModel,
    apiKey: String,
    onEnabledChange: (Boolean) -> Unit,
    onProviderChange: (AiProvider) -> Unit,
    onModelChange: (AiModel) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AppSheet(title = "AI autofill", onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Enable AI autofill", style = AppText.body, color = scheme.onSurface)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Off by default. When on, the add-food screen can ask an AI to fill " +
                        "nutrition — the food name is sent to your chosen provider.",
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            AppSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        if (!enabled) return@AppSheet

        Spacer(Modifier.height(Spacing.xxl))
        Section(
            title = "Provider",
            trailing = {
                Text(provider.displayName, style = AppText.bodyS, color = scheme.onSurfaceVariant)
            },
        ) {
            ExclusiveButtonGroup(
                options = AiProvider.entries,
                selected = provider,
                onSelect = onProviderChange,
                modifier = Modifier.fillMaxWidth(),
                label = { it.displayName },
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section(
            title = "Model",
            trailing = {
                Text(model.label, style = AppText.bodyS, color = scheme.onSurfaceVariant)
            },
        ) {
            ExclusiveButtonGroup(
                options = provider.models,
                selected = model,
                onSelect = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = false,
                label = { it.label },
            )
            Spacer(Modifier.height(Spacing.m))
            Text(
                "${provider.defaultModel.label} is the cheapest and accurate enough for most " +
                    "foods — leave it selected to save money. Bigger models are for unusual " +
                    "dishes and skip the extra verify step (accurate on the first pass).",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
        Section("API key") {
            AppTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("${provider.displayName} API key") },
                singleLine = true,
                // Masked, as it has always been. Nothing about moving this
                // control into a sheet, or about restyling the sheet, is
                // allowed to put a key on screen in plain text.
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Your key stays on this device.",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
            val uriHandler = LocalUriHandler.current
            // A text link is one of the accent's sanctioned uses (house rule 3).
            Text(
                "Get a ${provider.displayName} API key at ${provider.keyHint} ↗",
                style = AppText.bodyS,
                color = scheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .clickable { uriHandler.openUri(provider.keyUrl) },
            )
        }
    }
}
