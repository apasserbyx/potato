package com.potato.focus

import com.potato.focus.data.Habit
import com.potato.focus.data.HabitLog
import com.potato.focus.data.HabitLogStatus
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.HabitStats
import com.potato.focus.data.HabitType
import com.potato.focus.data.isScheduledOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitStatsTest {

    private val today = LocalDate.of(2026, 11, 5)
    private val createdAt = today.minusDays(10)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun habit(mask: Int = 0b1111111) = Habit(
        id = 1L,
        name = "Reading",
        icon = "📖",
        color = "#E2573E",
        type = HabitType.TIMED,
        targetSeconds = 25 * 60,
        targetCount = 0,
        unit = "",
        weekdaysMask = mask,
        reminderSlot = HabitReminderSlot.MORNING,
        reminderEnabled = true,
        note = "",
        sortOrder = 0,
        archivedAt = null,
        createdAt = createdAt
    )

    private fun log(date: LocalDate, status: HabitLogStatus) = HabitLog(
        id = date.toEpochDay(),
        habitId = 1L,
        date = date.toString(),
        status = status,
        focusedSeconds = 0,
        countValue = 0,
        source = "timer",
        completedAt = null,
        note = ""
    )

    @Test
    fun weekdayMaskMatchesOnlySelectedDays() {
        val mondayOnly = habit(mask = 1)
        assertTrue(mondayOnly.isScheduledOn(LocalDate.of(2026, 11, 2)))
        assertFalse(mondayOnly.isScheduledOn(LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun pendingTodayDoesNotBreakStreak() {
        val logs = listOf(
            log(today.minusDays(1), HabitLogStatus.COMPLETED),
            log(today.minusDays(2), HabitLogStatus.COMPLETED)
        ).associateBy { LocalDate.parse(it.date) }
        assertEquals(2, HabitStats.streak(habit(), logs, today))
    }

    @Test
    fun skippedDayPreservesButDoesNotIncreaseStreak() {
        val logs = listOf(
            log(today.minusDays(1), HabitLogStatus.COMPLETED),
            log(today.minusDays(2), HabitLogStatus.SKIPPED),
            log(today.minusDays(3), HabitLogStatus.COMPLETED)
        ).associateBy { LocalDate.parse(it.date) }
        assertEquals(2, HabitStats.streak(habit(), logs, today))
    }

    @Test
    fun missedScheduledDayResetsStreak() {
        val logs = listOf(
            log(today.minusDays(2), HabitLogStatus.COMPLETED)
        ).associateBy { LocalDate.parse(it.date) }
        assertEquals(0, HabitStats.streak(habit(), logs, today))
    }

    @Test
    fun completionRateUsesScheduledDaysInWindow() {
        val logs = listOf(
            log(today, HabitLogStatus.COMPLETED),
            log(today.minusDays(1), HabitLogStatus.COMPLETED)
        ).associateBy { LocalDate.parse(it.date) }
        assertEquals(2f / 3f, HabitStats.completionRate(habit(), logs, 3, today), 0.001f)
    }
}
