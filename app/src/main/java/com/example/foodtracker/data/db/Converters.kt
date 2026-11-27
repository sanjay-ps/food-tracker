package com.example.foodtracker.data.db

import androidx.room.TypeConverter
import com.example.foodtracker.data.ServingVariant
import com.example.foodtracker.data.StyleOption
import kotlinx.serialization.json.Json

object Converters {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parsed-variant cache, keyed by the raw column text.
     *
     * Every FoodEntity Room hands back runs this converter, and the Log food
     * screen keeps six queries over `foods` alive at once — all foods,
     * favourites, recent, frequent, custom visible, custom hidden. One write to
     * the table re-emits all six and re-parses every row of each, and the rows
     * have not changed between emissions, so the JSON text is identical and a
     * hit costs a map lookup instead of a parse.
     *
     * Safe to share a parsed list between entities: [ServingVariant] is an
     * @Immutable data class of two vals and nothing mutates the list. Bounded
     * and access-ordered so it cannot grow with the food table, and
     * synchronized because Room converts on whichever thread ran the query.
     */
    private const val VARIANT_CACHE_SIZE = 256

    private val variantCache =
        object : LinkedHashMap<String, List<ServingVariant>>(64, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<ServingVariant>>,
            ): Boolean = size > VARIANT_CACHE_SIZE
        }

    @TypeConverter
    @JvmStatic
    fun servingVariantsToJson(variants: List<ServingVariant>): String =
        json.encodeToString(variants)

    @TypeConverter
    @JvmStatic
    fun jsonToServingVariants(value: String): List<ServingVariant> {
        if (value.isBlank()) return emptyList()
        // Read inside the lock: accessOrder = true means get() mutates order.
        synchronized(variantCache) { variantCache[value] }?.let { return it }
        val parsed = runCatching {
            json.decodeFromString<List<ServingVariant>>(value)
        }.getOrDefault(emptyList())
        synchronized(variantCache) { variantCache[value] = parsed }
        return parsed
    }

    @TypeConverter
    @JvmStatic
    fun styleOptionsToJson(options: List<StyleOption>): String =
        if (options.isEmpty()) "" else json.encodeToString(options)

    /**
     * Uncached on purpose, unlike the serving variants above: only a handful of
     * seeded foods carry a style axis, so almost every row is blank and returns
     * before any parsing happens.
     */
    @TypeConverter
    @JvmStatic
    fun jsonToStyleOptions(value: String): List<StyleOption> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StyleOption>>(value)
        }.getOrDefault(emptyList())
    }
}
