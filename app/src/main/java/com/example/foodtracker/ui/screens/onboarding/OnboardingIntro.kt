package com.example.foodtracker.ui.screens.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Drumstick
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.Wheat
import com.composables.icons.lucide.Wifi
import com.example.foodtracker.ui.components.atoms.Ring
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.staggerIn
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Rich
import com.example.foodtracker.ui.theme.Spacing
import com.example.foodtracker.ui.theme.dotTexture
import com.example.foodtracker.ui.theme.meshGradient
import kotlin.math.PI
import kotlin.math.sin

/*
 * The two opening pages of onboarding: what the app does, then how it treats
 * your data. Both are mostly picture — a little mock of the home screen, a
 * shield — with three short lines under it.
 */

@Composable
internal fun WelcomePage() {
    IntroPage(
        art = { WelcomeArt() },
        title = "Eat well.\nSee it clearly.",
        subtitle = "A food diary made for Indian meals that shows what your day really adds up to.",
        points = listOf(
            IntroPoint(Lucide.Utensils, Rich.Carbs, "Log in a tap", "500+ dishes with real servings — 1 dosa, 1 katori, 1 roti."),
            IntroPoint(Lucide.Sparkles, Rich.Micros, "See 30 nutrients", "Calories and macros, plus vitamins and minerals, against targets made for you."),
            IntroPoint(Lucide.Flame, Rich.Streak, "Build the habit", "Streaks and a heatmap show your rhythm at a glance."),
        ),
    )
}

@Composable
internal fun PrivacyPage() {
    IntroPage(
        art = { PrivacyArt() },
        title = "Private by design.",
        subtitle = "Your diary lives on this phone. There is no account to make.",
        points = listOf(
            IntroPoint(Lucide.Smartphone, Rich.Water, "Stays on your phone", "Profile, meals, water and weight are stored only on this device. No ads, no analytics."),
            IntroPoint(Lucide.Wifi, Rich.Fat, "Online only when you tap", "Food search, barcode lookup and AI autofill (off by default) send only what you typed, the barcode, or the food name."),
            IntroPoint(Lucide.CloudUpload, Rich.Fiber, "Your backups, your call", "Export to Google Drive or any folder as readable JSON, whenever you choose."),
        ),
        footnote = "Read the details any time in You → Privacy.",
    )
}

private data class IntroPoint(val icon: ImageVector, val rich: Rich, val title: String, val body: String)

@Composable
private fun IntroPage(
    art: @Composable () -> Unit,
    title: String,
    subtitle: String,
    points: List<IntroPoint>,
    footnote: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
    ) {
        Box(Modifier.staggerIn(0)) { art() }
        Spacer(Modifier.height(Spacing.xxl))
        Text(title, style = AppText.headline, color = scheme.onSurface, modifier = Modifier.staggerIn(1))
        Spacer(Modifier.height(Spacing.s))
        Text(subtitle, style = AppText.body, color = scheme.onSurfaceVariant, modifier = Modifier.staggerIn(1))
        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            points.forEachIndexed { i, p ->
                Row(Modifier.staggerIn(2 + i), verticalAlignment = Alignment.Top) {
                    RichDisc(p.icon, p.rich, 44.dp)
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, style = AppText.titleM, color = scheme.onSurface)
                        Text(p.body, style = AppText.bodyS, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (footnote != null) {
            Spacer(Modifier.height(Spacing.xl))
            Text(footnote, style = AppText.bodyS, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.l))
    }
}

/** A white icon on a rich-gradient circle. */
@Composable
private fun RichDisc(icon: ImageVector, rich: Rich, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(rich.brush).dotTexture(color = rich.ink.copy(alpha = 0.10f), spacing = 5.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = rich.ink, modifier = Modifier.size(size * 0.46f)) }
}

/** A gentle bob, phase-shifted per element so the tiles don't move in step. */
@Composable
private fun floatOffset(phase: Float): Float {
    val t by rememberInfiniteTransition(label = "float").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart), label = "floatT",
    )
    return sin((t + phase) * 2 * PI).toFloat()
}

/** A tiny, live-looking home screen: protein ring, two macro tiles, a streak pill. */
@Composable
private fun WelcomeArt() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(Radius.lg + 4.dp))
            .meshGradient()
            .dotTexture(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            val a = floatOffset(0f)
            MiniTile(Rich.Protein, Modifier.weight(1f).fillMaxHeight().graphicsLayer { translationY = a * 6.dp.toPx() }) {
                MiniHead(Lucide.Drumstick, "Protein", Rich.Protein)
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    Ring(0.72f, diameter = 92.dp, stroke = 10.dp, color = Rich.Protein.ink, trackColor = Rich.Protein.ink.copy(alpha = 0.15f)) {
                        Text("66 g", style = AppText.numberM, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                val b = floatOffset(0.33f)
                MiniTile(Rich.Carbs, Modifier.weight(1f).fillMaxWidth().graphicsLayer { translationY = b * 5.dp.toPx() }) {
                    MiniHead(Lucide.Wheat, "Carbs", Rich.Carbs)
                    Spacer(Modifier.weight(1f))
                    Text("186 g", style = AppText.numberM, color = MaterialTheme.colorScheme.onSurface)
                }
                val c = floatOffset(0.66f)
                MiniTile(Rich.Streak, Modifier.weight(1f).fillMaxWidth().graphicsLayer { translationY = c * 5.dp.toPx() }) {
                    MiniHead(Lucide.Flame, "Streak", Rich.Streak)
                    Spacer(Modifier.weight(1f))
                    Text("7 days", style = AppText.numberM, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun MiniTile(rich: Rich, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.md + 2.dp)).background(cardColor())
            .dotTexture(color = rich.ink.copy(alpha = 0.22f)).padding(Spacing.m),
        content = content,
    )
}

@Composable
private fun MiniHead(icon: ImageVector, label: String, rich: Rich) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = rich.ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Spacing.xs + 2.dp))
        Text(label, style = AppText.label, color = rich.ink)
    }
}

/** A shield on a gradient, with the three things the app never does floating round it. */
@Composable
private fun PrivacyArt() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(Radius.lg + 4.dp))
            .meshGradient()
            .dotTexture(color = scheme.onSurface.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        val a = floatOffset(0f)
        Box(Modifier.graphicsLayer { translationY = a * 6.dp.toPx() }) {
            RichDisc(Lucide.ShieldCheck, Rich.Weight, 96.dp)
        }
        GlassChip("No account", Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 36.dp), 0.2f)
        GlassChip("No ads", Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 64.dp), 0.5f)
        GlassChip("No tracking", Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), 0.8f)
    }
}

@Composable
private fun GlassChip(text: String, modifier: Modifier, phase: Float) {
    val f = floatOffset(phase)
    Row(
        modifier
            .graphicsLayer { translationY = f * 4.dp.toPx() }
            .clip(RoundedCornerShape(Radius.pill))
            .background(cardColor().copy(alpha = 0.8f))
            .padding(horizontal = Spacing.m, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Ban, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(Spacing.xs + 2.dp))
        Text(text, style = AppText.label, color = MaterialTheme.colorScheme.onSurface)
    }
}
