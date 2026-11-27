package com.example.foodtracker.ui.components

import com.example.foodtracker.ui.components.atoms.LocalBehind
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.example.foodtracker.ui.components.atoms.softFill
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.data.FoodCategory
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.components.atoms.grouped
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Pastel
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.Spacing

/**
 * One food as a grid tile: its emoji on a soft pastel square, then the name
 * (one line, ellipsised, so a row of tiles lines up), kcal, and the serving.
 * [rank] adds a "#12" chip in the corner.
 * [badge] sits in the tile's top-right corner — the favourite star, a source
 * tag, "Hidden".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodCard(
    food: FoodEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    rank: Int? = null,
    badge: @Composable BoxScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val pastel = FoodCategory.fromName(food.category).pastel()
    val fill = cardColor()
    // Tell small controls inside (the tags) what they sit on, so softFill() contrasts.
    CompositionLocalProvider(LocalBehind provides fill) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.lg))
            .background(fill)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.9f)
                .clip(RoundedCornerShape(Radius.md))
                .background(pastel.brush),
        ) {
            Text(food.emoji(), style = EmojiStyle, modifier = Modifier.align(Alignment.Center))
            if (rank != null) {
                Text(
                    "#$rank",
                    style = AppText.label,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.s)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(cardColor().copy(alpha = 0.85f))
                        .padding(horizontal = Spacing.s, vertical = 2.dp),
                )
            }
            Box(Modifier.align(Alignment.TopEnd), content = badge)
        }
        Spacer(Modifier.height(Spacing.s))
        Column(Modifier.padding(horizontal = Spacing.xs)) {
            // One line, ellipsised: two reserved lines left a gap under every short name.
            Text(
                food.name,
                style = AppText.titleM,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text("${grouped(food.servingCalories())} kcal", style = AppText.label, color = scheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(Spacing.s))
            // "1 bowl (200 g)" reads as two facts, so it shows as two tags.
            val (unit, amount) = servingParts(food.servingLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (!food.brand.isNullOrBlank()) Tag(food.brand, Modifier.weight(1f, fill = false))
                Tag(unit, Modifier.weight(1f, fill = false))
                if (amount != null) Tag(amount)
            }
        }
        }
    }
}

/** A [FoodCard]-shaped stand-in, gently pulsing, while the first page of foods loads. */
@Composable
fun FoodCardPlaceholder(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "placeholder").animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val fill = cardColor()
    // The bars sit on the card, so softFill() must contrast with the card, as in FoodCard.
    CompositionLocalProvider(LocalBehind provides fill) {
    val bar = softFill()
    Column(
        modifier
            .graphicsLayer { alpha = pulse }
            .clip(RoundedCornerShape(Radius.lg))
            .background(fill)
            .padding(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.9f).clip(RoundedCornerShape(Radius.md)).background(bar))
        Spacer(Modifier.height(Spacing.s))
        Column(Modifier.padding(horizontal = Spacing.xs)) {
            Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(Radius.pill)).background(bar))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(Radius.pill)).background(bar))
            Spacer(Modifier.height(Spacing.s))
            Box(Modifier.fillMaxWidth(0.55f).height(18.dp).clip(RoundedCornerShape(Radius.pill)).background(bar))
        }
    }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppText.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(softFill())
            .padding(horizontal = Spacing.s, vertical = 3.dp),
    )
}

private val ServingSplit = Regex("""^(.*?)\s*\(([^()]+)\)\s*$""")

/** "1 bowl (200 g)" → ("1 bowl", "200 g"); a label without a bracketed amount stays whole. */
internal fun servingParts(label: String): Pair<String, String?> {
    val m = ServingSplit.find(label) ?: return label to null
    return m.groupValues[1].ifBlank { label } to m.groupValues[2]
}

/** An emoji used as an illustration, not as text. */
private val EmojiStyle = TextStyle(fontSize = 40.sp, lineHeight = 48.sp)

fun FoodCategory.pastel(): Pastel = when (this) {
    FoodCategory.SouthIndian -> Pastel.Peach
    FoodCategory.NorthIndian -> Pastel.Rose
    FoodCategory.Snack -> Pastel.Sand
    FoodCategory.Beverage -> Pastel.Sky
    FoodCategory.Dairy -> Pastel.Lavender
    FoodCategory.Fruit -> Pastel.Mint
    FoodCategory.Vegetable -> Pastel.Teal
    FoodCategory.Grain -> Pastel.Sand
    FoodCategory.Protein -> Pastel.Rose
    FoodCategory.Packaged -> Pastel.Lavender
    FoodCategory.Custom -> Pastel.Sky
    FoodCategory.Other -> Pastel.Sand
}

/**
 * A picture for any food without a photo library or a network call: the first
 * keyword in its name that we know, else its category's emoji. Order matters —
 * specific dishes ("mango pickle", "egg dosa") come before their ingredients.
 */
fun FoodEntity.emoji(): String {
    val n = name.lowercase()
    return FoodEmoji.firstOrNull { (keys, _) -> keys.any { it in n } }?.second
        ?: FoodCategory.fromName(category).emoji()
}

private val FoodEmoji: List<Pair<List<String>, String>> = listOf(
    listOf("pickle") to "🥫",                       // canned
    listOf("podi") to "🌶️",                   // chilli
    listOf("payasam", "kheer", "kesari", "sweet pongal") to "🍮",
    listOf("biryani") to "🍛",
    listOf("egg dosa") to "🍳",
    listOf("idiyappam") to "🍜",
    listOf("vada pav", "pav bhaji") to "🍔",
    listOf("banana chips") to "🍟",
    listOf("ragi mudde") to "🍙",
    listOf("chicken", "tandoori") to "🍗",
    listOf("sardine", "maththi", "fish") to "🐟",
    listOf("egg") to "🥚",
    listOf("paneer") to "🧀",
    listOf("idli") to "🍘",
    listOf("dosa", "uttapam", "pesarattu", "adai", "appam") to "🥞",
    listOf("paniyaram", "vada", "vadai", "bonda", "bajji", "pakora") to "🧆",
    listOf("samosa", "pani puri") to "🥟",
    listOf("murukku", "thattai", "mixture", "khakhra") to "🥨",
    listOf("roti", "chapati", "paratha", "parotta", "naan", "poori") to "🫓",
    listOf("rice", "pongal", "puttu", "bath", "sadam", "pulihora") to "🍚",
    listOf("upma", "poha", "oats", "bhel") to "🥣",
    listOf("chutney", "curd", "dahi") to "🥣",
    listOf("sprouted") to "🌱",
    listOf("sambar", "rasam", "kuzhambu", "kootu", "dal", "rajma", "chole", "lobia", "chana", "gram", "moong", "urad", "soya", "sundal", "makhani") to "🍲",
    listOf("poriyal", "usili", "avial", "masiyal", "gobi", "bhindi masala", "kurma", "stew", "curry") to "🥘",
    listOf("dhokla") to "🍞",
    listOf("papad") to "🥠",
    listOf("coffee") to "☕",
    listOf("tea", "chai") to "🍵",
    listOf("coconut") to "🥥",
    listOf("buttermilk", "lassi", "milk") to "🥛",
    listOf("juice", "sugarcane") to "🧃",
    listOf("lemon", "nimbu", "lime") to "🍋",
    listOf("ghee", "butter") to "🧈",
    listOf("almond", "cashew", "peanut") to "🥜",
    listOf("seeds") to "🌰",
    listOf("honey") to "🍯",
    listOf("sugar") to "🍬",
    listOf("apple", "pomegranate") to "🍎",
    listOf("banana") to "🍌",
    listOf("grape") to "🍇",
    listOf("guava") to "🍐",
    listOf("jackfruit", "papaya") to "🍈",
    listOf("mango") to "🥭",
    listOf("orange", "mosambi") to "🍊",
    listOf("pineapple") to "🍍",
    listOf("sapota", "chikoo") to "🥝",
    listOf("watermelon") to "🍉",
    listOf("tomato") to "🍅",
    listOf("onion") to "🧅",
    listOf("potato") to "🥔",
    listOf("carrot", "beetroot") to "🥕",
    listOf("cauliflower", "cabbage") to "🥦",
    listOf("brinjal", "eggplant") to "🍆",
    listOf("cucumber", "gourd", "ladyfinger") to "🥒",
    listOf("spinach", "keerai", "moringa", "drumstick", "beans") to "🥬",
    listOf("flour", "atta") to "🌾",
)

/** The fallback picture for a food whose name matched nothing above. */
fun FoodCategory.emoji(): String = when (this) {
    FoodCategory.SouthIndian -> "🥞"
    FoodCategory.NorthIndian -> "🍛"
    FoodCategory.Snack -> "🍪"
    FoodCategory.Beverage -> "🥤"
    FoodCategory.Dairy -> "🥛"
    FoodCategory.Fruit -> "🍎"
    FoodCategory.Vegetable -> "🥦"
    FoodCategory.Grain -> "🌾"
    FoodCategory.Protein -> "🥚"
    FoodCategory.Packaged -> "📦"
    FoodCategory.Custom -> "✏️"
    FoodCategory.Other -> "🍽️"
}
