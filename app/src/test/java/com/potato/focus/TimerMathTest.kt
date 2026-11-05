package com.potato.focus

import com.potato.focus.timer.TimerMath
import com.potato.focus.timer.TimerMode
import com.potato.focus.timer.TimerPhase
import com.potato.focus.timer.TimerSnapshot
import com.potato.focus.timer.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerMathTest {

    private val start = 1_000_000L

    private fun pomodoro(
        status: TimerStatus = TimerStatus.RUNNING,
        endsAt: Long = start + 25 * 60_000L,
        remainingMs: Long = 0L,
        accumulatedFocusMs: Long = 0L,
        segmentStartedAt: Long = start,
        completedCount: Int = 0
    ) = TimerSnapshot(
        mode = TimerMode.POMODORO,
        phase = TimerPhase.FOCUS,
        status = status,
        startedAt = start,
        endsAt = endsAt,
        segmentStartedAt = segmentStartedAt,
        accumulatedFocusMs = accumulatedFocusMs,
        remainingMs = remainingMs,
        plannedSeconds = 25 * 60,
        focusMin = 25,
        shortBreakMin = 5,
        longBreakMin = 15,
        longBreakEvery = 4,
        completedFocusCount = completedCount
    )

    @Test
    fun remainingAndProgressForRunningPomodoro() {
        val now = start + 5 * 60_000L
        val snapshot = pomodoro()
        assertEquals(20 * 60_000L, TimerMath.remainingMillis(snapshot, now))
        assertEquals(0.2f, TimerMath.progress(snapshot, now), 0.001f)
        assertEquals(20 * 60L, TimerMath.displaySeconds(snapshot, now))
    }

    @Test
    fun pausedPomodoroKeepsRemainingTime() {
        val snapshot = pomodoro(
            status = TimerStatus.PAUSED,
            endsAt = 0L,
            remainingMs = 60_000L,
            accumulatedFocusMs = 24 * 60_000L,
            segmentStartedAt = 0L
        )
        assertEquals(60_000L, TimerMath.remainingMillis(snapshot, start + 999_999L))
        assertEquals(0.96f, TimerMath.progress(snapshot, start + 999_999L), 0.001f)
    }

    @Test
    fun stopwatchElapsedIncludesCurrentSegment() {
        val now = start + 45_000L
        val snapshot = TimerSnapshot(
            mode = TimerMode.STOPWATCH,
            phase = TimerPhase.OPEN,
            status = TimerStatus.RUNNING,
            startedAt = start,
            segmentStartedAt = start + 30_000L,
            accumulatedFocusMs = 30_000L
        )
        assertEquals(45_000L, TimerMath.elapsedFocusMillis(snapshot, now))
        assertEquals(45L, TimerMath.displaySeconds(snapshot, now))
    }

    @Test
    fun dueCheckOnlyWhenEndsAtPassed() {
        val snapshot = pomodoro(endsAt = start + 60_000L)
        assertFalse(TimerMath.isPomodoroDue(snapshot, start + 59_999L))
        assertTrue(TimerMath.isPomodoroDue(snapshot, start + 60_000L))
        assertFalse(TimerMath.isPomodoroDue(snapshot.copy(status = TimerStatus.PAUSED), start + 70_000L))
    }

    @Test
    fun longBreakEveryFourthFocus() {
        val snapshot = pomodoro(completedCount = 3)
        assertEquals(TimerPhase.SHORT_BREAK, TimerMath.nextPhase(snapshot, 3))
        assertEquals(TimerPhase.LONG_BREAK, TimerMath.nextPhase(snapshot, 4))
        assertEquals(TimerPhase.SHORT_BREAK, TimerMath.nextPhase(snapshot, 5))
        assertEquals(TimerPhase.LONG_BREAK, TimerMath.nextPhase(snapshot, 8))
    }
}
