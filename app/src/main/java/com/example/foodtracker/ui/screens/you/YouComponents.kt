package com.example.foodtracker.ui.screens.you

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import com.example.foodtracker.ui.components.atoms.AppSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.UserRound
import com.example.foodtracker.ui.components.atoms.AppOutlinedButton
import com.example.foodtracker.ui.components.atoms.PastelIcon
import com.example.foodtracker.ui.components.atoms.RoundIconButton
import com.example.foodtracker.ui.components.atoms.cardColor
import com.example.foodtracker.ui.theme.Pastel
import com.example.foodtracker.ui.theme.Radius
import com.example.foodtracker.ui.theme.meshGradient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodtracker.data.AllNutrientFields
import com.example.foodtracker.data.Goal
import com.example.foodtracker.data.NutrientCategory
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.UserProfile
import com.example.foodtracker.domain.GoalCalculator
import com.example.foodtracker.domain.GoalProgress
import com.example.foodtracker.ui.components.atoms.AppDivider
import com.example.foodtracker.ui.components.atoms.Meter
import com.example.foodtracker.ui.components.atoms.NBSP
import com.example.foodtracker.ui.components.atoms.formatValue
import com.example.foodtracker.ui.components.atoms.Section
import com.example.foodtracker.ui.theme.AppText
import com.example.foodtracker.ui.theme.Sizes
import com.example.foodtracker.ui.theme.Spacing
import kotlin.math.round

/**
 * The molecules the You slice is built from, rebuilt against
 * `docs/ui-house-rules.md`.
 *
 * They live beside the three screens rather than in `ui/components/` because
 * every one of them is about *this* page: a profile masthead, a target row, a
 * settings row. Promoting them to the shared component layer would be claiming
 * a second caller that does not exist, which is how the old codebase ended up
 * with six implementations of a card.
 *
 * **What changed in the editorial pass.** Every one of these used to live
 * inside an `AppCard`, and the owner's verdict on the whole rewrite — "all ui
 * is new but still felt like old ui" — is mostly about that: a column of
 * full-width rounded outlines on black is the anatomy the old app already had,
 * so tidying what was inside the outlines changed nothing a user could see.
 *
 * House rule 1 replaces the outline with a standing head and a hairline *under*
 * the head — [Section], shared with Today — and emphatically not with a bigger
 * gap: the rule is what tells the eye a section has ended, and it does it in
 * 1dp instead of 32. That is why there is no closing divider anywhere in this
 * file. The rule belongs to the heading, not to a box drawn round the content,
 * so the content stays open to the page and bleeds to the gutter.
 */

/**
 * Macros + minerals + vitamins, plus water, which has a goal but no field.
 *
 * Counts scored fields only. This list is called "targets", and an untargeted
 * field has none — dietary cholesterol in here would render its goal of 0.0 as
 * a flat "0 mg", which reads as a target of zero rather than as the absence of
 * one. See [com.example.foodtracker.data.NutrientTarget.Untargeted].
 */
val TARGET_COUNT: Int = AllNutrientFields.count { it.scored } + 1

/**
 * How wide a settings row's right-hand value is allowed to get.
 *
 * A size, not a gap, so house rule 5 leaves it as a raw `.dp`. It exists
 * because `reminderSummary` can be as long as `Meals · Water · Weigh-in`:
 * without a cap the value wins the row and ellipsises the label, which is the
 * one thing in the row that must always be readable.
 */
private val SETTING_VALUE_MAX = 180.dp

// ---- Profile --------------------------------------------------------------

/**
 * Who the user is, restated as a masthead under a rule.
 *
 * The card, the avatar disc and the emoji inside it are all gone. The disc was
 * `IconCircle` with a `primaryContainer` fill, which house rule 3 does not
 * allow for chrome, and its content was one of six emoji picked by sex and age
 * bracket, which house rule 4 does not allow at all. Neither carried
 * information: the name directly beside it says who this is, and the emoji said
 * only what the two rows of numbers underneath already say.
 *
 * The goal line has moved up into the head's `Section`'s `trailing` slot, which is
 * where house rule 1 now puts every section's summary figure — so `PROFILE …
 * Lose · 0.50 kg/week` reads on one baseline and the body below the rule is
 * just the name and the numbers.
 */
@Composable
fun ProfileHeader(
    profile: UserProfile,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val initials = profile.name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg + 4.dp))
            .meshGradient(animate = false)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (profile.name.isBlank()) {
                    Icon(Lucide.UserRound, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
                } else {
                    Text(initials, style = AppText.titleL, color = scheme.onPrimary)
                }
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { "Welcome" },
                    style = AppText.titleL,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(goalLine(profile), style = AppText.bodyS, color = scheme.onSurfaceVariant)
            }
            RoundIconButton(icon = Lucide.Pencil, contentDescription = "Edit profile & goals", onClick = onEdit)
        }
        Spacer(Modifier.height(Spacing.l))
        // Equal-height rows; activity is prose, so it gets a full-width row
        // of its own instead of squeezing into a number-sized tile.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            GlassStat("Age", profile.ageYears.toString(), "yrs", Modifier.weight(1f).fillMaxHeight())
            GlassStat("Height", profile.heightCm.toString(), "cm", Modifier.weight(1f).fillMaxHeight())
            GlassStat("Weight", "%.1f".format(profile.weightKg), "kg", Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(Spacing.s))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            GlassStat("BMR", formatValue(GoalCalculator.bmr(profile)), "kcal", Modifier.weight(1f).fillMaxHeight())
            GlassStat("TDEE", formatValue(GoalCalculator.tdee(profile)), "kcal", Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(Spacing.s))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(cardColor().copy(alpha = 0.62f))
                .padding(horizontal = Spacing.m, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activity", style = AppText.eyebrow, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.m))
            Text(
                profile.activityLevel.displayName,
                style = AppText.label,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A frosted tile on the profile gradient. */
@Composable
private fun GlassStat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(cardColor().copy(alpha = 0.62f))
            .padding(horizontal = Spacing.m, vertical = Spacing.s + 2.dp)
    ) {
        Text(label, style = AppText.eyebrow, color = scheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = AppText.numberM, color = scheme.onSurface, maxLines = 1)
            if (unit.isNotEmpty()) Text(NBSP + unit, style = AppText.bodyS, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** `Lose · 0.50 kg/week`, or just `Maintain`. */
fun goalLine(profile: UserProfile): String = buildString {
    append(profile.goal.displayName)
    if (profile.goal != Goal.Maintain) {
        append(" · ")
        append("%.2f".format(profile.weightChangeRate.kgPerWeek))
        append(" kg/week")
    }
}

/**
 * Where the weight is now and where it is going — the one place on You the
 * accent is allowed.
 *
 * House rule 3 reserves `primary` for data that is on track, and this is the
 * page's only genuine progress reading, so it keeps its mint fill while the
 * selected-pill and tinted-row uses elsewhere lose theirs.
 *
 * The two weights used to be `bodyS` sat either side of the bar, which made the
 * numbers the smallest thing in a block that exists to compare two numbers.
 * They are `StatBlock`s now — the same eyebrow-over-numeral unit as the body
 * above — and the meter spans the full width underneath them instead of
 * fighting them for it. How far there is left to go is this section's summary
 * figure, so it sits in the head's trailing slot with every other total.
 *
 * [fraction] is null when there is no honest way to fill the bar — see
 * [YouUiState.goalFraction]. The meter still draws, empty: an empty track
 * between two real numbers reads as "not started", which is true, whereas
 * hiding the row entirely loses the two weights as well.
 */
@Composable
fun GoalSection(
    profile: UserProfile,
    progress: GoalProgress,
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val goalWeight = profile.goalWeightKg ?: profile.weightKg
    val done = progress.deltaKg <= 0.01
    val rate = profile.weightChangeRate.kgPerWeek
    Section(
        title = "Goal",
        modifier = modifier,
        trailing = {
            Text(
                if (done) {
                    "On target"
                } else {
                    "%.1f kg to %s".format(
                        progress.deltaKg,
                        if (profile.goal == Goal.Lose) "lose" else "gain",
                    )
                },
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatBlock("Now", "%.1f".format(profile.weightKg), "kg", Modifier.weight(1f))
            StatBlock("Target", "%.1f".format(goalWeight), "kg", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.l))
        Meter(fraction = fraction ?: 0f, color = scheme.secondary, height = 8.dp)
        if (!done && rate > 0.0 && progress.weeksRemaining > 0.0) {
            Spacer(Modifier.height(Spacing.m))
            Text(
                "About ${weeksLabel(progress.weeksRemaining)} at this rate",
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Weeks, rounded into whatever unit stops it reading as false precision. */
fun weeksLabel(weeks: Double): String {
    val rounded = round(weeks * 10) / 10.0
    if (rounded < 1.0) return "less than a week"
    if (rounded < 6.0) return "${"%.0f".format(rounded)} week${if (rounded < 1.5) "" else "s"}"
    val months = rounded / 4.345
    return "${"%.0f".format(round(months))} month${if (months < 1.5) "" else "s"}"
}

// ---- Stat blocks ----------------------------------------------------------

/**
 * Eyebrow label over a value, three to a row.
 *
 * The value style is [AppText.numberL] now rather than `numberM`. House rule 2
 * puts `numberM` on dense grids only, and this is not one: there are six of
 * these on the whole page and they are the only numbers in the profile block.
 * At 18sp against an 11sp label the pair read as two sizes of small text; at
 * 28sp it reads as a figure with a caption, which is the whole character of the
 * direction the app chose.
 *
 * [maxLines] defaults to 1 because a wrapped numeral is a layout bug, not a
 * long value — the caller raises it only for the activity phrase.
 */
@Composable
fun StatBlock(label: String, value: Double, unit: String, modifier: Modifier = Modifier) {
    StatBlock(label = label, value = formatValue(value), unit = unit, modifier = modifier)
}

/** [StatBlock] for a value that is already a string — a word, or a fixed decimal. */
@Composable
fun StatBlock(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = AppText.numberL,
    maxLines: Int = 1,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            label,
            style = AppText.eyebrow,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = valueStyle,
                color = scheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (unit.isNotEmpty()) {
                Text(NBSP + unit, style = AppText.bodyS, color = scheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Target rows ----------------------------------------------------------

/**
 * How loud a [TargetRow] is allowed to be.
 *
 * The same row renders the four headline targets on You and all thirty-one in
 * the sheet, and house rule 2 wants opposite things of those two: `numberL` for
 * a short list of figures that are the point of the section, `numberM` for a
 * dense list you scan. One renderer with two registers beats two renderers that
 * drift.
 */
enum class TargetEmphasis { Headline, Dense }

/**
 * `CALORIES — 2 100 kcal`, or `Calories  2 100 → 2 040 kcal` when [previous]
 * differs — the live preview on the edit page.
 *
 * Label left, number right, every time: house rule 2's row rhythm, and the same
 * one `StatRow` uses on Today. A [TargetEmphasis.Headline] row carries an
 * uppercase eyebrow because at that size the label would otherwise compete with
 * the figure; a dense row keeps a `body` label, because thirty-one uppercase
 * nutrient names in a column is a wall, not an eyebrow.
 */
@Composable
fun TargetRow(
    label: String,
    value: Double,
    unit: String,
    modifier: Modifier = Modifier,
    previous: Double? = null,
    emphasis: TargetEmphasis = TargetEmphasis.Dense,
) {
    val scheme = MaterialTheme.colorScheme
    val headline = emphasis == TargetEmphasis.Headline
    val changed = previous != null && kotlin.math.abs(previous - value) >= 0.5
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (headline) Spacing.m else Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (headline) AppText.eyebrow else AppText.body,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.s))
        Row(verticalAlignment = Alignment.Bottom) {
            if (changed) {
                Text(
                    "${formatValue(previous!!)} → ",
                    style = AppText.bodyS,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                formatValue(value),
                style = if (headline) AppText.numberL else AppText.numberM,
                // The accent means "this moved", which is data about the edit
                // in progress — rule 3's one sanctioned use of it here.
                color = if (changed) scheme.tertiary else scheme.onSurface,
            )
            if (unit.isNotEmpty()) {
                Text(NBSP + unit, style = AppText.bodyS, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Every target the profile produces, grouped the way the data is grouped.
 * Shared by the all-targets sheet and the edit page's preview, which is the
 * whole reason the sheet can be read-only: there is exactly one renderer.
 *
 * Each category is its own [Section] — a standing head with the count of what
 * is under it — so [TARGET_COUNT] rows read as four short lists rather than as
 * one scroll with headings floating in it.
 */
@Composable
fun TargetList(
    goals: NutrientGoals,
    modifier: Modifier = Modifier,
    previous: NutrientGoals? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        NutrientCategory.entries.forEachIndexed { index, category ->
            if (index > 0) Spacer(Modifier.height(Spacing.xxl))
            val fields = AllNutrientFields.filter { it.category == category && it.scored }
            val isMacros = category == NutrientCategory.Macros
            val count = fields.size + if (isMacros) 1 else 0
            Section(
                title = category.name,
                trailing = {
                    Text("$count", style = AppText.bodyS, color = scheme.onSurfaceVariant)
                },
            ) {
                fields.forEachIndexed { i, field ->
                    if (i > 0) AppDivider()
                    TargetRow(
                        label = field.label,
                        value = field.goal(goals),
                        unit = field.unit.symbol,
                        previous = previous?.let(field.goal),
                    )
                }
                if (isMacros) {
                    AppDivider()
                    TargetRow(
                        label = "Water",
                        value = goals.waterMl.toDouble(),
                        unit = "ml",
                        previous = previous?.waterMl?.toDouble(),
                    )
                }
            }
        }
    }
}

/**
 * `See all 31 targets` — the footer of the targets section.
 *
 * It used to be flanked by two dividers, which was the right call *while* the
 * section was a card: the dashes were what stopped it reading as a fifth
 * target. With the card gone, one rule above it does that job on its own, so
 * this is now the same footer Today's nutrient section uses — a hairline, then
 * a full-width accent label. Text links are rule 3's sanctioned accent.
 */
@Composable
fun SeeAllTargetsRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppOutlinedButton(
        text = "See all $TARGET_COUNT targets",
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(top = Spacing.m),
    )
}

// ---- Settings rows --------------------------------------------------------

/**
 * Label, value, chevron, on a rule.
 *
 * **This is a deliberate departure from the original sketch**, which adopted
 * reference shot 9's icon-circle row anatomy. Shot 9 is a Direction A shot, and
 * the app chose Direction B; the circles came in through a sketch rather than
 * through the direction. Two things are wrong with them here. A 36dp
 * `surfaceContainerHigh` disc per row is six filled shapes in a column that
 * house rule 1 has just stripped of its enclosing outline — the discs become
 * the only boxes left, so they read as the page's structure. And the icons
 * inside them carry nothing: a bell beside the word "Reminders" and a shield
 * beside "Privacy" are glosses on a label that is already two words long, which
 * is house rule 4's objection to emoji in a different medium.
 *
 * So the leading column goes, and the row becomes what a settings list actually
 * is: a name, its current state, and an arrow saying there is more. The value
 * on the right is the part worth keeping from that reasoning — it is what
 * makes a list of rows better than the wall of inline controls it replaced,
 * because you can read the state of all six settings without opening any.
 */
@Composable
fun SettingRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    icon: ImageVector? = null,
    pastel: Pastel = Pastel.Sand,
) {
    val scheme = MaterialTheme.colorScheme
    SettingRowFrame(label, modifier.clickable(onClick = onClick), icon, pastel) {
        if (value != null) {
            Text(
                value,
                style = AppText.bodyS,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = SETTING_VALUE_MAX),
            )
            Spacer(Modifier.width(Spacing.s))
        }
        Icon(
            Lucide.ChevronRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Same anatomy, but the control is the value — nothing to open, so no chevron. */
@Composable
fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    pastel: Pastel = Pastel.Sand,
) {
    SettingRowFrame(label, modifier, icon, pastel) {
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * The shared row box. No horizontal padding: house rule 1 has the content bleed
 * to the 16dp screen gutter now that there is no card to sit inside, and the
 * press ripple runs the full width with it.
 */
@Composable
private fun SettingRowFrame(
    label: String,
    modifier: Modifier,
    icon: ImageVector?,
    pastel: Pastel,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PastelIcon(icon, pastel, size = 36.dp)
            Spacer(Modifier.width(Spacing.m))
        }
        Text(
            label,
            style = AppText.body,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
