package com.example.foodtracker.ui.screens.trends.components

import com.composables.icons.lucide.ChevronRight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Spacing

/** How long a chart takes to swap when the range changes — the generic 200ms. */
private const val RANGE_FADE_MS = 200

/**
 * What a chart's headline number means for the goal behind it.
 *
 * Three values, not a `Color`, because house rule 3 is a rule about *meaning*:
 * `primary` is "on track" and `error` is "past a limit", and nothing else on
 * the page is allowed to be coloured. Handing [ChartCard] a colour would let a
 * call site tint a number because it looked nice; handing it a verdict means
 * the colour is decided in exactly one place, below.
 */
enum class InsightTone {
    /** No verdict — a number that is merely a number. */
    Neutral,

    /** At or inside the goal. */
    OnTrack,

    /** Past the goal, in the direction that matters. */
    Over,
}

/**
 * The reading a chart section leads with — a number, not a sentence.
 *
 * This replaces the `String` the insight line used to be ("2 981 kcal avg ·
 * 513 over goal"). That sentence was correct and completely inaudible: `body`
 * is the quiet register, so the one fact each section exists to deliver was set
 * in the same type as the empty-state apology underneath it, and the page read
 * as five paragraphs with pictures. House rule 2 puts the number at `numberL`
 * under an `eyebrow` and trails the rest as a caption, which is the same
 * anatomy as Today's hero and stat rows — one rhythm down the whole app.
 *
 * Splitting the sentence into fields is what makes that possible: the screen's
 * insight builders can no longer hand over prose, because there is nowhere to
 * put it.
 *
 * @param label the eyebrow over the number — what the number *is* ("VS GOAL").
 *        Uppercased here, like `Section` does with its title.
 * @param value the number itself, already formatted by `grouped`/`kgText` so
 *        this type stays presentation-only.
 * @param unit trailing unit in the quiet size ("kcal", "ml").
 * @param caption what the number is measured against ("goal 2 400 kcal").
 *        Optional: a number with no goal behind it has nothing to say.
 * @param tone colours the *number*, never the caption — a verdict belongs to
 *        the figure that earned it, which is what `CalorieHero` does on Today
 *        when the day goes over.
 */
data class ChartInsight(
    val label: String,
    val value: String,
    val unit: String? = null,
    val caption: String? = null,
    val tone: InsightTone = InsightTone.Neutral,
)

/**
 * One section of Trends: a [com.example.foodtracker.ui.components.atoms.Section]
 * card with the window's summary in the head, the headline reading, the chart,
 * and footer links.
 *
 *  - [summary] — the head's right-hand figure ("2 981 kcal avg").
 *  - [insight] — the verdict as a number under the head.
 *  - [empty] — under two points, a single muted line replaces the chart.
 *  - [crossfadeKey] — when non-null the chart crossfades as it changes (pass
 *    the range); null for content that owns its own scroll or drag state.
 */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    insight: ChartInsight? = null,
    empty: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    crossfadeKey: Any? = null,
    footer: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Section(
        title = title,
        modifier = modifier,
        trailing = if (summary != null) {
            {
                Text(
                    summary,
                    style = AppText.bodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else null,
    ) {
        if (insight != null) {
            InsightLine(insight)
            Spacer(Modifier.height(Spacing.l))
        }

        if (empty != null) {
            EmptyChartLine(empty)
        } else if (crossfadeKey != null) {
            Crossfade(
                targetState = crossfadeKey,
                animationSpec = tween(RANGE_FADE_MS, easing = FastOutSlowInEasing),
                label = "chart",
            ) { _ ->
                // The key is read for its identity only; `content` closes over
                // the state it draws, which has already changed with the key.
                content()
            }
        } else {
            content()
        }

        val hasAction = actionLabel != null && onAction != null
        if (hasAction || footer != null) {
            Spacer(Modifier.height(Spacing.l))
            AppDivider()
            if (actionLabel != null && onAction != null) {
                SectionLink(text = actionLabel, onClick = onAction)
            }
            footer?.invoke(this)
        }
    }
}

/**
 * `VS GOAL` / `+513 kcal · goal 2 400 kcal`.
 *
 * Eyebrow above, number on the baseline, unit and caption trailing it — the
 * same three parts in the same order as `CalorieHero` and `StatRow` on Today.
 * The number is the only thing here allowed a colour, and only for a verdict:
 * see [InsightTone].
 */
@Composable
private fun InsightLine(insight: ChartInsight) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            insight.label,
            style = AppText.eyebrow,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                insight.value,
                style = AppText.numberL,
                color = when (insight.tone) {
                    InsightTone.Neutral -> scheme.onSurface
                    InsightTone.OnTrack -> scheme.secondary
                    InsightTone.Over -> scheme.error
                },
            )
            if (insight.unit != null) {
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    insight.unit,
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            if (insight.caption != null) {
                Spacer(Modifier.width(Spacing.s))
                Text(
                    insight.caption,
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
        }
    }
}

/**
 * The one shape a section's links take — an action, a disclosure, either way a
 * line of accent text with a real tap target.
 *
 * It was a centred `AppTextButton` inside the card: a pill-shaped control
 * floating in the middle of a hairline strip. Left-aligned accent text in the
 * section's own gutter is what Today's "See all nutrients" does, and the two
 * are the same gesture. The vertical padding is what keeps it a 48dp target
 * without a shape — house rule 3 allows the accent for a text link and nothing
 * pill-shaped.
 */
@Composable
fun SectionLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m + 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = AppText.label,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Icon(
            com.composables.icons.lucide.Lucide.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The one muted line a section shows instead of a chart.
 *
 * Deliberately not an `EmptyState` with its 72dp disc: that component is for a
 * whole screen with nothing on it, and five of them stacked down Trends would
 * be a page of illustrations saying nothing. One sentence per section is the
 * honest size of the problem — the chart will fill in on its own as days
 * accumulate, and this says which days are missing.
 */
@Composable
fun EmptyChartLine(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = AppText.bodyS,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}
