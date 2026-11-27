package com.example.foodtracker.data

import android.content.Context
import com.example.foodtracker.data.db.FoodEntity
import com.example.foodtracker.data.db.IndianFoodSeed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * The extended food catalogue, kept as data rather than code:
 * `assets/food_catalogue.json` holds every food beyond the hand-tuned
 * [IndianFoodSeed] list — nutrition per 100 g, serving, default cooking oil and
 * preparation choices, and a one-line "how it's made".
 *
 * Foods are seeded into Room alongside [IndianFoodSeed.ALL]; the "made" lines
 * stay in memory (they are reference data, not user data, so they need no
 * column, migration or backup).
 */
object FoodCatalogue {

    private const val ASSET = "food_catalogue.json"

    @Serializable
    private data class File(
        val foods: List<Entry> = emptyList(),
    )

    @Serializable
    private data class Entry(
        val name: String,
        val uuid: String,
        val category: String,
        val servingGrams: Double,
        val servingLabel: String,
        val per100g: Per100g,
        val oilGrams: Double = 0.0,
        val oil: String = "Palmolein",
        val styles: List<Style> = emptyList(),
        val made: String = "",
    )

    @Serializable
    private data class Style(val label: String, val ingredient: String? = null, val grams: Double = 0.0)

    @Serializable
    private data class Per100g(
        val calories: Double = 0.0, val carbs: Double = 0.0, val sugar: Double = 0.0, val fiber: Double = 0.0,
        val protein: Double = 0.0, val fat: Double = 0.0, val saturatedFat: Double = 0.0,
        val cholesterol: Double = 0.0, val sodium: Double = 0.0, val potassium: Double = 0.0,
        val calcium: Double = 0.0, val iron: Double = 0.0, val magnesium: Double = 0.0, val zinc: Double = 0.0,
        val phosphorus: Double = 0.0, val vitaminA: Double = 0.0, val vitaminC: Double = 0.0,
        val vitaminE: Double = 0.0, val vitaminB1: Double = 0.0, val vitaminB2: Double = 0.0,
        val vitaminB3: Double = 0.0, val vitaminB6: Double = 0.0, val folate: Double = 0.0,
        val vitaminB12: Double = 0.0, val vitaminK: Double = 0.0,
    ) {
        fun toNutrients() = Nutrients(
            calories = calories, carbs = carbs, sugar = sugar, fiber = fiber, protein = protein, fat = fat,
            saturatedFat = saturatedFat, cholesterol = cholesterol, sodium = sodium, potassium = potassium,
            calcium = calcium, iron = iron, magnesium = magnesium, zinc = zinc, phosphorus = phosphorus,
            vitaminA = vitaminA, vitaminC = vitaminC, vitaminE = vitaminE, vitaminB1 = vitaminB1,
            vitaminB2 = vitaminB2, vitaminB3 = vitaminB3, vitaminB6 = vitaminB6, folate = folate,
            vitaminB12 = vitaminB12, vitaminK = vitaminK,
        )
    }

    class Loaded(
        val foods: List<FoodEntity>,
        val made: Map<String, String>,
    )

    @Volatile
    private var loaded: Loaded? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses the asset once per process; later calls return the same result. */
    fun load(context: Context): Loaded = loaded ?: synchronized(this) {
        loaded ?: parse(context).also { loaded = it }
    }

    /** One line on how the dish is made, when the catalogue has one. */
    fun madeOf(name: String): String? = loaded?.made?.get(key(name))

    private fun key(name: String) = name.lowercase(Locale.ROOT)

    private fun parse(context: Context): Loaded {
        val file = runCatching {
            context.assets.open(ASSET).use { json.decodeFromString(File.serializer(), it.reader().readText()) }
        }.getOrElse { File() } // A missing or broken asset leaves the hand-tuned seed working on its own.
        val foods = file.foods.map { e ->
            FoodEntity.fromNutrientsPer100g(
                name = e.name,
                clientUuid = e.uuid,
                category = FoodCategory.fromName(e.category).name,
                n = e.per100g.toNutrients(),
                servingGrams = e.servingGrams,
                servingLabel = e.servingLabel,
                oilGrams = e.oilGrams,
                oilDefault = CookingOil.entries.firstOrNull { it.name == e.oil } ?: CookingOil.Palmolein,
                styleOptions = e.styles.map { s ->
                    StyleOption(
                        label = s.label,
                        ingredient = VariantIngredient.entries.firstOrNull { it.name == s.ingredient },
                        grams = s.grams,
                    )
                },
                servingVariants = IndianFoodSeed.sizeVariantsFor(e.servingGrams, e.servingLabel),
            )
        }
        return Loaded(
            foods = foods,
            made = file.foods.filter { it.made.isNotBlank() }.associate { key(it.name) to it.made },
        )
    }
}
