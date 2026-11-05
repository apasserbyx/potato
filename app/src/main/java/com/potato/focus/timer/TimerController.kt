package com.potato.focus.timer

import android.content.Context
import com.potato.focus.PotatoApp
import com.potato.focus.data.Habit
import com.potato.focus.data.Preset
import com.potato.focus.data.SessionMode
import com.potato.focus.data.SessionStatus
import com.potato.focus.data.TaskItem
import com.potato.focus.habits.HabitSounds
import com.potato.focus.util.TimeFormat

data class StopResult(
    val mode: TimerMode,
    val phase: TimerPhase,
    val status: SessionStatus,
    val focusedSeconds: Int,
    val taskTitle: String
)

class TimerController(context: Context) {
    private val app = context.applicationContext as PotatoApp
    private val store = TimerStore(context)
    private val alarm = TimerAlarm(context)
    private val notifications = TimerNotifications
    private val appContext = context.applicationContext

    @Synchronized
    fun startPomodoro(preset: Preset, task: TaskItem?): Boolean {
        val current = store.read()
        if (current.isActive) return false
        val now = System.currentTimeMillis()
        val plannedSeconds = preset.focusMin.coerceIn(1, 180) * 60
        val sessionId = app.sessions.insertRunning(
            mode = SessionMode.FOCUS,
            plannedSeconds = plannedSeconds,
            presetId = preset.id,
            task = task,
            startedAt = now
        )
        val snapshot = TimerSnapshot(
            mode = TimerMode.POMODORO,
            phase = TimerPhase.FOCUS,
            status = TimerStatus.RUNNING,
            startedAt = now,
            endsAt = now + plannedSeconds * 1000L,
            segmentStartedAt = now,
            plannedSeconds = plannedSeconds,
            sessionId = sessionId,
            presetId = preset.id,
            presetName = preset.name,
            taskId = task?.id,
            taskTitle = task?.title.orEmpty(),
            tag = task?.tag.orEmpty(),
            completedFocusCount = app.settings.completedFocusCount,
            focusMin = preset.focusMin,
            shortBreakMin = preset.shortBreakMin,
            longBreakMin = preset.longBreakMin,
            longBreakEvery = preset.longBreakEvery,
            autoStartBreak = preset.autoStartBreak,
            autoStartFocus = preset.autoStartFocus,
            sound = preset.sound
        )
        store.write(snapshot)
        alarm.schedule(snapshot.endsAt)
        TimerNotifications.cancelEndNotifications(appContext)
        TimerService.start(appContext)
        return true
    }

    @Synchronized
    fun startHabit(habit: Habit, remainingSeconds: Int): Boolean {
        val current = store.read()
        if (current.isActive) return false
        val now = System.currentTimeMillis()
        val plannedSeconds = remainingSeconds.coerceIn(60, 180 * 60)
        val sessionId = app.sessions.insertRunningRaw(
            mode = SessionMode.HABIT,
            plannedSeconds = plannedSeconds,
            presetId = current.presetId.takeIf { it > 0L },
            taskId = null,
            habitId = habit.id,
            taskTitle = habit.name,
            tag = "习惯",
            startedAt = now
        )
        val snapshot = TimerSnapshot(
            mode = TimerMode.HABIT,
            phase = TimerPhase.HABIT,
            status = TimerStatus.RUNNING,
            startedAt = now,
            endsAt = now + plannedSeconds * 1000L,
            segmentStartedAt = now,
            plannedSeconds = plannedSeconds,
            sessionId = sessionId,
            presetId = current.presetId,
            presetName = current.presetName,
            habitId = habit.id,
            taskTitle = habit.name,
            tag = "习惯",
            completedFocusCount = app.settings.completedFocusCount,
            focusMin = current.focusMin,
            shortBreakMin = current.shortBreakMin,
            longBreakMin = current.longBreakMin,
            longBreakEvery = current.longBreakEvery,
            autoStartBreak = false,
            autoStartFocus = false,
            sound = current.sound
        )
        store.write(snapshot)
        alarm.schedule(snapshot.endsAt)
        TimerNotifications.cancelEndNotifications(appContext)
        TimerService.start(appContext)
        return true
    }

    @Synchronized
    fun startStopwatch(task: TaskItem?): Boolean {
        val current = store.read()
        if (current.isActive) return false
        val now = System.currentTimeMillis()
        val sessionId = app.sessions.insertRunning(
            mode = SessionMode.OPEN,
            plannedSeconds = 0,
            presetId = null,
            task = task,
            startedAt = now
        )
        val previous = current
        val snapshot = TimerSnapshot(
            mode = TimerMode.STOPWATCH,
            phase = TimerPhase.OPEN,
            status = TimerStatus.RUNNING,
            startedAt = now,
            segmentStartedAt = now,
            sessionId = sessionId,
            presetId = previous.presetId,
            presetName = previous.presetName,
            taskId = task?.id,
            taskTitle = task?.title.orEmpty(),
            tag = task?.tag.orEmpty(),
            completedFocusCount = app.settings.completedFocusCount,
            focusMin = previous.focusMin,
            shortBreakMin = previous.shortBreakMin,
            longBreakMin = previous.longBreakMin,
            longBreakEvery = previous.longBreakEvery,
            autoStartBreak = previous.autoStartBreak,
            autoStartFocus = previous.autoStartFocus,
            sound = previous.sound
        )
        store.write(snapshot)
        alarm.cancel()
        TimerNotifications.cancelEndNotifications(appContext)
        TimerService.start(appContext)
        return true
    }

    @Synchronized
    fun pause() {
        val snapshot = store.read()
        if (snapshot.status != TimerStatus.RUNNING) return
        val now = System.currentTimeMillis()
        val active = TimerMath.elapsedFocusMillis(snapshot, now)
        val remaining = TimerMath.remainingMillis(snapshot, now)
        val paused = snapshot.copy(
            status = TimerStatus.PAUSED,
            accumulatedFocusMs = active,
            segmentStartedAt = 0L,
            pausedAt = now,
            remainingMs = remaining,
            endsAt = 0L
        )
        store.write(paused)
        if (snapshot.sessionId > 0L) {
            app.sessions.updateProgress(
                snapshot.sessionId,
                (active / 1000L).toInt(),
                TimerMath.pausedSeconds(snapshot, now)
            )
        }
        alarm.cancel()
        TimerNotifications.update(appContext, paused)
    }

    @Synchronized
    fun resume() {
        val snapshot = store.read()
        if (snapshot.status != TimerStatus.PAUSED) return
        val now = System.currentTimeMillis()
        val running = snapshot.copy(
            status = TimerStatus.RUNNING,
            segmentStartedAt = now,
            endsAt = if (snapshot.isStopwatch) 0L else now + snapshot.remainingMs,
            pausedAt = 0L
        )
        store.write(running)
        if (!snapshot.isStopwatch) alarm.schedule(running.endsAt)
        TimerNotifications.update(appContext, running)
        TimerService.start(appContext)
    }

    @Synchronized
    fun stopCurrent(): StopResult? {
        val snapshot = store.read()
        if (!snapshot.isActive) return null
        val now = System.currentTimeMillis()
        val activeMs = TimerMath.elapsedFocusMillis(snapshot, now)
        val focusedSeconds = (activeMs / 1000L).toInt()
        val pausedSeconds = TimerMath.pausedSeconds(snapshot, now)
        val remaining = when {
            snapshot.isStopwatch -> 0L
            snapshot.status == TimerStatus.RUNNING -> (snapshot.endsAt - now).coerceAtLeast(0L)
            else -> snapshot.remainingMs
        }
        val finalStatus = when {
            snapshot.isStopwatch -> if (focusedSeconds >= 60) SessionStatus.COMPLETED else SessionStatus.DISCARDED
            remaining <= 1500L -> SessionStatus.COMPLETED
            focusedSeconds < 60 -> SessionStatus.DISCARDED
            else -> SessionStatus.PARTIAL
        }
        if (snapshot.sessionId > 0L) {
            app.sessions.finish(
                id = snapshot.sessionId,
                status = finalStatus,
                endedAt = now,
                focusedSeconds = focusedSeconds,
                pausedSeconds = pausedSeconds
            )
        }

        if (snapshot.isHabit && finalStatus != SessionStatus.DISCARDED) {
            val habitSeconds = if (finalStatus == SessionStatus.COMPLETED) {
                snapshot.plannedSeconds
            } else {
                focusedSeconds
            }
            recordHabitProgress(snapshot, habitSeconds)
        }

        var count = snapshot.completedFocusCount
        if (snapshot.phase == TimerPhase.FOCUS && finalStatus == SessionStatus.COMPLETED) {
            count += 1
            app.settings.completedFocusCount = count
        }
        var idle = snapshot.asIdle(count)
        if (snapshot.isHabit) {
            idle = idle.copy(habitId = null, taskId = null, taskTitle = "", tag = "")
        }
        store.write(idle)
        alarm.cancel()
        TimerNotifications.cancelOngoing(appContext)
        TimerService.stop(appContext)
        return StopResult(
            mode = snapshot.mode,
            phase = snapshot.phase,
            status = finalStatus,
            focusedSeconds = focusedSeconds,
            taskTitle = snapshot.taskTitle
        )
    }

    @Synchronized
    fun skipBreak() {
        val snapshot = store.read()
        if (!snapshot.isBreak || !snapshot.isActive) return
        finishBreak(snapshot, partial = true)
        if (snapshot.autoStartFocus) {
            startFocusInternal(snapshot, System.currentTimeMillis())
        } else {
            goIdle(snapshot)
        }
    }

    /**
     * Called by the foreground service and the alarm receiver. Idempotent.
     */
    @Synchronized
    fun completeDue(now: Long = System.currentTimeMillis()): Boolean {
        val snapshot = store.read()
        if (snapshot.isHabit) {
            if (!TimerMath.isDue(snapshot, now)) return false
            val plannedSeconds = snapshot.plannedSeconds.coerceAtLeast(1)
            val wallMs = (snapshot.endsAt - snapshot.startedAt).coerceAtLeast(0L)
            val pausedSeconds = ((wallMs - plannedSeconds * 1000L) / 1000L).coerceAtLeast(0L).toInt()
            if (snapshot.sessionId > 0L) {
                app.sessions.finish(
                    id = snapshot.sessionId,
                    status = SessionStatus.COMPLETED,
                    endedAt = snapshot.endsAt,
                    focusedSeconds = plannedSeconds,
                    pausedSeconds = pausedSeconds
                )
            }
            recordHabitProgress(snapshot, plannedSeconds)
            TimerNotifications.postHabitComplete(appContext, snapshot.taskTitle)
            val idle = snapshot.asIdle(snapshot.completedFocusCount)
                .copy(habitId = null, taskId = null, taskTitle = "", tag = "")
            store.write(idle)
            alarm.cancel()
            TimerNotifications.cancelOngoing(appContext)
            TimerService.stop(appContext)
            return true
        }
        if (!TimerMath.isPomodoroDue(snapshot, now)) return false
        val plannedSeconds = snapshot.plannedSeconds.coerceAtLeast(1)
        val wallMs = (snapshot.endsAt - snapshot.startedAt).coerceAtLeast(0L)
        val pausedSeconds = ((wallMs - plannedSeconds * 1000L) / 1000L).coerceAtLeast(0L).toInt()
        if (snapshot.sessionId > 0L) {
            app.sessions.finish(
                id = snapshot.sessionId,
                status = SessionStatus.COMPLETED,
                endedAt = snapshot.endsAt,
                focusedSeconds = plannedSeconds,
                pausedSeconds = pausedSeconds
            )
        }
        var count = snapshot.completedFocusCount
        val completed = snapshot.copy(
            accumulatedFocusMs = plannedSeconds * 1000L,
            pausedAt = 0L,
            remainingMs = 0L
        )

        when (snapshot.phase) {
            TimerPhase.FOCUS -> {
                count += 1
                app.settings.completedFocusCount = count
                val withCount = completed.copy(completedFocusCount = count)
                val nextPhase = TimerMath.nextPhase(withCount, count)
                HabitSounds.playReminder(appContext, app.settings.timerFocusSoundKey)
                TimerNotifications.postFocusComplete(appContext, snapshot, nextPhase)
                if (snapshot.autoStartBreak) {
                    startBreak(withCount, nextPhase, now)
                } else {
                    goIdle(withCount)
                }
            }
            TimerPhase.SHORT_BREAK, TimerPhase.LONG_BREAK -> {
                HabitSounds.playReminder(appContext, app.settings.timerBreakSoundKey)
                TimerNotifications.postBreakComplete(appContext, snapshot)
                if (snapshot.autoStartFocus) {
                    startFocusInternal(completed.copy(completedFocusCount = count), now)
                } else {
                    goIdle(completed.copy(completedFocusCount = count))
                }
            }
            else -> goIdle(completed)
        }
        return true
    }

    @Synchronized
    fun startNextFocus() {
        val snapshot = store.read()
        if (snapshot.isActive) return
        startFocusInternal(snapshot, System.currentTimeMillis())
    }

    fun isActive(): Boolean = store.read().isActive

    private fun startBreak(snapshot: TimerSnapshot, phase: TimerPhase, now: Long) {
        val seconds = TimerMath.breakMinutes(snapshot, phase).coerceIn(1, 120) * 60
        val mode = if (phase == TimerPhase.LONG_BREAK) SessionMode.LONG_BREAK else SessionMode.SHORT_BREAK
        val sessionId = app.sessions.insertRunningRaw(
            mode = mode,
            plannedSeconds = seconds,
            presetId = snapshot.presetId.takeIf { it > 0L },
            taskId = snapshot.taskId,
            taskTitle = snapshot.taskTitle,
            tag = snapshot.tag,
            startedAt = now
        )
        val breakSnapshot = snapshot.copy(
            mode = TimerMode.POMODORO,
            phase = phase,
            status = TimerStatus.RUNNING,
            startedAt = now,
            endsAt = now + seconds * 1000L,
            segmentStartedAt = now,
            accumulatedFocusMs = 0L,
            pausedAt = 0L,
            remainingMs = 0L,
            plannedSeconds = seconds,
            sessionId = sessionId
        )
        store.write(breakSnapshot)
        alarm.schedule(breakSnapshot.endsAt)
        TimerService.start(appContext)
    }

    private fun startFocusInternal(snapshot: TimerSnapshot, now: Long) {
        val seconds = snapshot.focusMin.coerceIn(1, 180) * 60
        val sessionId = app.sessions.insertRunningRaw(
            mode = SessionMode.FOCUS,
            plannedSeconds = seconds,
            presetId = snapshot.presetId.takeIf { it > 0L },
            taskId = snapshot.taskId,
            taskTitle = snapshot.taskTitle,
            tag = snapshot.tag,
            startedAt = now
        )
        val focusSnapshot = snapshot.copy(
            mode = TimerMode.POMODORO,
            phase = TimerPhase.FOCUS,
            status = TimerStatus.RUNNING,
            startedAt = now,
            endsAt = now + seconds * 1000L,
            segmentStartedAt = now,
            accumulatedFocusMs = 0L,
            pausedAt = 0L,
            remainingMs = 0L,
            plannedSeconds = seconds,
            sessionId = sessionId
        )
        store.write(focusSnapshot)
        alarm.schedule(focusSnapshot.endsAt)
        TimerService.start(appContext)
    }

    private fun finishBreak(snapshot: TimerSnapshot, partial: Boolean) {
        if (snapshot.sessionId <= 0L) return
        val now = System.currentTimeMillis()
        val focusedSeconds = TimerMath.elapsedFocusMillis(snapshot, now).div(1000L).toInt()
        app.sessions.finish(
            id = snapshot.sessionId,
            status = if (partial) SessionStatus.PARTIAL else SessionStatus.COMPLETED,
            endedAt = now,
            focusedSeconds = focusedSeconds,
            pausedSeconds = TimerMath.pausedSeconds(snapshot, now)
        )
    }

    private fun recordHabitProgress(snapshot: TimerSnapshot, seconds: Int) {
        val habitId = snapshot.habitId ?: return
        if (seconds <= 0) return
        val habit = app.habits.byId(habitId) ?: return
        val date = TimeFormat.localDate(snapshot.startedAt)
        app.habitLogs.addTimedProgress(
            habitId = habit.id,
            date = date,
            seconds = seconds,
            targetSeconds = habit.targetSeconds
        )
    }

    private fun goIdle(snapshot: TimerSnapshot) {
        store.write(snapshot.asIdle(snapshot.completedFocusCount))
        alarm.cancel()
        TimerNotifications.cancelOngoing(appContext)
        TimerService.stop(appContext)
    }

    private fun TimerSnapshot.asIdle(count: Int): TimerSnapshot = copy(
        phase = TimerPhase.IDLE,
        status = TimerStatus.IDLE,
        startedAt = 0L,
        endsAt = 0L,
        segmentStartedAt = 0L,
        accumulatedFocusMs = 0L,
        pausedAt = 0L,
        remainingMs = 0L,
        plannedSeconds = 0,
        sessionId = 0L,
        completedFocusCount = count
    )
}
