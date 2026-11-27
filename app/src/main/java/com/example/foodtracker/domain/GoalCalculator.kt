package com.example.foodtracker.domain

import com.example.foodtracker.data.ActivityLevel
import com.example.foodtracker.data.Goal
import com.example.foodtracker.data.NutrientGoals
import com.example.foodtracker.data.Sex
import com.example.foodtracker.data.UserProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Computes daily nutrient goals from a user profile using Mifflin-St Jeor
 * for BMR and a configurable activity multiplier for TDEE. Protein is set
 * per kg of body weight (see [proteinPerKg]), fat takes 28% of calories, and
 * carbs absorb the remainder — so the split moves with the goal and the
 * training load rather than being fixed. Micronutrient goals follow the
 * Indian ICMR RDA where reasonable, with sensible defaults otherwise.
 */
object GoalCalculator {

    fun bmr(profile: UserProfile): Double {
        // Mifflin-St Jeor
        val base = (10.0 * profile.weightKg) + (6.25 * profile.heightCm) - (5.0 * profile.ageYears)
        return when (profile.sex) {
            Sex.Male -> base + 5.0
            Sex.Female -> base - 161.0
        }
    }

    fun tdee(profile: UserProfile): Double = bmr(profile) * profile.activityLevel.multiplier

    /**
     * Signed daily kcal delta for the chosen goal at the chosen pace. Lose
     * subtracts, Gain adds, Maintain is always zero. Magnitude follows the
     * ~7700 kcal-per-kg rule scaled to a weekly target.
     */
    fun calorieAdjustment(profile: UserProfile): Double = when (profile.goal) {
        Goal.Maintain -> 0.0
        Goal.Lose -> -profile.weightChangeRate.kcalPerDay
        Goal.Gain -> profile.weightChangeRate.kcalPerDay
    }

    fun targetCalories(profile: UserProfile): Double =
        max(1200.0, tdee(profile) + calorieAdjustment(profile))

    /**
     * Grams of protein per kg of body weight. Protein is the one macro set
     * from body mass rather than from calories, so it has to account for
     * training: 1.2 g/kg is roughly the sedentary RDA and is far too low for
     * someone holding their weight while lifting. [ActivityLevel] is the only
     * training signal the profile carries, so it does that work here — a
     * Maintain goal runs 1.0 g/kg sedentary up to 1.8 g/kg for an athlete,
     * which is where muscle gain at stable weight actually sits.
     *
     * Lose and Gain start higher for different reasons: a deficit needs the
     * extra protein to spare lean mass, a surplus to build it.
     *
     * [UserProfile.trainingMix] then splits that activity bump by *kind* of
     * training, because the two are not equivalent: the ISSN puts strength
     * athletes around 1.6-2.0 g/kg and endurance athletes around 1.2-1.4. All
     * gym takes 125% of the bump, all running 75%, and the unset midpoint
     * takes it whole — so a profile that never touches the slider gets the
     * same number it did before the field existed. The two are multiplied
     * rather than added, which also means the mix correctly does nothing at
     * Sedentary: there is no training to characterise.
     */
    fun proteinPerKg(profile: UserProfile): Double {
        val base = when (profile.goal) {
            Goal.Maintain -> 1.0
            Goal.Gain -> 1.3
            Goal.Lose -> 1.5
        }
        val training = when (profile.activityLevel) {
            ActivityLevel.Sedentary -> 0.0
            ActivityLevel.Light -> 0.2
            ActivityLevel.Moderate -> 0.4
            ActivityLevel.Active -> 0.6
            ActivityLevel.VeryActive -> 0.8
        }
        val mix = profile.trainingMix?.coerceIn(0.0, 1.0) ?: UserProfile.TRAINING_MIX_BALANCED
        val emphasis = 1.25 - (0.5 * mix)
        // Clamped to the span the literature actually supports. Only the
        // extreme corner (hard cut, athlete, all gym) reaches the ceiling.
        return (base + training * emphasis).coerceIn(0.8, 2.4)
    }

    fun goalsFor(profile: UserProfile): NutrientGoals {
        val cals = targetCalories(profile)

        // Capped at 40% of calories. Body-weight protein and calorie-derived
        // fat are computed independently, so a heavy person pinned to the
        // 1200 kcal floor could otherwise be handed 200 g of protein and
        // almost no carbs. Inert for any realistic profile — a hard cut at
        // 2.3 g/kg still only reaches about 30%.
        val proteinG = min(profile.weightKg * proteinPerKg(profile), (cals * 0.40) / 4.0)
        val fatG = (cals * 0.28) / 9.0
        val proteinCals = proteinG * 4.0
        val fatCals = fatG * 9.0
        val carbCals = max(0.0, cals - proteinCals - fatCals)
        val carbsG = carbCals / 4.0
        val fiberG = max(25.0, cals / 1000.0 * 14.0)
        val sugarG = (cals * 0.10) / 4.0
        val satFatG = (cals * 0.10) / 9.0

        // ICMR-leaning micro RDA defaults
        val isFemale = profile.sex == Sex.Female
        return NutrientGoals(
            calories = cals.roundTo(0),
            carbs = carbsG.roundTo(0),
            protein = proteinG.roundTo(0),
            fat = fatG.roundTo(0),
            fiber = fiberG.roundTo(0),
            sugar = sugarG.roundTo(0),
            saturatedFat = satFatG.roundTo(0),
            omega3 = 1.6,
            // Deliberately zero, and deliberately not a target: the field is
            // NutrientTarget.Untargeted, so nothing reads this number. 300 mg
            // was the pre-2015 DGA cap and no current guideline has reinstated
            // it — see NutrientTarget.Untargeted for the three sources.
            cholesterol = 0.0,
            sodium = 2300.0,
            calcium = 1000.0,
            iron = if (isFemale) 21.0 else 11.0,
            magnesium = if (isFemale) 310.0 else 400.0,
            potassium = 3500.0,
            zinc = if (isFemale) 8.0 else 11.0,
            phosphorus = 700.0,
            selenium = 55.0,
            copper = 0.9,
            manganese = if (isFemale) 1.8 else 2.3,
            vitaminA = 900.0,
            vitaminC = if (isFemale) 75.0 else 90.0,
            vitaminD = 15.0,
            vitaminE = 15.0,
            vitaminK = if (isFemale) 90.0 else 120.0,
            vitaminB12 = 2.4,
            folate = 400.0,
            vitaminB1 = if (isFemale) 1.1 else 1.2,
            vitaminB2 = if (isFemale) 1.1 else 1.3,
            vitaminB3 = if (isFemale) 14.0 else 16.0,
            vitaminB6 = 1.3,
            waterMl = (profile.weightKg * 35.0).roundToInt().coerceAtLeast(1500),
        )
    }

    private fun Double.roundTo(decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        return Math.round(this * factor) / factor
    }
}
