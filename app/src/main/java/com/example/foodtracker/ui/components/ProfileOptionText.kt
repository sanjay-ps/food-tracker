package com.example.foodtracker.ui.components

import com.example.foodtracker.data.ActivityLevel
import com.example.foodtracker.data.WeightChangeRate

/** "Moderate" — the row's title; [detail] says what it means. */
val ActivityLevel.title: String
    get() = when (this) {
        ActivityLevel.Sedentary -> "Sedentary"
        ActivityLevel.Light -> "Light"
        ActivityLevel.Moderate -> "Moderate"
        ActivityLevel.Active -> "Active"
        ActivityLevel.VeryActive -> "Very active"
    }

val ActivityLevel.detail: String
    get() = when (this) {
        ActivityLevel.Sedentary -> "Little or no exercise"
        ActivityLevel.Light -> "Exercise 1–3 days a week"
        ActivityLevel.Moderate -> "Exercise 3–5 days a week"
        ActivityLevel.Active -> "Exercise 6–7 days a week"
        ActivityLevel.VeryActive -> "Athlete or physical job"
    }

/** "Standard" — the tile's title, short enough for one line in a grid; [detail] is the pace. */
val WeightChangeRate.title: String
    get() = when (this) {
        WeightChangeRate.Mild -> "Mild"
        WeightChangeRate.Standard -> "Standard"
        WeightChangeRate.Aggressive -> "Fast"
        WeightChangeRate.VeryAggressive -> "Very fast"
    }

val WeightChangeRate.detail: String
    get() = "${if (kgPerWeek % 1.0 == 0.0) kgPerWeek.toInt() else kgPerWeek} kg a week"
