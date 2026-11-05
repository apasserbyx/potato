package com.potato.focus

import com.potato.focus.data.FocusSession
import com.potato.focus.data.SessionMode
import com.potato.focus.data.SessionStatus
import com.potato.focus.data.StatsCalculator
import com.potato.focus.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsCalculatorTest {

    private val zone = ZoneId.systemDefault()
    private val now = LocalDate.of(2026, 11, 4)
        .atTime(12, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun session(
        id: Long,
        mode: SessionMode,
        status: SessionStatus,
        focusedSeconds: Int,
        tag: String,
        dayOffset: Long = 0,
        task: String = "任务"
    ): FocusSession {
        val started = LocalDate.of(2026, 11, 4)
            .minusDays(dayOffset)
            .atTime(9, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return FocusSession(
            id = id,
            mode = mode,
            status = status,
            startedAt = started,
            endedAt = started + focusedSeconds * 1000L,
            plannedSeconds = focusedSeconds,
            focusedSeconds = focusedSeconds,
            pausedSeconds = 0,
            presetId = 1L,
            taskId = null,
            habitId = null,
            taskTitle = task,
            tag = tag,
            mood = null,
            note = "",
            source = "timer",
            editedAt = null
        )
    }

    @Test
    fun summaryCountsFocusTimeAndSeparatesStopwatch() {
        val sessions = listOf(
            session(1, SessionMode.FOCUS, SessionStatus.COMPLETED, 1500, "工作"),
            session(2, SessionMode.OPEN, SessionStatus.COMPLETED, 600, "工作"),
            session(3, SessionMode.FOCUS, SessionStatus.PARTIAL, 300, "学习"),
            session(4, SessionMode.SHORT_BREAK, SessionStatus.COMPLETED, 300, ""),
            session(5, SessionMode.FOCUS, SessionStatus.COMPLETED, 1500, "工作", dayOffset = 1)
        )
        val summary = StatsCalculator.summary(sessions, now)
        assertEquals(3900L, summary.focusSeconds)
        assertEquals(2, summary.tomatoCount)
        assertEquals(1, summary.openCount)
        assertEquals(3, summary.completedCount)
        assertEquals(1, summary.partialCount)
        assertEquals(0.75f, summary.completionRate, 0.001f)
        assertEquals(2, summary.streak)
        assertEquals(3600L, summary.tagSlices.first { it.tag == "工作" }.focusSeconds)
    }

    @Test
    fun dailyBucketsKeepTodayAndYesterdaySeparate() {
        val sessions = listOf(
            session(1, SessionMode.FOCUS, SessionStatus.COMPLETED, 1500, "工作"),
            session(2, SessionMode.OPEN, SessionStatus.COMPLETED, 600, "工作"),
            session(3, SessionMode.FOCUS, SessionStatus.COMPLETED, 900, "学习", dayOffset = 1)
        )
        val days = StatsCalculator.daily(sessions, 2, now)
        assertEquals(2, days.size)
        assertEquals(900L, days[0].focusSeconds)
        assertEquals(2100L, days[1].focusSeconds)
        assertEquals(1, days[1].tomatoCount)
        assertEquals(1, days[1].openCount)
    }

    @Test
    fun streakCanContinueFromYesterdayWhenTodayIsEmpty() {
        val sessions = listOf(
            session(1, SessionMode.FOCUS, SessionStatus.COMPLETED, 1500, "工作", dayOffset = 1),
            session(2, SessionMode.FOCUS, SessionStatus.COMPLETED, 1500, "工作", dayOffset = 2)
        )
        assertEquals(2, StatsCalculator.streak(sessions, now))
        assertEquals(0, StatsCalculator.streak(emptyList(), now))
    }

    @Test
    fun dateAttributionUsesLocalStartDate() {
        val session = session(1, SessionMode.FOCUS, SessionStatus.COMPLETED, 1500, "工作")
        assertEquals(LocalDate.of(2026, 11, 4), TimeFormat.localDate(session.startedAt))
    }
}
