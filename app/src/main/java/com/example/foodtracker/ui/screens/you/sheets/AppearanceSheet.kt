package com.example.foodtracker.ui.screens.you.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sun
import com.example.foodtracker.data.ThemeMode
import com.example.foodtracker.ui.components.atoms.AppSheet
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.pressable
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/** Light / Dark / Match system as three tappable preview cards. */
@Composable
fun AppearanceSheet(
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSheet(title = "Appearance", onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ThemeMode.entries.forEach { option ->
                ThemeOption(
                    option = option,
                    selected = option == mode,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Text(
            "Light is the default. \"Match system\" follows your phone's dark-mode setting.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeOption(
    option: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "themeRing")
    // A tiny preview of each canvas: light card on grey, dark card on black, half and half.
    val (top, bottom) = when (option) {
        ThemeMode.Light -> Color(0xFFF4F4F7) to Color(0xFFFFFFFF)
        ThemeMode.Dark -> Color(0xFF0B0B0E) to Color(0xFF17171B)
        ThemeMode.System -> Color(0xFFF4F4F7) to Color(0xFF17171B)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(cardColor())
            .border(2.dp, ring, RoundedCornerShape(Radius.md))
            .semantics { role = Role.RadioButton; this.selected = selected }
            .pressable(onClick = onClick)
            .padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(top)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(Radius.sm)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm))
                    .background(bottom)
            )
        }
        Spacer(Modifier.height(Spacing.s))
        Icon(
            when (option) {
                ThemeMode.System -> Lucide.Smartphone
                ThemeMode.Light -> Lucide.Sun
                ThemeMode.Dark -> Lucide.Moon
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(option.displayName, style = AppText.label, color = scheme.onSurface)
    }
}
