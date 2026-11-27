package com.example.foodtracker.domain

import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.Nutrients

/**
 * How nutrition-rich a food is: the Nutrient Rich Foods index (NRF9.3),
 * scored against the user's own targets.
 *
 * - Add the nine nutrients to encourage — protein, fiber, vitamins A, C and E,
 *   calcium, iron, magnesium, potassium — each as a % of its daily goal,
 *   capped at 100% so one huge figure cannot carry a food.
 * - Subtract the three to limit — saturated fat, sugar, sodium — as a % of
 *   their caps.
 * - Express it per 100 kcal: nutrition for the energy it costs.
 *
 * Sugar is total sugar (the app has no added-sugar figure), so fruit and milk
 * pay a small penalty the original index would not charge.
 */
object NutrientDensity {

    /**
     * Calories below this are counted as this, so near-zero foods (black tea,
     * cucumber) rank on their nutrients instead of dividing by almost nothing.
     */
    private const val KCAL_FLOOR = 20.0

    /** Higher is richer; negative means the limits outweigh the nutrients. */
    fun score(per100g: Nutrients, goals: NutrientGoals): Double {
        fun pct(value: Double, goal: Double) = if (goal > 0) value / goal * 100 else 0.0
        val encourage = listOf(
            per100g.protein to goals.protein,
            per100g.fiber to goals.fiber,
            per100g.vitaminA to goals.vitaminA,
            per100g.vitaminC to goals.vitaminC,
            per100g.vitaminE to goals.vitaminE,
            per100g.calcium to goals.calcium,
            per100g.iron to goals.iron,
            per100g.magnesium to goals.magnesium,
            per100g.potassium to goals.potassium,
        ).sumOf { (v, g) -> pct(v, g).coerceAtMost(100.0) }
        val limit = listOf(
            per100g.saturatedFat to goals.saturatedFat,
            per100g.sugar to goals.sugar,
            per100g.sodium to goals.sodium,
        ).sumOf { (v, g) -> pct(v, g) }
        return (encourage - limit) * 100 / per100g.calories.coerceAtLeast(KCAL_FLOOR)
    }
}
