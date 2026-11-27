package com.example.foodtracker.data

/** The appearance setting. Light is the default for a new install. */
enum class ThemeMode(val displayName: String) {
    System("Match system"),
    Light("Light"),
    Dark("Dark");

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: Light
    }
}
