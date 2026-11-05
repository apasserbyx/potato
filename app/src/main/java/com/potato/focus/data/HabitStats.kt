package com.potato.focus.data

import com.potato.focus.util.TimeFormat
import java.time.LocalDate

object HabitStats {

    fun isCompleted(log: HabitLog?): Boolean = log?.status == HabitLogStatus.COMPLETED

    fun isSkipped(log: HabitLog?): Boolean = log?.status == HabitLogStatus.SKIPPED

    fun progressText(habit: Habit, log: HabitLog?): String = when (habit.type) {
        HabitType.TIMED -> {
            val focused = log?.focusedSeconds ?: 0
            "今日 ${(focused / 60)} / ${(habit.targetSeconds / 60)} 分钟"
        }
        HabitType.COUNT -> {
            val count = log?.countValue ?: 0
            "$count / ${habit.targetCount} ${habit.unit}"
        }
    }

    fun targetText(habit: Habit): String = when (habit.type) {
        HabitType.TIMED -> "目标 ${habit.targetSeconds / 60} 分钟"
        HabitType.COUNT -> "目标 ${habit.targetCount} ${habit.unit}"
    }

    /**
     * Current streak for a habit. Today's pending state does not break the
     * streak; skipped scheduled days preserve it; missed scheduled days break it.
     */
    fun streak(
        habit: Habit,
        logsByDate: Map<LocalDate, HabitLog>,
        today: LocalDate = TimeFormat.localDate(System.currentTimeMillis())
    ): Int {
        val created = TimeFormat.localDate(habit.createdAt)
        var cursor = today
        var count = 0
        var guard = 0
        while (guard++ < 400 && !cursor.isBefore(created)) {
            if (habit.isScheduledOn(cursor)) {
                val log = logsByDate[cursor]
                when {
                    isCompleted(log) -> count++
                    isSkipped(log) -> Unit
                    cursor == today -> Unit
                    else -> break
                }
            }
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun completionRate(
        habit: Habit,
        logsByDate: Map<LocalDate, HabitLog>,
        days: Int,
        today: LocalDate = TimeFormat.localDate(System.currentTimeMillis())
    ): Float {
        val created = TimeFormat.localDate(habit.createdAt)
        var cursor = today
        var scheduled = 0
        var completed = 0
        var guard = 0
        while (guard++ < days && !cursor.isBefore(created)) {
            if (habit.isScheduledOn(cursor)) {
                scheduled++
                if (isCompleted(logsByDate[cursor])) completed++
            }
            cursor = cursor.minusDays(1)
        }
        return if (scheduled == 0) 0f else completed.toFloat() / scheduled
    }

    fun logsByHabit(logs: List<HabitLog>): Map<Long, Map<LocalDate, HabitLog>> {
        val result = mutableMapOf<Long, MutableMap<LocalDate, HabitLog>>()
        logs.forEach { log ->
            val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return@forEach
            result.getOrPut(log.habitId) { mutableMapOf() }[date] = log
        }
        return result
    }

    fun remainingSeconds(habit: Habit, log: HabitLog?): Int {
        val done = log?.focusedSeconds ?: 0
        return (habit.targetSeconds - done).coerceAtLeast(1)
    }
}
