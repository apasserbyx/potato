package com.potato.focus.timer

import com.potato.focus.data.Preset
import com.potato.focus.data.TaskItem
import kotlin.math.max
import kotlin.math.min

enum class TimerMode {
    POMODORO,
    STOPWATCH,
    HABIT
}

enum class TimerPhase {
    IDLE,
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
    OPEN,
    HABIT
}

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED
}

data class TimerSnapshot(
    val mode: TimerMode = TimerMode.POMODORO,
    val phase: TimerPhase = TimerPhase.IDLE,
    val status: TimerStatus = TimerStatus.IDLE,
    val startedAt: Long = 0L,
    val endsAt: Long = 0L,
    val segmentStartedAt: Long = 0L,
    val accumulatedFocusMs: Long = 0L,
    val pausedAt: Long = 0L,
    val remainingMs: Long = 0L,
    val plannedSeconds: Int = 0,
    val sessionId: Long = 0L,
    val presetId: Long = 0L,
    val presetName: String = "经典 25/5",
    val taskId: Long? = null,
    val habitId: Long? = null,
    val taskTitle: String = "",
    val tag: String = "",
    val completedFocusCount: Int = 0,
    val focusMin: Int = 25,
    val shortBreakMin: Int = 5,
    val longBreakMin: Int = 15,
    val longBreakEvery: Int = 4,
    val autoStartBreak: Boolean = true,
    val autoStartFocus: Boolean = false,
    val sound: Boolean = true
) {
    val isActive: Boolean
        get() = status == TimerStatus.RUNNING || status == TimerStatus.PAUSED

    val isBreak: Boolean
        get() = phase == TimerPhase.SHORT_BREAK || phase == TimerPhase.LONG_BREAK

    val isStopwatch: Boolean
        get() = mode == TimerMode.STOPWATCH || phase == TimerPhase.OPEN

    val isHabit: Boolean
        get() = mode == TimerMode.HABIT || phase == TimerPhase.HABIT

    val phaseTitle: String
        get() = when (phase) {
            TimerPhase.FOCUS -> "专注中"
            TimerPhase.SHORT_BREAK -> "短休息"
            TimerPhase.LONG_BREAK -> "长休息"
            TimerPhase.OPEN -> "正计时中"
            TimerPhase.HABIT -> "习惯中"
            TimerPhase.IDLE -> "专注"
        }

    val phaseDurationSeconds: Int
        get() = when (phase) {
            TimerPhase.FOCUS -> focusMin * 60
            TimerPhase.SHORT_BREAK -> shortBreakMin * 60
            TimerPhase.LONG_BREAK -> longBreakMin * 60
            TimerPhase.HABIT -> plannedSeconds
            else -> 0
        }

    companion object {
        fun idleFrom(preset: Preset, mode: TimerMode, task: TaskItem?, completedCount: Int): TimerSnapshot =
            TimerSnapshot(
                mode = mode,
                phase = TimerPhase.IDLE,
                status = TimerStatus.IDLE,
                presetId = preset.id,
                presetName = preset.name,
                focusMin = preset.focusMin,
                shortBreakMin = preset.shortBreakMin,
                longBreakMin = preset.longBreakMin,
                longBreakEvery = preset.longBreakEvery,
                autoStartBreak = preset.autoStartBreak,
                autoStartFocus = preset.autoStartFocus,
                sound = preset.sound,
                taskId = task?.id,
                taskTitle = task?.title.orEmpty(),
                tag = task?.tag.orEmpty(),
                completedFocusCount = completedCount
            )
    }
}

object TimerMath {

    fun remainingMillis(snapshot: TimerSnapshot, now: Long): Long {
        if (snapshot.isStopwatch) return 0L
        return when (snapshot.status) {
            TimerStatus.RUNNING -> max(0L, snapshot.endsAt - now)
            TimerStatus.PAUSED -> max(0L, snapshot.remainingMs)
            TimerStatus.IDLE -> 0L
        }
    }

    fun elapsedFocusMillis(snapshot: TimerSnapshot, now: Long): Long {
        val segment = if (snapshot.status == TimerStatus.RUNNING && snapshot.segmentStartedAt > 0L) {
            max(0L, now - snapshot.segmentStartedAt)
        } else {
            0L
        }
        return snapshot.accumulatedFocusMs + segment
    }

    fun totalWallMillis(snapshot: TimerSnapshot, now: Long): Long {
        if (snapshot.startedAt <= 0L) return 0L
        return max(0L, now - snapshot.startedAt)
    }

    fun progress(snapshot: TimerSnapshot, now: Long): Float {
        val total = snapshot.phaseDurationSeconds
        if (total <= 0) return 0f
        val remaining = remainingMillis(snapshot, now)
        return (1f - remaining.toFloat() / (total * 1000f)).coerceIn(0f, 1f)
    }

    fun displaySeconds(snapshot: TimerSnapshot, now: Long): Long {
        return if (snapshot.isStopwatch) {
            (elapsedFocusMillis(snapshot, now) / 1000L).coerceAtLeast(0L)
        } else {
            val remaining = remainingMillis(snapshot, now)
            if (snapshot.status == TimerStatus.IDLE) 0L
            else ((remaining + 999L) / 1000L).coerceAtLeast(0L)
        }
    }

    fun isDue(snapshot: TimerSnapshot, now: Long): Boolean =
        snapshot.status == TimerStatus.RUNNING &&
            snapshot.phase != TimerPhase.IDLE &&
            snapshot.phase != TimerPhase.OPEN &&
            snapshot.endsAt in 1..now

    fun isPomodoroDue(snapshot: TimerSnapshot, now: Long): Boolean =
        snapshot.mode == TimerMode.POMODORO && isDue(snapshot, now)

    fun pausedSeconds(snapshot: TimerSnapshot, endedAt: Long): Int {
        val wall = if (snapshot.startedAt <= 0L) 0L else max(0L, endedAt - snapshot.startedAt)
        val active = elapsedFocusMillis(snapshot, endedAt)
        return max(0L, (wall - active) / 1000L).toInt()
    }

    fun nextPhase(snapshot: TimerSnapshot, completedCount: Int): TimerPhase {
        return if (completedCount > 0 && completedCount % snapshot.longBreakEvery.coerceAtLeast(1) == 0) {
            TimerPhase.LONG_BREAK
        } else {
            TimerPhase.SHORT_BREAK
        }
    }

    fun breakMinutes(snapshot: TimerSnapshot, phase: TimerPhase): Int = when (phase) {
        TimerPhase.LONG_BREAK -> snapshot.longBreakMin
        else -> snapshot.shortBreakMin
    }

    fun progressForDisplay(snapshot: TimerSnapshot, now: Long): Float =
        min(1f, progress(snapshot, now))
}
