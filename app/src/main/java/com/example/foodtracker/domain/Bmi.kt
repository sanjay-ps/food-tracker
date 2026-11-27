package com.example.foodtracker.domain

import com.example.foodtracker.data.Goal
import com.example.foodtracker.data.UserProfile
import kotlin.math.abs

/**
 * BMI helpers — weight (kg) / height² (m²) with WHO category bands. Used both
 * for the onboarding/profile visualisation and for the goal-progress banner.
 */
object Bmi {

    /** Range of BMI values rendered on the bar — covers WHO bands with headroom. */
    const val DISPLAY_MIN = 15.0
    const val DISPLAY_MAX = 40.0

    /** Band cut-offs in BMI units. */
    const val UNDERWEIGHT_MAX = 18.5
    const val NORMAL_MAX = 25.0
    const val OVERWEIGHT_MAX = 30.0

    enum class Category(val label: String) {
        Underweight("Underweight"),
        Normal("Normal"),
        Overweight("Overweight"),
        Obese("Obese"),
    }

    fun bmi(weightKg: Double, heightCm: Int): Double {
        if (heightCm <= 0) return 0.0
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun category(bmi: Double): Category = when {
        bmi < UNDERWEIGHT_MAX -> Category.Underweight
        bmi < NORMAL_MAX -> Category.Normal
        bmi < OVERWEIGHT_MAX -> Category.Overweight
        else -> Category.Obese
    }
}

/**
 * Snapshot of a user's progress toward their [UserProfile.goalWeightKg].
 * Only meaningful when [UserProfile.goal] is Lose or Gain AND a goal weight
 * is set. Returns null otherwise so callers can hide the goal banner cleanly.
 */
data class GoalProgress(
    val deltaKg: Double,
    val weeksRemaining: Double,
) {
    val onTrack: Boolean get() = deltaKg > 0.01
}

fun goalProgress(profile: UserProfile): GoalProgress? {
    if (profile.goal == Goal.Maintain) return null
    val goalWeight = profile.goalWeightKg ?: return null
    val deltaSigned = goalWeight - profile.weightKg
    val expectedSign = if (profile.goal == Goal.Lose) -1.0 else 1.0
    // If the user already passed their goal (or set a goal that contradicts
    // their chosen direction), surface zero remaining instead of negative.
    val remainingKg = if (deltaSigned * expectedSign > 0.0) abs(deltaSigned) else 0.0
    val kgPerWeek = profile.weightChangeRate.kgPerWeek
    val weeks = if (kgPerWeek > 0.0) remainingKg / kgPerWeek else 0.0
    return GoalProgress(deltaKg = remainingKg, weeksRemaining = weeks)
}
