package com.example.foodtracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.foodtracker.data.CookingOil
import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.OilAmount
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.StyleOption
import com.example.foodtracker.data.withStyle
import com.example.foodtracker.data.withCookingOil
import com.example.foodtracker.data.withOilAmount

@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["barcode"], unique = false),
        // No index on `name`: every name query is COLLATE NOCASE or LIKE '%q%',
        // which a plain index cannot serve. Dropped in MIGRATION_12_13.
        Index(value = ["client_uuid"], unique = true),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Stable identity for backup/restore. The Room `id` is local to this DB
     * and re-numbers on a fresh install; `clientUuid` is generated once at
     * insert time and travels with the row through any export/import. The
     * restore code uses it to merge custom foods without disturbing existing
     * local customs.
     */
    @ColumnInfo(name = "client_uuid") val clientUuid: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val brand: String? = null,
    val category: String,
    @ColumnInfo(name = "serving_grams") val servingGrams: Double = 100.0,
    @ColumnInfo(name = "serving_label") val servingLabel: String = "1 serving (100 g)",
    @ColumnInfo(name = "serving_variants") val servingVariants: List<ServingVariant> = emptyList(),
    val barcode: String? = null,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false,
    @ColumnInfo(name = "is_favourite", defaultValue = "0") val isFavourite: Boolean = false,
    /**
     * Soft-delete flag. A hidden food is kept in the DB only so historical
     * food-log rows can still resolve to a name + nutrients; it must not appear
     * in any pick list (search, favourites, recent/frequent, barcode lookup).
     */
    @ColumnInfo(name = "is_hidden", defaultValue = "0") val isHidden: Boolean = false,

    val calories: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    @ColumnInfo(name = "saturated_fat") val saturatedFat: Double = 0.0,
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
    @ColumnInfo(name = "vitamin_a") val vitaminA: Double = 0.0,
    @ColumnInfo(name = "vitamin_c") val vitaminC: Double = 0.0,
    @ColumnInfo(name = "vitamin_d") val vitaminD: Double = 0.0,
    @ColumnInfo(name = "vitamin_e") val vitaminE: Double = 0.0,
    @ColumnInfo(name = "vitamin_k") val vitaminK: Double = 0.0,
    @ColumnInfo(name = "vitamin_b12") val vitaminB12: Double = 0.0,
    val folate: Double = 0.0,
    @ColumnInfo(name = "vitamin_b1") val vitaminB1: Double = 0.0,
    @ColumnInfo(name = "vitamin_b2") val vitaminB2: Double = 0.0,
    @ColumnInfo(name = "vitamin_b3") val vitaminB3: Double = 0.0,
    @ColumnInfo(name = "vitamin_b6") val vitaminB6: Double = 0.0,

    // Optional sub-components of fiber and protein. 0 means UNKNOWN, not
    // "none" — see the note on [com.example.foodtracker.data.Nutrients].
    @ColumnInfo(name = "soluble_fiber") val solubleFiber: Double = 0.0,
    @ColumnInfo(name = "insoluble_fiber") val insolubleFiber: Double = 0.0,
    @ColumnInfo(name = "essential_amino_acids") val essentialAminoAcids: Double = 0.0,
    val leucine: Double = 0.0,

    /**
     * Added cooking fat per 100 g of the finished dish — the oil absorbed in
     * the fryer or spent on the tempering, not the fat the ingredients brought
     * with them. A vada is ~13; an idli, a banana and a glass of milk are 0.
     *
     * 0 means "no cooking fat to reattribute", which is the correct answer for
     * every raw food, every custom food and every AI-filled food, and is why
     * the oil picker simply does not appear for them.
     */
    @ColumnInfo(name = "oil_grams") val oilGrams: Double = 0.0,
    /**
     * Name of the [CookingOil] this row's stored nutrients were computed with.
     * Only meaningful when [oilGrams] > 0. Held as a String because Room has
     * no enum column and the set may grow; unknown values fall back rather
     * than throwing — see [defaultOil].
     */
    @ColumnInfo(name = "oil_default") val oilDefault: String = CookingOil.Default.name,
    /**
     * Preparation options beyond the choice of fat — plain vs ghee roast, with
     * coconut vs without. Empty for most foods, and always empty for custom and
     * AI-filled ones: a variant axis is a curated claim about how a dish is
     * made, and we have no such claim for a food the user typed in.
     *
     * The first entry is the default, and the stored nutrients describe the
     * food *as that option* — so its [StyleOption.delta] is zero and every
     * other option adds to it.
     */
    @ColumnInfo(name = "style_options") val styleOptions: List<StyleOption> = emptyList(),
    /**
     * Last oil and style the user picked for this food, remembered so the sheet
     * reopens where they left it.
     *
     * Written the moment a chip is tapped, not when an entry is logged: someone
     * who opens a dosa, switches to ghee roast and then backs out has still
     * told us something about their kitchen, and making them re-pick it next
     * time is the kind of small friction that stops a control being used.
     */
    @ColumnInfo(name = "last_oil") val lastOil: String? = null,
    @ColumnInfo(name = "last_style") val lastStyle: String? = null,
    /**
     * Last [OilAmount] the user picked for this food, remembered for the same
     * reason and on the same tap as [lastOil].
     *
     * It carries more weight than the other two, because how much oil a kitchen
     * uses is a standing fact about that kitchen rather than a choice made per
     * dosa — so this column is what makes "Light" the default from the second
     * dosa onward, and it is why no separate preselect concept was needed.
     */
    @ColumnInfo(name = "last_oil_amount") val lastOilAmount: String? = null,
) {
    /** [oilDefault] resolved, tolerating a stored name this build no longer has. */
    val defaultOil: CookingOil
        get() = CookingOil.fromName(oilDefault) ?: CookingOil.Default

    /** True when this food's numbers depend on a choice of cooking fat. */
    fun usesCookingOil(): Boolean = oilGrams > 0.0

    /** True when this food offers a preparation choice beyond the oil. */
    fun hasStyleAxis(): Boolean = styleOptions.size > 1

    /**
     * True when this food lets the user say *how much* oil, not just which.
     *
     * Narrower than [usesCookingOil] on purpose — see
     * [OilAmount.AXIS_FOOD_UUIDS] for why the list starts at one dish.
     */
    fun usesOilAmountAxis(): Boolean =
        usesCookingOil() && clientUuid in OilAmount.AXIS_FOOD_UUIDS

    /** The option the sheet should open on — last picked, else the default. */
    fun preselectedStyle(): StyleOption? =
        StyleOption.find(styleOptions, lastStyle) ?: styleOptions.firstOrNull()

    /** The oil the sheet should open on — last picked, else this dish's own. */
    fun preselectedOil(): CookingOil =
        CookingOil.fromName(lastOil) ?: defaultOil

    /**
     * The oil amount the sheet should open on — last picked, else the seed's
     * own figure, which is what the stored nutrients describe.
     */
    fun preselectedOilAmount(): OilAmount =
        OilAmount.fromName(lastOilAmount) ?: OilAmount.Default
    /** Variants to show in pickers. Always non-empty — falls back to the canonical serving. */
    fun variantsOrDefault(): List<ServingVariant> = servingVariants.ifEmpty {
        listOf(ServingVariant(servingLabel, servingGrams))
    }

    /**
     * Calories for one canonical serving. Stored nutrient values are per 100 g,
     * so anything that pairs a number with [servingLabel] must scale first — a
     * 35 g idli is 51 kcal, not the 145 kcal held in [calories].
     */
    fun servingCalories(): Double = calories * (servingGrams / 100.0)

    fun nutrientsPer100g(): Nutrients = Nutrients(
        calories = calories,
        carbs = carbs,
        sugar = sugar,
        fiber = fiber,
        protein = protein,
        fat = fat,
        saturatedFat = saturatedFat,
        omega3 = omega3,
        cholesterol = cholesterol,
        sodium = sodium,
        calcium = calcium,
        iron = iron,
        magnesium = magnesium,
        potassium = potassium,
        zinc = zinc,
        phosphorus = phosphorus,
        selenium = selenium,
        copper = copper,
        manganese = manganese,
        vitaminA = vitaminA,
        vitaminC = vitaminC,
        vitaminD = vitaminD,
        vitaminE = vitaminE,
        vitaminK = vitaminK,
        vitaminB12 = vitaminB12,
        folate = folate,
        vitaminB1 = vitaminB1,
        vitaminB2 = vitaminB2,
        vitaminB3 = vitaminB3,
        vitaminB6 = vitaminB6,
        solubleFiber = solubleFiber,
        insolubleFiber = insolubleFiber,
        essentialAminoAcids = essentialAminoAcids,
        leucine = leucine,
    )

    /**
     * Per-100 g nutrients as if cooked in [oil], using [oilAmount] of it.
     *
     * Pass null — or a food with no [oilGrams] — to get the stored figures
     * unchanged, so callers that have no opinion about oil need no branch.
     *
     * The three steps are ordered and the order is load-bearing: swap the fat
     * for the one the user picked, take back however much of it they did not
     * use, then add whatever was put on top.
     */
    fun nutrientsPer100g(
        oil: CookingOil?,
        style: StyleOption? = null,
        oilAmount: OilAmount? = null,
    ): Nutrients {
        var n = nutrientsPer100g()
        if (oil != null && oilGrams > 0.0) {
            n = n.withCookingOil(oilGrams, defaultOil, oil)
            // Second, and against the oil just chosen — by this point the
            // figures describe that oil, so that is the one being removed.
            // Gated on the axis as well as the value so a stored "Light" on a
            // food that no longer offers the choice cannot silently re-price
            // it.
            if (oilAmount != null && usesOilAmountAxis()) {
                n = n.withOilAmount(oilGrams, oil, oilAmount)
            }
        }
        // Applied after the oil swap, never before: a style addition brings its
        // own fat, which the dish did not absorb in the pan and which must not
        // be re-priced as though it had been.
        return n.withStyle(style)
    }

    companion object {
        fun fromNutrientsPer100g(
            name: String,
            category: String,
            /**
             * Cross-install identity. Seeded foods pass their pinned literal
             * from [IndianFoodSeed]; everything else — customs, barcode saves,
             * online results — omits it and takes a fresh random one, which is
             * correct because those rows have no identity to share with any
             * other install.
             */
            clientUuid: String = java.util.UUID.randomUUID().toString(),
            n: Nutrients,
            servingGrams: Double = 100.0,
            servingLabel: String = "1 serving (100 g)",
            servingVariants: List<ServingVariant> = emptyList(),
            brand: String? = null,
            barcode: String? = null,
            isCustom: Boolean = false,
            isFavourite: Boolean = false,
            oilGrams: Double = 0.0,
            oilDefault: CookingOil = CookingOil.Default,
            styleOptions: List<StyleOption> = emptyList(),
        ) = FoodEntity(
            name = name,
            clientUuid = clientUuid,
            brand = brand,
            category = category,
            servingGrams = servingGrams,
            servingLabel = servingLabel,
            servingVariants = servingVariants.ifEmpty {
                ServingVariant.defaultVariantsFor(servingGrams, servingLabel)
            },
            barcode = barcode,
            isCustom = isCustom,
            isFavourite = isFavourite,
            calories = n.calories,
            carbs = n.carbs,
            sugar = n.sugar,
            fiber = n.fiber,
            protein = n.protein,
            fat = n.fat,
            saturatedFat = n.saturatedFat,
            omega3 = n.omega3,
            cholesterol = n.cholesterol,
            sodium = n.sodium,
            calcium = n.calcium,
            iron = n.iron,
            magnesium = n.magnesium,
            potassium = n.potassium,
            zinc = n.zinc,
            phosphorus = n.phosphorus,
            selenium = n.selenium,
            copper = n.copper,
            manganese = n.manganese,
            vitaminA = n.vitaminA,
            vitaminC = n.vitaminC,
            vitaminD = n.vitaminD,
            vitaminE = n.vitaminE,
            vitaminK = n.vitaminK,
            vitaminB12 = n.vitaminB12,
            folate = n.folate,
            vitaminB1 = n.vitaminB1,
            vitaminB2 = n.vitaminB2,
            vitaminB3 = n.vitaminB3,
            vitaminB6 = n.vitaminB6,
            solubleFiber = n.solubleFiber,
            insolubleFiber = n.insolubleFiber,
            essentialAminoAcids = n.essentialAminoAcids,
            leucine = n.leucine,
            oilGrams = oilGrams,
            oilDefault = oilDefault.name,
            styleOptions = styleOptions,
        )
    }
}
