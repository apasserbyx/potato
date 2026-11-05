package com.potato.focus.data

import com.potato.focus.util.TimeFormat
import java.time.LocalDate

data class DayStat(
    val date: LocalDate,
    val focusSeconds: Long,
    val tomatoCount: Int,
    val openCount: Int
)

data class TagSlice(
    val tag: String,
    val focusSeconds: Long
)

data class StatsSummary(
    val focusSeconds: Long,
    val tomatoCount: Int,
    val openCount: Int,
    val completedCount: Int,
    val partialCount: Int,
    val streak: Int,
    val tagSlices: List<TagSlice>
) {
    val completionRate: Float
        get() {
            val total = completedCount + partialCount
            return if (total == 0) 0f else completedCount.toFloat() / total
        }
}

object StatsCalculator {

    fun isCountedFocus(session: FocusSession): Boolean =
        session.isFocus &&
            (session.status == SessionStatus.COMPLETED || session.status == SessionStatus.PARTIAL)

    fun summary(sessions: List<FocusSession>, now: Long = System.currentTimeMillis()): StatsSummary {
        val focus = sessions.filter(::isCountedFocus)
        val tomatoes = focus.count { it.mode == SessionMode.FOCUS && it.status == SessionStatus.COMPLETED }
        val open = focus.count { it.mode == SessionMode.OPEN && it.status == SessionStatus.COMPLETED }
        val completed = focus.count { it.status == SessionStatus.COMPLETED }
        val partial = focus.count { it.status == SessionStatus.PARTIAL }

        val tags = focus
            .filter { it.focusedSeconds > 0 }
            .groupBy { it.tag.ifBlank { "未分类" } }
            .map { (tag, rows) -> TagSlice(tag, rows.sumOf { it.focusedSeconds.toLong() }) }
            .sortedByDescending { it.focusSeconds }

        return StatsSummary(
            focusSeconds = focus.sumOf { it.focusedSeconds.toLong() },
            tomatoCount = tomatoes,
            openCount = open,
            completedCount = completed,
            partialCount = partial,
            streak = streak(sessions, now),
            tagSlices = tags
        )
    }

    fun daily(sessions: List<FocusSession>, days: Int, now: Long = System.currentTimeMillis()): List<DayStat> {
        val end = TimeFormat.localDate(now)
        val start = end.minusDays((days - 1).toLong())
        val byDate = sessions
            .filter(::isCountedFocus)
            .filter { TimeFormat.localDate(it.startedAt) in start..end }
            .groupBy { TimeFormat.localDate(it.startedAt) }

        return (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            val rows = byDate[date].orEmpty()
            DayStat(
                date = date,
                focusSeconds = rows.sumOf { it.focusedSeconds.toLong() },
                tomatoCount = rows.count { it.mode == SessionMode.FOCUS && it.status == SessionStatus.COMPLETED },
                openCount = rows.count { it.mode == SessionMode.OPEN && it.status == SessionStatus.COMPLETED }
            )
        }
    }

    fun streak(sessions: List<FocusSession>, now: Long = System.currentTimeMillis()): Int {
        val dates = sessions
            .filter { isCountedFocus(it) && it.status == SessionStatus.COMPLETED }
            .map { TimeFormat.localDate(it.startedAt) }
            .toSet()
        if (dates.isEmpty()) return 0

        var cursor = TimeFormat.localDate(now)
        if (cursor !in dates) cursor = cursor.minusDays(1)
        var count = 0
        while (cursor in dates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun focusSessions(sessions: List<FocusSession>): List<FocusSession> =
        sessions.filter(::isCountedFocus)

    fun sessionsForDay(sessions: List<FocusSession>, date: LocalDate): List<FocusSession> =
        sessions.filter { TimeFormat.localDate(it.startedAt) == date && it.isFocus }
}
