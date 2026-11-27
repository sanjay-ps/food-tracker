package com.example.foodtracker.data

enum class MealType(val displayName: String, val emoji: String) {
    Breakfast("Breakfast", "🌞"),
    Lunch("Lunch", "🍛"),
    Snack("Snack", "🍪"),
    Dinner("Dinner", "🌙");

    companion object {
        fun fromName(name: String): MealType = entries.firstOrNull { it.name == name } ?: Snack

        /**
         * The meal the wall clock says you are in.
         *
         * One definition, because there were two and they disagreed: Today's
         * FAB called 18:00–19:00 dinner while the deep-link router called it a
         * snack, so the same tap landed in different meals depending on which
         * screen you came from. 19:00 wins — filter coffee and an evening
         * tiffin are the overwhelmingly more likely 18:30, and a wrong guess
         * costs one tap on a footer chip either way.
         *
         * Drives Today's auto-expanded row and FAB, the reminder deep links,
         * and the meal an any-time plan line is checked off into.
         */
        fun currentForTime(): MealType {
            val hour = java.time.LocalTime.now().hour
            return when {
                hour < 11 -> Breakfast
                hour < 15 -> Lunch
                hour < 19 -> Snack
                else -> Dinner
            }
        }
    }
}

enum class FoodCategory(val displayName: String) {
    SouthIndian("South Indian"),
    NorthIndian("North Indian"),
    Snack("Snack"),
    Beverage("Beverage"),
    Dairy("Dairy"),
    Fruit("Fruit"),
    Vegetable("Vegetable"),
    Grain("Grain"),
    Protein("Protein"),
    Packaged("Packaged"),
    Custom("Custom"),
    Other("Other");

    companion object {
        fun fromName(name: String): FoodCategory = entries.firstOrNull { it.name == name } ?: Other
    }
}

enum class ActivityType(val displayName: String, val metValue: Double) {
    Walking("Walking", 3.5),
    BriskWalking("Brisk Walking", 4.5),
    Running("Running", 9.8),
    Cycling("Cycling", 7.5),
    Swimming("Swimming", 8.0),
    Yoga("Yoga", 3.0),
    WeightTraining("Weight Training", 5.0),
    HIIT("HIIT", 9.0),
    Dancing("Dancing", 5.0),
    Cardio("Cardio", 7.0),
    Other("Other", 4.0);

    companion object {
        fun fromName(name: String): ActivityType = entries.firstOrNull { it.name == name } ?: Other
    }
}

enum class Sex(val displayName: String) {
    Male("Male"), Female("Female");

    companion object {
        fun fromName(name: String): Sex = entries.firstOrNull { it.name == name } ?: Male
    }
}

enum class ActivityLevel(val displayName: String, val multiplier: Double) {
    Sedentary("Sedentary (little/no exercise)", 1.2),
    Light("Light (1-3 days/week)", 1.375),
    Moderate("Moderate (3-5 days/week)", 1.55),
    Active("Active (6-7 days/week)", 1.725),
    VeryActive("Very Active (athlete)", 1.9);

    companion object {
        fun fromName(name: String): ActivityLevel = entries.firstOrNull { it.name == name } ?: Moderate
    }
}

enum class Goal(val displayName: String) {
    Lose("Lose weight"),
    Maintain("Maintain weight"),
    Gain("Gain weight");

    companion object {
        fun fromName(name: String): Goal = entries.firstOrNull { it.name == name } ?: Maintain
    }
}

/**
 * Pace selector for Lose / Gain goals. The kcal delta per day is derived from
 * the kg/week target using the rule of thumb ~7700 kcal per kilogram of body
 * mass — direction (deficit vs surplus) is taken from the [Goal]. Ignored when
 * the goal is [Goal.Maintain].
 */
enum class WeightChangeRate(val displayName: String, val kgPerWeek: Double) {
    Mild("Mild · 0.25 kg/week", 0.25),
    Standard("Standard · 0.5 kg/week", 0.5),
    Aggressive("Aggressive · 0.75 kg/week", 0.75),
    VeryAggressive("Very aggressive · 1 kg/week", 1.0);

    /** Approximate daily kcal delta needed to hit this weekly rate (unsigned). */
    val kcalPerDay: Double get() = kgPerWeek * 7700.0 / 7.0

    companion object {
        fun fromName(name: String?): WeightChangeRate =
            entries.firstOrNull { it.name == name } ?: Standard
    }
}
