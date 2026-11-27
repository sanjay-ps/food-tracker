package com.example.foodtracker.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Guards `assets/food_catalogue.json`, the extended food list. */
class FoodCatalogueTest {
    private val root: JsonObject = Json.parseToJsonElement(
        File("src/main/assets/food_catalogue.json").readText()
    ).jsonObject
    private val foods = root.getValue("foods").jsonArray.map { it.jsonObject }

    @Test fun catalogueBringsTheAppPast500Foods() {
        // 178 hand-tuned seed foods plus this file.
        assertTrue("only ${178 + foods.size} foods", 178 + foods.size >= 500)
    }

    @Test fun namesAndIdsAreUnique() {
        assertEquals(foods.size, foods.map { it.str("name").lowercase() }.toSet().size)
        assertEquals(foods.size, foods.map { it.str("uuid") }.toSet().size)
    }

    @Test fun energyAgreesWithMacros() {
        foods.forEach { f ->
            val n = f.getValue("per100g").jsonObject
            fun v(k: String) = n.getValue(k).jsonPrimitive.double
            val kcal = v("calories")
            val est = 4 * v("carbs") + 4 * v("protein") + 9 * v("fat")
            if (kcal > 20) assertTrue("${f.str("name")}: $kcal vs $est", abs(kcal - est) / kcal <= 0.18)
            assertTrue("${f.str("name")}: fiber > carbs", v("fiber") <= v("carbs") + 0.01)
        }
    }

    @Test fun servingLabelSplitsIntoTwoTags() {
        assertEquals("1 bowl" to "200 g", com.example.foodtracker.ui.components.servingParts("1 bowl (200 g)"))
        assertEquals("1 glass" to "250 ml", com.example.foodtracker.ui.components.servingParts("1 glass (250 ml)"))
        assertEquals("100 g" to null, com.example.foodtracker.ui.components.servingParts("100 g"))
    }

    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
}
