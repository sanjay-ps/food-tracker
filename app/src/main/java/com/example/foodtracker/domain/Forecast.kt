package com.example.foodtracker.domain

import kotlin.math.abs

/** kcal in one kg of body weight — the usual planning figure. */
const val KCAL_PER_KG = 7700.0

/**
 * The planned weight path from your goal settings.
 *
 * Anchored at ([anchorDay], [anchorKg]) and moving [ratePerWeek] kg/week
 * (negative to lose) — back to [from] and forward to [to] — but never past
 * [goalKg]: once the goal is reached the path holds flat. Returns the corner
 * points (enough to draw the line), oldest first.
 */
fun planWeightPath(
    anchorDay: Long,
    anchorKg: Double,
    ratePerWeek: Double,
    goalKg: Double?,
    from: Long,
    to: Long,
): List<Pair<Long, Double>> {
    fun at(day: Long): Double {
        val raw = anchorKg + ratePerWeek * (day - anchorDay) / 7.0
        if (goalKg == null || ratePerWeek == 0.0 || day < anchorDay) return raw
        return if (ratePerWeek < 0) maxOf(raw, goalKg) else minOf(raw, goalKg)
    }
    val reach = goalReachDay(anchorDay, anchorKg, ratePerWeek, goalKg)
    return buildList {
        add(from to at(from))
        if (reach != null && reach in (from + 1) until to) add(reach to at(reach))
        add(to to at(to))
    }
}

/** The day the plan reaches [goalKg], or null if it never does (no goal, no rate, wrong way). */
fun goalReachDay(anchorDay: Long, anchorKg: Double, ratePerWeek: Double, goalKg: Double?): Long? {
    if (goalKg == null || ratePerWeek == 0.0) return null
    val gap = goalKg - anchorKg
    if (gap == 0.0) return anchorDay
    if (gap * ratePerWeek < 0) return null
    return anchorDay + (abs(gap) / abs(ratePerWeek) * 7.0).toLong()
}

/** Weekly weight change implied by eating [avgKcal] a day against a burn of [tdee]. */
fun paceKgPerWeek(avgKcal: Double, tdee: Double): Double = (avgKcal - tdee) * 7.0 / KCAL_PER_KG
