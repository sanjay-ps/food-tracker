package com.example.foodtracker.data.db

import com.example.foodtracker.data.CookingOil
import com.example.foodtracker.data.FoodCategory
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.StyleOption
import com.example.foodtracker.data.VariantIngredient
import kotlin.math.roundToInt

/**
 * Curated seed list of Indian foods. Nutrient values are per 100 g unless noted.
 * Values are reasonable approximations drawn from common food composition tables
 * (IFCT, USDA equivalents). Not a substitute for clinical accuracy.
 *
 * ## Where the fat-soluble numbers come from
 *
 * Saturated fat, vitamin E and vitamin K are each the sum of two parts, and the
 * split matters because only one of them is fixed:
 *
 *  - **The cooking fat.** [oilGrams] grams of it per 100 g of dish, priced at
 *    whichever [CookingOil] the entry names. This part is *not* a property of
 *    the dish — a vada is 13 g of absorbed oil whether that oil is palmolein
 *    (5.9 g saturated fat) or gingelly (1.8 g), and the user can say which from
 *    the serving sheet. See [com.example.foodtracker.data.withCookingOil].
 *  - **The food itself.** Everything the ingredients brought with them, which
 *    no choice of oil changes: the 53 µg of vitamin K in a cabbage poriyal's
 *    cabbage, the 4.6 mg of vitamin E in a peanut chutney's peanuts.
 *
 * Each entry below stores the total at its own default oil, so the stored
 * figures read correctly on their own and a swap only ever moves the first
 * part.
 *
 * Defaults follow the kitchen rather than a national average: palmolein for
 * frying and most tempering (India's highest-volume cooking oil, and what
 * commercial snacks are fried in), coconut for the Kerala dishes, gingelly for
 * the Tamil tempering of kuzhambu, poriyal, pickle and tamarind rice, and ghee
 * for pongal, kesari and the payasams.
 *
 * ## What was deliberately left blank
 *
 * **Omega-3.** Filled only for the ~20 foods where one serving clears about 3%
 * of the 1.6 g goal. Nothing in this list is an omega-3 source: there is no
 * fish, flaxseed, walnut, chia or mustard oil in it, and the richest entry —
 * palak paneer — reaches 13.7% of a day. Writing 0.02 g against the other 146
 * would have dressed a structural gap up as data. The honest fix is to add
 * foods that carry it.
 *
 * **Vitamin D.** Left at 0 outside egg. Without fish, fortified milk or
 * sun-dried mushrooms, Indian vegetarian food genuinely has almost none, and a
 * zero here is a finding rather than a hole.
 *
 * **Vitamin B12 and cholesterol.** Carried only by the animal and dairy
 * entries. Zero is the correct value for every plant food, not a missing one.
 *
 * ## Zinc
 *
 * Zinc for composite dishes is derived from the entry's own protein at the
 * ratio its base grain or pulse shows in IFCT/USDA — roughly 0.13 mg per gram
 * of protein for milled-rice dishes, 0.17 mixed, 0.20 for wheat and besan.
 * Derived rather than looked up because no table lists an uttapam, and the
 * ratio is stable where the absolute is not.
 */
object IndianFoodSeed {

    /**
     * Bump this whenever any seed entry's nutrient values change. On the next
     * cold start the repository will run `refreshSeedIfStale()`, which updates
     * every seeded food in place by name — preserving the user's log history,
     * favourites, weight entries, and custom foods.
     *
     * Covers `assets/food_catalogue.json` too: bump it when that file changes,
     * so existing installs pick up added or corrected catalogue foods.
     */
    const val SEED_VERSION = 24

    private fun food(
        name: String,
        /**
         * This entry's permanent cross-install identity.
         *
         * **Generated once and never changed.** It is what a backup stores to
         * say "this log entry was an idli", so editing one re-keys the food on
         * every install and orphans every entry in every snapshot that
         * referenced it. Rename the food freely — that is the whole point of
         * having this — but the string stays.
         *
         * It lives next to the name rather than in a lookup table keyed by
         * name so the two cannot drift: there is no second place to forget to
         * update.
         */
        uuid: String,
        category: FoodCategory,
        servingGrams: Double,
        servingLabel: String,
        n: Nutrients,
        servingVariants: List<ServingVariant> = emptyList(),
        /**
         * Added cooking fat per 100 g of finished dish, and the oil [n] was
         * computed in. Left at 0 for anything with no cooking fat of its own —
         * raw fruit and veg, plain-boiled dals, dairy, the beverages — which
         * is most of this list. See [com.example.foodtracker.data.CookingOil].
         */
        oilGrams: Double = 0.0,
        oil: CookingOil = CookingOil.Palmolein,
        /**
         * Preparation choices, first entry being the one [n] describes. Added
         * only where the choice genuinely moves the numbers — a ghee roast is
         * +36 kcal and more than double the saturated fat of a plain dosa,
         * where "with or without curry leaves" is not worth a chip.
         */
        styleOptions: List<StyleOption> = emptyList(),
    ) = FoodEntity.fromNutrientsPer100g(
        name = name,
        clientUuid = uuid,
        category = category.name,
        n = n,
        servingGrams = servingGrams,
        servingLabel = servingLabel,
        oilGrams = oilGrams,
        oilDefault = oil,
        styleOptions = styleOptions,
        // Explicit variants win; otherwise attach Small/Medium/Large presets to
        // foods whose portion size genuinely varies (see [sizeVariantsFor]).
        servingVariants = servingVariants.ifEmpty { sizeVariantsFor(servingGrams, servingLabel) },
    )

    /** Rounds a derived gram amount to a clean, human-friendly step. */
    private fun roundServing(g: Double): Double = when {
        g < 50 -> (g / 5.0).roundToInt() * 5.0
        g < 200 -> (g / 10.0).roundToInt() * 10.0
        else -> (g / 25.0).roundToInt() * 25.0
    }

    /**
     * Small / Medium / Large presets for foods whose portion size genuinely
     * varies (fruits, breads, tiffin, bowls, plates, glasses). Medium is the
     * canonical serving, so the default logged amount is unchanged; Small/Large
     * are derived from it (×0.7 / ×1.4) and rounded to clean steps.
     *
     * Tiny fixed condiments measured by the spoon (tbsp/tsp — pickles, podis,
     * chutneys, ghee, sugar) and sub-25 g crisp items get NO size presets: the
     * servings slider and Custom-g chip already cover those, and "large ghee"
     * is nonsense. Returns [] when not applicable, so
     * [FoodEntity.fromNutrientsPer100g] falls back to its default gram-reference
     * variants (canonical + 100 g / 50 g).
     */
    fun sizeVariantsFor(grams: Double, label: String): List<ServingVariant> {
        val l = label.lowercase()
        val applicable = grams >= 25.0 && !l.contains("tbsp") && !l.contains("tsp")
        if (!applicable) return emptyList()
        return listOf(
            ServingVariant("Medium", grams),
            ServingVariant("Small", roundServing(grams * 0.7)),
            ServingVariant("Large", roundServing(grams * 1.4)),
        )
    }


    /**
     * The protein quality of one food, held as grams per gram of protein
     * rather than as absolute grams: [eaa] is the share of its protein made up
     * of the nine essential amino acids, [leucine] the share that is leucine.
     *
     * Ratios, not absolutes, because our per-100 g protein figures are our own
     * (IFCT-leaning, rounded) and rarely match the USDA entry these profiles
     * come from. Scaling a published ratio by OUR protein keeps the parts
     * consistent with the whole shown right above them, and makes it
     * arithmetically impossible for them to exceed it.
     *
     * Each pair is that food's own USDA SR profile summed, not a food-group
     * average — which is why only the ~25 single-ingredient staples below
     * carry one. Every other seeded food, and every custom or AI food, keeps 0
     * (unknown) and shows no breakdown at all. A mixed dish like sambar or
     * biryani gets none: its protein comes from several sources in proportions
     * that vary by cook.
     */
    private class AminoPattern(val eaa: Double, val leucine: Double)

    // Animal protein and dairy — the complete profiles, 41-47% EAA.
    private val AA_EGG = AminoPattern(eaa = 0.445, leucine = 0.087)
    private val AA_CHICKEN = AminoPattern(eaa = 0.415, leucine = 0.075)
    // One pattern for milk, curd and paneer: all three are the same casein +
    // whey protein. Curd ferments the lactose, not the amino acids, and paneer
    // concentrates the casein rather than changing its profile.
    private val AA_DAIRY = AminoPattern(eaa = 0.465, leucine = 0.099)

    // Cereals — 29-37% EAA, and the lowest leucine yields per gram of food,
    // because there is so little protein to take a share of.
    private val AA_RICE_WHITE = AminoPattern(eaa = 0.373, leucine = 0.083)
    private val AA_RICE_BROWN = AminoPattern(eaa = 0.370, leucine = 0.081)
    private val AA_OATS = AminoPattern(eaa = 0.348, leucine = 0.074)
    private val AA_WHEAT = AminoPattern(eaa = 0.292, leucine = 0.065)

    // Pulses — 36-39% EAA, between the cereals and animal protein, which is
    // why a rice-and-dal meal scores better than either grain alone.
    private val AA_LENTIL = AminoPattern(eaa = 0.365, leucine = 0.073)
    private val AA_CHICKPEA = AminoPattern(eaa = 0.364, leucine = 0.071)
    private val AA_MUNG = AminoPattern(eaa = 0.393, leucine = 0.080)
    private val AA_COWPEA = AminoPattern(eaa = 0.386, leucine = 0.077)
    private val AA_URAD = AminoPattern(eaa = 0.393, leucine = 0.079)
    private val AA_TOOR = AminoPattern(eaa = 0.369, leucine = 0.071)
    private val AA_SOY = AminoPattern(eaa = 0.383, leucine = 0.079)

    // Fish — as complete as egg, and the only pattern here that arrives with
    // EPA and DHA attached rather than ALA.
    private val AA_FISH = AminoPattern(eaa = 0.424, leucine = 0.078)

    // Nuts and seeds — the spread is wider than the pulses'. Almond is the
    // weakest here, lysine-poor and barely ahead of wheat; sesame and pumpkin
    // sit up with the pulses. Worth carrying per-seed rather than as one
    // "nuts" average, since a 30 g handful is a real share of a day's leucine.
    private val AA_ALMOND = AminoPattern(eaa = 0.295, leucine = 0.071)
    private val AA_CASHEW = AminoPattern(eaa = 0.345, leucine = 0.076)
    private val AA_PUMPKIN_SEED = AminoPattern(eaa = 0.380, leucine = 0.077)
    private val AA_CHIA = AminoPattern(eaa = 0.378, leucine = 0.071)
    private val AA_FLAX = AminoPattern(eaa = 0.374, leucine = 0.062)
    private val AA_SESAME = AminoPattern(eaa = 0.399, leucine = 0.071)

    /**
     * Attach the optional detail — the protein profile and/or the soluble
     * share of fiber — to an otherwise plain [Nutrients].
     *
     * [solubleFiberFraction] is the soluble share of this food's total dietary
     * fiber, applied to OUR fiber figure for the same reason the amino acids
     * are ratios: the two parts then add up to the total on screen instead of
     * contradicting it.
     *
     * Every fraction here is FIBSOL / FIBTG from Table 1 of the Indian Food
     * Composition Tables 2017 (ICMR-NIN), which measures soluble and insoluble
     * dietary fibre separately for ~500 Indian foods. That table, rather than
     * USDA, because it covers rice varieties, dals and Indian vegetables by
     * name — and because USDA cannot answer this at all: its soluble and
     * insoluble nutrients (1082/1084) are unpopulated across SR Legacy, which
     * is also why [com.example.foodtracker.data.UsdaFoodApi] never fills these
     * fields in practice.
     *
     * Unlike the amino profiles, this is carried by EVERY seeded food that has
     * any fiber, not just the single ingredients, because the soluble share is
     * a property of the fibre's SOURCE and a dish's fibre comes from few
     * enough sources to account for. A single-ingredient food takes its own
     * IFCT row. A composite dish is computed from its components' IFCT rows,
     * each weighted by the fibre IT contributes rather than by its mass —
     * which is not the same thing and repeatedly changes the answer. Idli
     * batter is three parts rice to one part urad, yet the urad carries more
     * of the fibre than the rice does: 0.25 x 11.93 g beats 0.75 x 2.8 g. Its
     * fraction is 1.70/5.08 = 0.33, nothing like the 0.12 that rice alone
     * would suggest.
     *
     * The spread is wide and often counterintuitive, which is the argument for
     * looking it up rather than reasoning about it. Urad dal is 0.36 and
     * Bengal gram whole 0.10, though both are pulses. Milled white rice is
     * 0.29 while the brown rice it comes from is 0.18, because milling strips
     * insoluble bran and raises what remains as a share — and atta is 0.14
     * against refined wheat flour's 0.23 for the same reason. Guava, a
     * byword for fibre at 8.6 g, is only 0.17 of it soluble; watermelon, with
     * 0.7 g, is 0.49.
     *
     * Two exceptions carry pre-IFCT figures because the table has no row for
     * them: oats (0.50, on beta-glucan) and soya chunks (0.15, an extruded
     * product rather than a food). Both are flagged here rather than silently
     * mixed in with the sourced ones.
     *
     * Leaving either argument null leaves those fields at 0, which the app
     * reads as unknown and hides.
     */
    private fun Nutrients.detail(
        amino: AminoPattern? = null,
        solubleFiberFraction: Double? = null,
    ): Nutrients {
        val soluble = solubleFiberFraction?.let { fiber * it }
        return copy(
            solubleFiber = soluble ?: solubleFiber,
            insolubleFiber = soluble?.let { fiber - it } ?: insolubleFiber,
            essentialAminoAcids = amino?.let { protein * it.eaa } ?: essentialAminoAcids,
            leucine = amino?.let { protein * it.leucine } ?: leucine,
        )
    }

    /**
     * Ghee on a griddle item. Four grams is a restaurant ghee roast; two is the
     * spoonful that goes over an idli at home.
     */
    private fun gheeStyle(grams: Double) = listOf(
        StyleOption("Plain"),
        StyleOption("Ghee roast", VariantIngredient.Ghee, grams),
    )

    val ALL: List<FoodEntity> = listOf(
        // ---------- SOUTH INDIAN ----------
        food("Idli", "5775d1e1-f376-4b82-ab01-f93ac6dfdebc", FoodCategory.SouthIndian, 35.0, "1 idli (35 g)",
            Nutrients(calories = 130.0, carbs = 27.0, sugar = 0.4, fiber = 1.2, protein = 4.0, fat = 0.4, saturatedFat = 0.10,
                sodium = 220.0, calcium = 18.0, iron = 1.0, magnesium = 25.0, potassium = 60.0,
                zinc = 0.40, phosphorus = 50.0, selenium = 3.5, copper = 0.10, manganese = 0.30,
                vitaminE = 0.10, vitaminK = 0.3, vitaminB1 = 0.05, vitaminB2 = 0.03, vitaminB3 = 0.70,
                vitaminB6 = 0.04, folate = 14.0).detail(solubleFiberFraction = 0.33),
            styleOptions = listOf(StyleOption("Plain"), StyleOption("Ghee", VariantIngredient.Ghee, 2.0))),
        food("Dosa (plain)", "dbed1a1c-dc61-4849-b572-e885480d7c5a", FoodCategory.SouthIndian, 80.0, "1 dosa (80 g)",
            Nutrients(calories = 168.0, carbs = 30.0, sugar = 0.5, fiber = 1.5, protein = 4.0, fat = 3.7,
                saturatedFat = 1.5, omega3 = 0.01, sodium = 230.0, calcium = 14.0, iron = 1.2,
                magnesium = 23.0, potassium = 70.0, zinc = 0.50, phosphorus = 75.0, selenium = 4.5,
                copper = 0.14, manganese = 0.40, vitaminE = 0.55, vitaminK = 0.5, vitaminB1 = 0.07,
                vitaminB2 = 0.04, vitaminB3 = 1.00, vitaminB6 = 0.06, folate = 16.0).detail(solubleFiberFraction = 0.33),
            oilGrams = 2.7, oil = CookingOil.Palmolein,
            styleOptions = gheeStyle(4.0)),
        // Godhumai dosai — wheat flour whisked to a pourable batter and griddled
        // straight away, with none of the overnight ferment a rice dosa needs.
        // Thinner and wetter than a chapati of the same flour, so per 100 g it
        // reads lighter: the atta is the same, there is just more water left in it.
        food("Wheat Dosa", "e450728c-21d1-447f-b378-fa1c4ad0a6f8", FoodCategory.SouthIndian, 70.0, "1 dosa (70 g)",
            Nutrients(calories = 185.0, carbs = 30.0, sugar = 1.0, fiber = 3.5, protein = 5.5, fat = 4.5,
                saturatedFat = 1.4, omega3 = 0.01, sodium = 280.0, calcium = 20.0, iron = 1.8,
                magnesium = 42.0, potassium = 150.0, zinc = 0.85, phosphorus = 90.0, selenium = 15.0,
                copper = 0.14, manganese = 0.65, vitaminE = 0.55, vitaminK = 1.0, vitaminB1 = 0.13,
                vitaminB2 = 0.05, vitaminB3 = 1.40, vitaminB6 = 0.08,
                folate = 16.0).detail(amino = AA_WHEAT, solubleFiberFraction = 0.14),
            oilGrams = 3.0, oil = CookingOil.Palmolein),
        food("Masala Dosa", "13282086-58a7-4a9a-81a6-b59076071c69", FoodCategory.SouthIndian, 150.0, "1 masala dosa (150 g)",
            Nutrients(calories = 168.0, carbs = 27.0, sugar = 1.2, fiber = 2.2, protein = 3.6, fat = 5.4,
                saturatedFat = 2.1, omega3 = 0.01, sodium = 280.0, calcium = 17.0, iron = 1.4,
                magnesium = 26.0, potassium = 195.0, zinc = 0.60, phosphorus = 70.0, selenium = 3.5,
                copper = 0.13, manganese = 0.35, vitaminA = 4.0, vitaminC = 6.0, vitaminE = 0.84,
                vitaminK = 1.8, vitaminB1 = 0.08, vitaminB2 = 0.04, vitaminB3 = 1.10, vitaminB6 = 0.12,
                folate = 18.0).detail(solubleFiberFraction = 0.33),
            oilGrams = 4.0, oil = CookingOil.Palmolein,
            styleOptions = gheeStyle(4.0)),
        food("Uttapam", "c1a5f41e-35eb-44ab-bc22-2328966ca85f", FoodCategory.SouthIndian, 100.0, "1 uttapam (100 g)",
            Nutrients(calories = 155.0, carbs = 28.0, sugar = 1.4, fiber = 1.8, protein = 4.5, fat = 3.0,
                saturatedFat = 1.1, sodium = 240.0, calcium = 20.0, iron = 1.2, magnesium = 24.0,
                potassium = 110.0, zinc = 0.60, phosphorus = 70.0, selenium = 4.0, copper = 0.13,
                manganese = 0.38, vitaminA = 3.0, vitaminC = 3.0, vitaminE = 0.47, vitaminK = 1.0,
                vitaminB1 = 0.07, vitaminB2 = 0.04, vitaminB3 = 1.00, vitaminB6 = 0.08, folate = 18.0).detail(solubleFiberFraction = 0.31),
            oilGrams = 2.0, oil = CookingOil.Palmolein),
        food("Medu Vada", "dd233677-affe-4d03-ae81-e17a2b9f0659", FoodCategory.SouthIndian, 40.0, "1 vada (40 g)",
            Nutrients(calories = 290.0, carbs = 30.0, sugar = 0.6, fiber = 3.0, protein = 9.0, fat = 14.0,
                saturatedFat = 6.0, omega3 = 0.03, sodium = 340.0, calcium = 30.0, iron = 1.6,
                magnesium = 50.0, potassium = 250.0, zinc = 1.40, phosphorus = 110.0, selenium = 3.0,
                copper = 0.30, manganese = 0.55, vitaminE = 2.33, vitaminK = 2.5, vitaminB1 = 0.12,
                vitaminB2 = 0.05, vitaminB3 = 1.00, vitaminB6 = 0.10, folate = 60.0).detail(solubleFiberFraction = 0.36),
            oilGrams = 13.0, oil = CookingOil.Palmolein),
        food("Sambar", "63803274-98a8-471a-9e4f-d7f091a67ecf", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 65.0, carbs = 9.0, sugar = 2.2, fiber = 3.0, protein = 2.7, fat = 2.0,
                saturatedFat = 0.3, sodium = 350.0, calcium = 28.0, iron = 1.4, magnesium = 35.0,
                potassium = 290.0, zinc = 0.70, phosphorus = 60.0, selenium = 2.0, copper = 0.18,
                manganese = 0.30, vitaminA = 60.0, vitaminC = 8.0, vitaminE = 0.32, vitaminK = 4.2,
                vitaminB1 = 0.08, vitaminB2 = 0.04, vitaminB3 = 0.70, vitaminB6 = 0.12, folate = 35.0).detail(solubleFiberFraction = 0.26),
            oilGrams = 1.5, oil = CookingOil.Gingelly),
        food("Rasam", "05d93b71-abd2-469b-a925-23fd3c91bebc", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 33.0, carbs = 6.0, sugar = 1.6, fiber = 0.7, protein = 0.9, fat = 0.8,
                saturatedFat = 0.1, sodium = 300.0, calcium = 14.0, iron = 0.8, magnesium = 15.0,
                potassium = 180.0, zinc = 0.25, phosphorus = 25.0, selenium = 1.0, copper = 0.08,
                manganese = 0.15, vitaminA = 15.0, vitaminC = 3.0, vitaminE = 0.26, vitaminK = 2.1,
                vitaminB1 = 0.04, vitaminB2 = 0.02, vitaminB3 = 0.50, vitaminB6 = 0.08, folate = 10.0).detail(solubleFiberFraction = 0.24),
            oilGrams = 0.6, oil = CookingOil.Gingelly),
        food("Coconut Chutney", "928fb5d2-487d-4c59-8aba-513791559a46", FoodCategory.SouthIndian, 30.0, "2 tbsp (30 g)",
            Nutrients(calories = 220.0, carbs = 7.0, sugar = 2.4, fiber = 5.0, protein = 3.0, fat = 21.0,
                saturatedFat = 18.6, sodium = 240.0, calcium = 14.0, iron = 1.2, magnesium = 28.0,
                potassium = 270.0, zinc = 1.10, phosphorus = 80.0, selenium = 4.0, copper = 0.30,
                manganese = 0.90, vitaminC = 2.0, vitaminE = 0.20, vitaminK = 0.3, vitaminB1 = 0.04,
                vitaminB2 = 0.02, vitaminB3 = 0.50, vitaminB6 = 0.05, folate = 16.0).detail(solubleFiberFraction = 0.1),
            oilGrams = 2.0, oil = CookingOil.Coconut),
        food("Tomato Chutney", "5b13e5fc-4465-46bd-bd89-02e623754a8f", FoodCategory.SouthIndian, 30.0, "2 tbsp (30 g)",
            Nutrients(calories = 70.0, carbs = 8.0, sugar = 3.6, fiber = 1.6, protein = 1.5, fat = 3.5,
                saturatedFat = 0.5, omega3 = 0.01, sodium = 320.0, calcium = 12.0, iron = 0.6,
                magnesium = 14.0, potassium = 230.0, zinc = 0.25, phosphorus = 35.0, selenium = 0.6,
                copper = 0.12, manganese = 0.20, vitaminA = 80.0, vitaminC = 16.0, vitaminE = 0.44,
                vitaminK = 4.9, vitaminB1 = 0.04, vitaminB2 = 0.03, vitaminB3 = 0.70, vitaminB6 = 0.10,
                folate = 14.0).detail(solubleFiberFraction = 0.19),
            oilGrams = 3.0, oil = CookingOil.Gingelly),
        food("Curd Rice", "b9ef8700-1657-497a-b299-c23f4fc77850", FoodCategory.SouthIndian, 250.0, "1 plate (250 g)",
            Nutrients(calories = 110.0, carbs = 19.0, sugar = 1.8, fiber = 0.5, protein = 2.9, fat = 2.5, saturatedFat = 1.50,
                sodium = 210.0, calcium = 60.0, iron = 0.5, magnesium = 15.0, potassium = 90.0,
                zinc = 0.50, phosphorus = 75.0, selenium = 5.0, copper = 0.08, manganese = 0.25,
                vitaminA = 12.0, vitaminC = 0.4, vitaminE = 0.08, vitaminK = 0.4, cholesterol = 4.0,
                vitaminB1 = 0.04,
                vitaminB2 = 0.08, vitaminB3 = 0.50, vitaminB6 = 0.05, vitaminB12 = 0.30, folate = 8.0).detail(solubleFiberFraction = 0.29)),
        food("Lemon Rice", "6e473b55-69fc-4edd-a106-d36f1638c7e4", FoodCategory.SouthIndian, 200.0, "1 plate (200 g)",
            Nutrients(calories = 175.0, carbs = 30.0, sugar = 0.6, fiber = 1.0, protein = 3.5, fat = 4.5,
                saturatedFat = 0.6, omega3 = 0.01, sodium = 230.0, calcium = 10.0, iron = 0.7,
                magnesium = 14.0, potassium = 70.0, zinc = 0.50, phosphorus = 50.0, selenium = 6.0,
                copper = 0.12, manganese = 0.40, vitaminC = 6.0, vitaminE = 0.16, vitaminK = 1.1,
                vitaminB1 = 0.05, vitaminB2 = 0.02, vitaminB3 = 0.80, vitaminB6 = 0.06, folate = 6.0).detail(solubleFiberFraction = 0.29),
            oilGrams = 4.5, oil = CookingOil.Gingelly),
        food("Tamarind Rice (Pulihora)", "b612da97-3534-4618-8a64-fee0481b3528", FoodCategory.SouthIndian, 200.0, "1 plate (200 g)",
            Nutrients(calories = 185.0, carbs = 32.0, sugar = 2.4, fiber = 1.3, protein = 3.0, fat = 5.0,
                saturatedFat = 0.7, omega3 = 0.01, sodium = 280.0, calcium = 22.0, iron = 1.0,
                magnesium = 18.0, potassium = 130.0, zinc = 0.55, phosphorus = 55.0, selenium = 6.0,
                copper = 0.16, manganese = 0.45, vitaminA = 6.0, vitaminC = 2.0, vitaminE = 0.31,
                vitaminK = 1.4, vitaminB1 = 0.05, vitaminB2 = 0.02, vitaminB3 = 0.80, vitaminB6 = 0.08,
                folate = 8.0).detail(solubleFiberFraction = 0.28),
            oilGrams = 4.5, oil = CookingOil.Gingelly),
        food("Ven Pongal", "2e9b4dfd-431e-437b-80f6-bab14775742e", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 180.0, carbs = 24.5, sugar = 0.5, fiber = 2.1, protein = 4.6, fat = 7.0,
                saturatedFat = 3.9, omega3 = 0.08, sodium = 240.0, calcium = 22.0, iron = 1.3,
                magnesium = 38.0, potassium = 180.0, zinc = 1.00, phosphorus = 70.0, selenium = 4.0,
                copper = 0.18, manganese = 0.40, vitaminA = 42.0, vitaminE = 0.37, vitaminK = 1.5,
                cholesterol = 15.4, vitaminB1 = 0.08,
                vitaminB2 = 0.03, vitaminB3 = 0.70, vitaminB6 = 0.07, folate = 22.0).detail(solubleFiberFraction = 0.25),
            oilGrams = 6.0, oil = CookingOil.Ghee),
        food("Rava Upma", "ca03b68d-12a6-47b8-9d13-739035528f24", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 132.0, carbs = 20.0, sugar = 1.4, fiber = 1.4, protein = 3.6, fat = 4.0,
                saturatedFat = 0.6, omega3 = 0.01, sodium = 280.0, calcium = 15.0, iron = 1.4,
                magnesium = 24.0, potassium = 90.0, zinc = 0.70, phosphorus = 55.0, selenium = 8.0,
                copper = 0.12, manganese = 0.30, vitaminA = 18.0, vitaminC = 2.0, vitaminE = 0.23,
                vitaminK = 2.5, vitaminB1 = 0.07, vitaminB2 = 0.03, vitaminB3 = 0.90, vitaminB6 = 0.06,
                folate = 30.0).detail(solubleFiberFraction = 0.18),
            oilGrams = 3.5, oil = CookingOil.Gingelly),
        food("Appam", "aa30e68a-2467-483f-8f19-487154df3c44", FoodCategory.SouthIndian, 60.0, "1 appam (60 g)",
            Nutrients(calories = 145.0, carbs = 28.0, sugar = 2.0, fiber = 0.8, protein = 2.4, fat = 2.5,
                saturatedFat = 2.2, sodium = 180.0, calcium = 10.0, iron = 0.8, magnesium = 18.0,
                potassium = 70.0, zinc = 0.35, phosphorus = 40.0, selenium = 2.5, copper = 0.10,
                manganese = 0.30, vitaminE = 0.10, vitaminK = 0.2, vitaminB1 = 0.04, vitaminB2 = 0.02,
                vitaminB3 = 0.60, vitaminB6 = 0.04, folate = 8.0).detail(solubleFiberFraction = 0.28),
            oilGrams = 1.0, oil = CookingOil.Coconut,
            styleOptions = listOf(StyleOption("Plain"), StyleOption("Coconut milk", VariantIngredient.CoconutMilk, 25.0))),
        // One string-hopper nest is ~35–40 g; a serving is 2 nests. Count in nests,
        // not a scaled "1 nest" that the auto-Large would inflate past a real nest.
        food("Idiyappam", "c5aa8d74-f1d6-44fa-9a54-1eaf0edcf41b", FoodCategory.SouthIndian, 80.0, "2 nests (80 g)",
            Nutrients(calories = 155.0, carbs = 33.0, fiber = 0.6, protein = 2.8, fat = 0.8, saturatedFat = 0.5,
                sodium = 90.0, calcium = 8.0, iron = 0.5, magnesium = 14.0, potassium = 50.0, zinc = 0.35,
                phosphorus = 35.0, selenium = 3.0, copper = 0.08, manganese = 0.25, vitaminE = 0.08,
                vitaminK = 0.1, vitaminB1 = 0.03, vitaminB2 = 0.01, vitaminB3 = 0.50, vitaminB6 = 0.04,
                folate = 6.0).detail(solubleFiberFraction = 0.29),
            servingVariants = listOf(
                ServingVariant("2 nests", 80.0),
                ServingVariant("1 nest", 40.0),
                ServingVariant("3 nests", 120.0),
            ),
            oilGrams = 0.5, oil = CookingOil.Coconut,
            styleOptions = listOf(StyleOption("Plain"), StyleOption("Coconut", VariantIngredient.CoconutFresh, 12.0))),
        food("Puttu", "cd0cd47f-45d5-4ab8-9f8e-69db6bb5d70e", FoodCategory.SouthIndian, 100.0, "1 cup (100 g)",
            Nutrients(calories = 155.0, carbs = 33.0, sugar = 0.8, fiber = 1.7, protein = 3.5, fat = 0.8, saturatedFat = 0.25,
                sodium = 80.0, calcium = 14.0, iron = 1.0, magnesium = 22.0, potassium = 105.0,
                zinc = 0.45, phosphorus = 45.0, selenium = 3.0, copper = 0.10, manganese = 0.30,
                vitaminE = 0.12, vitaminK = 0.3, vitaminB1 = 0.04, vitaminB2 = 0.02, vitaminB3 = 0.60,
                vitaminB6 = 0.05, folate = 8.0).detail(solubleFiberFraction = 0.25),
            styleOptions = listOf(StyleOption("Plain"), StyleOption("Coconut", VariantIngredient.CoconutFresh, 12.0))),
        food("Avial", "dea70250-af2e-40a7-9bbb-eff1f7812a35", FoodCategory.SouthIndian, 150.0, "1 serving (150 g)",
            Nutrients(calories = 110.0, carbs = 9.0, sugar = 2.6, fiber = 3.2, protein = 2.5, fat = 7.5,
                saturatedFat = 6.5, sodium = 280.0, calcium = 32.0, iron = 1.0, magnesium = 24.0,
                potassium = 280.0, zinc = 0.45, phosphorus = 55.0, selenium = 2.0, copper = 0.18,
                manganese = 0.35, vitaminA = 220.0, vitaminC = 10.0, vitaminE = 0.35, vitaminK = 15.0,
                vitaminB1 = 0.07, vitaminB2 = 0.05, vitaminB3 = 0.70, vitaminB6 = 0.14, folate = 28.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 3.0, oil = CookingOil.Coconut),
        food("Kerala Parotta", "69646fc5-a836-492b-9e67-e504b085e515", FoodCategory.SouthIndian, 90.0, "1 parotta (90 g)",
            Nutrients(calories = 320.0, carbs = 45.0, sugar = 1.0, fiber = 2.0, protein = 7.0, fat = 12.0,
                saturatedFat = 6.5, sodium = 380.0, calcium = 25.0, iron = 2.0, magnesium = 22.0,
                potassium = 95.0, zinc = 0.70, phosphorus = 75.0, selenium = 18.0, copper = 0.13,
                manganese = 0.45, vitaminE = 0.26, vitaminK = 0.8, vitaminB1 = 0.12, vitaminB2 = 0.05,
                vitaminB3 = 1.50, vitaminB6 = 0.06, folate = 22.0).detail(solubleFiberFraction = 0.23),
            oilGrams = 6.5, oil = CookingOil.Coconut),
        food("Bisi Bele Bath", "f803edea-a339-4e78-bcae-9cabf3b66bf0", FoodCategory.SouthIndian, 250.0, "1 plate (250 g)",
            Nutrients(calories = 145.0, carbs = 22.0, sugar = 1.6, fiber = 2.2, protein = 4.0, fat = 4.5,
                saturatedFat = 0.7, omega3 = 0.01, sodium = 320.0, calcium = 24.0, iron = 1.4,
                magnesium = 30.0, potassium = 210.0, zinc = 0.80, phosphorus = 75.0, selenium = 4.0,
                copper = 0.20, manganese = 0.45, vitaminA = 50.0, vitaminC = 4.0, vitaminE = 0.29,
                vitaminK = 4.4, vitaminB1 = 0.09, vitaminB2 = 0.04, vitaminB3 = 0.90, vitaminB6 = 0.12,
                folate = 28.0).detail(solubleFiberFraction = 0.27),
            oilGrams = 3.0, oil = CookingOil.Gingelly),
        food("Coconut Rice", "0033d20d-e79e-4168-b4b5-4fda51db7263", FoodCategory.SouthIndian, 200.0, "1 plate (200 g)",
            Nutrients(calories = 192.0, carbs = 24.0, sugar = 1.6, fiber = 1.5, protein = 3.0, fat = 9.0,
                saturatedFat = 7.8, sodium = 200.0, calcium = 16.0, iron = 0.9, magnesium = 24.0,
                potassium = 130.0, zinc = 0.55, phosphorus = 60.0, selenium = 4.5, copper = 0.18,
                manganese = 0.55, vitaminE = 0.15, vitaminK = 0.3, vitaminB1 = 0.05, vitaminB2 = 0.02,
                vitaminB3 = 0.80, vitaminB6 = 0.06, folate = 10.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 3.0, oil = CookingOil.Coconut),
        food("Sambar Vada", "5c9d6020-dd20-488f-9222-bc27d4869852", FoodCategory.SouthIndian, 220.0, "1 plate (220 g)",
            Nutrients(calories = 175.0, carbs = 20.0, sugar = 1.4, fiber = 3.0, protein = 6.0, fat = 8.0,
                saturatedFat = 3.1, omega3 = 0.01, sodium = 360.0, calcium = 30.0, iron = 1.5,
                magnesium = 36.0, potassium = 260.0, zinc = 0.95, phosphorus = 95.0, selenium = 3.0,
                copper = 0.26, manganese = 0.50, vitaminA = 30.0, vitaminC = 3.0, vitaminE = 1.21,
                vitaminK = 3.5, vitaminB1 = 0.11, vitaminB2 = 0.05, vitaminB3 = 0.90, vitaminB6 = 0.11,
                folate = 42.0).detail(solubleFiberFraction = 0.32),
            oilGrams = 6.0, oil = CookingOil.Palmolein),
        food("Filter Coffee", "608fad0a-d94b-4978-80a6-df3ea801c568", FoodCategory.Beverage, 150.0, "1 tumbler (150 ml)",
            Nutrients(calories = 45.0, carbs = 6.0, sugar = 6.0, protein = 1.8, fat = 1.6, saturatedFat = 1.00, cholesterol = 6.0,
                sodium = 25.0, calcium = 60.0, magnesium = 7.0, potassium = 90.0, zinc = 0.30, phosphorus = 55.0,
                selenium = 1.5, copper = 0.02, manganese = 0.04, vitaminA = 22.0, vitaminE = 0.02,
                vitaminK = 0.1, vitaminB1 = 0.03, vitaminB2 = 0.18, vitaminB3 = 0.30, vitaminB6 = 0.04,
                vitaminB12 = 0.20, folate = 5.0)),

        // South Indian — additions (seed v3): breakfast, lunch/dinner, accompaniments.
        food("Poori", "a7045470-45f3-44c0-9bf0-8b7c372eafe6", FoodCategory.SouthIndian, 30.0, "1 poori (30 g)",
            Nutrients(calories = 310.0, carbs = 44.0, sugar = 0.5, fiber = 5.5, protein = 7.5, fat = 12.0,
                saturatedFat = 4.8, omega3 = 0.02, sodium = 220.0, calcium = 20.0, iron = 2.4,
                magnesium = 60.0, potassium = 130.0, zinc = 1.00, phosphorus = 75.0, selenium = 20.0,
                copper = 0.14, manganese = 0.50, vitaminE = 2.15, vitaminK = 2.2, vitaminB1 = 0.15,
                vitaminB2 = 0.05, vitaminB3 = 1.80, vitaminB6 = 0.08, folate = 20.0).detail(solubleFiberFraction = 0.14),
            oilGrams = 10.0, oil = CookingOil.Palmolein),
        food("Set Dosa", "f8abc754-6332-4c4d-9b89-d4ae8c4d5814", FoodCategory.SouthIndian, 50.0, "1 dosa (50 g)",
            Nutrients(calories = 175.0, carbs = 28.0, sugar = 0.6, fiber = 1.4, protein = 5.0, fat = 5.0,
                saturatedFat = 1.9, omega3 = 0.01, sodium = 230.0, calcium = 18.0, iron = 1.1,
                magnesium = 22.0, potassium = 80.0, zinc = 0.55, phosphorus = 75.0, selenium = 4.5,
                copper = 0.12, manganese = 0.38, vitaminE = 0.68, vitaminK = 0.6, vitaminB1 = 0.06,
                vitaminB2 = 0.04, vitaminB3 = 1.00, vitaminB6 = 0.05, folate = 16.0).detail(solubleFiberFraction = 0.33),
            oilGrams = 3.5, oil = CookingOil.Palmolein,
            styleOptions = gheeStyle(3.0)),
        food("Onion Dosa", "37ad7a3a-e410-429e-83d3-d24321d58aa8", FoodCategory.SouthIndian, 100.0, "1 dosa (100 g)",
            Nutrients(calories = 180.0, carbs = 28.0, sugar = 1.6, fiber = 2.0, protein = 5.0, fat = 5.5,
                saturatedFat = 2.2, omega3 = 0.01, sodium = 260.0, calcium = 22.0, iron = 1.3,
                magnesium = 24.0, potassium = 140.0, zinc = 0.55, phosphorus = 80.0, selenium = 4.0,
                copper = 0.13, manganese = 0.36, vitaminC = 4.0, vitaminE = 0.79, vitaminK = 0.8,
                vitaminB1 = 0.07, vitaminB2 = 0.04, vitaminB3 = 1.10, vitaminB6 = 0.08, folate = 18.0).detail(solubleFiberFraction = 0.31),
            oilGrams = 4.0, oil = CookingOil.Palmolein,
            styleOptions = gheeStyle(4.0)),
        food("Rava Dosa", "66606222-6515-42c0-bfb3-e586131c1728", FoodCategory.SouthIndian, 80.0, "1 dosa (80 g)",
            Nutrients(calories = 185.0, carbs = 32.0, sugar = 0.8, fiber = 1.0, protein = 4.0, fat = 5.0,
                saturatedFat = 2.0, omega3 = 0.01, sodium = 280.0, calcium = 12.0, iron = 1.4,
                magnesium = 22.0, potassium = 90.0, zinc = 0.60, phosphorus = 70.0, selenium = 8.0,
                copper = 0.12, manganese = 0.35, vitaminA = 4.0, vitaminE = 0.79, vitaminK = 0.7,
                vitaminB1 = 0.08, vitaminB2 = 0.04, vitaminB3 = 1.20, vitaminB6 = 0.05, folate = 24.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 4.0, oil = CookingOil.Palmolein,
            styleOptions = gheeStyle(4.0)),
        food("Egg Dosa (Muttai Dosa)", "d77bee40-6b05-41af-9098-df0f18f80ec3", FoodCategory.SouthIndian, 120.0, "1 dosa (120 g)",
            Nutrients(calories = 195.0, carbs = 24.0, sugar = 0.8, fiber = 1.2, protein = 7.5, fat = 8.0,
                saturatedFat = 3.0, omega3 = 0.04, cholesterol = 95.0, sodium = 260.0, calcium = 22.0,
                iron = 1.5, magnesium = 22.0, potassium = 120.0, zinc = 0.75, phosphorus = 110.0,
                selenium = 11.0, copper = 0.11, manganese = 0.30, vitaminA = 42.0, vitaminD = 0.5,
                vitaminE = 0.99, vitaminK = 0.8, vitaminB1 = 0.07, vitaminB2 = 0.12, vitaminB3 = 0.90,
                vitaminB6 = 0.07, vitaminB12 = 0.30, folate = 24.0).detail(solubleFiberFraction = 0.33),
            oilGrams = 4.0, oil = CookingOil.Palmolein),
        food("Pesarattu", "c6923e76-f653-4655-9554-7e21465e8a90", FoodCategory.SouthIndian, 90.0, "1 dosa (90 g)",
            Nutrients(calories = 165.0, carbs = 23.0, sugar = 1.0, fiber = 4.0, protein = 9.0, fat = 4.0,
                saturatedFat = 1.4, omega3 = 0.01, sodium = 200.0, calcium = 30.0, iron = 2.2,
                magnesium = 60.0, potassium = 320.0, zinc = 1.10, phosphorus = 120.0, selenium = 2.5,
                copper = 0.32, manganese = 0.55, vitaminC = 2.0, vitaminE = 0.65, vitaminK = 2.7,
                vitaminB1 = 0.18, vitaminB2 = 0.08, vitaminB3 = 1.40, vitaminB6 = 0.10, folate = 110.0).detail(solubleFiberFraction = 0.16),
            oilGrams = 2.5, oil = CookingOil.Palmolein),
        food("Adai", "b99fd993-71bd-4045-aa47-9512a55b6407", FoodCategory.SouthIndian, 100.0, "1 adai (100 g)",
            Nutrients(calories = 210.0, carbs = 32.0, sugar = 0.8, fiber = 5.0, protein = 9.0, fat = 4.0,
                saturatedFat = 1.4, omega3 = 0.01, sodium = 260.0, calcium = 30.0, iron = 2.5,
                magnesium = 60.0, potassium = 290.0, zinc = 1.20, phosphorus = 140.0, selenium = 3.5,
                copper = 0.35, manganese = 0.60, vitaminE = 0.65, vitaminK = 2.2, vitaminB1 = 0.20,
                vitaminB2 = 0.08, vitaminB3 = 1.50, vitaminB6 = 0.12, folate = 90.0).detail(solubleFiberFraction = 0.25),
            oilGrams = 2.5, oil = CookingOil.Palmolein),
        food("Ragi Dosa", "ff44e195-8c84-4487-bfb2-cba32445bb44", FoodCategory.SouthIndian, 80.0, "1 dosa (80 g)",
            Nutrients(calories = 195.0, carbs = 33.0, sugar = 0.8, fiber = 4.5, protein = 6.0, fat = 4.0,
                saturatedFat = 1.4, omega3 = 0.01, sodium = 230.0, calcium = 200.0, iron = 3.0,
                magnesium = 90.0, potassium = 270.0, zinc = 0.65, phosphorus = 90.0, selenium = 3.0,
                copper = 0.20, manganese = 0.85, vitaminE = 0.60, vitaminK = 1.0, vitaminB1 = 0.10,
                vitaminB2 = 0.05, vitaminB3 = 0.90, vitaminB6 = 0.06, folate = 30.0).detail(solubleFiberFraction = 0.18),
            oilGrams = 2.5, oil = CookingOil.Palmolein),
        food("Bonda", "d12d905b-f02b-4bf9-9104-0856e68af123", FoodCategory.SouthIndian, 50.0, "1 bonda (50 g)",
            Nutrients(calories = 250.0, carbs = 28.0, sugar = 1.0, fiber = 2.2, protein = 5.0, fat = 13.0,
                saturatedFat = 5.6, omega3 = 0.02, sodium = 320.0, calcium = 22.0, iron = 1.5,
                magnesium = 28.0, potassium = 240.0, zinc = 0.80, phosphorus = 85.0, selenium = 3.5,
                copper = 0.16, manganese = 0.40, vitaminC = 4.0, vitaminE = 2.12, vitaminK = 3.0,
                vitaminB1 = 0.09, vitaminB2 = 0.04, vitaminB3 = 1.00, vitaminB6 = 0.12,
                folate = 30.0).detail(solubleFiberFraction = 0.33),
            oilGrams = 12.0, oil = CookingOil.Palmolein),
        food("Mysore Bonda", "43104a46-a4f4-4ae9-b104-9dbc5386c4c2", FoodCategory.SouthIndian, 40.0, "1 piece (40 g)",
            Nutrients(calories = 245.0, carbs = 26.0, sugar = 0.8, fiber = 2.0, protein = 5.5, fat = 13.0,
                saturatedFat = 5.6, omega3 = 0.02, sodium = 300.0, calcium = 20.0, iron = 1.4,
                magnesium = 26.0, potassium = 200.0, zinc = 0.70, phosphorus = 95.0, selenium = 3.0,
                copper = 0.28, manganese = 0.45, vitaminE = 2.07, vitaminK = 1.5, vitaminB1 = 0.12,
                vitaminB2 = 0.06, vitaminB3 = 1.10, vitaminB6 = 0.09, folate = 14.0).detail(solubleFiberFraction = 0.34),
            oilGrams = 12.0, oil = CookingOil.Palmolein),
        food("Bajji", "4004ae9a-d935-44c9-81ff-e8b613b844ed", FoodCategory.SouthIndian, 40.0, "1 piece (40 g)",
            Nutrients(calories = 280.0, carbs = 30.0, sugar = 1.4, fiber = 3.0, protein = 6.0, fat = 15.0,
                saturatedFat = 6.5, omega3 = 0.03, sodium = 340.0, calcium = 24.0, iron = 1.8,
                magnesium = 32.0, potassium = 230.0, zinc = 0.90, phosphorus = 90.0, selenium = 3.0,
                copper = 0.30, manganese = 0.42, vitaminC = 4.0, vitaminE = 2.49, vitaminK = 2.6,
                vitaminB1 = 0.11, vitaminB2 = 0.05, vitaminB3 = 1.00, vitaminB6 = 0.10, folate = 28.0).detail(solubleFiberFraction = 0.18),
            oilGrams = 14.0, oil = CookingOil.Palmolein),
        food("Kothu Parotta", "67052ec4-0971-4054-96e8-49ed580a07a7", FoodCategory.SouthIndian, 200.0, "1 plate (200 g)",
            Nutrients(calories = 280.0, carbs = 36.0, sugar = 1.2, fiber = 2.4, protein = 8.0, fat = 11.0,
                saturatedFat = 4.0, omega3 = 0.01, sodium = 460.0, calcium = 32.0, iron = 2.0,
                magnesium = 24.0, potassium = 180.0, zinc = 1.00, phosphorus = 100.0, selenium = 12.0,
                copper = 0.13, manganese = 0.30, vitaminA = 20.0, vitaminC = 3.0, vitaminE = 1.42,
                vitaminK = 2.6, vitaminB1 = 0.10, vitaminB2 = 0.08, vitaminB3 = 1.40, vitaminB6 = 0.09,
                folate = 24.0).detail(solubleFiberFraction = 0.23),
            oilGrams = 7.0, oil = CookingOil.Palmolein),
        food("Veg Kurma", "3d233544-5370-400e-9dc8-8c4aa5cb019b", FoodCategory.SouthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 110.0, carbs = 9.0, sugar = 2.8, fiber = 2.5, protein = 3.0, fat = 7.0,
                saturatedFat = 6.0, sodium = 320.0, calcium = 26.0, iron = 1.0, magnesium = 22.0,
                potassium = 260.0, zinc = 0.55, phosphorus = 55.0, selenium = 1.5, copper = 0.12,
                manganese = 0.25, vitaminA = 120.0, vitaminC = 12.0, vitaminE = 0.40, vitaminK = 10.0,
                vitaminB1 = 0.07, vitaminB2 = 0.04, vitaminB3 = 0.70, vitaminB6 = 0.12, folate = 24.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 3.0, oil = CookingOil.Coconut),
        food("Vatha Kuzhambu", "f15c5c4b-6ef3-496f-be6e-7653babf25f6", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 90.0, carbs = 10.0, sugar = 2.6, fiber = 1.6, protein = 2.0, fat = 5.0,
                saturatedFat = 0.7, omega3 = 0.01, sodium = 380.0, calcium = 22.0, iron = 1.0,
                magnesium = 18.0, potassium = 220.0, zinc = 0.45, phosphorus = 45.0, selenium = 1.0,
                copper = 0.13, manganese = 0.30, vitaminA = 20.0, vitaminC = 4.0, vitaminE = 0.36,
                vitaminK = 2.6, vitaminB1 = 0.05, vitaminB2 = 0.03, vitaminB3 = 0.60, vitaminB6 = 0.10,
                folate = 14.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 4.5, oil = CookingOil.Gingelly),
        food("Mor Kuzhambu", "3999c59a-da7a-4a35-95c1-1640a2e3cb9b", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 85.0, carbs = 6.0, sugar = 2.0, fiber = 1.0, protein = 3.0, fat = 5.0,
                saturatedFat = 3.0, omega3 = 0.01, sodium = 280.0, calcium = 80.0, iron = 0.7,
                magnesium = 18.0, potassium = 180.0, zinc = 0.45, phosphorus = 60.0, selenium = 2.0,
                copper = 0.10, manganese = 0.20, vitaminA = 14.0, vitaminC = 1.0, vitaminE = 0.23,
                vitaminK = 2.8, vitaminB1 = 0.05, vitaminB2 = 0.08, vitaminB3 = 0.50, vitaminB6 = 0.07,
                vitaminB12 = 0.20, folate = 16.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 2.0, oil = CookingOil.Gingelly),
        food("Paruppu Usili", "3ced644d-3344-47c5-a124-173343e0986a", FoodCategory.SouthIndian, 120.0, "1 bowl (120 g)",
            Nutrients(calories = 175.0, carbs = 12.0, sugar = 1.6, fiber = 4.5, protein = 9.0, fat = 9.0,
                saturatedFat = 1.4, omega3 = 0.02, sodium = 280.0, calcium = 30.0, iron = 2.4,
                magnesium = 50.0, potassium = 340.0, zinc = 1.20, phosphorus = 130.0, selenium = 3.0,
                copper = 0.32, manganese = 0.55, vitaminA = 25.0, vitaminC = 6.0, vitaminE = 0.45,
                vitaminK = 24.0, vitaminB1 = 0.18, vitaminB2 = 0.08, vitaminB3 = 1.30, vitaminB6 = 0.13,
                folate = 80.0).detail(solubleFiberFraction = 0.17),
            oilGrams = 7.0, oil = CookingOil.Gingelly),
        // Corrected in seed v23. The water-soluble vitamins were raw cabbage's,
        // carried into a dish that spends ten minutes in a hot pan: vitamin C
        // stood at 30 mg against raw cabbage's own 36.6, and folate at 40
        // against 43, as though the cooking cost nothing. Sauteing destroys
        // roughly half the ascorbate and a third of the folate, and the B6 goes
        // with them. Beans Poriyal two entries down was already costed this way,
        // which is what made this one visible as the outlier.
        //
        // Vitamin K is deliberately NOT reduced: it is fat-soluble and heat
        // stable, and 53.6 against raw cabbage's 76 is already just the dilution
        // by oil and tempering. Nothing was wrong with it.
        food("Cabbage Poriyal", "3e66e394-facb-40ca-b9d7-10ac4cc9058b", FoodCategory.SouthIndian, 100.0, "1 bowl (100 g)",
            Nutrients(calories = 80.0, carbs = 7.0, sugar = 2.6, fiber = 2.8, protein = 2.0, fat = 5.0,
                saturatedFat = 0.7, omega3 = 0.04, sodium = 240.0, calcium = 40.0, iron = 0.8,
                magnesium = 14.0, potassium = 200.0, zinc = 0.30, phosphorus = 35.0, selenium = 0.6,
                copper = 0.05, manganese = 0.18, vitaminA = 6.0, vitaminC = 14.0, vitaminE = 0.26,
                vitaminK = 53.6, vitaminB1 = 0.04, vitaminB2 = 0.04, vitaminB3 = 0.30, vitaminB6 = 0.07,
                folate = 22.0).detail(solubleFiberFraction = 0.29),
            oilGrams = 4.5, oil = CookingOil.Gingelly),
        food("Beans Poriyal", "a7ea6e58-8241-4684-ad93-66d08640930f", FoodCategory.SouthIndian, 100.0, "1 bowl (100 g)",
            Nutrients(calories = 90.0, carbs = 8.0, sugar = 2.4, fiber = 3.0, protein = 2.5, fat = 5.0,
                saturatedFat = 0.7, omega3 = 0.06, sodium = 230.0, calcium = 36.0, iron = 1.0,
                magnesium = 22.0, potassium = 230.0, zinc = 0.35, phosphorus = 40.0, selenium = 0.6,
                copper = 0.07, manganese = 0.22, vitaminA = 30.0, vitaminC = 8.0, vitaminE = 0.36,
                vitaminK = 32.6, vitaminB1 = 0.06, vitaminB2 = 0.05, vitaminB3 = 0.50, vitaminB6 = 0.07,
                folate = 28.0).detail(solubleFiberFraction = 0.16),
            oilGrams = 4.5, oil = CookingOil.Gingelly),
        food("Carrot Poriyal", "1363d11b-e1cc-4bcd-aa62-7d7f58a72e54", FoodCategory.SouthIndian, 100.0, "1 bowl (100 g)",
            Nutrients(calories = 85.0, carbs = 9.0, sugar = 3.6, fiber = 2.8, protein = 1.5, fat = 5.0,
                saturatedFat = 0.7, omega3 = 0.01, sodium = 240.0, calcium = 32.0, iron = 0.6,
                magnesium = 12.0, potassium = 280.0, zinc = 0.30, phosphorus = 35.0, selenium = 0.6,
                copper = 0.05, manganese = 0.15, vitaminA = 690.0, vitaminC = 5.0, vitaminE = 0.56,
                vitaminK = 10.6, vitaminB1 = 0.05, vitaminB2 = 0.04, vitaminB3 = 0.60, vitaminB6 = 0.10,
                folate = 16.0).detail(solubleFiberFraction = 0.3),
            oilGrams = 4.5, oil = CookingOil.Gingelly),
        food("Mor Kootu", "71a614f5-94a7-4621-9b18-f25b780095fb", FoodCategory.SouthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 95.0, carbs = 8.0, sugar = 1.8, fiber = 2.5, protein = 3.0, fat = 6.0,
                saturatedFat = 3.8, omega3 = 0.01, sodium = 260.0, calcium = 60.0, iron = 0.9,
                magnesium = 24.0, potassium = 240.0, zinc = 0.45, phosphorus = 70.0, selenium = 2.0,
                copper = 0.13, manganese = 0.30, vitaminA = 12.0, vitaminC = 3.0, vitaminE = 0.23,
                vitaminK = 3.3, vitaminB1 = 0.07, vitaminB2 = 0.07, vitaminB3 = 0.60, vitaminB6 = 0.09,
                vitaminB12 = 0.20, folate = 18.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 2.0, oil = CookingOil.Gingelly),
        food("Chow Chow Kootu", "12f36120-ee55-41d4-9fa4-338003e1ebf5", FoodCategory.SouthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 80.0, carbs = 8.0, sugar = 1.6, fiber = 2.4, protein = 3.0, fat = 4.5,
                saturatedFat = 2.5, omega3 = 0.01, sodium = 250.0, calcium = 28.0, iron = 1.0,
                magnesium = 22.0, potassium = 260.0, zinc = 0.40, phosphorus = 55.0, selenium = 1.5,
                copper = 0.11, manganese = 0.28, vitaminA = 4.0, vitaminC = 10.0, vitaminE = 0.23,
                vitaminK = 3.3, vitaminB1 = 0.06, vitaminB2 = 0.04, vitaminB3 = 0.50, vitaminB6 = 0.08,
                folate = 20.0).detail(solubleFiberFraction = 0.24),
            oilGrams = 2.0, oil = CookingOil.Gingelly),
        food("Sambar Sadam", "4d274bc4-1ab2-41b7-9686-ae3bd5c091dd", FoodCategory.SouthIndian, 250.0, "1 plate (250 g)",
            Nutrients(calories = 132.0, carbs = 21.0, sugar = 1.6, fiber = 2.6, protein = 4.0, fat = 3.5,
                saturatedFat = 0.5, omega3 = 0.01, sodium = 360.0, calcium = 24.0, iron = 1.4,
                magnesium = 28.0, potassium = 240.0, zinc = 0.75, phosphorus = 75.0, selenium = 5.5,
                copper = 0.16, manganese = 0.40, vitaminA = 40.0, vitaminC = 4.0, vitaminE = 0.29,
                vitaminK = 4.4, vitaminB1 = 0.09, vitaminB2 = 0.04, vitaminB3 = 1.00, vitaminB6 = 0.09,
                folate = 30.0).detail(solubleFiberFraction = 0.27),
            oilGrams = 3.0, oil = CookingOil.Gingelly),
        food("Tomato Rice", "da92170d-05e1-43fa-b9d4-e0c836b43c10", FoodCategory.SouthIndian, 200.0, "1 plate (200 g)",
            Nutrients(calories = 155.0, carbs = 25.0, sugar = 2.4, fiber = 1.4, protein = 3.0, fat = 4.5,
                saturatedFat = 0.7, omega3 = 0.01, sodium = 300.0, calcium = 18.0, iron = 1.0,
                magnesium = 16.0, potassium = 180.0, zinc = 0.55, phosphorus = 55.0, selenium = 4.5,
                copper = 0.11, manganese = 0.35, vitaminA = 60.0, vitaminC = 8.0, vitaminE = 0.26,
                vitaminK = 3.0, vitaminB1 = 0.07, vitaminB2 = 0.03, vitaminB3 = 1.00, vitaminB6 = 0.10,
                folate = 12.0).detail(solubleFiberFraction = 0.27),
            oilGrams = 4.0, oil = CookingOil.Gingelly),
        food("Papad (fried)", "25ccbbec-e0c4-4b8e-8a3f-80d2378a807b", FoodCategory.SouthIndian, 13.0, "1 piece (13 g)",
            Nutrients(calories = 380.0, carbs = 47.0, sugar = 1.0, fiber = 8.0, protein = 23.0, fat = 12.0,
                saturatedFat = 5.2, omega3 = 0.02, sodium = 1640.0, calcium = 80.0, iron = 4.8,
                magnesium = 130.0, potassium = 720.0, zinc = 1.80, phosphorus = 200.0, selenium = 5.0,
                copper = 0.45, manganese = 0.70, vitaminE = 1.96, vitaminK = 1.9, vitaminB1 = 0.12,
                vitaminB2 = 0.09, vitaminB3 = 1.50, vitaminB6 = 0.15, folate = 90.0).detail(solubleFiberFraction = 0.36),
            oilGrams = 11.0, oil = CookingOil.Palmolein),
        food("Mango Pickle", "e90eee1d-7dc7-499c-8422-3483f06ccc43", FoodCategory.SouthIndian, 10.0, "1 tbsp (10 g)",
            Nutrients(calories = 180.0, carbs = 12.0, sugar = 4.0, fiber = 2.4, protein = 1.5, fat = 14.0,
                saturatedFat = 2.0, omega3 = 0.04, sodium = 2200.0, calcium = 20.0, iron = 0.8,
                magnesium = 20.0, potassium = 140.0, zinc = 0.35, phosphorus = 20.0, selenium = 0.5,
                copper = 0.10, manganese = 0.25, vitaminA = 90.0, vitaminC = 14.0, vitaminE = 0.98,
                vitaminK = 4.8, vitaminB1 = 0.03, vitaminB2 = 0.03, vitaminB3 = 0.40, vitaminB6 = 0.08,
                folate = 12.0).detail(solubleFiberFraction = 0.45),
            oilGrams = 13.0, oil = CookingOil.Gingelly),
        food("Lime Pickle", "02d549ef-b999-4f88-9a2b-f77a92f22ff5", FoodCategory.SouthIndian, 10.0, "1 tbsp (10 g)",
            Nutrients(calories = 110.0, carbs = 16.0, sugar = 2.0, fiber = 2.0, protein = 1.0, fat = 4.0,
                saturatedFat = 0.6, omega3 = 0.01, sodium = 2400.0, calcium = 22.0, iron = 0.6,
                magnesium = 14.0, potassium = 120.0, zinc = 0.20, phosphorus = 20.0, selenium = 0.6,
                copper = 0.18, manganese = 0.45, vitaminC = 12.0, vitaminE = 0.25, vitaminK = 1.5,
                vitaminB1 = 0.03, vitaminB2 = 0.04, vitaminB3 = 0.50, vitaminB6 = 0.08, folate = 6.0).detail(solubleFiberFraction = 0.4),
            oilGrams = 3.5, oil = CookingOil.Gingelly),

        // South Indian — household additions: festival items, regional staples
        // (Karnataka / Kerala / Andhra-Telangana), tiffin sweets, and the
        // everyday vegetable curries that round out a home-cooked meal.
        food("Sweet Pongal (Sakkarai Pongal)", "0da99646-c180-47c4-8d9d-e50c9a80e4b7", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 260.0, carbs = 45.0, sugar = 20.0, fiber = 1.6, protein = 2.8, fat = 7.5,
                saturatedFat = 4.2, omega3 = 0.09, sodium = 60.0, calcium = 35.0, iron = 1.4,
                magnesium = 28.0, potassium = 130.0, zinc = 0.70, phosphorus = 70.0, selenium = 3.5,
                copper = 0.15, manganese = 0.40, vitaminA = 60.0, vitaminE = 0.38, vitaminK = 1.2,
                cholesterol = 16.6,
                vitaminB1 = 0.08, vitaminB2 = 0.05, vitaminB3 = 0.80, vitaminB6 = 0.07, folate = 12.0).detail(solubleFiberFraction = 0.25),
            oilGrams = 6.5, oil = CookingOil.Ghee),
        food("Khara Bhath", "17e8252b-8b7f-4d3e-a877-990118ab5729", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 140.0, carbs = 18.0, sugar = 1.6, fiber = 2.0, protein = 3.2, fat = 6.0,
                saturatedFat = 0.9, omega3 = 0.01, sodium = 320.0, calcium = 24.0, iron = 1.4,
                magnesium = 22.0, potassium = 180.0, zinc = 0.70, phosphorus = 55.0, selenium = 8.0,
                copper = 0.12, manganese = 0.35, vitaminA = 80.0, vitaminC = 6.0, vitaminE = 0.32,
                vitaminK = 3.7, vitaminB1 = 0.12, vitaminB2 = 0.05, vitaminB3 = 1.20, vitaminB6 = 0.08,
                folate = 18.0).detail(solubleFiberFraction = 0.18),
            oilGrams = 5.0, oil = CookingOil.Gingelly),
        food("Rava Kesari", "2f91386e-3437-4989-b0b9-a09d7bc781ad", FoodCategory.SouthIndian, 100.0, "1 piece (100 g)",
            Nutrients(calories = 330.0, carbs = 55.0, sugar = 30.0, fiber = 0.8, protein = 4.0, fat = 11.0,
                saturatedFat = 6.4, omega3 = 0.14, sodium = 40.0, calcium = 14.0, iron = 1.0,
                magnesium = 18.0, potassium = 70.0, zinc = 0.45, phosphorus = 35.0, selenium = 6.0,
                copper = 0.08, manganese = 0.25, vitaminA = 70.0, vitaminE = 0.48, vitaminK = 1.4,
                cholesterol = 25.6,
                vitaminB1 = 0.09, vitaminB2 = 0.04, vitaminB3 = 0.90, vitaminB6 = 0.04, folate = 10.0).detail(solubleFiberFraction = 0.16),
            oilGrams = 10.0, oil = CookingOil.Ghee),
        food("Neer Dosa", "ee132b7c-bc4d-449c-89d9-058fe94d30cd", FoodCategory.SouthIndian, 60.0, "1 dosa (60 g)",
            Nutrients(calories = 145.0, carbs = 26.0, sugar = 0.6, fiber = 1.0, protein = 3.0, fat = 3.5,
                saturatedFat = 3.0, sodium = 90.0, calcium = 10.0, iron = 0.8, magnesium = 16.0,
                potassium = 80.0, zinc = 0.35, phosphorus = 50.0, selenium = 4.0, copper = 0.10,
                manganese = 0.40, vitaminE = 0.10, vitaminK = 0.2, vitaminB1 = 0.06, vitaminB2 = 0.03,
                vitaminB3 = 0.90, vitaminB6 = 0.06, folate = 6.0).detail(solubleFiberFraction = 0.29),
            oilGrams = 1.5, oil = CookingOil.Coconut),
        food("Akki Roti", "f960298e-2367-4832-a340-c91faf3a4e1d", FoodCategory.SouthIndian, 80.0, "1 roti (80 g)",
            Nutrients(calories = 225.0, carbs = 40.0, sugar = 0.8, fiber = 1.6, protein = 4.0, fat = 5.5,
                saturatedFat = 2.1, omega3 = 0.01, sodium = 280.0, calcium = 14.0, iron = 1.2,
                magnesium = 18.0, potassium = 140.0, zinc = 0.50, phosphorus = 75.0, selenium = 6.0,
                copper = 0.14, manganese = 0.60, vitaminA = 10.0, vitaminC = 4.0, vitaminE = 0.79,
                vitaminK = 0.9, vitaminB1 = 0.09, vitaminB2 = 0.04, vitaminB3 = 1.40, vitaminB6 = 0.10,
                folate = 10.0).detail(solubleFiberFraction = 0.29),
            oilGrams = 4.0, oil = CookingOil.Palmolein),
        food("Ragi Mudde (Ragi Ball)", "f85e4b52-b3ce-42e4-b550-69dd0a2f7aa2", FoodCategory.SouthIndian, 150.0, "1 ball (150 g)",
            Nutrients(calories = 155.0, carbs = 32.0, sugar = 0.4, fiber = 4.0, protein = 4.0, fat = 1.0, saturatedFat = 0.20, sodium = 5.0,
                calcium = 175.0, iron = 2.2, magnesium = 70.0, potassium = 210.0, zinc = 0.45,
                phosphorus = 110.0, selenium = 2.5, copper = 0.35, manganese = 2.20, vitaminE = 0.20,
                vitaminK = 0.5, vitaminB1 = 0.15, vitaminB2 = 0.08, vitaminB3 = 0.80, vitaminB6 = 0.10,
                folate = 28.0).detail(solubleFiberFraction = 0.15)),
        food("Ragi Roti", "a960d649-33fa-4807-bcc5-b94b39c7c12c", FoodCategory.SouthIndian, 80.0, "1 roti (80 g)",
            Nutrients(calories = 210.0, carbs = 38.0, sugar = 0.8, fiber = 4.5, protein = 5.0, fat = 4.0,
                saturatedFat = 1.6, omega3 = 0.01, sodium = 280.0, calcium = 250.0, iron = 3.2,
                magnesium = 110.0, potassium = 320.0, zinc = 0.60, phosphorus = 130.0, selenium = 2.5,
                copper = 0.40, manganese = 2.60, vitaminE = 0.68, vitaminK = 1.0, vitaminB1 = 0.18,
                vitaminB2 = 0.10, vitaminB3 = 1.00, vitaminB6 = 0.12, folate = 30.0).detail(solubleFiberFraction = 0.15),
            oilGrams = 3.0, oil = CookingOil.Palmolein),
        food("Kuzhi Paniyaram (Paddu)", "42508a9a-6d16-460f-94a3-c4f12c648e1f", FoodCategory.SouthIndian, 100.0, "5 pieces (100 g)",
            Nutrients(calories = 170.0, carbs = 28.0, sugar = 0.8, fiber = 2.0, protein = 5.0, fat = 5.0,
                saturatedFat = 2.0, omega3 = 0.01, sodium = 260.0, calcium = 22.0, iron = 1.4,
                magnesium = 26.0, potassium = 130.0, zinc = 0.60, phosphorus = 90.0, selenium = 5.0,
                copper = 0.18, manganese = 0.50, vitaminE = 0.79, vitaminK = 0.9, vitaminB1 = 0.12,
                vitaminB2 = 0.06, vitaminB3 = 1.30, vitaminB6 = 0.09, folate = 22.0).detail(solubleFiberFraction = 0.33),
            oilGrams = 4.0, oil = CookingOil.Palmolein),
        food("Rava Idli", "d259dd25-8566-437e-a4c3-7ab538b0e168", FoodCategory.SouthIndian, 50.0, "1 idli (50 g)",
            Nutrients(calories = 155.0, carbs = 25.0, sugar = 1.0, fiber = 1.4, protein = 5.0, fat = 3.5,
                saturatedFat = 1.3, omega3 = 0.01, sodium = 280.0, calcium = 18.0, iron = 1.2,
                magnesium = 22.0, potassium = 90.0, zinc = 0.55, phosphorus = 60.0, selenium = 8.0,
                copper = 0.12, manganese = 0.35, vitaminA = 6.0, vitaminE = 0.55, vitaminK = 0.7,
                vitaminB1 = 0.10, vitaminB2 = 0.05, vitaminB3 = 1.20, vitaminB6 = 0.07, folate = 26.0).detail(solubleFiberFraction = 0.18),
            oilGrams = 2.5, oil = CookingOil.Palmolein),
        food("Masala Vada (Paruppu Vadai)", "00138772-7e8c-408b-846b-36dc0c811ea6", FoodCategory.SouthIndian, 35.0, "1 vada (35 g)",
            Nutrients(calories = 280.0, carbs = 28.0, sugar = 1.2, fiber = 5.0, protein = 11.0, fat = 14.0,
                saturatedFat = 6.0, omega3 = 0.03, sodium = 360.0, calcium = 40.0, iron = 2.5,
                magnesium = 60.0, potassium = 380.0, zinc = 1.40, phosphorus = 160.0, selenium = 4.0,
                copper = 0.45, manganese = 0.70, vitaminC = 2.0, vitaminE = 2.38, vitaminK = 3.0,
                vitaminB1 = 0.25, vitaminB2 = 0.10, vitaminB3 = 1.50, vitaminB6 = 0.15, folate = 110.0).detail(solubleFiberFraction = 0.16),
            oilGrams = 13.0, oil = CookingOil.Palmolein),
        food("Semiya Payasam (Vermicelli Kheer)", "fafe69e3-f975-4639-afcb-53ebdfd3b861", FoodCategory.SouthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 165.0, carbs = 25.0, sugar = 15.0, protein = 4.0, fat = 5.0, saturatedFat = 3.1,
                omega3 = 0.04, cholesterol = 12.0, sodium = 40.0, calcium = 90.0, iron = 0.4,
                magnesium = 11.0, potassium = 110.0, zinc = 0.50, phosphorus = 75.0, selenium = 5.0, copper = 0.08,
                manganese = 0.20, vitaminA = 60.0, vitaminE = 0.28, vitaminK = 0.8, vitaminB1 = 0.08,
                vitaminB2 = 0.15, vitaminB3 = 0.70, vitaminB6 = 0.05, vitaminB12 = 0.30, folate = 8.0),
            oilGrams = 3.0, oil = CookingOil.Ghee),
        food("Paal Payasam (Rice Kheer)", "dc8d4dc8-2457-4a94-8ac6-2172e1550630", FoodCategory.SouthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 135.0, carbs = 21.0, sugar = 14.0, protein = 4.0, fat = 4.0, saturatedFat = 2.5,
                omega3 = 0.03, cholesterol = 10.0, sodium = 45.0, calcium = 110.0, iron = 0.3,
                magnesium = 12.0, potassium = 130.0, zinc = 0.45, phosphorus = 85.0, selenium = 3.0, copper = 0.06,
                manganese = 0.20, vitaminA = 40.0, vitaminE = 0.16, vitaminK = 0.6, vitaminB1 = 0.06,
                vitaminB2 = 0.18,
                vitaminB3 = 0.50, vitaminB6 = 0.05, vitaminB12 = 0.30, folate = 6.0),
            oilGrams = 2.0, oil = CookingOil.Ghee),
        food("Sundal (Channa)", "0bd68fcf-68c4-4171-ad41-1f4e96df6810", FoodCategory.SouthIndian, 100.0, "1 bowl (100 g)",
            Nutrients(calories = 170.0, carbs = 22.0, sugar = 2.6, fiber = 6.5, protein = 8.0, fat = 5.5,
                saturatedFat = 1.1, omega3 = 0.01, sodium = 280.0, calcium = 48.0, iron = 2.5,
                magnesium = 50.0, potassium = 320.0, zinc = 1.30, phosphorus = 150.0, selenium = 3.5,
                copper = 0.35, manganese = 0.60, vitaminC = 2.0, vitaminE = 0.34, vitaminK = 4.4,
                vitaminB1 = 0.12, vitaminB2 = 0.06, vitaminB3 = 0.60, vitaminB6 = 0.12, folate = 95.0).detail(solubleFiberFraction = 0.1),
            oilGrams = 3.0, oil = CookingOil.Gingelly),
        food("Thattai", "6ad94355-437c-4cd2-8444-6ad32e53beb1", FoodCategory.SouthIndian, 15.0, "1 piece (15 g)",
            Nutrients(calories = 510.0, carbs = 60.0, sugar = 1.0, fiber = 4.0, protein = 10.0, fat = 26.0,
                saturatedFat = 11.2, omega3 = 0.05, sodium = 520.0, calcium = 30.0, iron = 3.0,
                magnesium = 50.0, potassium = 220.0, zinc = 1.20, phosphorus = 140.0, selenium = 6.0,
                copper = 0.30, manganese = 0.80, vitaminE = 4.14, vitaminK = 2.9, vitaminB1 = 0.20,
                vitaminB2 = 0.07, vitaminB3 = 1.80, vitaminB6 = 0.12).detail(solubleFiberFraction = 0.22),
            oilGrams = 24.0, oil = CookingOil.Palmolein),
        food("Mixture (South Indian)", "285ef132-28ea-4899-931d-fb53dc029519", FoodCategory.SouthIndian, 30.0, "1 small bowl (30 g)",
            Nutrients(calories = 540.0, carbs = 52.0, sugar = 2.0, fiber = 5.0, protein = 13.0, fat = 30.0,
                saturatedFat = 13.0, omega3 = 0.06, sodium = 700.0, calcium = 40.0, iron = 3.5,
                magnesium = 70.0, potassium = 320.0, zinc = 1.40, phosphorus = 180.0, selenium = 7.0,
                copper = 0.40, manganese = 1.00, vitaminE = 5.28, vitaminK = 4.2, vitaminB1 = 0.25,
                vitaminB2 = 0.10, vitaminB3 = 2.50, vitaminB6 = 0.18, folate = 40.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 23.0, oil = CookingOil.Palmolein),
        food("Idli Podi (Milagai Podi)", "f97370b5-b976-4a44-a4bb-d2f83856440f", FoodCategory.SouthIndian, 10.0, "1 tbsp (10 g)",
            Nutrients(calories = 440.0, carbs = 38.0, sugar = 2.0, fiber = 9.0, protein = 18.0, fat = 22.0,
                saturatedFat = 3.2, omega3 = 0.06, sodium = 1200.0, calcium = 80.0, iron = 6.0,
                magnesium = 150.0, potassium = 720.0, zinc = 2.40, phosphorus = 280.0, selenium = 8.0,
                copper = 0.80, manganese = 1.50, vitaminA = 60.0, vitaminC = 4.0, vitaminE = 1.48,
                vitaminK = 5.7, vitaminB1 = 0.35, vitaminB2 = 0.15, vitaminB3 = 3.50, vitaminB6 = 0.30,
                folate = 80.0).detail(solubleFiberFraction = 0.25),
            oilGrams = 10.0, oil = CookingOil.Gingelly),
        food("Vazhakkai Curry (Raw Banana)", "7f3a841b-e0cf-4158-9626-ef7090468381", FoodCategory.SouthIndian, 100.0, "1 bowl (100 g)",
            Nutrients(calories = 95.0, carbs = 18.0, sugar = 2.0, fiber = 2.8, protein = 1.5, fat = 2.5,
                saturatedFat = 0.4, omega3 = 0.01, sodium = 230.0, calcium = 16.0, iron = 0.7,
                magnesium = 28.0, potassium = 480.0, zinc = 0.25, phosphorus = 35.0, selenium = 1.0,
                copper = 0.10, manganese = 0.30, vitaminC = 14.0, vitaminE = 0.16, vitaminK = 1.3,
                vitaminB1 = 0.05, vitaminB2 = 0.04, vitaminB3 = 0.70, vitaminB6 = 0.25, folate = 14.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 2.5, oil = CookingOil.Gingelly),
        food("Keerai Masiyal", "b8799701-0da3-455d-b80f-6fdae4731cb3", FoodCategory.SouthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 55.0, carbs = 6.0, sugar = 0.6, fiber = 2.2, protein = 3.5, fat = 2.0,
                saturatedFat = 0.3, omega3 = 0.12, sodium = 220.0, calcium = 110.0, iron = 2.8,
                magnesium = 60.0, potassium = 380.0, zinc = 0.55, phosphorus = 45.0, selenium = 0.9,
                copper = 0.13, manganese = 0.60, vitaminA = 380.0, vitaminC = 18.0, vitaminE = 1.23,
                vitaminK = 380.3, vitaminB1 = 0.06, vitaminB2 = 0.12, vitaminB3 = 0.60, vitaminB6 = 0.15,
                folate = 130.0).detail(solubleFiberFraction = 0.27),
            oilGrams = 2.0, oil = CookingOil.Gingelly),
        food("Beetroot Poriyal", "493e7a58-a2a8-4ca2-a90f-de5444d6dc57", FoodCategory.SouthIndian, 100.0, "1 bowl (100 g)",
            Nutrients(calories = 75.0, carbs = 9.0, sugar = 5.0, fiber = 2.6, protein = 1.5, fat = 3.5,
                saturatedFat = 0.5, omega3 = 0.01, sodium = 240.0, calcium = 22.0, iron = 0.8,
                magnesium = 22.0, potassium = 280.0, zinc = 0.35, phosphorus = 40.0, selenium = 0.7,
                copper = 0.08, manganese = 0.35, vitaminA = 2.0, vitaminC = 4.0, vitaminE = 0.14,
                vitaminK = 1.4, vitaminB1 = 0.03, vitaminB2 = 0.04, vitaminB3 = 0.40, vitaminB6 = 0.07,
                folate = 90.0).detail(solubleFiberFraction = 0.21),
            oilGrams = 3.0, oil = CookingOil.Gingelly),
        food("Kathirikai Curry (Brinjal)", "9462873d-1672-4a0e-a8b4-37cb41603470", FoodCategory.SouthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 110.0, carbs = 9.0, sugar = 3.0, fiber = 3.0, protein = 1.5, fat = 7.0,
                saturatedFat = 1.1, omega3 = 0.02, sodium = 280.0, calcium = 16.0, iron = 0.6,
                magnesium = 16.0, potassium = 240.0, zinc = 0.25, phosphorus = 30.0, selenium = 0.4,
                copper = 0.10, manganese = 0.25, vitaminC = 3.0, vitaminE = 0.33, vitaminK = 3.3,
                vitaminB1 = 0.04, vitaminB2 = 0.04, vitaminB3 = 0.60, vitaminB6 = 0.09, folate = 18.0).detail(solubleFiberFraction = 0.28),
            oilGrams = 6.0, oil = CookingOil.Gingelly),
        food("Snake Gourd Poriyal (Pudalankai)", "2ffd7e78-56f1-4f56-b66a-a498b8b7534d", FoodCategory.SouthIndian, 100.0, "1 bowl (100 g)",
            Nutrients(calories = 60.0, carbs = 6.0, sugar = 1.6, fiber = 1.8, protein = 1.5, fat = 3.0,
                saturatedFat = 0.4, omega3 = 0.01, sodium = 220.0, calcium = 22.0, iron = 0.5,
                magnesium = 16.0, potassium = 200.0, zinc = 0.25, phosphorus = 25.0, selenium = 0.5,
                copper = 0.05, manganese = 0.15, vitaminA = 8.0, vitaminC = 6.0, vitaminE = 0.19,
                vitaminK = 5.4, vitaminB1 = 0.04, vitaminB2 = 0.04, vitaminB3 = 0.40, vitaminB6 = 0.06,
                folate = 12.0).detail(solubleFiberFraction = 0.26),
            oilGrams = 3.0, oil = CookingOil.Gingelly),
        food("Vegetable Stew (Kerala)", "57756020-d406-454f-b809-cef847da436a", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 110.0, carbs = 10.0, sugar = 2.4, fiber = 2.5, protein = 2.0, fat = 7.0,
                saturatedFat = 6.0, sodium = 260.0, calcium = 28.0, iron = 0.9, magnesium = 22.0,
                potassium = 320.0, zinc = 0.45, phosphorus = 45.0, selenium = 0.8, copper = 0.12,
                manganese = 0.30, vitaminA = 100.0, vitaminC = 14.0, vitaminE = 0.35, vitaminK = 12.0,
                vitaminB1 = 0.06, vitaminB2 = 0.04, vitaminB3 = 0.70, vitaminB6 = 0.12, folate = 22.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 3.0, oil = CookingOil.Coconut),
        food("Kadala Curry (Kerala Black Chickpea)", "75e5fed1-492d-4285-9695-ee078b1a70a5", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 140.0, carbs = 16.0, sugar = 2.0, fiber = 5.0, protein = 6.0, fat = 6.0,
                saturatedFat = 5.1, sodium = 320.0, calcium = 48.0, iron = 2.8, magnesium = 35.0,
                potassium = 320.0, zinc = 1.30, phosphorus = 115.0, selenium = 3.0, copper = 0.25,
                manganese = 0.80, vitaminA = 12.0, vitaminC = 2.0, vitaminE = 0.35, vitaminK = 4.0,
                vitaminB1 = 0.12, vitaminB2 = 0.06, vitaminB3 = 0.70, vitaminB6 = 0.13, folate = 110.0).detail(solubleFiberFraction = 0.1),
            oilGrams = 3.0, oil = CookingOil.Coconut),
        food("Mint Chutney (Pudina)", "966a045e-fe6a-432b-b732-9ad598d8ee20", FoodCategory.SouthIndian, 30.0, "2 tbsp (30 g)",
            Nutrients(calories = 50.0, carbs = 6.0, sugar = 1.6, fiber = 2.0, protein = 2.0, fat = 2.0,
                saturatedFat = 0.3, sodium = 240.0, calcium = 70.0, iron = 2.0, magnesium = 30.0, potassium = 220.0,
                zinc = 0.30, phosphorus = 60.0, selenium = 1.0, copper = 0.25, manganese = 0.90,
                vitaminA = 200.0, vitaminC = 8.0, vitaminE = 0.62, vitaminK = 50.2, vitaminB1 = 0.06,
                vitaminB2 = 0.18, vitaminB3 = 1.00, vitaminB6 = 0.18, folate = 30.0).detail(solubleFiberFraction = 0.3),
            oilGrams = 1.5, oil = CookingOil.Gingelly),
        food("Onion Chutney", "3f44bc21-d200-4008-839b-829a14e7c713", FoodCategory.SouthIndian, 30.0, "2 tbsp (30 g)",
            Nutrients(calories = 85.0, carbs = 10.0, sugar = 3.0, fiber = 1.6, protein = 1.5, fat = 4.5,
                saturatedFat = 0.7, omega3 = 0.01, sodium = 280.0, calcium = 14.0, iron = 0.5,
                magnesium = 14.0, potassium = 160.0, zinc = 0.25, phosphorus = 45.0, selenium = 0.6, copper = 0.12,
                manganese = 0.25, vitaminC = 6.0, vitaminE = 0.26, vitaminK = 1.5, vitaminB1 = 0.05,
                vitaminB2 = 0.04, vitaminB3 = 0.60, vitaminB6 = 0.15, folate = 12.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 4.0, oil = CookingOil.Gingelly),
        food("Peanut Chutney", "578b81b3-8423-41f5-84ad-ac2f57583129", FoodCategory.SouthIndian, 30.0, "2 tbsp (30 g)",
            Nutrients(calories = 360.0, carbs = 14.0, sugar = 2.4, fiber = 6.0, protein = 13.0, fat = 28.0,
                saturatedFat = 3.9, omega3 = 0.01, sodium = 320.0, calcium = 40.0, iron = 2.0,
                magnesium = 130.0, potassium = 480.0, zinc = 1.80, phosphorus = 160.0, selenium = 3.0,
                copper = 0.40, manganese = 0.90, vitaminA = 4.0, vitaminC = 1.0, vitaminE = 4.64,
                vitaminK = 0.9, vitaminB1 = 0.25, vitaminB2 = 0.08, vitaminB3 = 6.00, vitaminB6 = 0.18,
                folate = 80.0).detail(solubleFiberFraction = 0.17),
            oilGrams = 3.0, oil = CookingOil.Gingelly),
        food("Drumstick Sambar", "44fb45f5-656e-4a05-9b0a-8b0a9e714373", FoodCategory.SouthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 70.0, carbs = 9.0, sugar = 2.0, fiber = 3.5, protein = 2.8, fat = 2.4,
                saturatedFat = 0.4, sodium = 360.0, calcium = 38.0, iron = 1.5, magnesium = 36.0,
                potassium = 340.0, zinc = 0.65, phosphorus = 70.0, selenium = 2.0, copper = 0.15,
                manganese = 0.30, vitaminA = 80.0, vitaminC = 18.0, vitaminE = 0.37, vitaminK = 5.2,
                vitaminB1 = 0.10, vitaminB2 = 0.05, vitaminB3 = 0.80, vitaminB6 = 0.12, folate = 50.0).detail(solubleFiberFraction = 0.24),
            oilGrams = 1.5, oil = CookingOil.Gingelly),

        // ---------- NORTH INDIAN ----------
        food("Chapati / Roti", "3e420f59-d232-48ef-b878-021ea688cef5", FoodCategory.NorthIndian, 40.0, "1 chapati (40 g)",
            Nutrients(calories = 290.0, carbs = 55.0, sugar = 0.6, fiber = 7.0, protein = 10.0, fat = 3.5,
                saturatedFat = 1.1, sodium = 250.0, calcium = 24.0, iron = 3.5, magnesium = 80.0,
                potassium = 290.0, zinc = 1.60, phosphorus = 160.0, selenium = 30.0, copper = 0.25,
                manganese = 1.20, vitaminE = 0.84, vitaminK = 1.6, vitaminB1 = 0.25, vitaminB2 = 0.10,
                vitaminB3 = 2.50, vitaminB6 = 0.15,
                folate = 30.0).detail(amino = AA_WHEAT, solubleFiberFraction = 0.14),
            oilGrams = 1.5, oil = CookingOil.Palmolein),
        food("Naan", "f0d46c5d-9ea6-4fc6-8757-27bde8aa5e52", FoodCategory.NorthIndian, 90.0, "1 naan (90 g)",
            Nutrients(calories = 305.0, carbs = 50.0, sugar = 2.0, fiber = 2.5, protein = 9.0, fat = 7.5,
                saturatedFat = 2.5, omega3 = 0.01, cholesterol = 4.0, sodium = 460.0, calcium = 60.0,
                iron = 3.0, magnesium = 25.0, potassium = 120.0, zinc = 0.80, phosphorus = 110.0,
                selenium = 20.0, copper = 0.15, manganese = 0.60, vitaminE = 0.78, vitaminK = 1.0,
                vitaminB1 = 0.30, vitaminB2 = 0.20, vitaminB3 = 2.80, vitaminB6 = 0.05, folate = 40.0).detail(solubleFiberFraction = 0.23),
            oilGrams = 3.0, oil = CookingOil.Palmolein),
        food("Aloo Paratha", "27c4803e-f43d-4719-ad4e-2554a7990be4", FoodCategory.NorthIndian, 120.0, "1 paratha (120 g)",
            Nutrients(calories = 260.0, carbs = 36.0, sugar = 1.2, fiber = 3.5, protein = 6.0, fat = 9.5,
                saturatedFat = 3.4, omega3 = 0.01, sodium = 320.0, calcium = 22.0, iron = 2.0,
                magnesium = 30.0, potassium = 280.0, zinc = 1.00, phosphorus = 110.0, selenium = 18.0,
                copper = 0.18, manganese = 0.70, vitaminC = 8.0, vitaminE = 1.46, vitaminK = 2.5,
                vitaminB1 = 0.18, vitaminB2 = 0.08, vitaminB3 = 1.80, vitaminB6 = 0.20, folate = 26.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 6.0, oil = CookingOil.Palmolein),
        food("Plain Paratha", "a0bf840a-8151-484f-868b-3cd76cff6754", FoodCategory.NorthIndian, 80.0, "1 paratha (80 g)",
            Nutrients(calories = 310.0, carbs = 45.0, sugar = 0.8, fiber = 4.0, protein = 7.5, fat = 11.0,
                saturatedFat = 4.0, omega3 = 0.01, sodium = 300.0, calcium = 18.0, iron = 2.4,
                magnesium = 32.0, potassium = 130.0, zinc = 1.30, phosphorus = 120.0, selenium = 22.0,
                copper = 0.18, manganese = 0.85, vitaminE = 1.67, vitaminK = 2.0, vitaminB1 = 0.20,
                vitaminB2 = 0.08, vitaminB3 = 2.00, vitaminB6 = 0.10, folate = 28.0).detail(solubleFiberFraction = 0.14),
            oilGrams = 7.0, oil = CookingOil.Palmolein),
        food("Dal Tadka", "93b79256-e4c3-49f2-a7b2-10eb5847a599", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 90.0, carbs = 11.0, sugar = 0.8, fiber = 3.0, protein = 5.0, fat = 2.5,
                saturatedFat = 1.1, omega3 = 0.01, sodium = 330.0, calcium = 25.0, iron = 2.0,
                magnesium = 35.0, potassium = 270.0, zinc = 0.90, phosphorus = 90.0, selenium = 2.0,
                copper = 0.20, manganese = 0.35, vitaminA = 12.0, vitaminC = 2.0, vitaminE = 0.55,
                vitaminK = 1.7, vitaminB1 = 0.12, vitaminB2 = 0.05, vitaminB3 = 0.90, vitaminB6 = 0.10,
                folate = 60.0).detail(solubleFiberFraction = 0.26),
            oilGrams = 2.5, oil = CookingOil.Palmolein),
        food("Dal Makhani", "1eeda37c-be40-4af0-a201-124697440b76", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 160.0, carbs = 14.0, sugar = 1.6, fiber = 4.0, protein = 7.0, fat = 8.0,
                saturatedFat = 5.0, omega3 = 0.06, cholesterol = 14.0, sodium = 380.0, calcium = 55.0,
                iron = 2.4, magnesium = 38.0, potassium = 380.0, zinc = 1.20, phosphorus = 120.0,
                selenium = 2.5, copper = 0.30, manganese = 0.40, vitaminA = 50.0, vitaminE = 0.36,
                vitaminK = 1.8, vitaminB1 = 0.15, vitaminB2 = 0.10, vitaminB3 = 1.00, vitaminB6 = 0.12,
                folate = 65.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 4.0, oil = CookingOil.Ghee),
        food("Rajma", "354d5eaf-df5a-46c2-a7bd-b9c7092dd0b6", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 110.0, carbs = 16.0, sugar = 1.4, fiber = 5.0, protein = 6.0, fat = 2.5,
                saturatedFat = 1.1, omega3 = 0.09, sodium = 330.0, calcium = 42.0, iron = 2.8,
                magnesium = 33.0, potassium = 340.0, zinc = 0.90, phosphorus = 120.0, selenium = 1.5,
                copper = 0.18, manganese = 0.45, vitaminA = 10.0, vitaminC = 2.0, vitaminE = 0.50,
                vitaminK = 4.2, vitaminB1 = 0.15, vitaminB2 = 0.06, vitaminB3 = 0.70, vitaminB6 = 0.15,
                folate = 80.0).detail(solubleFiberFraction = 0.16),
            oilGrams = 2.5, oil = CookingOil.Palmolein),
        food("Chole / Chana Masala", "58b90be5-fa77-4967-8702-0e6a49482632", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 135.0, carbs = 17.0, sugar = 2.4, fiber = 5.0, protein = 5.6, fat = 5.0,
                saturatedFat = 1.55, omega3 = 0.01, sodium = 360.0, calcium = 50.0, iron = 2.5,
                magnesium = 32.0, potassium = 350.0, zinc = 1.10, phosphorus = 120.0, selenium = 3.0,
                copper = 0.23, manganese = 0.70, vitaminA = 14.0, vitaminC = 3.0, vitaminE = 0.83,
                vitaminK = 4.2, vitaminB1 = 0.12, vitaminB2 = 0.06, vitaminB3 = 0.90, vitaminB6 = 0.25,
                folate = 70.0).detail(solubleFiberFraction = 0.12),
            oilGrams = 3.0, oil = CookingOil.Palmolein),
        food("Paneer Butter Masala", "ea5d1deb-2c0b-4296-914b-8c233e72f7e1", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 250.0, carbs = 9.0, sugar = 4.0, fiber = 1.8, protein = 11.0, fat = 19.0,
                saturatedFat = 11.9, omega3 = 0.17, cholesterol = 48.0, sodium = 420.0, calcium = 220.0,
                iron = 1.2, magnesium = 28.0, potassium = 220.0, zinc = 1.10, phosphorus = 130.0,
                selenium = 6.0, copper = 0.12, manganese = 0.20, vitaminA = 110.0, vitaminC = 4.0,
                vitaminD = 0.3, vitaminE = 0.62, vitaminK = 3.7, vitaminB1 = 0.05, vitaminB2 = 0.15,
                vitaminB3 = 0.60, vitaminB6 = 0.10, vitaminB12 = 0.80, folate = 20.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 8.0, oil = CookingOil.Ghee),
        food("Palak Paneer", "69722371-60f8-44ed-9b53-a012deed7fe5", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 165.0, carbs = 7.0, sugar = 1.8, fiber = 2.4, protein = 9.0, fat = 12.0,
                saturatedFat = 6.7, omega3 = 0.11, cholesterol = 32.0, sodium = 380.0, calcium = 220.0,
                iron = 2.6, magnesium = 60.0, potassium = 360.0, zinc = 1.20, phosphorus = 120.0,
                selenium = 4.0, copper = 0.15, manganese = 0.45, vitaminA = 480.0, vitaminC = 16.0,
                vitaminD = 0.2, vitaminE = 1.90, vitaminK = 280.4, vitaminB1 = 0.06, vitaminB2 = 0.18,
                vitaminB3 = 0.70, vitaminB6 = 0.15, vitaminB12 = 0.50, folate = 110.0).detail(solubleFiberFraction = 0.35),
            oilGrams = 5.0, oil = CookingOil.Palmolein),
        food("Butter Chicken", "21522328-2066-49ab-9182-6492a024038f", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 220.0, carbs = 6.0, sugar = 3.0, fiber = 1.0, protein = 14.0, fat = 16.0,
                saturatedFat = 10.0, omega3 = 0.15, cholesterol = 72.0, sodium = 410.0, calcium = 70.0,
                iron = 1.2, magnesium = 20.0, potassium = 280.0, zinc = 1.30, phosphorus = 140.0,
                selenium = 14.0, copper = 0.08, manganese = 0.12, vitaminA = 90.0, vitaminC = 3.0,
                vitaminD = 0.2, vitaminE = 0.55, vitaminK = 3.6, vitaminB1 = 0.08, vitaminB2 = 0.18,
                vitaminB3 = 5.00, vitaminB6 = 0.30, vitaminB12 = 0.60, folate = 12.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 7.0, oil = CookingOil.Ghee),
        food("Tandoori Chicken", "b25978ba-7a12-4154-9a66-8d97929fb92d", FoodCategory.NorthIndian, 100.0, "1 piece (100 g)",
            Nutrients(calories = 175.0, carbs = 3.0, sugar = 1.0, fiber = 0.4, protein = 26.0, fat = 6.5,
                saturatedFat = 2.3, omega3 = 0.01, cholesterol = 92.0, sodium = 420.0, calcium = 40.0,
                iron = 1.1, magnesium = 26.0, potassium = 260.0, zinc = 1.50, phosphorus = 180.0,
                selenium = 22.0, copper = 0.06, manganese = 0.05, vitaminA = 30.0, vitaminC = 2.0,
                vitaminD = 0.2, vitaminE = 0.78, vitaminK = 1.2, vitaminB1 = 0.09, vitaminB2 = 0.20,
                vitaminB3 = 7.00, vitaminB6 = 0.40, vitaminB12 = 0.80, folate = 8.0).detail(solubleFiberFraction = 0.22),
            oilGrams = 3.0, oil = CookingOil.Palmolein),
        food("Veg Biryani", "b9dfbdae-e29b-4efb-abaf-2ba64663d8bd", FoodCategory.NorthIndian, 250.0, "1 plate (250 g)",
            Nutrients(calories = 170.0, carbs = 24.0, sugar = 2.0, fiber = 1.8, protein = 3.4, fat = 6.5,
                saturatedFat = 2.8, omega3 = 0.01, sodium = 400.0, calcium = 26.0, iron = 1.4,
                magnesium = 22.0, potassium = 200.0, zinc = 0.80, phosphorus = 75.0, selenium = 6.0,
                copper = 0.12, manganese = 0.40, vitaminA = 90.0, vitaminC = 4.0, vitaminE = 1.31,
                vitaminK = 8.5, vitaminB1 = 0.10, vitaminB2 = 0.04, vitaminB3 = 1.50, vitaminB6 = 0.12,
                folate = 24.0).detail(solubleFiberFraction = 0.27),
            oilGrams = 6.0, oil = CookingOil.Palmolein),
        food("Chicken Biryani", "5a4c2015-c77b-4906-a1a6-debe6e76114b", FoodCategory.NorthIndian, 300.0, "1 plate (300 g)",
            Nutrients(calories = 230.0, carbs = 28.0, sugar = 1.4, fiber = 1.5, protein = 11.0, fat = 8.0,
                saturatedFat = 3.1, omega3 = 0.01, cholesterol = 58.0, sodium = 480.0, calcium = 28.0,
                iron = 1.6, magnesium = 24.0, potassium = 250.0, zinc = 1.30, phosphorus = 110.0,
                selenium = 12.0, copper = 0.10, manganese = 0.35, vitaminA = 40.0, vitaminC = 2.0,
                vitaminD = 0.2, vitaminE = 1.10, vitaminK = 4.4, vitaminB1 = 0.10, vitaminB2 = 0.08,
                vitaminB3 = 3.50, vitaminB6 = 0.20, vitaminB12 = 0.40, folate = 20.0).detail(solubleFiberFraction = 0.28),
            oilGrams = 5.0, oil = CookingOil.Palmolein),
        food("Aloo Gobi", "78faf903-e1b3-49cc-a0fd-9c99358567b7", FoodCategory.NorthIndian, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 95.0, carbs = 12.0, sugar = 2.4, fiber = 3.2, protein = 3.0, fat = 4.5,
                saturatedFat = 2.0, omega3 = 0.01, sodium = 280.0, calcium = 24.0, iron = 1.0,
                magnesium = 20.0, potassium = 320.0, zinc = 0.35, phosphorus = 55.0, selenium = 1.0,
                copper = 0.12, manganese = 0.20, vitaminA = 12.0, vitaminC = 32.0, vitaminE = 0.87,
                vitaminK = 8.4, vitaminB1 = 0.08, vitaminB2 = 0.05, vitaminB3 = 0.80, vitaminB6 = 0.20,
                folate = 50.0).detail(solubleFiberFraction = 0.3),
            oilGrams = 4.5, oil = CookingOil.Palmolein),
        food("Bhindi Masala", "0e0331a5-e90a-44b2-aead-e024bb326e50", FoodCategory.NorthIndian, 150.0, "1 bowl (150 g)",
            Nutrients(calories = 90.0, carbs = 8.0, sugar = 2.2, fiber = 3.5, protein = 2.5, fat = 5.5,
                saturatedFat = 2.5, omega3 = 0.01, sodium = 300.0, calcium = 80.0, iron = 1.0,
                magnesium = 50.0, potassium = 290.0, zinc = 0.55, phosphorus = 55.0, selenium = 0.7,
                copper = 0.10, manganese = 0.40, vitaminA = 40.0, vitaminC = 13.0, vitaminE = 1.18,
                vitaminK = 22.4, vitaminB1 = 0.10, vitaminB2 = 0.06, vitaminB3 = 0.80, vitaminB6 = 0.15,
                folate = 60.0).detail(solubleFiberFraction = 0.3),
            oilGrams = 5.5, oil = CookingOil.Palmolein),

        // ---------- SNACKS ----------
        food("Samosa", "1d723941-e9b6-4270-b89a-956ba05817ce", FoodCategory.Snack, 60.0, "1 samosa (60 g)",
            Nutrients(calories = 308.0, carbs = 32.0, sugar = 1.4, fiber = 2.5, protein = 5.0, fat = 18.0,
                saturatedFat = 7.6, omega3 = 0.03, sodium = 420.0, calcium = 20.0, iron = 1.6,
                magnesium = 24.0, potassium = 250.0, zinc = 0.80, phosphorus = 80.0, selenium = 8.0,
                copper = 0.15, manganese = 0.40, vitaminA = 8.0, vitaminC = 4.0, vitaminE = 2.86,
                vitaminK = 6.3, vitaminB1 = 0.15, vitaminB2 = 0.06, vitaminB3 = 1.80, vitaminB6 = 0.18,
                folate = 24.0).detail(solubleFiberFraction = 0.25),
            oilGrams = 16.0, oil = CookingOil.Palmolein),
        food("Pakora", "cd8f8183-5427-42f8-88fb-a40d2bf6888e", FoodCategory.Snack, 50.0, "1 serving (50 g)",
            Nutrients(calories = 315.0, carbs = 28.0, sugar = 1.6, fiber = 4.0, protein = 8.0, fat = 20.0,
                saturatedFat = 8.5, omega3 = 0.04, sodium = 400.0, calcium = 30.0, iron = 2.5,
                magnesium = 60.0, potassium = 320.0, zinc = 1.20, phosphorus = 110.0, selenium = 4.0,
                copper = 0.30, manganese = 0.50, vitaminA = 20.0, vitaminC = 4.0, vitaminE = 3.28,
                vitaminK = 6.4, vitaminB1 = 0.12, vitaminB2 = 0.07, vitaminB3 = 0.90, vitaminB6 = 0.20,
                folate = 80.0).detail(solubleFiberFraction = 0.18),
            oilGrams = 18.0, oil = CookingOil.Palmolein),
        food("Bhel Puri", "b5658e44-a43b-4398-9117-3b0da2318a52", FoodCategory.Snack, 100.0, "1 plate (100 g)",
            Nutrients(calories = 195.0, carbs = 35.0, sugar = 4.0, fiber = 3.0, protein = 4.5, fat = 5.0,
                saturatedFat = 1.8, omega3 = 0.01, sodium = 540.0, calcium = 22.0, iron = 1.6,
                magnesium = 30.0, potassium = 280.0, zinc = 0.70, phosphorus = 90.0, selenium = 6.0,
                copper = 0.18, manganese = 0.50, vitaminA = 30.0, vitaminC = 8.0, vitaminE = 0.83,
                vitaminK = 6.2, vitaminB1 = 0.12, vitaminB2 = 0.05, vitaminB3 = 1.50, vitaminB6 = 0.15,
                folate = 22.0).detail(solubleFiberFraction = 0.25),
            oilGrams = 3.0, oil = CookingOil.Palmolein),
        food("Pani Puri", "6d11b5c8-cb97-4d28-848e-e263a9fd8d6d", FoodCategory.Snack, 80.0, "6 pieces (80 g)",
            Nutrients(calories = 175.0, carbs = 28.0, sugar = 2.0, fiber = 2.5, protein = 4.0, fat = 5.5,
                saturatedFat = 2.4, omega3 = 0.01, sodium = 480.0, calcium = 18.0, iron = 1.5,
                magnesium = 25.0, potassium = 220.0, zinc = 0.60, phosphorus = 70.0, selenium = 7.0,
                copper = 0.15, manganese = 0.45, vitaminA = 10.0, vitaminE = 1.00, vitaminK = 2.4,
                vitaminB1 = 0.10, vitaminB2 = 0.04, vitaminB3 = 1.30, vitaminB6 = 0.12, folate = 18.0).detail(solubleFiberFraction = 0.24),
            oilGrams = 5.0, oil = CookingOil.Palmolein),
        food("Vada Pav", "eb821a95-3ad4-4474-b7b1-bc853bced81e", FoodCategory.Snack, 120.0, "1 piece (120 g)",
            Nutrients(calories = 290.0, carbs = 38.0, sugar = 2.0, fiber = 3.0, protein = 7.0, fat = 13.0,
                saturatedFat = 4.8, omega3 = 0.02, sodium = 520.0, calcium = 50.0, iron = 2.5,
                magnesium = 30.0, potassium = 260.0, zinc = 1.00, phosphorus = 105.0, selenium = 12.0,
                copper = 0.18, manganese = 0.45, vitaminA = 6.0, vitaminC = 5.0, vitaminE = 1.69,
                vitaminK = 2.7, vitaminB1 = 0.18, vitaminB2 = 0.08, vitaminB3 = 1.80, vitaminB6 = 0.12,
                folate = 30.0).detail(solubleFiberFraction = 0.27),
            oilGrams = 9.0, oil = CookingOil.Palmolein),
        food("Pav Bhaji", "d8357515-ec44-44b8-8d57-5fa9d471db97", FoodCategory.Snack, 300.0, "1 plate (300 g)",
            Nutrients(calories = 160.0, carbs = 22.0, sugar = 3.0, fiber = 3.5, protein = 4.5, fat = 6.5,
                saturatedFat = 4.1, omega3 = 0.06, cholesterol = 11.0, sodium = 460.0, calcium = 38.0,
                iron = 1.6, magnesium = 28.0, potassium = 380.0, zinc = 0.70, phosphorus = 75.0,
                selenium = 6.0, copper = 0.14, manganese = 0.25, vitaminA = 150.0, vitaminC = 12.0,
                vitaminE = 0.46, vitaminK = 10.3, vitaminB1 = 0.12, vitaminB2 = 0.07, vitaminB3 = 1.20,
                vitaminB6 = 0.15, folate = 30.0).detail(solubleFiberFraction = 0.28),
            oilGrams = 4.0, oil = CookingOil.Ghee),
        food("Dhokla", "07694d08-143c-40be-b2c3-cd8fdcd85c39", FoodCategory.Snack, 100.0, "1 plate (100 g)",
            Nutrients(calories = 160.0, carbs = 24.0, sugar = 2.4, fiber = 2.5, protein = 6.0, fat = 4.5,
                saturatedFat = 1.5, omega3 = 0.01, sodium = 360.0, calcium = 30.0, iron = 1.5,
                magnesium = 32.0, potassium = 180.0, zinc = 0.90, phosphorus = 110.0, selenium = 8.0,
                copper = 0.22, manganese = 0.50, vitaminA = 6.0, vitaminC = 1.0, vitaminE = 0.70,
                vitaminK = 2.2, vitaminB1 = 0.15, vitaminB2 = 0.08, vitaminB3 = 1.00, vitaminB6 = 0.10,
                folate = 35.0).detail(solubleFiberFraction = 0.16),
            oilGrams = 2.5, oil = CookingOil.Palmolein),
        food("Poha", "ac5ad3e3-b58f-4422-aebb-b50d9e4047af", FoodCategory.Snack, 200.0, "1 plate (200 g)",
            Nutrients(calories = 175.0, carbs = 32.0, sugar = 1.6, fiber = 1.8, protein = 3.5, fat = 4.0,
                saturatedFat = 1.8, omega3 = 0.01, sodium = 280.0, calcium = 14.0, iron = 1.5,
                magnesium = 18.0, potassium = 110.0, zinc = 0.60, phosphorus = 75.0, selenium = 7.0,
                copper = 0.15, manganese = 0.60, vitaminA = 14.0, vitaminC = 4.0, vitaminE = 0.79,
                vitaminK = 3.3, vitaminB1 = 0.20, vitaminB2 = 0.04, vitaminB3 = 1.50, vitaminB6 = 0.10,
                folate = 12.0).detail(solubleFiberFraction = 0.23),
            oilGrams = 4.0, oil = CookingOil.Palmolein),
        food("Murukku", "736852aa-804e-491b-b589-80f26656485e", FoodCategory.Snack, 30.0, "1 small bowl (30 g)",
            Nutrients(calories = 520.0, carbs = 56.0, sugar = 0.8, fiber = 3.0, protein = 9.0, fat = 28.0,
                saturatedFat = 12.1, omega3 = 0.05, sodium = 480.0, calcium = 25.0, iron = 3.0,
                magnesium = 40.0, potassium = 180.0, zinc = 1.10, phosphorus = 130.0, selenium = 6.0,
                copper = 0.25, manganese = 0.70, vitaminE = 4.41, vitaminK = 2.9, vitaminB1 = 0.12,
                vitaminB2 = 0.06, vitaminB3 = 1.40, vitaminB6 = 0.12, folate = 24.0).detail(solubleFiberFraction = 0.24),
            oilGrams = 26.0, oil = CookingOil.Palmolein),
        food("Banana Chips", "ffc5f4e0-ce9e-4cf2-91fc-d04532ef884c", FoodCategory.Snack, 30.0, "1 small bowl (30 g)",
            Nutrients(calories = 520.0, carbs = 58.0, sugar = 4.0, fiber = 7.5, protein = 2.0, fat = 32.0,
                saturatedFat = 26.5, sodium = 360.0, calcium = 18.0, iron = 1.2, magnesium = 75.0,
                potassium = 510.0, zinc = 0.30, phosphorus = 40.0, selenium = 1.5, copper = 0.15,
                manganese = 0.30, vitaminA = 10.0, vitaminC = 4.0, vitaminE = 0.13, vitaminK = 0.6,
                vitaminB1 = 0.05, vitaminB2 = 0.05, vitaminB3 = 0.70, vitaminB6 = 0.30, folate = 14.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 30.0, oil = CookingOil.Coconut),
        food("Khakhra", "e557b7ef-0930-4a67-a2f7-c4375701d5c1", FoodCategory.Snack, 25.0, "1 piece (25 g)",
            Nutrients(calories = 360.0, carbs = 62.0, sugar = 1.0, fiber = 5.0, protein = 11.0, fat = 7.0,
                saturatedFat = 2.2, omega3 = 0.01, sodium = 480.0, calcium = 30.0, iron = 3.5,
                magnesium = 60.0, potassium = 240.0, zinc = 1.90, phosphorus = 140.0, selenium = 30.0,
                copper = 0.28, manganese = 1.20, vitaminE = 1.08, vitaminK = 1.8, vitaminB1 = 0.25,
                vitaminB2 = 0.08, vitaminB3 = 2.50, vitaminB6 = 0.12, folate = 34.0).detail(solubleFiberFraction = 0.14),
            oilGrams = 3.0, oil = CookingOil.Palmolein),

        // ---------- BEVERAGES ----------
        food("Masala Chai", "ed28a194-84ba-4f3e-b9ca-a79613a4a1af", FoodCategory.Beverage, 150.0, "1 cup (150 ml)",
            Nutrients(calories = 60.0, carbs = 8.0, sugar = 7.5, protein = 2.0, fat = 2.4, saturatedFat = 1.50, cholesterol = 6.0,
                sodium = 30.0, calcium = 70.0, magnesium = 7.0, potassium = 110.0, zinc = 0.20, phosphorus = 50.0,
                selenium = 1.0, copper = 0.01, manganese = 0.05, vitaminA = 20.0, vitaminE = 0.05,
                vitaminK = 0.2, vitaminB1 = 0.02, vitaminB2 = 0.09, vitaminB3 = 0.10, vitaminB6 = 0.02,
                vitaminB12 = 0.20, folate = 4.0)),
        food("Lassi (sweet)", "47b225a8-02b9-43ed-94be-6db1df7068a5", FoodCategory.Beverage, 250.0, "1 glass (250 ml)",
            Nutrients(calories = 110.0, carbs = 14.0, sugar = 13.0, protein = 3.0, fat = 4.5, saturatedFat = 2.5,
                cholesterol = 14.0, sodium = 50.0, calcium = 120.0, magnesium = 12.0, potassium = 170.0, zinc = 0.40,
                phosphorus = 90.0, selenium = 2.5, copper = 0.01, manganese = 0.01, vitaminA = 30.0,
                vitaminC = 0.5, vitaminE = 0.05, vitaminK = 0.2, vitaminB1 = 0.03, vitaminB2 = 0.15,
                vitaminB3 = 0.10, vitaminB6 = 0.03, vitaminB12 = 0.30, folate = 6.0)),
        food("Buttermilk (Chaas)", "7b91066e-66fc-449a-a7d2-6bf50b2a40e4", FoodCategory.Beverage, 250.0, "1 glass (250 ml)",
            Nutrients(calories = 35.0, carbs = 4.0, sugar = 4.0, protein = 2.0, fat = 1.5, saturatedFat = 0.95, cholesterol = 5.0,
                sodium = 220.0, calcium = 80.0, magnesium = 10.0, potassium = 130.0, zinc = 0.25, phosphorus = 75.0,
                selenium = 2.0, copper = 0.01, manganese = 0.00, vitaminA = 12.0, vitaminC = 0.4,
                vitaminE = 0.03, vitaminK = 0.1, vitaminB1 = 0.03, vitaminB2 = 0.13, vitaminB3 = 0.10,
                vitaminB6 = 0.03, vitaminB12 = 0.20, folate = 4.0)),
        food("Coconut Water", "d6a71286-5768-446a-9a4b-3b46655c65e8", FoodCategory.Beverage, 250.0, "1 glass (250 ml)",
            Nutrients(calories = 19.0, carbs = 3.7, sugar = 2.6, protein = 0.7, fat = 0.2, sodium = 105.0,
                calcium = 24.0, magnesium = 25.0, potassium = 250.0, zinc = 0.10, phosphorus = 20.0,
                selenium = 1.0, copper = 0.04, manganese = 0.14, vitaminC = 2.4, vitaminE = 0.02,
                vitaminB1 = 0.03, vitaminB2 = 0.06, vitaminB3 = 0.08, vitaminB6 = 0.03, folate = 3.0)),
        food("Nimbu Pani", "26953c0f-bd11-4359-a50c-fd909f143cd5", FoodCategory.Beverage, 250.0, "1 glass (250 ml)",
            Nutrients(calories = 30.0, carbs = 8.0, sugar = 7.0, sodium = 80.0, zinc = 0.02, phosphorus = 3.0,
                selenium = 0.1, copper = 0.01, manganese = 0.01, vitaminC = 12.0, vitaminB1 = 0.01,
                vitaminB2 = 0.00, vitaminB3 = 0.05, vitaminB6 = 0.02, folate = 2.0)),
        food("Black Tea (no milk, no sugar)", "6d2b34bd-38ce-45c1-812c-d1bf894d64fd", FoodCategory.Beverage, 240.0, "1 cup (240 ml)",
            Nutrients(calories = 1.0, carbs = 0.3, protein = 0.0, fat = 0.0, sodium = 3.0, magnesium = 3.0, potassium = 37.0,
                phosphorus = 1.0, selenium = 0.1, copper = 0.01, manganese = 0.22, vitaminB1 = 0.00,
                vitaminB2 = 0.01, vitaminB3 = 0.00, vitaminB6 = 0.00)),
        food("Tea (milk, sugar)", "b7fa86ce-1ce1-4ef6-93ed-6905c57b854d", FoodCategory.Beverage, 150.0, "1 cup (150 ml)",
            Nutrients(calories = 55.0, carbs = 7.5, sugar = 7.0, protein = 1.6, fat = 2.0, saturatedFat = 1.25, cholesterol = 5.0,
                sodium = 25.0, calcium = 62.0, magnesium = 6.0, potassium = 90.0, zinc = 0.15, phosphorus = 35.0,
                selenium = 1.0, copper = 0.01, manganese = 0.08, vitaminA = 16.0, vitaminE = 0.04,
                vitaminK = 0.1, vitaminB1 = 0.02, vitaminB2 = 0.07, vitaminB3 = 0.10, vitaminB6 = 0.02,
                vitaminB12 = 0.20, folate = 3.0)),
        food("Ginger Tea", "d3c55369-80b6-4aa4-aaf4-1a67b83b9091", FoodCategory.Beverage, 240.0, "1 cup (240 ml)",
            Nutrients(calories = 8.0, carbs = 2.0, sugar = 1.5, sodium = 3.0, magnesium = 3.0, potassium = 40.0, zinc = 0.02,
                phosphorus = 4.0, selenium = 0.1, copper = 0.01, manganese = 0.15, vitaminB1 = 0.01,
                vitaminB2 = 0.01, vitaminB3 = 0.10, vitaminB6 = 0.01)),
        food("Black Coffee (no sugar)", "5e6461ee-1180-4f18-90d7-f4262cd8113d", FoodCategory.Beverage, 240.0, "1 cup (240 ml)",
            Nutrients(calories = 2.0, carbs = 0.0, protein = 0.3, fat = 0.0, sodium = 5.0, magnesium = 3.0,
                potassium = 49.0, phosphorus = 7.0, selenium = 0.0, copper = 0.01, manganese = 0.03,
                vitaminB1 = 0.00, vitaminB2 = 0.08, vitaminB3 = 0.70, vitaminB6 = 0.00)),
        food("Lemon Juice (plain)", "c770c04e-50bc-49cb-894b-eb611ceffd67", FoodCategory.Beverage, 250.0, "1 glass (250 ml)",
            Nutrients(calories = 7.0, carbs = 2.5, sugar = 1.0, protein = 0.1, fat = 0.0, sodium = 1.0,
                potassium = 25.0, zinc = 0.05, phosphorus = 8.0, selenium = 0.1, copper = 0.02,
                manganese = 0.01, vitaminC = 14.0, vitaminB1 = 0.02, vitaminB2 = 0.01, vitaminB3 = 0.10,
                vitaminB6 = 0.05, folate = 10.0)),
        food("Sugarcane Juice", "20cd9198-7331-4a6c-9b85-94b53549abb2", FoodCategory.Beverage, 250.0, "1 glass (250 ml)",
            Nutrients(calories = 40.0, carbs = 10.0, sugar = 9.0, protein = 0.2, fat = 0.1, sodium = 12.0,
                calcium = 18.0, iron = 0.4, magnesium = 13.0, potassium = 75.0, zinc = 0.10, phosphorus = 15.0,
                selenium = 0.6, copper = 0.03, manganese = 0.10, vitaminA = 2.0, vitaminC = 3.0,
                vitaminB1 = 0.03, vitaminB2 = 0.04, vitaminB3 = 0.20, vitaminB6 = 0.03, folate = 2.0)),
        food("Tender Coconut (with flesh)", "c558795e-eb9b-407b-b82e-43dbda3eb544", FoodCategory.Beverage, 300.0, "1 fruit (300 ml + flesh)",
            Nutrients(calories = 45.0, carbs = 6.0, sugar = 3.0, fiber = 0.9, protein = 1.0, fat = 1.5, saturatedFat = 1.30, sodium = 80.0,
                magnesium = 20.0, potassium = 220.0, zinc = 0.25, phosphorus = 35.0, selenium = 1.5,
                copper = 0.10, manganese = 0.50, vitaminC = 2.0, vitaminE = 0.05, vitaminK = 0.1,
                vitaminB1 = 0.04, vitaminB2 = 0.02, vitaminB3 = 0.30, vitaminB6 = 0.03, folate = 10.0).detail(solubleFiberFraction = 0.1)),

        // ---------- DAIRY ----------
        food("Milk (toned, cow)", "148127ff-c21d-412b-8293-d826d291d507", FoodCategory.Dairy, 250.0, "1 glass (250 ml)",
            Nutrients(calories = 58.0, carbs = 4.8, sugar = 4.8, protein = 3.2, fat = 3.0, saturatedFat = 1.9,
                cholesterol = 10.0, sodium = 44.0, calcium = 125.0, magnesium = 11.0, potassium = 150.0,
                zinc = 0.40, phosphorus = 95.0, selenium = 2.0, copper = 0.01, manganese = 0.00,
                vitaminA = 28.0, vitaminC = 0.9, vitaminD = 1.2, vitaminE = 0.07, vitaminK = 0.3,
                vitaminB1 = 0.04, vitaminB2 = 0.18, vitaminB3 = 0.10, vitaminB6 = 0.04, vitaminB12 = 0.40,
                folate = 5.0).detail(amino = AA_DAIRY)),
        food("Curd / Dahi", "cba6999a-133d-4b7f-a289-b38d889a60ec", FoodCategory.Dairy, 100.0, "1 small bowl (100 g)",
            Nutrients(calories = 60.0, carbs = 4.7, sugar = 4.7, protein = 3.1, fat = 3.3, saturatedFat = 2.1,
                cholesterol = 13.0, sodium = 36.0, calcium = 110.0, magnesium = 10.0, potassium = 140.0,
                zinc = 0.50, phosphorus = 100.0, selenium = 2.2, copper = 0.01, manganese = 0.00,
                vitaminA = 27.0, vitaminC = 0.5, vitaminD = 0.1, vitaminE = 0.06, vitaminK = 0.2,
                vitaminB1 = 0.04, vitaminB2 = 0.17, vitaminB3 = 0.10, vitaminB6 = 0.04, vitaminB12 = 0.40,
                folate = 7.0).detail(amino = AA_DAIRY)),
        food("Paneer", "75d96eac-c145-470f-9971-5eb5358fa334", FoodCategory.Dairy, 50.0, "50 g cube",
            Nutrients(calories = 265.0, carbs = 1.2, sugar = 1.2, protein = 18.0, fat = 21.0, saturatedFat = 12.0,
                omega3 = 0.21, cholesterol = 65.0, sodium = 22.0, calcium = 480.0, iron = 0.2,
                magnesium = 18.0, potassium = 104.0, zinc = 2.70, phosphorus = 210.0, selenium = 5.0,
                copper = 0.03, manganese = 0.02, vitaminA = 200.0, vitaminD = 0.4, vitaminE = 0.30,
                vitaminK = 2.0, vitaminB1 = 0.03, vitaminB2 = 0.20, vitaminB3 = 0.10, vitaminB6 = 0.06,
                vitaminB12 = 1.00, folate = 10.0).detail(amino = AA_DAIRY)),
        food("Ghee", "ac8fb3b1-8e5b-4d91-8512-c1933b8428d4", FoodCategory.Dairy, 5.0, "1 tsp (5 g)",
            Nutrients(calories = 900.0, fat = 100.0, saturatedFat = 62.0, omega3 = 1.40, cholesterol = 256.0,
                phosphorus = 1.0, selenium = 0.0, copper = 0.00, manganese = 0.00, vitaminA = 700.0,
                vitaminD = 1.8, vitaminE = 2.80, vitaminK = 8.6, vitaminB1 = 0.00, vitaminB2 = 0.00,
                vitaminB3 = 0.00, vitaminB6 = 0.00)),
        food("Butter (salted)", "03da4efa-ea6e-42d3-94cf-d15ec363ca13", FoodCategory.Dairy, 10.0, "2 tsp (10 g)",
            Nutrients(calories = 717.0, fat = 81.0, saturatedFat = 51.0, omega3 = 0.32,
                cholesterol = 215.0, sodium = 643.0, zinc = 0.09, phosphorus = 24.0, selenium = 1.0,
                copper = 0.00, manganese = 0.00, vitaminA = 684.0, vitaminD = 1.5, vitaminE = 2.32,
                vitaminK = 7.0, vitaminB1 = 0.00, vitaminB2 = 0.03, vitaminB3 = 0.04, vitaminB6 = 0.00,
                vitaminB12 = 0.17, folate = 3.0)),

        // ---------- FRUITS ----------
        food("Banana", "7d2dbc9f-271c-4ec3-9fd8-4f5dd0069d9b", FoodCategory.Fruit, 120.0, "1 medium (120 g)",
            Nutrients(calories = 89.0, carbs = 23.0, sugar = 12.0, fiber = 2.6, protein = 1.1, fat = 0.3,
                magnesium = 27.0, potassium = 358.0, zinc = 0.15, phosphorus = 22.0, selenium = 1.0,
                copper = 0.08, manganese = 0.27, vitaminA = 3.0, vitaminC = 8.7, vitaminE = 0.10,
                vitaminK = 0.5, vitaminB1 = 0.03, vitaminB2 = 0.07, vitaminB3 = 0.67, vitaminB6 = 0.37,
                folate = 20.0).detail(solubleFiberFraction = 0.38),
            servingVariants = listOf(
                ServingVariant("Medium", 120.0),
                ServingVariant("Small", 100.0),
                ServingVariant("Large", 140.0),
            )),
        food("Mango", "05c167b6-0822-46e6-9b3f-f0eb8a401b14", FoodCategory.Fruit, 200.0, "1 medium (200 g)",
            Nutrients(calories = 60.0, carbs = 15.0, sugar = 13.7, fiber = 1.6, protein = 0.8, fat = 0.4,
                omega3 = 0.05, magnesium = 10.0, potassium = 168.0, zinc = 0.09, phosphorus = 14.0, selenium = 0.6,
                copper = 0.11, manganese = 0.06, vitaminA = 54.0, vitaminC = 36.4, vitaminE = 0.90,
                vitaminK = 4.2, vitaminB1 = 0.03, vitaminB2 = 0.04, vitaminB3 = 0.67, vitaminB6 = 0.12,
                folate = 43.0).detail(solubleFiberFraction = 0.45),
            servingVariants = listOf(
                ServingVariant("Medium", 200.0),
                ServingVariant("Small", 150.0),
                ServingVariant("Large", 300.0),
            )),
        food("Apple", "17226e9b-89f0-469a-9237-5b7afd5f0456", FoodCategory.Fruit, 150.0, "1 medium (150 g)",
            Nutrients(calories = 52.0, carbs = 14.0, sugar = 10.4, fiber = 2.4, protein = 0.3, fat = 0.2,
                magnesium = 5.0, potassium = 107.0, zinc = 0.04, phosphorus = 11.0, selenium = 0.0, copper = 0.03,
                manganese = 0.04, vitaminA = 3.0, vitaminC = 4.6, vitaminE = 0.18, vitaminK = 2.2,
                vitaminB1 = 0.02, vitaminB2 = 0.03, vitaminB3 = 0.09, vitaminB6 = 0.04, folate = 3.0).detail(solubleFiberFraction = 0.33)),
        food("Papaya", "ddce0760-c786-4463-9c0c-f7f13f90c975", FoodCategory.Fruit, 150.0, "1 cup cubed (150 g)",
            Nutrients(calories = 43.0, carbs = 11.0, sugar = 8.0, fiber = 1.7, protein = 0.5, fat = 0.3,
                magnesium = 21.0, potassium = 182.0, zinc = 0.08, phosphorus = 10.0, selenium = 0.6, copper = 0.05,
                manganese = 0.04, vitaminA = 47.0, vitaminC = 60.9, vitaminE = 0.30, vitaminK = 2.6,
                vitaminB1 = 0.02, vitaminB2 = 0.03, vitaminB3 = 0.36, vitaminB6 = 0.04, folate = 37.0).detail(solubleFiberFraction = 0.38)),
        food("Pomegranate", "e20fa9bb-8edb-4ec4-90e6-30090836c79f", FoodCategory.Fruit, 150.0, "1 cup arils (150 g)",
            Nutrients(calories = 83.0, carbs = 19.0, sugar = 13.7, fiber = 4.0, protein = 1.7, fat = 1.2, saturatedFat = 0.12,
                magnesium = 12.0, potassium = 236.0, zinc = 0.35, phosphorus = 36.0, selenium = 0.5, copper = 0.16,
                manganese = 0.12, vitaminC = 10.2, vitaminE = 0.60, vitaminK = 16.4, vitaminB1 = 0.07,
                vitaminB2 = 0.05, vitaminB3 = 0.29, vitaminB6 = 0.08, folate = 38.0).detail(solubleFiberFraction = 0.2)),
        food("Guava", "387ddc1f-8521-4ede-b0c6-961894b5cfab", FoodCategory.Fruit, 100.0, "1 fruit (100 g)",
            Nutrients(calories = 68.0, carbs = 14.0, sugar = 8.9, fiber = 5.4, protein = 2.6, fat = 1.0, saturatedFat = 0.27, omega3 = 0.11,
                magnesium = 22.0, potassium = 417.0, zinc = 0.23, phosphorus = 40.0, selenium = 0.6, copper = 0.23,
                manganese = 0.15, vitaminA = 31.0, vitaminC = 228.0, vitaminE = 0.73, vitaminK = 2.6,
                vitaminB1 = 0.07, vitaminB2 = 0.04, vitaminB3 = 1.08, vitaminB6 = 0.11, folate = 49.0).detail(solubleFiberFraction = 0.17)),
        food("Watermelon", "1b54fd5e-dd8d-4833-9ac3-0ce1970f6a50", FoodCategory.Fruit, 200.0, "1 cup (200 g)",
            Nutrients(calories = 30.0, carbs = 7.6, sugar = 6.2, fiber = 0.4, protein = 0.6, fat = 0.2,
                magnesium = 10.0, potassium = 112.0, zinc = 0.10, phosphorus = 11.0, selenium = 0.4, copper = 0.04,
                manganese = 0.04, vitaminA = 28.0, vitaminC = 8.1, vitaminE = 0.05, vitaminK = 0.1,
                vitaminB1 = 0.03, vitaminB2 = 0.02, vitaminB3 = 0.18, vitaminB6 = 0.05, folate = 3.0).detail(solubleFiberFraction = 0.49)),
        food("Jackfruit (ripe)", "8e2dbc58-9b94-4ac8-8e56-e662a6bcd850", FoodCategory.Fruit, 150.0, "1 cup (150 g)",
            Nutrients(calories = 95.0, carbs = 23.0, sugar = 19.0, fiber = 1.5, protein = 1.7, fat = 0.6,
                magnesium = 29.0, potassium = 303.0, zinc = 0.42, phosphorus = 21.0, selenium = 0.6,
                copper = 0.08, manganese = 0.04, vitaminA = 5.0, vitaminC = 13.7, vitaminE = 0.34,
                vitaminB1 = 0.10, vitaminB2 = 0.05, vitaminB3 = 0.92, vitaminB6 = 0.33, folate = 24.0).detail(solubleFiberFraction = 0.39)),
        food("Raw Jackfruit (cooked)", "802d39b0-85f2-4b92-a49d-37fda3129f06", FoodCategory.Fruit, 150.0, "1 cup (150 g)",
            Nutrients(calories = 50.0, carbs = 11.0, sugar = 2.5, fiber = 3.6, protein = 1.7, fat = 0.4,
                calcium = 32.0, iron = 0.6, magnesium = 27.0, potassium = 220.0, zinc = 0.30, phosphorus = 21.0,
                selenium = 0.6, copper = 0.19, manganese = 0.20, vitaminA = 1.0, vitaminC = 9.0,
                vitaminE = 0.30, vitaminB1 = 0.03, vitaminB2 = 0.06, vitaminB3 = 0.40, vitaminB6 = 0.11,
                folate = 14.0).detail(solubleFiberFraction = 0.07)),
        food("Orange", "aecbe8b9-9db7-415b-af94-7443fc8db7c4", FoodCategory.Fruit, 130.0, "1 medium (130 g)",
            Nutrients(calories = 47.0, carbs = 12.0, sugar = 9.4, fiber = 2.4, protein = 0.9, fat = 0.1,
                calcium = 40.0, magnesium = 10.0, potassium = 181.0, zinc = 0.07, phosphorus = 14.0, selenium = 0.5,
                copper = 0.05, manganese = 0.03, vitaminA = 11.0, vitaminC = 53.2, vitaminE = 0.18,
                vitaminB1 = 0.09, vitaminB2 = 0.04, vitaminB3 = 0.28, vitaminB6 = 0.06, folate = 30.0).detail(solubleFiberFraction = 0.43)),
        food("Mosambi (Sweet Lime)", "6879d910-a288-4017-a560-4ee07a88d57f", FoodCategory.Fruit, 150.0, "1 fruit (150 g)",
            Nutrients(calories = 43.0, carbs = 9.3, sugar = 6.5, fiber = 1.8, protein = 0.8, fat = 0.3, calcium = 40.0,
                magnesium = 10.0, potassium = 170.0, zinc = 0.06, phosphorus = 13.0, selenium = 0.4, copper = 0.04,
                manganese = 0.03, vitaminC = 50.0, vitaminE = 0.15, vitaminB1 = 0.08, vitaminB2 = 0.03,
                vitaminB3 = 0.25, vitaminB6 = 0.05, folate = 25.0).detail(solubleFiberFraction = 0.43)),
        food("Grapes", "b1d9c85f-3674-4b00-a9c8-62bf01a89918", FoodCategory.Fruit, 100.0, "1 cup (100 g)",
            Nutrients(calories = 67.0, carbs = 17.0, sugar = 16.0, fiber = 0.9, protein = 0.6, fat = 0.4,
                magnesium = 7.0, potassium = 191.0, zinc = 0.07, phosphorus = 20.0, selenium = 0.1, copper = 0.13,
                manganese = 0.07, vitaminA = 3.0, vitaminC = 4.0, vitaminE = 0.19, vitaminK = 14.6,
                vitaminB1 = 0.07, vitaminB2 = 0.07, vitaminB3 = 0.19, vitaminB6 = 0.09, folate = 2.0).detail(solubleFiberFraction = 0.35)),
        food("Pineapple", "7b515de4-d5d8-4aab-a1f4-a9c5517f245a", FoodCategory.Fruit, 150.0, "1 cup chunks (150 g)",
            Nutrients(calories = 50.0, carbs = 13.0, sugar = 9.9, fiber = 1.4, protein = 0.5, fat = 0.1,
                magnesium = 12.0, potassium = 109.0, zinc = 0.12, phosphorus = 8.0, selenium = 0.1, copper = 0.11,
                manganese = 0.93, vitaminA = 3.0, vitaminC = 47.8, vitaminE = 0.02, vitaminK = 0.1,
                vitaminB1 = 0.08, vitaminB2 = 0.03, vitaminB3 = 0.50, vitaminB6 = 0.11, folate = 18.0).detail(solubleFiberFraction = 0.17)),
        food("Sapota (Chikoo)", "84ad35c7-b206-4408-9117-cadcbcc15a3c", FoodCategory.Fruit, 100.0, "1 medium (100 g)",
            Nutrients(calories = 83.0, carbs = 20.0, sugar = 14.5, fiber = 5.3, protein = 0.4, fat = 1.1, saturatedFat = 0.19,
                calcium = 21.0, iron = 0.8, magnesium = 12.0, potassium = 193.0, zinc = 0.10, phosphorus = 12.0,
                selenium = 0.6, copper = 0.09, manganese = 0.09, vitaminA = 3.0, vitaminC = 14.7,
                vitaminE = 0.30, vitaminB1 = 0.04, vitaminB2 = 0.02, vitaminB3 = 0.20, vitaminB6 = 0.04,
                folate = 14.0).detail(solubleFiberFraction = 0.12)),

        // ---------- VEGETABLES ----------
        food("Tomato (raw)", "85a2750f-a23c-405b-9514-bf5ad15c1a05", FoodCategory.Vegetable, 100.0, "1 medium (100 g)",
            Nutrients(calories = 18.0, carbs = 3.9, sugar = 2.6, fiber = 1.2, protein = 0.9, fat = 0.2, sodium = 5.0,
                calcium = 10.0, magnesium = 11.0, potassium = 237.0, zinc = 0.17, phosphorus = 24.0, selenium = 0.0,
                copper = 0.06, manganese = 0.11, vitaminA = 42.0, vitaminC = 14.0, vitaminE = 0.54,
                vitaminK = 7.9, vitaminB1 = 0.04, vitaminB2 = 0.02, vitaminB3 = 0.59, vitaminB6 = 0.08,
                folate = 15.0).detail(solubleFiberFraction = 0.19)),
        food("Onion (raw)", "1ac20373-b4a4-44c8-a87b-984c165dd9d9", FoodCategory.Vegetable, 50.0, "1/2 medium (50 g)",
            Nutrients(calories = 40.0, carbs = 9.3, sugar = 4.2, fiber = 1.7, protein = 1.1, fat = 0.1, calcium = 23.0,
                magnesium = 10.0, potassium = 146.0, zinc = 0.17, phosphorus = 29.0, selenium = 0.5,
                copper = 0.04, manganese = 0.13, vitaminC = 7.4, vitaminE = 0.02, vitaminK = 0.4,
                vitaminB1 = 0.05, vitaminB2 = 0.03, vitaminB3 = 0.12, vitaminB6 = 0.12, folate = 19.0).detail(solubleFiberFraction = 0.22)),
        // Raw leaf spinach is fluffy: 1 loosely-packed cup ≈ 30 g, not 100 g.
        food("Spinach (raw)", "63399649-2820-417c-b0ef-3ed886761234", FoodCategory.Vegetable, 30.0, "1 cup (30 g)",
            Nutrients(calories = 23.0, carbs = 3.6, sugar = 0.4, fiber = 2.2, protein = 2.9, fat = 0.4, omega3 = 0.14,
                sodium = 79.0, calcium = 99.0, iron = 2.7, magnesium = 79.0, potassium = 558.0,
                zinc = 0.53, phosphorus = 49.0, selenium = 1.0, copper = 0.13, manganese = 0.90,
                vitaminA = 469.0, vitaminC = 28.1, vitaminE = 2.03, vitaminK = 482.0, vitaminB1 = 0.08,
                vitaminB2 = 0.19, vitaminB3 = 0.72, vitaminB6 = 0.20, folate = 194.0).detail(solubleFiberFraction = 0.36)),
        food("Carrot (raw)", "e52a842b-2af4-4fc2-852f-408dc177c32e", FoodCategory.Vegetable, 80.0, "1 medium (80 g)",
            Nutrients(calories = 41.0, carbs = 9.6, sugar = 4.7, fiber = 2.8, protein = 0.9, fat = 0.2, sodium = 69.0,
                calcium = 33.0, magnesium = 12.0, potassium = 320.0, zinc = 0.24, phosphorus = 35.0,
                selenium = 0.1, copper = 0.05, manganese = 0.14, vitaminA = 835.0, vitaminC = 5.9,
                vitaminE = 0.66, vitaminK = 13.2, vitaminB1 = 0.07, vitaminB2 = 0.06, vitaminB3 = 0.98,
                vitaminB6 = 0.14, folate = 19.0).detail(solubleFiberFraction = 0.32)),
        food("Potato (boiled)", "289ce83a-134f-4559-9dde-d3ee6b891202", FoodCategory.Vegetable, 150.0, "1 medium (150 g)",
            Nutrients(calories = 87.0, carbs = 20.0, sugar = 0.9, fiber = 1.8, protein = 1.9, fat = 0.1, sodium = 4.0,
                calcium = 5.0, iron = 0.3, magnesium = 20.0, potassium = 379.0, zinc = 0.30,
                phosphorus = 44.0, selenium = 0.3, copper = 0.18, manganese = 0.14, vitaminC = 13.0,
                vitaminE = 0.01, vitaminK = 2.1, vitaminB1 = 0.11, vitaminB2 = 0.02, vitaminB3 = 1.44,
                vitaminB6 = 0.30, folate = 10.0).detail(solubleFiberFraction = 0.33)),
        food("Ladyfinger (Bhindi, raw)", "2939b7a6-c0f1-41d2-a21b-488eb8f8bc62", FoodCategory.Vegetable, 100.0, "1 cup (100 g)",
            Nutrients(calories = 33.0, carbs = 7.5, sugar = 1.5, fiber = 3.2, protein = 1.9, fat = 0.2, sodium = 7.0,
                calcium = 82.0, iron = 0.6, magnesium = 57.0, potassium = 299.0, zinc = 0.58,
                phosphorus = 61.0, selenium = 0.7, copper = 0.11, manganese = 0.79, vitaminA = 36.0,
                vitaminC = 23.0, vitaminE = 0.27, vitaminK = 31.3, vitaminB1 = 0.20, vitaminB2 = 0.06,
                vitaminB3 = 1.00, vitaminB6 = 0.22, folate = 60.0).detail(solubleFiberFraction = 0.31)),
        food("Brinjal (Eggplant, raw)", "313d9f56-d1b0-429b-b065-dc8cfc7ed91c", FoodCategory.Vegetable, 100.0, "1 cup cubed (100 g)",
            Nutrients(calories = 25.0, carbs = 5.9, sugar = 3.5, fiber = 3.0, protein = 1.0, fat = 0.2, sodium = 2.0,
                magnesium = 14.0, potassium = 229.0, zinc = 0.12, phosphorus = 24.0, selenium = 0.3,
                copper = 0.08, manganese = 0.23, vitaminC = 2.2, vitaminE = 0.30, vitaminK = 3.5,
                vitaminB1 = 0.04, vitaminB2 = 0.04, vitaminB3 = 0.65, vitaminB6 = 0.08, folate = 22.0).detail(solubleFiberFraction = 0.29)),
        food("Cauliflower (raw)", "8a1b05c1-872a-46c8-a6a8-8372c4ba73fc", FoodCategory.Vegetable, 100.0, "1 cup florets (100 g)",
            Nutrients(calories = 25.0, carbs = 5.0, sugar = 1.9, fiber = 2.0, protein = 1.9, fat = 0.3, sodium = 30.0,
                calcium = 22.0, magnesium = 15.0, potassium = 299.0, zinc = 0.28, phosphorus = 44.0, selenium = 0.6,
                copper = 0.04, manganese = 0.16, vitaminC = 48.2, vitaminE = 0.08, vitaminK = 15.5,
                vitaminB1 = 0.05, vitaminB2 = 0.06, vitaminB3 = 0.51, vitaminB6 = 0.18, folate = 57.0).detail(solubleFiberFraction = 0.28)),
        food("Cabbage (raw)", "6f54634e-715a-4f74-bb63-98bd47597e13", FoodCategory.Vegetable, 100.0, "1 cup shredded (100 g)",
            Nutrients(calories = 25.0, carbs = 5.8, sugar = 3.2, fiber = 2.5, protein = 1.3, fat = 0.1, omega3 = 0.05,
                sodium = 18.0, magnesium = 12.0, potassium = 170.0, zinc = 0.18, phosphorus = 26.0, selenium = 0.3,
                copper = 0.02, manganese = 0.16, vitaminA = 5.0, vitaminC = 36.6, vitaminE = 0.15,
                vitaminK = 76.0, vitaminB1 = 0.06, vitaminB2 = 0.04, vitaminB3 = 0.23, vitaminB6 = 0.12,
                folate = 43.0).detail(solubleFiberFraction = 0.31)),
        food("Cucumber", "b3f31ac8-701c-4990-86b4-55cd1a413b4c", FoodCategory.Vegetable, 100.0, "1 cup sliced (100 g)",
            Nutrients(calories = 16.0, carbs = 3.6, sugar = 1.7, fiber = 0.5, protein = 0.7, fat = 0.1, sodium = 2.0,
                magnesium = 13.0, potassium = 147.0, zinc = 0.20, phosphorus = 24.0, selenium = 0.3, copper = 0.04,
                manganese = 0.08, vitaminA = 5.0, vitaminC = 2.8, vitaminE = 0.03, vitaminK = 16.4,
                vitaminB1 = 0.03, vitaminB2 = 0.03, vitaminB3 = 0.10, vitaminB6 = 0.04, folate = 7.0).detail(solubleFiberFraction = 0.29)),
        food("Beetroot (raw)", "79786c2a-e4aa-4a3b-ab4e-b0eff230eca1", FoodCategory.Vegetable, 80.0, "1 medium (80 g)",
            Nutrients(calories = 43.0, carbs = 9.6, sugar = 6.8, fiber = 2.8, protein = 1.6, fat = 0.2, sodium = 78.0,
                iron = 0.8, magnesium = 23.0, potassium = 325.0, zinc = 0.35, phosphorus = 40.0,
                selenium = 0.7, copper = 0.08, manganese = 0.33, vitaminA = 2.0, vitaminC = 4.9,
                vitaminE = 0.04, vitaminK = 0.2, vitaminB1 = 0.03, vitaminB2 = 0.04, vitaminB3 = 0.33,
                vitaminB6 = 0.07, folate = 109.0).detail(solubleFiberFraction = 0.21)),
        food("Bottle Gourd (raw)", "7b3c38fc-c832-4956-8d2b-0b2956f08922", FoodCategory.Vegetable, 100.0, "1 cup cubed (100 g)",
            Nutrients(calories = 14.0, carbs = 3.4, sugar = 1.4, fiber = 0.5, protein = 0.6, fat = 0.0, sodium = 2.0,
                magnesium = 11.0, potassium = 150.0, zinc = 0.70, phosphorus = 13.0, selenium = 0.2, copper = 0.03,
                manganese = 0.07, vitaminC = 10.1, vitaminE = 0.10, vitaminB1 = 0.03, vitaminB2 = 0.02,
                vitaminB3 = 0.32, vitaminB6 = 0.04, folate = 6.0).detail(solubleFiberFraction = 0.19)),
        food("Drumstick (Moringa)", "8e556ff3-7048-49f6-af5f-2561fcac7552", FoodCategory.Vegetable, 100.0, "1 cup chopped (100 g)",
            Nutrients(calories = 37.0, carbs = 8.5, sugar = 1.5, fiber = 3.2, protein = 2.1, fat = 0.2, sodium = 42.0,
                calcium = 30.0, iron = 0.4, magnesium = 45.0, potassium = 461.0, zinc = 0.45, phosphorus = 50.0,
                selenium = 0.5, copper = 0.10, manganese = 0.26, vitaminA = 4.0, vitaminC = 41.0,
                vitaminE = 0.10, vitaminB1 = 0.05, vitaminB2 = 0.07, vitaminB3 = 0.62, vitaminB6 = 0.12,
                folate = 44.0).detail(solubleFiberFraction = 0.18)),
        food("French Beans (raw)", "26f8e01c-57ae-4f68-870f-f957d0b303e9", FoodCategory.Vegetable, 100.0, "1 cup (100 g)",
            Nutrients(calories = 31.0, carbs = 7.0, sugar = 3.3, fiber = 3.4, protein = 1.8, fat = 0.2, omega3 = 0.07,
                sodium = 6.0, calcium = 37.0, iron = 1.0, magnesium = 25.0, potassium = 211.0, zinc = 0.24,
                phosphorus = 38.0, selenium = 0.6, copper = 0.07, manganese = 0.22, vitaminA = 35.0,
                vitaminC = 12.2, vitaminE = 0.41, vitaminK = 43.0, vitaminB1 = 0.08, vitaminB2 = 0.10,
                vitaminB3 = 0.75, vitaminB6 = 0.14, folate = 33.0).detail(solubleFiberFraction = 0.14)),

        // ---------- GRAINS / STAPLES ----------
        food("White Rice (cooked)", "28279d52-5a22-482c-ab7e-aed670dfca28", FoodCategory.Grain, 150.0, "1 cup (150 g)",
            Nutrients(calories = 130.0, carbs = 28.0, sugar = 0.1, fiber = 0.4, protein = 2.7, fat = 0.3, sodium = 1.0,
                calcium = 10.0, iron = 0.2, magnesium = 12.0, potassium = 35.0, zinc = 0.49,
                phosphorus = 43.0, selenium = 7.5, copper = 0.07, manganese = 0.47, vitaminE = 0.04,
                vitaminB1 = 0.02, vitaminB2 = 0.01, vitaminB3 = 0.40, vitaminB6 = 0.05, folate = 3.0).detail(amino = AA_RICE_WHITE, solubleFiberFraction = 0.29)),
        food("Brown Rice (cooked)", "151458dc-bfa3-46eb-a1a9-1ddc1e11177b", FoodCategory.Grain, 150.0, "1 cup (150 g)",
            Nutrients(calories = 123.0, carbs = 25.6, sugar = 0.3, fiber = 1.6, protein = 2.7, fat = 1.0, saturatedFat = 0.26, sodium = 4.0,
                calcium = 3.0, iron = 0.6, magnesium = 39.0, potassium = 86.0, zinc = 1.05,
                phosphorus = 83.0, selenium = 9.8, copper = 0.10, manganese = 0.97, vitaminE = 0.17,
                vitaminK = 0.6, vitaminB1 = 0.10, vitaminB2 = 0.03, vitaminB3 = 1.50, vitaminB6 = 0.15,
                folate = 4.0).detail(amino = AA_RICE_BROWN, solubleFiberFraction = 0.18)),
        food("Oats (cooked)", "89ab5257-5929-4cba-a5fa-1f238c3dd79b", FoodCategory.Grain, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 71.0, carbs = 12.0, sugar = 0.3, fiber = 1.7, protein = 2.5, fat = 1.5, saturatedFat = 0.26, sodium = 49.0,
                calcium = 9.0, iron = 0.9, magnesium = 26.0, potassium = 70.0, zinc = 0.60,
                phosphorus = 77.0, selenium = 4.5, copper = 0.09, manganese = 0.60, vitaminE = 0.08,
                vitaminK = 0.3, vitaminB1 = 0.08, vitaminB2 = 0.02, vitaminB3 = 0.20, vitaminB6 = 0.01,
                folate = 6.0).detail(amino = AA_OATS, solubleFiberFraction = 0.5)),
        food("Whole Wheat Flour (atta)", "ce7c195b-af88-48f2-b8c1-7cf0f55c231a", FoodCategory.Grain, 30.0, "1/4 cup (30 g)",
            Nutrients(calories = 340.0, carbs = 72.0, sugar = 0.4, fiber = 11.0, protein = 13.0, fat = 2.5, saturatedFat = 0.43,
                omega3 = 0.05, sodium = 2.0, calcium = 34.0, iron = 3.6, magnesium = 137.0,
                potassium = 363.0, zinc = 2.90, phosphorus = 355.0, selenium = 40.0, copper = 0.41,
                manganese = 3.80, vitaminE = 0.80, vitaminK = 1.9, vitaminB1 = 0.45, vitaminB2 = 0.17,
                vitaminB3 = 5.50, vitaminB6 = 0.34, folate = 44.0).detail(amino = AA_WHEAT, solubleFiberFraction = 0.14)),

        // ---------- PROTEINS ----------
        // Real egg weights, not a ×0.7/×1.4 scale of "1 large". USDA: medium ≈ 44 g,
        // large ≈ 50 g, jumbo ≈ 60 g. Default to one medium egg.
        food("Egg (boiled)", "c95c90d1-5ceb-4149-ad54-8698db1cdef5", FoodCategory.Protein, 50.0, "1 egg (50 g)",
            Nutrients(calories = 155.0, carbs = 1.1, sugar = 1.1, protein = 13.0, fat = 11.0, saturatedFat = 3.3,
                omega3 = 0.10, cholesterol = 373.0, sodium = 124.0, calcium = 50.0, iron = 1.2,
                magnesium = 10.0, potassium = 126.0, zinc = 1.30, phosphorus = 172.0, selenium = 30.0,
                copper = 0.07, manganese = 0.03, vitaminA = 149.0, vitaminD = 2.0, vitaminE = 1.03,
                vitaminK = 0.3, vitaminB1 = 0.07, vitaminB2 = 0.51, vitaminB3 = 0.07, vitaminB6 = 0.12,
                vitaminB12 = 1.10, folate = 44.0).detail(amino = AA_EGG),
            servingVariants = listOf(
                ServingVariant("Medium", 50.0),
                ServingVariant("Small", 44.0),
                ServingVariant("Large", 60.0),
            )),
        food("Chicken Breast (cooked)", "40e38ade-54bd-434a-87b6-5d25e8e2a6d4", FoodCategory.Protein, 100.0, "1 fillet (100 g)",
            Nutrients(calories = 165.0, carbs = 0.0, protein = 31.0, fat = 3.6, saturatedFat = 1.0, cholesterol = 85.0,
                sodium = 74.0, calcium = 15.0, iron = 1.0, magnesium = 29.0, potassium = 256.0,
                zinc = 1.00, phosphorus = 210.0, selenium = 27.0, copper = 0.05, manganese = 0.02,
                vitaminA = 9.0, vitaminD = 0.1, vitaminE = 0.27, vitaminK = 0.3, vitaminB1 = 0.07,
                vitaminB2 = 0.12, vitaminB3 = 13.50, vitaminB6 = 0.60, vitaminB12 = 0.30, folate = 4.0).detail(amino = AA_CHICKEN)),
        // South Indian home-style chicken curry — bone-in pieces in a coconut /
        // onion-tomato masala gravy (nattu kozhi / kozhi kuzhambu). Values are per
        // 100 g of served curry (bone-in portion + gravy), so protein reads lower
        // than boneless cuts because the weight includes bone.
        food("Chicken Curry (home-style, bone-in)", "b1af7de3-3d98-40f9-86af-124da5308088", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 190.0, carbs = 5.0, sugar = 1.5, fiber = 1.0, protein = 17.0, fat = 12.0,
                saturatedFat = 5.5, omega3 = 0.05, cholesterol = 78.0, sodium = 400.0, calcium = 30.0,
                iron = 1.6, magnesium = 24.0, potassium = 260.0, zinc = 1.20, phosphorus = 130.0,
                selenium = 15.0, copper = 0.08, manganese = 0.12, vitaminA = 60.0, vitaminC = 4.0,
                vitaminD = 0.1, vitaminE = 0.45, vitaminK = 3.0, vitaminB1 = 0.06, vitaminB2 = 0.12,
                vitaminB3 = 5.00, vitaminB6 = 0.25, vitaminB12 = 0.40, folate = 10.0).detail(solubleFiberFraction = 0.2),
            oilGrams = 4.0, oil = CookingOil.Coconut),
        // Kerala maththi / mathi — Indian oil sardine, masala-smeared and
        // shallow fried.
        //
        // **Per 100 g of FLESH, not of fish.** Every other bone-in entry here
        // (see the chicken curry above) is costed per gram of what reaches the
        // plate, bone included. This one is not, by request: the spine comes out
        // and is never eaten, so logging the whole fish against flesh figures
        // would book a third more of everything than was swallowed. Weigh or
        // estimate what you actually ate. One medium maththi yields about 55 g.
        //
        // Consequently the calcium here is flesh calcium — 85 mg, not the ~380 mg
        // of a tinned sardine. That famous number is the dissolved skeleton, and
        // a spine you spit out contributes none of it.
        //
        // Oil sardine fat is the least certain figure in this file: it is the
        // fish's whole commercial point and it swings from roughly 2% to 20% of
        // body weight across the year, lean after spawning and heavy before the
        // monsoon. 14.5 g here is a mid-season fish plus the frying oil, and the
        // omega-3 moves with it. Unlike every plant entry in this seed, that
        // omega-3 is EPA and DHA rather than ALA.
        food("Kerala Maththi (Sardine, fried)", "9faa12b5-23b4-4c1e-b7d6-0c9ac3e348f6", FoodCategory.Protein, 55.0, "1 fish, flesh only (55 g)",
            Nutrients(calories = 242.0, carbs = 2.0, sugar = 0.3, fiber = 0.3, protein = 24.5, fat = 14.5,
                saturatedFat = 3.6, omega3 = 1.40, cholesterol = 75.0, sodium = 420.0, calcium = 85.0,
                iron = 1.8, magnesium = 35.0, potassium = 390.0, zinc = 1.10, phosphorus = 280.0,
                selenium = 45.0, copper = 0.12, manganese = 0.10, vitaminA = 30.0, vitaminD = 4.5,
                vitaminE = 1.60, vitaminK = 2.0, vitaminB1 = 0.06, vitaminB2 = 0.22, vitaminB3 = 6.50,
                vitaminB6 = 0.35, vitaminB12 = 9.00, folate = 10.0).detail(amino = AA_FISH),
            oilGrams = 4.5, oil = CookingOil.Coconut),
        // Boneless chicken, masala-coated and shallow fried — the dry fry, not a
        // curry. Weight is all meat, so protein reads high next to the bone-in
        // curry above.
        food("Chicken Boneless (Fried)", "5988db31-52f9-41b3-a8b4-108700363af4", FoodCategory.Protein, 100.0, "1 serving (100 g)",
            Nutrients(calories = 245.0, carbs = 4.0, sugar = 0.5, fiber = 0.3, protein = 27.0, fat = 13.5,
                saturatedFat = 3.4, omega3 = 0.08, cholesterol = 95.0, sodium = 430.0, calcium = 18.0,
                iron = 1.3, magnesium = 28.0, potassium = 290.0, zinc = 1.60, phosphorus = 215.0,
                selenium = 25.0, copper = 0.06, manganese = 0.05, vitaminA = 15.0, vitaminD = 0.2,
                vitaminE = 0.90, vitaminK = 2.5, vitaminB1 = 0.08, vitaminB2 = 0.17, vitaminB3 = 9.50,
                vitaminB6 = 0.50, vitaminB12 = 0.45, folate = 8.0).detail(amino = AA_CHICKEN),
            oilGrams = 5.0, oil = CookingOil.Palmolein),
        food("Toor Dal (cooked)", "ccead290-ba21-4c70-8aa6-f9788cfc8bb1", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 95.0, carbs = 16.5, sugar = 0.4, fiber = 3.5, protein = 6.0, fat = 0.5, saturatedFat = 0.10,
                calcium = 17.0, iron = 1.6, magnesium = 38.0, potassium = 280.0, zinc = 1.30,
                phosphorus = 110.0, selenium = 3.0, copper = 0.25, manganese = 0.45, vitaminE = 0.10,
                vitaminK = 1.5, vitaminB1 = 0.15, vitaminB2 = 0.05, vitaminB3 = 0.70, vitaminB6 = 0.10,
                folate = 110.0).detail(amino = AA_TOOR, solubleFiberFraction = 0.26)),
        food("Moong Dal (cooked)", "ca0b4f93-4925-449d-898d-763020050391", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 90.0, carbs = 15.5, sugar = 0.4, fiber = 3.5, protein = 6.0, fat = 0.35, saturatedFat = 0.10,
                calcium = 27.0, iron = 1.4, magnesium = 48.0, potassium = 266.0, zinc = 1.10,
                phosphorus = 100.0, selenium = 2.5, copper = 0.22, manganese = 0.30, vitaminE = 0.10,
                vitaminK = 1.2, vitaminB1 = 0.16, vitaminB2 = 0.06, vitaminB3 = 0.60, vitaminB6 = 0.07,
                folate = 159.0).detail(amino = AA_MUNG, solubleFiberFraction = 0.17)),
        food("Masoor Dal (cooked)", "b253041f-20ba-414e-9e04-81a33e9a73c5", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 100.0, carbs = 16.5, sugar = 0.5, fiber = 4.0, protein = 7.0, fat = 0.25, saturatedFat = 0.05,
                calcium = 19.0, iron = 3.3, magnesium = 36.0, potassium = 369.0, zinc = 1.30,
                phosphorus = 180.0, selenium = 2.8, copper = 0.25, manganese = 0.49, vitaminE = 0.10,
                vitaminK = 1.7, vitaminB1 = 0.17, vitaminB2 = 0.07, vitaminB3 = 1.00, vitaminB6 = 0.18,
                folate = 181.0).detail(amino = AA_LENTIL, solubleFiberFraction = 0.17)),
        food("Horse Gram (Kollu, cooked)", "841efa19-9eaf-4978-84ce-ac7e5689ca0e", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 130.0, carbs = 23.0, sugar = 0.5, fiber = 5.0, protein = 9.0, fat = 0.30, saturatedFat = 0.08,
                calcium = 118.0, iron = 2.8, magnesium = 60.0, potassium = 490.0, zinc = 1.20,
                phosphorus = 130.0, selenium = 3.0, copper = 0.30, manganese = 0.55, vitaminE = 0.10,
                vitaminK = 1.5, vitaminB1 = 0.14, vitaminB2 = 0.08, vitaminB3 = 0.55, vitaminB6 = 0.10,
                folate = 90.0).detail(solubleFiberFraction = 0.21)),
        food("Whole Moong (cooked)", "5527cfce-be60-44f5-816c-c6f486b5ce44", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 105.0, carbs = 19.0, sugar = 2.0, fiber = 7.6, protein = 7.0, fat = 0.4,
                calcium = 27.0, iron = 1.4, magnesium = 48.0, potassium = 266.0, zinc = 0.84,
                phosphorus = 99.0, selenium = 2.5, copper = 0.21, manganese = 0.30, vitaminE = 0.15,
                vitaminK = 2.7, vitaminB1 = 0.16, vitaminB2 = 0.06, vitaminB3 = 0.58, vitaminB6 = 0.07,
                folate = 159.0).detail(amino = AA_MUNG, solubleFiberFraction = 0.14)),
        food("Whole Moong (dry)", "914bbca5-671f-45b8-b4c5-51432cd321c2", FoodCategory.Protein, 40.0, "40 g dry",
            Nutrients(calories = 347.0, carbs = 62.6, sugar = 6.6, fiber = 16.3, protein = 23.9, fat = 1.15, saturatedFat = 0.35,
                sodium = 15.0, calcium = 132.0, iron = 6.7, magnesium = 189.0, potassium = 1246.0,
                zinc = 2.68, phosphorus = 367.0, selenium = 8.2, copper = 0.94, manganese = 1.04,
                vitaminC = 4.8, vitaminE = 0.51, vitaminK = 9.0, vitaminB1 = 0.62, vitaminB2 = 0.23,
                vitaminB3 = 2.25, vitaminB6 = 0.38, folate = 625.0).detail(amino = AA_MUNG, solubleFiberFraction = 0.14)),
        food("Whole Urad (Black Gram, cooked)", "9665671e-65ca-4974-864c-90e224648b94", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 120.0, carbs = 20.0, sugar = 1.0, fiber = 8.0, protein = 8.0, fat = 0.4,
                calcium = 22.0, iron = 2.9, magnesium = 39.0, potassium = 280.0, zinc = 1.40,
                phosphorus = 140.0, selenium = 2.8, copper = 0.27, manganese = 0.50, vitaminE = 0.10,
                vitaminK = 1.5, vitaminB1 = 0.17, vitaminB2 = 0.06, vitaminB3 = 0.55, vitaminB6 = 0.08,
                folate = 130.0).detail(amino = AA_URAD, solubleFiberFraction = 0.24)),
        food("Kala Chana (Black Chickpea, cooked)", "0fc63a51-dcb6-4be3-931f-884670145c2a", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 160.0, carbs = 28.0, sugar = 4.8, fiber = 8.0, protein = 9.0, fat = 2.5, saturatedFat = 0.27,
                calcium = 49.0, iron = 2.9, magnesium = 48.0, potassium = 291.0, zinc = 1.50,
                phosphorus = 168.0, selenium = 3.7, copper = 0.35, manganese = 1.00, vitaminE = 0.35,
                vitaminK = 4.0, vitaminB1 = 0.12, vitaminB2 = 0.06, vitaminB3 = 0.53, vitaminB6 = 0.14,
                folate = 172.0).detail(amino = AA_CHICKPEA, solubleFiberFraction = 0.1)),
        food("Cowpea / Lobia (cooked)", "91cc0e79-0e57-460a-84fa-c9b84966127f", FoodCategory.Protein, 200.0, "1 bowl (200 g)",
            Nutrients(calories = 116.0, carbs = 21.0, sugar = 3.3, fiber = 6.5, protein = 8.0, fat = 0.5,
                calcium = 24.0, iron = 2.5, magnesium = 53.0, potassium = 278.0, zinc = 1.30,
                phosphorus = 156.0, selenium = 2.5, copper = 0.27, manganese = 0.48, vitaminE = 0.28,
                vitaminK = 1.7, vitaminB1 = 0.20, vitaminB2 = 0.06, vitaminB3 = 0.50, vitaminB6 = 0.10,
                folate = 208.0).detail(amino = AA_COWPEA, solubleFiberFraction = 0.24)),
        food("Sprouted Moong (raw)", "66db3f93-80aa-4cbd-af85-a2cfd633b97d", FoodCategory.Protein, 100.0, "1 cup (100 g)",
            Nutrients(calories = 135.0, carbs = 24.0, sugar = 3.0, fiber = 6.0, protein = 9.5, fat = 0.45,
                saturatedFat = 0.12, sodium = 8.0,
                calcium = 50.0, iron = 2.6, magnesium = 74.0, potassium = 490.0, zinc = 1.05,
                phosphorus = 145.0, selenium = 3.2, copper = 0.37, manganese = 0.41, vitaminA = 1.0,
                vitaminC = 15.0, vitaminE = 0.20, vitaminK = 12.0, vitaminB1 = 0.24, vitaminB2 = 0.12,
                vitaminB3 = 0.88, vitaminB6 = 0.15, folate = 280.0).detail(amino = AA_MUNG, solubleFiberFraction = 0.14)),
        food("Soya Chunks (cooked)", "199238f8-c4ba-45c1-834c-6be419a5ebfc", FoodCategory.Protein, 100.0, "1 cup (100 g)",
            Nutrients(calories = 105.0, carbs = 10.0, sugar = 2.0, fiber = 5.5, protein = 16.0, fat = 0.5,
                omega3 = 0.05, sodium = 25.0, calcium = 60.0, iron = 2.8, magnesium = 80.0,
                potassium = 360.0, zinc = 1.30, phosphorus = 215.0, selenium = 8.0, copper = 0.50,
                manganese = 0.92, vitaminE = 0.06, vitaminK = 0.6, vitaminB1 = 0.22, vitaminB2 = 0.08,
                vitaminB3 = 0.80, vitaminB6 = 0.20, folate = 100.0).detail(amino = AA_SOY, solubleFiberFraction = 0.15)),
        food("Soya Chunks (dry)", "8d3c6d33-c715-4dfe-9f23-f44422c175ec", FoodCategory.Protein, 30.0, "30 g dry",
            Nutrients(calories = 345.0, carbs = 33.0, sugar = 11.0, fiber = 18.0, protein = 52.0, fat = 1.2, saturatedFat = 0.20,
                omega3 = 0.15, sodium = 20.0, calcium = 240.0, iron = 9.0, magnesium = 290.0,
                potassium = 2100.0, zinc = 4.50, phosphorus = 700.0, selenium = 20.0, copper = 1.50,
                manganese = 3.00, vitaminE = 0.20, vitaminK = 2.0, vitaminB1 = 0.70, vitaminB2 = 0.25,
                vitaminB3 = 2.60, vitaminB6 = 0.57, folate = 305.0).detail(amino = AA_SOY, solubleFiberFraction = 0.15)),

        // ---------- NUTS & SEEDS ----------
        // All six are USDA SR Legacy single ingredients, per 100 g of the kernel
        // or seed as eaten. Serving sizes are deliberately small and carry no
        // Small/Medium/Large presets: these are measured by the handful or the
        // spoon, and a "large almonds" is not a portion anyone serves.
        //
        // Worth knowing before reading the omega-3 column: flax and chia are the
        // two largest ALA sources in this entire seed by an order of magnitude,
        // and both beat the 1.6 g daily goal in a single tablespoon. That is ALA,
        // not the EPA and DHA in the sardine above — the body converts it at a
        // few percent, so the two are not interchangeable even though the app
        // adds them into one figure.
        food("Almonds", "05a2cf83-c784-4e77-a1ab-cfddab9fc5dc", FoodCategory.Snack, 15.0, "~12 almonds (15 g)",
            Nutrients(calories = 579.0, carbs = 21.6, sugar = 4.4, fiber = 12.5, protein = 21.2, fat = 49.9,
                saturatedFat = 3.8, sodium = 1.0, calcium = 269.0, iron = 3.7, magnesium = 270.0,
                potassium = 733.0, zinc = 3.12, phosphorus = 481.0, selenium = 4.1, copper = 1.03,
                manganese = 2.18, vitaminE = 25.63, vitaminB1 = 0.21, vitaminB2 = 1.14,
                vitaminB3 = 3.62, vitaminB6 = 0.14, folate = 44.0).detail(amino = AA_ALMOND, solubleFiberFraction = 0.08),
            servingVariants = listOf(ServingVariant("~12 almonds", 15.0), ServingVariant("Handful", 30.0))),
        food("Cashews", "a780e99e-2e58-45bd-964b-5d09a0a9e19b", FoodCategory.Snack, 15.0, "~9 cashews (15 g)",
            Nutrients(calories = 553.0, carbs = 30.2, sugar = 5.9, fiber = 3.3, protein = 18.2, fat = 43.8,
                saturatedFat = 7.8, omega3 = 0.06, sodium = 12.0, calcium = 37.0, iron = 6.7,
                magnesium = 292.0, potassium = 660.0, zinc = 5.78, phosphorus = 593.0, selenium = 19.9,
                copper = 2.20, manganese = 1.66, vitaminE = 0.90, vitaminK = 34.1, vitaminB1 = 0.42,
                vitaminB2 = 0.06, vitaminB3 = 1.06, vitaminB6 = 0.42, folate = 25.0).detail(amino = AA_CASHEW, solubleFiberFraction = 0.15),
            servingVariants = listOf(ServingVariant("~9 cashews", 15.0), ServingVariant("Handful", 30.0))),
        food("Pumpkin Seeds", "211d85f5-3235-4192-8a69-8ce1ac98f9bb", FoodCategory.Snack, 15.0, "1 tbsp (15 g)",
            Nutrients(calories = 559.0, carbs = 10.7, sugar = 1.3, fiber = 6.0, protein = 30.2, fat = 49.1,
                saturatedFat = 8.7, omega3 = 0.12, sodium = 7.0, calcium = 46.0, iron = 8.8,
                magnesium = 592.0, potassium = 809.0, zinc = 7.81, phosphorus = 1233.0, selenium = 9.4,
                copper = 1.34, manganese = 4.54, vitaminE = 2.18, vitaminK = 7.3, vitaminB1 = 0.27,
                vitaminB2 = 0.15, vitaminB3 = 4.99, vitaminB6 = 0.14, folate = 58.0).detail(amino = AA_PUMPKIN_SEED, solubleFiberFraction = 0.12),
            servingVariants = listOf(ServingVariant("1 tbsp", 15.0), ServingVariant("Handful", 30.0))),
        food("Chia Seeds", "e253a4a4-f59e-4363-bd47-fa72bab1c3bd", FoodCategory.Snack, 12.0, "1 tbsp (12 g)",
            Nutrients(calories = 486.0, carbs = 42.1, fiber = 34.4, protein = 16.5, fat = 30.7,
                saturatedFat = 3.3, omega3 = 17.83, sodium = 16.0, calcium = 631.0, iron = 7.7,
                magnesium = 335.0, potassium = 407.0, zinc = 4.58, phosphorus = 860.0, selenium = 55.2,
                copper = 0.92, manganese = 2.72, vitaminC = 1.6, vitaminE = 0.50, vitaminB1 = 0.62,
                vitaminB2 = 0.17, vitaminB3 = 8.83, folate = 49.0).detail(amino = AA_CHIA, solubleFiberFraction = 0.15)),
        food("Flaxseeds", "2c0c86f2-1fa8-4f39-a010-995efb493d4e", FoodCategory.Snack, 10.0, "1 tbsp (10 g)",
            Nutrients(calories = 534.0, carbs = 28.9, sugar = 1.6, fiber = 27.3, protein = 18.3, fat = 42.2,
                saturatedFat = 3.7, omega3 = 22.81, sodium = 30.0, calcium = 255.0, iron = 5.7,
                magnesium = 392.0, potassium = 813.0, zinc = 4.34, phosphorus = 642.0, selenium = 25.4,
                copper = 1.22, manganese = 2.48, vitaminC = 0.6, vitaminE = 0.31, vitaminK = 4.3,
                vitaminB1 = 1.64, vitaminB2 = 0.16, vitaminB3 = 3.08, vitaminB6 = 0.47,
                folate = 87.0).detail(amino = AA_FLAX, solubleFiberFraction = 0.30)),
        food("Sesame Seeds", "191d98b5-6a33-4e03-a028-d5ef50beca73", FoodCategory.Snack, 9.0, "1 tbsp (9 g)",
            Nutrients(calories = 573.0, carbs = 23.5, sugar = 0.3, fiber = 11.8, protein = 17.7, fat = 49.7,
                saturatedFat = 7.0, omega3 = 0.38, sodium = 11.0, calcium = 975.0, iron = 14.6,
                magnesium = 351.0, potassium = 468.0, zinc = 7.75, phosphorus = 629.0, selenium = 34.4,
                copper = 4.08, manganese = 2.46, vitaminE = 0.25, vitaminB1 = 0.79, vitaminB2 = 0.25,
                vitaminB3 = 4.52, vitaminB6 = 0.79, folate = 97.0).detail(amino = AA_SESAME, solubleFiberFraction = 0.10)),

        // ---------- OTHER ----------
        // Plain sugar — default serving is 1 tsp (~4 g), but log can be scaled
        // up for 1 tbsp (~12 g) or measured weights.
        food("Sugar", "95cea744-8b28-4097-b437-9600789e9373", FoodCategory.Other, 4.0, "1 tsp (4 g)",
            Nutrients(calories = 387.0, carbs = 100.0, sugar = 100.0, protein = 0.0, fat = 0.0, sodium = 1.0,
                calcium = 1.0, potassium = 2.0, phosphorus = 0.0, selenium = 0.0, copper = 0.00,
                manganese = 0.00, vitaminB1 = 0.00, vitaminB2 = 0.00, vitaminB3 = 0.00, vitaminB6 = 0.00)),
        // Honey is ~82 g of sugar per 100 g and 17 kcal/100 g LIGHTER than plain
        // sugar, because the rest is water. The mineral column is real but
        // nutritionally beside the point at a teaspoon a time: this is sugar
        // with trace elements, and the app counts it as such.
        food("Honey", "a6e7a4e1-e3da-44af-8162-ff43f45d7a14", FoodCategory.Other, 21.0, "1 tbsp (21 g)",
            Nutrients(calories = 304.0, carbs = 82.4, sugar = 82.1, fiber = 0.2, protein = 0.3, fat = 0.0,
                sodium = 4.0, calcium = 6.0, iron = 0.42, magnesium = 2.0, potassium = 52.0,
                zinc = 0.22, phosphorus = 4.0, selenium = 0.8, copper = 0.04, manganese = 0.08,
                vitaminC = 0.5, vitaminB2 = 0.04, vitaminB3 = 0.12, vitaminB6 = 0.02, folate = 2.0)),
    )
}
