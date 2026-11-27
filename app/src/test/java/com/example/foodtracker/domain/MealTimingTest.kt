package com.example.foodtracker.domain

import com.example.foodtracker.data.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

class MealTimingTest {
    private val now = LocalTime.of(14, 7, 33)

    @Test fun currentMealTodayDefaultsToNow() =
        assertEquals(LocalTime.of(14, 7), MealTiming.defaultFor(MealType.Lunch, 10, 10, now, MealType.Lunch))

    @Test fun earlierMealTodayUsesItsUsualTime() =
        assertEquals(LocalTime.of(8, 30), MealTiming.defaultFor(MealType.Breakfast, 10, 10, now, MealType.Lunch))

    @Test fun laterMealTodayIsNeverInTheFuture() =
        assertEquals(LocalTime.of(14, 7), MealTiming.defaultFor(MealType.Dinner, 10, 10, now, MealType.Lunch))

    @Test fun pastDayUsesUsualTime() =
        assertEquals(LocalTime.of(20, 30), MealTiming.defaultFor(MealType.Dinner, 9, 10, now, MealType.Lunch))

    @Test fun lateWindowWrapsMidnight() {
        assertTrue(MealTiming.isLate(LocalTime.of(22, 0)))
        assertTrue(MealTiming.isLate(LocalTime.of(1, 15)))
        assertFalse(MealTiming.isLate(LocalTime.of(20, 59)))
        assertEquals(25 * 60 + 15, MealTiming.eatingMinute(LocalTime.of(1, 15)))
    }

    @Test fun millisRoundTrip() {
        val zone = ZoneId.of("Asia/Kolkata")
        val t = LocalTime.of(21, 45)
        assertEquals(t, MealTiming.timeOf(MealTiming.toMillis(20_000, t, zone), zone))
    }
}
