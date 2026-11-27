package com.example.foodtracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastTest {
    @Test fun losingHalfAKiloAWeekReachesGoal() {
        // 70 → 68 at 0.5 kg/week is 4 weeks.
        assertEquals(28L, goalReachDay(0, 70.0, -0.5, 68.0))
        val path = planWeightPath(0, 70.0, -0.5, 68.0, from = 0, to = 60)
        assertEquals(listOf(0L to 70.0, 28L to 68.0, 60L to 68.0), path)
    }

    @Test fun pathExtendsBackBeforeTheAnchor() {
        val path = planWeightPath(14, 70.0, -0.5, 60.0, from = 0, to = 14)
        assertEquals(71.0, path.first().second, 1e-9)
    }

    @Test fun wrongDirectionNeverArrives() = assertNull(goalReachDay(0, 70.0, 0.5, 65.0))

    @Test fun maintainIsFlat() =
        assertEquals(listOf(0L to 70.0, 30L to 70.0), planWeightPath(0, 70.0, 0.0, null, 0, 30))

    @Test fun eating550UnderBurnIsHalfAKiloAWeek() =
        assertEquals(-0.5, paceKgPerWeek(1950.0, 2500.0), 1e-9)
}
