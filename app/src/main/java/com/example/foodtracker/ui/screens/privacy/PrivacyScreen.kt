package com.example.foodtracker.ui.screens.privacy

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.ScanBarcode
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Sparkles
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.AppTopBar
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/**
 * What this app does and does not send anywhere.
 *
 * Written as blocks rather than paragraphs on purpose — a wall of policy prose
 * is not read, and everything here is short enough to be a headline, a row or a
 * label. Every claim is checked against the code: the only outbound calls in
 * the app are the three features in [ShareBlock] below, and the only
 * third-party SDK is ML Kit barcode scanning, which runs on-device.
 *
 * The two caveats are deliberately included. They are disclosures rather than
 * reassurances, and leaving them out would make the rest less true.
 *
 * **Every string on this screen is byte-identical to the version before the
 * restyle, and must stay that way**. This page
 * enumerates exactly which three network calls the app makes; a word changed
 * here is a privacy claim changed, and a restyle has no business doing that.
 * The editorial pass therefore moved nothing but containers, type and spacing:
 *
 *  - The eight cards are gone. What the page is actually made of is four
 *    questions — what never leaves, what leaves and when, what is kept where,
 *    and who receives it — so it is now four [Section]s, each a standing head
 *    over a rule, with the three outbound features as ruled rows inside the
 *    one they belong to (house rule 1). Eight boxes made eight equal-looking
 *    claims out of four questions and three answers.
 *  - The verdict's 48dp icon disc and the three bordered claim pills went with
 *    them. Rule 3 keeps colour and fill for data; "No ads" is a claim, and it
 *    reads as one in uppercase type without a ring drawn around it.
 *  - Every `uppercase()` on this page is applied at render time by [Section] or
 *    by the eyebrow style. The literals in this file are untouched, which is
 *    what makes "byte-identical" checkable by `git diff` rather than by eye.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit = {}) {
    Scaffold(
        topBar = { AppTopBar(title = "Privacy", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = Spacing.screenContent,
            // 24dp between sections — each one carries a rule under its head,
            // so the gap does not have to mark the boundary on its own.
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            item("verdict") { Verdict() }
            item("stays") {
                Section("Stays here") {
                    CheckLine("Everything you log — foods, water, weight")
                    Spacer(Modifier.height(Spacing.s))
                    CheckLine("Your profile, goals and targets")
                    Spacer(Modifier.height(Spacing.s))
                    CheckLine("Camera frames while scanning, read on this device")
                }
            }
            item("leaves") {
                Section("Leaves only when you tap") {
                    ShareBlock(
                        icon = Lucide.Search,
                        feature = "Search online",
                        badge = "Only on tap",
                        sends = "the words you typed",
                        to = "OpenFoodFacts · USDA",
                    )
                    AppDivider()
                    ShareBlock(
                        icon = Lucide.ScanBarcode,
                        feature = "Scan barcode",
                        badge = "Only on scan",
                        sends = "the barcode number",
                        to = "OpenFoodFacts · USDA",
                    )
                    AppDivider()
                    ShareBlock(
                        icon = Lucide.Sparkles,
                        feature = "AI autofill",
                        badge = "Off by default",
                        sends = "the food name, and any note you add",
                        to = "the provider you chose",
                    )
                    AppDivider()
                    Text(
                        "Nothing else rides along — no name, no diary, no device id.",
                        style = AppText.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
            }
            item("key") {
                Section("Your AI key") {
                    Text(
                        "Kept on this device, and deliberately left out of Android's cloud backup.",
                        style = AppText.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item("caveats") {
                Section("Two things worth knowing") {
                    Caveat(
                        title = "Android Backup",
                        body = "If it is switched on for your phone, Google copies this app's " +
                            "database along with every other app's. Turn it off in Android " +
                            "Settings → Google → Backup.",
                    )
                    AppDivider()
                    Caveat(
                        title = "Files you export",
                        body = "Backups and log exports are plain JSON in the folder you pick. " +
                            "Any app with storage access can read them.",
                    )
                }
            }
            item("links") { SourceLinks() }
        }
    }
}

/**
 * The one line someone reads if they read nothing else.
 *
 * It has no head and no rule, because it *is* the page's head: the sentence at
 * 28sp over three uppercase claims is the standing head for everything below,
 * and the first [Section]'s rule is the line under it. The filled
 * `primaryContainer` panel this started as, the outlined card it became, and
 * the 48dp cloud disc that sat on top of both were three different ways of
 * saying "this part is important" to a sentence that is already the largest
 * type on the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Verdict() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Your food diary never leaves this phone.",
            style = AppText.numberL,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.l))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            listOf("No account", "No analytics", "No ads").forEach { VerdictClaim(it) }
        }
    }
}

/**
 * One claim, stated quietly under the one that counts.
 *
 * It used to be a bordered pill. Three capsules in a row read as three things
 * you can tap, and none of them is; as eyebrow type they read as what they are,
 * which is a list of three nouns the app does not have.
 */
@Composable
private fun VerdictClaim(text: String) {
    Text(
        text,
        style = AppText.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CheckLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Lucide.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(text, style = AppText.body, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * One outbound feature, read as trigger → payload → recipient. The badge
 * carries the fact people actually want (is this on right now?), so it sits on
 * the feature's own line where it is seen before the detail.
 *
 * It is a row in a ruled list rather than a card of its own: the three of them
 * answer one question — "what leaves this phone?" — and belong under that one
 * head. The badge keeps its words and loses its fill; `Only on tap` in eyebrow
 * type says it without spending the accent on a label (rule 3).
 */
@Composable
private fun ShareBlock(
    icon: ImageVector,
    feature: String,
    badge: String,
    sends: String,
    to: String,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.m))
            Text(
                feature,
                style = AppText.titleM,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                badge,
                style = AppText.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.m))
        FlowLine(label = "Sends", value = sends)
        Spacer(Modifier.height(Spacing.xs))
        FlowLine(label = "To", value = to)
    }
}

/** `SENDS · the words you typed` — eyebrow left, value right of it, every time. */
@Composable
private fun FlowLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = AppText.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(value, style = AppText.body, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * These two are disclosures, not promises, and they keep the error tone that
 * says so — carried by the title alone now. The red dot beside it was a second
 * copy of the same signal, and the card around it a third.
 */
@Composable
private fun Caveat(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
        Text(title, style = AppText.titleM, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(Spacing.s))
        Text(
            body,
            style = AppText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceLinks() {
    val uriHandler = LocalUriHandler.current
    Section("Who receives it") {
        LinkRow("OpenFoodFacts privacy") {
            uriHandler.openUri("https://world.openfoodfacts.org/privacy")
        }
        AppDivider()
        LinkRow("USDA FoodData Central") {
            uriHandler.openUri("https://fdc.nal.usda.gov/")
        }
        AppDivider()
        Text(
            "Your AI provider's policy is linked next to the key field in Settings.",
            style = AppText.bodyS,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

/**
 * An outbound link as a row on a rule, not a bordered chip.
 *
 * `primary` on the label is one of the uses rule 3 keeps the accent for — a
 * text link — so the colour survives the box going away, and the trailing
 * glyph still says the tap leaves the app.
 */
@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppText.body,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Lucide.ExternalLink,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}
