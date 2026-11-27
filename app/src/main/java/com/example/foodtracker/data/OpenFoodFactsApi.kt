package com.example.foodtracker.data

import com.example.foodtracker.data.db.FoodEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A product fetched by barcode, with both common bases the source may publish.
 * The UI lets the user pick which to trust before saving — OFF and USDA both
 * contain contributor-entered data where the per-100g slot is sometimes filled
 * with per-serving numbers, so blindly trusting per-100g leads to undercounts.
 *
 * `perHundredG`  : values labelled per 100 g by the source (may be null/absent)
 * `perServing`   : values labelled per serving (may be null/absent)
 * `defaultBasis` : our best guess based on consistency — used as the initial chip selection
 */
data class BarcodeProduct(
    val name: String,
    val brand: String?,
    val barcode: String,
    val category: String,
    val servingGrams: Double,
    val servingLabel: String,
    val perHundredG: Nutrients?,
    val perServing: Nutrients?,
    val defaultBasis: NutritionBasis,
) {
    /** Nutrients as the source reports them for the picked basis (no scaling). */
    fun nutrientsAs(basis: NutritionBasis): Nutrients = when (basis) {
        NutritionBasis.Per100g -> perHundredG
            ?: perServing?.times(100.0 / servingGrams.coerceAtLeast(1.0))
            ?: Nutrients.Zero
        NutritionBasis.PerServing -> perServing
            ?: perHundredG?.times(servingGrams.coerceAtLeast(1.0) / 100.0)
            ?: Nutrients.Zero
    }

    /** Normalise to per-100g for storage, scaling from the picked basis when needed. */
    fun toPerHundredG(basis: NutritionBasis): Nutrients = when (basis) {
        NutritionBasis.Per100g -> nutrientsAs(NutritionBasis.Per100g)
        NutritionBasis.PerServing -> {
            val s = servingGrams.coerceAtLeast(1.0)
            nutrientsAs(NutritionBasis.PerServing) * (100.0 / s)
        }
    }
}

enum class NutritionBasis { Per100g, PerServing }

/**
 * Pick the basis we'll show the user by default.
 *
 * Heuristic: if both sets are present and serving_quantity is set, check whether
 * `perServing.calories * 100 / servingGrams ≈ per100g.calories`. If they agree
 * within ~10%, the data is internally consistent and per-100g is the canonical
 * choice (also lets the user log fractional servings cleanly). If they disagree
 * — common for Indian whey/supplements where contributors paste the only number
 * they see (per-serving) into the per-100g slot — we prefer per-serving since
 * that's what the on-package label actually shows.
 *
 * Edge cases: missing per-100g → per-serving; missing per-serving → per-100g.
 */
internal fun inferBasis(
    per100g: Nutrients?,
    perServing: Nutrients?,
    servingGrams: Double,
): NutritionBasis {
    if (per100g == null && perServing != null) return NutritionBasis.PerServing
    if (perServing == null) return NutritionBasis.Per100g
    if (per100g == null) return NutritionBasis.PerServing
    if (servingGrams <= 0.0 || servingGrams == 100.0) return NutritionBasis.Per100g

    val per100Cal = per100g.calories
    val perServingCal = perServing.calories
    if (per100Cal <= 0.0 || perServingCal <= 0.0) return NutritionBasis.Per100g
    val implied100g = perServingCal * 100.0 / servingGrams
    val ratio = implied100g / per100Cal
    // Tolerance of 10% absorbs rounding noise from OFF's two-decimal storage.
    return if (ratio in 0.90..1.10) NutritionBasis.Per100g else NutritionBasis.PerServing
}

/**
 * Looks up packaged products on Open Food Facts by barcode.
 * Returns both bases when available so the user can confirm which one is correct.
 */
class OpenFoodFactsApi {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookupBarcode(barcode: String): BarcodeProduct? = withContext(Dispatchers.IO) {
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "FoodTracker/1.0 (Android)")
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            val status = root["status"]?.jsonPrimitive?.contentOrNull
            if (status != "1") return@withContext null
            val product = root["product"]?.jsonObject ?: return@withContext null
            val name = product.str("product_name")
                ?: product.str("generic_name")
                ?: return@withContext null
            val brand = product.str("brands")
            val nutriments = product["nutriments"]?.jsonObject ?: JsonObject(emptyMap())

            val per100g = nutrientsFromOff(nutriments, suffix = "_100g").takeIfNonEmpty()
            val perServingRaw = nutrientsFromOff(nutriments, suffix = "_serving").takeIfNonEmpty()

            val servingGrams = (product.num("serving_quantity") ?: 0.0).takeIf { it > 0 } ?: 100.0
            val servingLabel = product.str("serving_size") ?: "1 serving (${servingGrams.toInt()} g)"

            val basis = inferBasis(per100g, perServingRaw, servingGrams)

            BarcodeProduct(
                name = name.trim(),
                brand = brand,
                barcode = barcode,
                category = FoodCategory.Packaged.name,
                servingGrams = servingGrams,
                servingLabel = servingLabel,
                perHundredG = per100g,
                perServing = perServingRaw,
                defaultBasis = basis,
            )
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Free-text search against Open Food Facts. Returns at most [limit] products
     * with usable per-100g energy data, mapped into our FoodEntity shape and
     * tagged with their barcode so we can deduplicate against later barcode scans.
     */
    suspend fun searchByName(query: String, limit: Int = 15): List<FoodEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val fields = listOf(
            "code", "product_name", "generic_name", "brands",
            "serving_quantity", "serving_size", "nutriments",
        ).joinToString(",")
        val url = URL(
            "https://world.openfoodfacts.org/api/v2/search" +
                "?search_terms=$encoded&page_size=$limit&fields=$fields"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "FoodTracker/1.0 (Android)")
        }
        try {
            if (conn.responseCode != 200) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            val products = root["products"]?.jsonArray ?: return@withContext emptyList()
            products.mapNotNull { el ->
                val product = el.jsonObject
                val name = product.str("product_name")
                    ?: product.str("generic_name")
                    ?: return@mapNotNull null
                val brand = product.str("brands")
                val barcode = product.str("code")
                val nutriments = product["nutriments"]?.jsonObject ?: return@mapNotNull null
                val per100g = nutrientsFromOff(nutriments, suffix = "_100g").takeIfNonEmpty()
                val perServing = nutrientsFromOff(nutriments, suffix = "_serving").takeIfNonEmpty()
                // Drop entries without any energy value at all — they're not useful to log.
                val anyCalories = (per100g?.calories ?: 0.0) > 0.0 || (perServing?.calories ?: 0.0) > 0.0
                if (!anyCalories) return@mapNotNull null
                val servingGrams = (product.num("serving_quantity") ?: 0.0).takeIf { it > 0 } ?: 100.0
                val servingLabel = product.str("serving_size") ?: "1 serving (${servingGrams.toInt()} g)"
                val basis = inferBasis(per100g, perServing, servingGrams)
                // For search hits we surface the FoodEntity in the basis we trust;
                // values are saved as per-100g so logging math stays uniform.
                val normalised = when (basis) {
                    NutritionBasis.Per100g -> per100g ?: perServing?.times(100.0 / servingGrams) ?: Nutrients.Zero
                    NutritionBasis.PerServing -> perServing?.times(100.0 / servingGrams)
                        ?: per100g ?: Nutrients.Zero
                }
                FoodEntity.fromNutrientsPer100g(
                    name = name.trim(),
                    category = FoodCategory.Packaged.name,
                    n = normalised,
                    servingGrams = servingGrams,
                    servingLabel = servingLabel,
                    brand = brand,
                    barcode = barcode,
                )
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Read a Nutrients block from OFF's `nutriments` JSON, scoped to either the
     * per-100g (`suffix = "_100g"`) or per-serving (`suffix = "_serving"`) keys.
     * OFF publishes both when it has the data; consumers can pick whichever the
     * contributor actually filled in.
     */
    private fun nutrientsFromOff(nutriments: JsonObject, suffix: String) = Nutrients(
        calories = nutriments.num("energy-kcal$suffix")
            ?: (nutriments.num("energy$suffix")?.let { it / 4.184 } ?: 0.0),
        carbs = nutriments.num("carbohydrates$suffix") ?: 0.0,
        sugar = nutriments.num("sugars$suffix") ?: 0.0,
        fiber = nutriments.num("fiber$suffix") ?: 0.0,
        // Rarely declared on a label, so usually absent — which leaves
        // these at 0 (unknown) and the breakdown hidden.
        solubleFiber = nutriments.num("soluble-fiber$suffix") ?: 0.0,
        insolubleFiber = nutriments.num("insoluble-fiber$suffix") ?: 0.0,
        protein = nutriments.num("proteins$suffix") ?: 0.0,
        fat = nutriments.num("fat$suffix") ?: 0.0,
        saturatedFat = nutriments.num("saturated-fat$suffix") ?: 0.0,
        // OFF reports omega-3 in grams — no scaling, matches our grams field.
        omega3 = nutriments.num("omega-3-fat$suffix") ?: 0.0,
        cholesterol = nutriments.num("cholesterol$suffix")?.times(1000.0) ?: 0.0,
        sodium = nutriments.num("sodium$suffix")?.times(1000.0)
            ?: nutriments.num("salt$suffix")?.times(400.0) ?: 0.0,
        calcium = nutriments.num("calcium$suffix")?.times(1000.0) ?: 0.0,
        iron = nutriments.num("iron$suffix")?.times(1000.0) ?: 0.0,
        magnesium = nutriments.num("magnesium$suffix")?.times(1000.0) ?: 0.0,
        potassium = nutriments.num("potassium$suffix")?.times(1000.0) ?: 0.0,
        zinc = nutriments.num("zinc$suffix")?.times(1000.0) ?: 0.0,
        // OFF reports minerals in grams (base SI); ×1000 → mg, ×1_000_000 → µg.
        phosphorus = nutriments.num("phosphorus$suffix")?.times(1000.0) ?: 0.0,
        selenium = nutriments.num("selenium$suffix")?.times(1_000_000.0) ?: 0.0,
        copper = nutriments.num("copper$suffix")?.times(1000.0) ?: 0.0,
        manganese = nutriments.num("manganese$suffix")?.times(1000.0) ?: 0.0,
        vitaminA = nutriments.num("vitamin-a$suffix")?.times(1_000_000.0) ?: 0.0,
        vitaminC = nutriments.num("vitamin-c$suffix")?.times(1000.0) ?: 0.0,
        vitaminD = nutriments.num("vitamin-d$suffix")?.times(1_000_000.0) ?: 0.0,
        vitaminE = nutriments.num("vitamin-e$suffix")?.times(1000.0) ?: 0.0,
        vitaminK = nutriments.num("vitamin-k$suffix")?.times(1_000_000.0) ?: 0.0,
        vitaminB12 = nutriments.num("vitamin-b12$suffix")?.times(1_000_000.0) ?: 0.0,
        folate = nutriments.num("folates$suffix")?.times(1_000_000.0)
            ?: nutriments.num("folic-acid$suffix")?.times(1_000_000.0) ?: 0.0,
        // B-complex reported in grams → ×1000 for mg. OFF labels niacin as
        // "vitamin-pp" (pellagra-preventing); accept "vitamin-b3" as a fallback.
        vitaminB1 = nutriments.num("vitamin-b1$suffix")?.times(1000.0) ?: 0.0,
        vitaminB2 = nutriments.num("vitamin-b2$suffix")?.times(1000.0) ?: 0.0,
        vitaminB3 = nutriments.num("vitamin-pp$suffix")?.times(1000.0)
            ?: nutriments.num("vitamin-b3$suffix")?.times(1000.0) ?: 0.0,
        vitaminB6 = nutriments.num("vitamin-b6$suffix")?.times(1000.0) ?: 0.0,
    )

    private fun Nutrients.takeIfNonEmpty(): Nutrients? =
        if (calories > 0.0 || carbs > 0.0 || protein > 0.0 || fat > 0.0) this else null

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.num(key: String): Double? {
        val el: JsonElement = this[key] ?: return null
        val prim = el as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return prim.doubleOrNull ?: prim.contentOrNull?.toDoubleOrNull()
    }
}
