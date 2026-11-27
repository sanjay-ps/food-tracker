package com.example.foodtracker.domain

import com.example.foodtracker.data.MealType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When food was eaten. Stored in the entry's existing `logged_at_millis`, which
 * now means "eaten at": the day is the entry's date, the clock time is when
 * you ate (editable, defaulting sensibly), so late eating can be measured.
 */
object MealTiming {
    /** From here until [NIGHT_END] a meal counts as late. */
    val LATE_FROM: LocalTime = LocalTime.of(21, 30)

    /** Before this, a time belongs to the night before (a 1 AM snack is late, not early). */
    val NIGHT_END: LocalTime = LocalTime.of(4, 0)

    fun usual(meal: MealType): LocalTime = when (meal) {
        MealType.Breakfast -> LocalTime.of(8, 30)
        MealType.Lunch -> LocalTime.of(13, 0)
        MealType.Snack -> LocalTime.of(17, 0)
        MealType.Dinner -> LocalTime.of(20, 30)
    }

    /**
     * Logging the meal you are in right now, today → now. Anything else — a
     * different meal, or a past day — → that meal's usual time, never later
     * than now on today.
     */
    fun defaultFor(meal: MealType, day: Long, today: Long, now: LocalTime, currentMeal: MealType): LocalTime = when {
        day == today && meal == currentMeal -> now.withSecond(0).withNano(0)
        day == today -> minOf(usual(meal), now.withSecond(0).withNano(0))
        else -> usual(meal)
    }

    fun isLate(t: LocalTime): Boolean = t >= LATE_FROM || t < NIGHT_END

    /** Minutes into the eating day: after-midnight times sort after 11:59 PM. */
    fun eatingMinute(t: LocalTime): Int = t.hour * 60 + t.minute + if (t < NIGHT_END) 24 * 60 else 0

    fun toMillis(day: Long, time: LocalTime, zone: ZoneId): Long =
        LocalDate.ofEpochDay(day).atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun timeOf(millis: Long, zone: ZoneId): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

    fun label(t: LocalTime): String = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    /** "9:12 PM" for an eating-day minute, wrapping past midnight. */
    fun label(eatingMinute: Int): String = label(LocalTime.of((eatingMinute / 60) % 24, eatingMinute % 60))
}
