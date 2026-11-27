package com.example.foodtracker.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtil {
    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
    fun epochDayToDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
    fun formatHeader(epochDay: Long): String {
        val today = todayEpochDay()
        val date = epochDayToDate(epochDay)
        return when (epochDay) {
            today -> "Today"
            today - 1 -> "Yesterday"
            today + 1 -> "Tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
        }
    }
    fun shortDay(epochDay: Long): String =
        epochDayToDate(epochDay).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))

    /** Compact calendar label like "5 Aug" — used for multi-week chart axes. */
    fun shortDate(epochDay: Long): String =
        epochDayToDate(epochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    fun zoneId(): ZoneId = ZoneId.systemDefault()
}
