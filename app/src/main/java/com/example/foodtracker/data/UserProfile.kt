package com.example.foodtracker.data

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val name: String = "",
    val ageYears: Int = 25,
    val sex: Sex = Sex.Male,
    val heightCm: Int = 170,
    val weightKg: Double = 65.0,
    val activityLevel: ActivityLevel = ActivityLevel.Moderate,
    val goal: Goal = Goal.Maintain,
    val weightChangeRate: WeightChangeRate = WeightChangeRate.Standard,
    /**
     * Target weight in kg, only meaningful when [goal] is `Lose` or `Gain`.
     * Null = not set; the app then shows no progress banner and no goal marker
     * in the BMI visual. The caloric deficit/surplus is still driven entirely
     * by [weightChangeRate]; this field is purely for progress feedback.
     */
    val goalWeightKg: Double? = null,
    /**
     * Where training sits between resistance work and endurance work:
     * `0.0` = all gym, `1.0` = all running, `0.5` = both in equal measure.
     *
     * Optional. [activityLevel] already says *how much* someone trains; this
     * says *what kind*, which is what protein requirements actually turn on.
     * Null means the question was never answered and is read as
     * [TRAINING_MIX_BALANCED], so skipping it leaves every target exactly as
     * it was before the field existed.
     */
    val trainingMix: Double? = null,
) {
    companion object {
        /** Equal parts resistance and endurance — the reading for an unset [trainingMix]. */
        const val TRAINING_MIX_BALANCED = 0.5
    }
}

/**
 * Human label for a [UserProfile.trainingMix] position. Written against the
 * five slider stops rather than exact equality, since the value round-trips
 * through a Float.
 */
fun trainingMixLabel(mix: Double?): String =
    when (mix ?: UserProfile.TRAINING_MIX_BALANCED) {
        in 0.0..0.125 -> "Gym only"
        in 0.125..0.375 -> "Mostly gym"
        in 0.375..0.625 -> "Both equally"
        in 0.625..0.875 -> "Mostly running"
        else -> "Running only"
    }
