package com.example.foodtracker.data

import androidx.compose.runtime.Immutable

@Immutable
data class Nutrients(
    val calories: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val omega3: Double = 0.0,
    val cholesterol: Double = 0.0,
    val sodium: Double = 0.0,
    val calcium: Double = 0.0,
    val iron: Double = 0.0,
    val magnesium: Double = 0.0,
    val potassium: Double = 0.0,
    val zinc: Double = 0.0,
    val phosphorus: Double = 0.0,
    val selenium: Double = 0.0,
    val copper: Double = 0.0,
    val manganese: Double = 0.0,
    val vitaminA: Double = 0.0,
    val vitaminC: Double = 0.0,
    val vitaminD: Double = 0.0,
    val vitaminE: Double = 0.0,
    val vitaminK: Double = 0.0,
    val vitaminB12: Double = 0.0,
    val folate: Double = 0.0,
    val vitaminB1: Double = 0.0,
    val vitaminB2: Double = 0.0,
    val vitaminB3: Double = 0.0,
    val vitaminB6: Double = 0.0,

    // ── Optional detail ──────────────────────────────────────────────
    // Sub-components of fiber and protein. 0 means UNKNOWN here, not
    // "none": most foods carry no split at all (every custom food, every
    // AI food filled before this existed, and the seeded foods outside the
    // hand-filled subset). The UI must therefore hide the breakdown rather
    // than draw it as zero. See [hasFiberSplit] / [hasAminoAcids].
    val solubleFiber: Double = 0.0,
    val insolubleFiber: Double = 0.0,
    val essentialAminoAcids: Double = 0.0,
    val leucine: Double = 0.0,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        calories + other.calories,
        carbs + other.carbs,
        sugar + other.sugar,
        fiber + other.fiber,
        protein + other.protein,
        fat + other.fat,
        saturatedFat + other.saturatedFat,
        omega3 + other.omega3,
        cholesterol + other.cholesterol,
        sodium + other.sodium,
        calcium + other.calcium,
        iron + other.iron,
        magnesium + other.magnesium,
        potassium + other.potassium,
        zinc + other.zinc,
        phosphorus + other.phosphorus,
        selenium + other.selenium,
        copper + other.copper,
        manganese + other.manganese,
        vitaminA + other.vitaminA,
        vitaminC + other.vitaminC,
        vitaminD + other.vitaminD,
        vitaminE + other.vitaminE,
        vitaminK + other.vitaminK,
        vitaminB12 + other.vitaminB12,
        folate + other.folate,
        vitaminB1 + other.vitaminB1,
        vitaminB2 + other.vitaminB2,
        vitaminB3 + other.vitaminB3,
        vitaminB6 + other.vitaminB6,
        solubleFiber + other.solubleFiber,
        insolubleFiber + other.insolubleFiber,
        essentialAminoAcids + other.essentialAminoAcids,
        leucine + other.leucine,
    )

    operator fun times(factor: Double) = Nutrients(
        calories * factor,
        carbs * factor,
        sugar * factor,
        fiber * factor,
        protein * factor,
        fat * factor,
        saturatedFat * factor,
        omega3 * factor,
        cholesterol * factor,
        sodium * factor,
        calcium * factor,
        iron * factor,
        magnesium * factor,
        potassium * factor,
        zinc * factor,
        phosphorus * factor,
        selenium * factor,
        copper * factor,
        manganese * factor,
        vitaminA * factor,
        vitaminC * factor,
        vitaminD * factor,
        vitaminE * factor,
        vitaminK * factor,
        vitaminB12 * factor,
        folate * factor,
        vitaminB1 * factor,
        vitaminB2 * factor,
        vitaminB3 * factor,
        vitaminB6 * factor,
        solubleFiber * factor,
        insolubleFiber * factor,
        essentialAminoAcids * factor,
        leucine * factor,
    )

    /** True when this food carries a soluble/insoluble split worth drawing. */
    fun hasFiberSplit(): Boolean = solubleFiber > 0.0 || insolubleFiber > 0.0

    /** True when this food carries an amino-acid breakdown worth drawing. */
    fun hasAminoAcids(): Boolean = essentialAminoAcids > 0.0

    companion object {
        val Zero = Nutrients()
    }
}

enum class NutrientCategory { Macros, Minerals, Vitamins }

enum class NutrientUnit(val symbol: String) {
    Kcal("kcal"), Gram("g"), Milligram("mg"), Microgram("µg")
}

/**
 * Whether a field's number is something to reach, something to stay under, or
 * nothing at all.
 *
 * [Limit] fields are capped by dietary guidance rather than targeted: sitting at
 * 40% of the number is a good day, not a shortfall. The UI must not present them
 * the same way it presents an RDA, or a low reading reads as a failure.
 *
 * [Untargeted] fields are measured and shown but scored against nothing, because
 * no current authority publishes a number to score them against. Dietary
 * cholesterol is the only one. Its 300 mg cap was the pre-2015 figure, and the
 * three guidelines this app otherwise follows have all since dropped it: the
 * 2025-2030 Dietary Guidelines for Americans do not contain the word
 * "cholesterol" at all; the AHA's 2020 science advisory declines a figure
 * outright, on the grounds that "a recommendation that gives a specific dietary
 * cholesterol target within the context of food-based advice is challenging for
 * clinicians and consumers to implement"; and ICMR-NIN's 2024 guidelines for
 * Indians mention cholesterol twenty-five times without ever naming a limit.
 *
 * Tracking it anyway, rather than deleting the field, is deliberate. The
 * evidence that retired the cap is about saturated fat being the dominant
 * dietary driver of LDL, not about dietary cholesterol being inert — a 2026
 * Nature paper has chronic dietary cholesterol routing the LDL receptor to
 * lysosomes for degradation, which is a mechanism for impaired clearance. The
 * honest position is the one the guidelines themselves take: show the number,
 * assert nothing about it.
 *
 * An untargeted field is NOT a field with a goal of zero. See [statusFor].
 */
enum class NutrientTarget { Goal, Limit, Untargeted }

data class NutrientField(
    val key: String,
    val label: String,
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val read: (Nutrients) -> Double,
    val goal: (NutrientGoals) -> Double,
    /** Defaults to [NutrientTarget.Goal] - only the capped fields opt out. */
    val target: NutrientTarget = NutrientTarget.Goal,
) {
    /**
     * True when this field is measured against a number — so false only for
     * [NutrientTarget.Untargeted].
     *
     * Every tally, strip and heatmap filters on this rather than on `goal > 0`.
     * The two are not the same test: a goal of zero means the number has not
     * been worked out yet, which is a bug; untargeted means there is no number
     * to work out, which is the finding.
     */
    val scored: Boolean get() = target != NutrientTarget.Untargeted
}

/**
 * What a single nutrient reading means today.
 *
 * [Headroom] is the state the app used to get wrong. A [NutrientTarget.Limit]
 * field sitting at 40% of its cap is a good day, and drawing it on the same
 * 0→100% track as an RDA made it read as a failure.
 *
 * [Unmet] / [Partial] / [Met] split at 80%, not 100%: RDAs sit roughly 2 SD
 * above average requirement so they cover 97.5% of people, which puts the EAR —
 * the number that is genuinely adequate — near 80%. Holding the strip to 100%
 * makes it cry wolf over trivial shortfalls. No time
 * weighting, deliberately: the strip must mean the same thing whenever you look
 * at it, not soften because it is only 10am.
 */
enum class NutrientStatus { Unmet, Partial, Met, Headroom, Over, FarOver, Untracked }

/**
 * True when a reading counts toward the "n of 29 on track" tally.
 *
 * A limit under its cap is a pass — staying under sodium
 * genuinely is on track, and it makes the denominator match the strip directly
 * above it instead of silently dropping four cells.
 *
 * [NutrientStatus.Untracked] is the one status that is neither a pass nor a
 * fail: an untargeted field has nothing to be on track *for*. It must be left
 * out of the denominator too, not merely counted as a miss — hence
 * [NutrientField.scored], which every caller of this filters on first.
 */
val NutrientStatus.onTrack: Boolean
    get() = this == NutrientStatus.Met || this == NutrientStatus.Headroom

/** Fraction of goal at which a [NutrientTarget.Goal] field counts as met. */
const val MET_FRACTION = 0.80

/**
 * Resolve a reading against its goal. [goal] of zero or less is always
 * [NutrientStatus.Unmet].
 *
 * The untargeted check has to come first, and that ordering is the whole reason
 * [NutrientTarget.Untargeted] exists as a target rather than as a goal of zero:
 * a zeroed goal falls into the line below and scores [NutrientStatus.Unmet],
 * which would paint "we decline to set a target for this" as "you failed to
 * reach it" — the exact opposite reading.
 */
fun NutrientField.statusFor(consumed: Double, goal: Double): NutrientStatus {
    if (target == NutrientTarget.Untargeted) return NutrientStatus.Untracked
    if (goal <= 0.0) return NutrientStatus.Unmet
    val f = consumed / goal
    return if (target == NutrientTarget.Limit) {
        when {
            f > 1.25 -> NutrientStatus.FarOver
            f > 1.0 -> NutrientStatus.Over
            else -> NutrientStatus.Headroom
        }
    } else {
        when {
            f >= MET_FRACTION -> NutrientStatus.Met
            f >= MET_FRACTION / 2 -> NutrientStatus.Partial
            else -> NutrientStatus.Unmet
        }
    }
}

val AllNutrientFields: List<NutrientField> = listOf(
    NutrientField("calories", "Calories", NutrientUnit.Kcal, NutrientCategory.Macros, { it.calories }, { it.calories }),
    NutrientField("carbs", "Carbs", NutrientUnit.Gram, NutrientCategory.Macros, { it.carbs }, { it.carbs }),
    NutrientField("sugar", "Sugar", NutrientUnit.Gram, NutrientCategory.Macros, { it.sugar }, { it.sugar }, NutrientTarget.Limit),
    NutrientField("fiber", "Fiber", NutrientUnit.Gram, NutrientCategory.Macros, { it.fiber }, { it.fiber }),
    NutrientField("protein", "Protein", NutrientUnit.Gram, NutrientCategory.Macros, { it.protein }, { it.protein }),
    NutrientField("fat", "Fat", NutrientUnit.Gram, NutrientCategory.Macros, { it.fat }, { it.fat }),
    NutrientField("saturatedFat", "Saturated Fat", NutrientUnit.Gram, NutrientCategory.Macros, { it.saturatedFat }, { it.saturatedFat }, NutrientTarget.Limit),
    NutrientField("omega3", "Omega-3", NutrientUnit.Gram, NutrientCategory.Macros, { it.omega3 }, { it.omega3 }),
    NutrientField("cholesterol", "Dietary cholesterol", NutrientUnit.Milligram, NutrientCategory.Macros, { it.cholesterol }, { it.cholesterol }, NutrientTarget.Untargeted),
    NutrientField("sodium", "Sodium", NutrientUnit.Milligram, NutrientCategory.Macros, { it.sodium }, { it.sodium }, NutrientTarget.Limit),

    NutrientField("calcium", "Calcium", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.calcium }, { it.calcium }),
    NutrientField("iron", "Iron", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.iron }, { it.iron }),
    NutrientField("magnesium", "Magnesium", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.magnesium }, { it.magnesium }),
    NutrientField("potassium", "Potassium", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.potassium }, { it.potassium }),
    NutrientField("zinc", "Zinc", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.zinc }, { it.zinc }),
    NutrientField("phosphorus", "Phosphorus", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.phosphorus }, { it.phosphorus }),
    NutrientField("selenium", "Selenium", NutrientUnit.Microgram, NutrientCategory.Minerals, { it.selenium }, { it.selenium }),
    NutrientField("copper", "Copper", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.copper }, { it.copper }),
    NutrientField("manganese", "Manganese", NutrientUnit.Milligram, NutrientCategory.Minerals, { it.manganese }, { it.manganese }),

    NutrientField("vitaminA", "Vitamin A", NutrientUnit.Microgram, NutrientCategory.Vitamins, { it.vitaminA }, { it.vitaminA }),
    NutrientField("vitaminC", "Vitamin C", NutrientUnit.Milligram, NutrientCategory.Vitamins, { it.vitaminC }, { it.vitaminC }),
    NutrientField("vitaminD", "Vitamin D", NutrientUnit.Microgram, NutrientCategory.Vitamins, { it.vitaminD }, { it.vitaminD }),
    NutrientField("vitaminE", "Vitamin E", NutrientUnit.Milligram, NutrientCategory.Vitamins, { it.vitaminE }, { it.vitaminE }),
    NutrientField("vitaminK", "Vitamin K", NutrientUnit.Microgram, NutrientCategory.Vitamins, { it.vitaminK }, { it.vitaminK }),
    NutrientField("vitaminB1", "Vitamin B1 (Thiamin)", NutrientUnit.Milligram, NutrientCategory.Vitamins, { it.vitaminB1 }, { it.vitaminB1 }),
    NutrientField("vitaminB2", "Vitamin B2 (Riboflavin)", NutrientUnit.Milligram, NutrientCategory.Vitamins, { it.vitaminB2 }, { it.vitaminB2 }),
    NutrientField("vitaminB3", "Vitamin B3 (Niacin)", NutrientUnit.Milligram, NutrientCategory.Vitamins, { it.vitaminB3 }, { it.vitaminB3 }),
    NutrientField("vitaminB6", "Vitamin B6", NutrientUnit.Milligram, NutrientCategory.Vitamins, { it.vitaminB6 }, { it.vitaminB6 }),
    NutrientField("vitaminB12", "Vitamin B12", NutrientUnit.Microgram, NutrientCategory.Vitamins, { it.vitaminB12 }, { it.vitaminB12 }),
    NutrientField("folate", "Folate", NutrientUnit.Microgram, NutrientCategory.Vitamins, { it.folate }, { it.folate }),
)

/**
 * [AllNutrientFields] indexed by key.
 *
 * Use `getValue` rather than a `firstOrNull` scan: a key that does not exist is
 * a typo in a caller, and `getValue` says so at the call instead of returning
 * null for the caller to quietly treat as "nothing to show".
 */
val NutrientFieldsByKey: Map<String, NutrientField> = AllNutrientFields.associateBy { it.key }

/**
 * A component of a parent nutrient — soluble fiber under Fiber, leucine under
 * Protein. Deliberately NOT a [NutrientField]: sub-nutrients carry no goal, no
 * bar and no contribution sheet. They are a detail you can open when curious,
 * not a number to hit, and the UI is built so they never compete with the ten
 * macro rows above them.
 */
data class NutrientSubField(
    val key: String,
    val label: String,
    val read: (Nutrients) -> Double,
)

/** Shown under the Fiber row. Soluble + insoluble should total [Nutrients.fiber]. */
val FiberSubFields: List<NutrientSubField> = listOf(
    NutrientSubField("solubleFiber", "Soluble") { it.solubleFiber },
    NutrientSubField("insolubleFiber", "Insoluble") { it.insolubleFiber },
)

/**
 * Shown under the Protein row.
 *
 * Only two values, by design. The nine essential amino acids are each real and
 * each knowable, but outside a lab you get them from a composition table, and
 * for most of what this app logs there is no table — so the choice is between
 * two numbers that can be sanity-checked (essential AAs run 29-47% of protein,
 * leucine 15-24% of that) and nine that cannot. Leucine earns its own row
 * because it is the one with a number worth acting on: roughly 2.5 g in a meal
 * is what triggers muscle protein synthesis.
 */
val ProteinSubFields: List<NutrientSubField> = listOf(
    NutrientSubField("essentialAminoAcids", "Essential amino acids") { it.essentialAminoAcids },
    NutrientSubField("leucine", "of which leucine") { it.leucine },
)

/** Every sub-nutrient, for the exports that walk the whole set. All in grams. */
val AllNutrientSubFields: List<NutrientSubField> = FiberSubFields + ProteinSubFields

/** The sub-nutrients to offer under a given [NutrientField], if any. */
fun subFieldsFor(key: String): List<NutrientSubField> = when (key) {
    "fiber" -> FiberSubFields
    "protein" -> ProteinSubFields
    else -> emptyList()
}
