package com.example.foodtracker.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.foodtracker.ui.components.atoms.AppTextButton
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * Optional nudge shown when reminders are enabled but the system can still
 * delay alarms under aggressive battery savers (OEM "app standby", App Sleep,
 * etc.). Tapping deeplinks to the per-app battery setting — the user decides
 * whether to allow the exemption. The block auto-hides once the app is exempt.
 *
 * **Why it is no longer a card at all.** De-filling it was the first half of
 * the fix: it used to be the one `tertiaryContainer` fill left on any page,
 * which made the mildest warning in the app its loudest surface. Keeping the
 * outline was the half that did not go far enough — on both screens that show
 * it, this sits directly under a list that is already divided by hairlines, so
 * the border was a second boundary doing the first one's job (house rule 1).
 * It is now a [Section] like everything else on those screens: a standing head,
 * a rule under it, and the explanation and its one action below.
 *
 * The tone moves with the framing. The title is no longer `tertiary`: rule 3
 * reserves colour for data and for over-limit readings, and urgency here is
 * genuinely low — nothing is broken, reminders will still fire, just possibly
 * late. Being a named section on the page is emphasis enough.
 *
 * Shared by the Reminders screen and onboarding's reminders section, which both
 * carried a private copy of it: one warning, worded once. The copy is unchanged
 * from the filled version — it is the accurate description of an OEM behaviour,
 * and a restyle has no standing to reword it.
 */
@Composable
fun BatteryOptCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Section("Make reminders reliable", modifier = modifier) {
        Text(
            "Android's battery saver can delay reminders by minutes or hours — especially on " +
                "OEM skins (Xiaomi / Oppo / Vivo / Realme / Samsung). Allow this app to ignore " +
                "battery optimisation so reminders fire on time.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        AppTextButton(text = "Open battery settings", onClick = onOpenSettings)
    }
}
