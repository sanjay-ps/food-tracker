package com.example.foodtracker.domain

import com.example.foodtracker.data.Nutrients
import com.example.foodtracker.data.UserProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientDensityTest {
    private val goals = GoalCalculator.goalsFor(UserProfile())

    // Per 100 g, rounded from standard tables.
    private val spinach = Nutrients(
        calories = 23.0, carbs = 3.6, fiber = 2.2, protein = 2.9, fat = 0.4, sodium = 79.0,
        vitaminA = 469.0, vitaminC = 28.0, vitaminE = 2.0, calcium = 99.0, iron = 2.7,
        magnesium = 79.0, potassium = 558.0,
    )
    private val dal = Nutrients(
        calories = 116.0, carbs = 20.0, fiber = 8.0, protein = 9.0, fat = 0.4, sodium = 2.0,
        calcium = 19.0, iron = 3.3, magnesium = 36.0, potassium = 369.0, vitaminC = 1.5,
    )
    private val rice = Nutrients(calories = 130.0, carbs = 28.0, fiber = 0.4, protein = 2.7, fat = 0.3, iron = 0.2, magnesium = 12.0, potassium = 35.0)
    private val sugar = Nutrients(calories = 387.0, carbs = 100.0, sugar = 100.0)

    @Test fun leafyGreensBeatPulsesBeatRice() {
        val s = NutrientDensity.score(spinach, goals)
        val d = NutrientDensity.score(dal, goals)
        val r = NutrientDensity.score(rice, goals)
        assertTrue("spinach $s > dal $d", s > d)
        assertTrue("dal $d > rice $r", d > r)
    }

    @Test fun plainSugarScoresBelowZero() {
        assertTrue(NutrientDensity.score(sugar, goals) < 0)
    }

    @Test fun nearZeroCalorieFoodsStayFinite() {
        val water = Nutrients(calories = 0.0, potassium = 2.0)
        val score = NutrientDensity.score(water, goals)
        assertTrue("water $score", score.isFinite() && score < NutrientDensity.score(spinach, goals))
    }
}
