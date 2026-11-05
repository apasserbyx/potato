package com.potato.focus.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object TimeFormat {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun dateTitle(now: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(now).atZone(zone).format(dateFormatter)

    fun shortDate(now: Long): String =
        Instant.ofEpochMilli(now).atZone(zone).format(shortDateFormatter)

    fun weekdayShort(date: LocalDate): String = date.format(weekdayFormatter)

    fun clockTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    fun timerText(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun roundedMinutes(totalSeconds: Long): Int =
        ((totalSeconds.coerceAtLeast(0) + 30) / 60).toInt()

    fun durationText(totalSeconds: Long): String {
        val minutes = roundedMinutes(totalSeconds).toLong()
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 -> "${hours} 小时 ${rest} 分"
            hours > 0 -> "${hours} 小时"
            else -> "${minutes} 分钟"
        }
    }

    fun compactDuration(totalSeconds: Long): String {
        val minutes = (totalSeconds.coerceAtLeast(0) + 30) / 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) "${hours}h" else "${hours}h ${rest}m"
        } else {
            "${minutes}m"
        }
    }

    fun startOfToday(now: Long = System.currentTimeMillis()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfWeek(now: Long = System.currentTimeMillis()): LocalDate {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.with(DayOfWeek.MONDAY)
    }

    fun localDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun localDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    fun greeting(now: Long = System.currentTimeMillis()): String {
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        return when (hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..22 -> "晚上好"
            else -> "夜深了"
        }
    }

    fun daysBetweenInclusive(start: LocalDate, end: LocalDate): Long =
        ChronoUnit.DAYS.between(start, end) + 1
}
