package com.example.foodtracker.domain

import com.example.foodtracker.ui.components.atoms.grouped
import org.junit.Assert.assertEquals
import org.junit.Test

class StreaksTest {
    @Test fun emptyHasNoStreak() = assertEquals(Streak(0, 0), streakOf(emptyList(), 100))

    @Test fun runEndingTodayCounts() = assertEquals(Streak(3, 3), streakOf(listOf(98, 99, 100), 100))

    @Test fun yesterdayKeepsStreakAlive() = assertEquals(Streak(2, 2), streakOf(listOf(98, 99), 100))

    @Test fun gapBreaksCurrentButNotBest() =
        assertEquals(Streak(0, 4), streakOf(listOf(90, 91, 92, 93, 97), 100))

    @Test fun bestIsLongestRunAnywhere() =
        assertEquals(Streak(1, 3), streakOf(listOf(10, 11, 12, 50, 100), 100))

    @Test fun numbersUseCommas() {
        assertEquals("2,468", grouped(2468.0))
        assertEquals("1,234,567", grouped(1234567.4))
        assertEquals("-1,200", grouped(-1200.0))
        assertEquals("999", grouped(999.0))
    }
}
