package com.example.foodtracker.domain

/** Consecutive days with at least one food logged. */
data class Streak(val current: Int, val best: Int)

/**
 * [current] counts back from today — or from yesterday when today has nothing
 * yet, so a streak stays alive until the day is actually over. [best] is the
 * longest run anywhere in [days] (any order, duplicates fine).
 */
fun streakOf(days: Collection<Long>, today: Long): Streak {
    if (days.isEmpty()) return Streak(0, 0)
    val set = days.toHashSet()
    var best = 0
    for (d in set) {
        if (d - 1 in set) continue // only start counting at the first day of a run
        var len = 1
        while (d + len in set) len++
        if (len > best) best = len
    }
    val anchor = when {
        today in set -> today
        today - 1 in set -> today - 1
        else -> return Streak(0, best)
    }
    var current = 0
    while (anchor - current in set) current++
    return Streak(current, best)
}
