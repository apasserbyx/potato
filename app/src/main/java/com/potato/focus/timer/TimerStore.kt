package com.potato.focus.timer

import android.content.Context
import com.potato.focus.data.SettingsStore

class TimerStore(context: Context) {
    private val prefs = context.getSharedPreferences("potato_timer", Context.MODE_PRIVATE)
    private val settings = SettingsStore(context)

    fun read(): TimerSnapshot {
        val defaultMode = runCatching {
            TimerMode.valueOf(settings.defaultMode)
        }.getOrDefault(TimerMode.POMODORO)
        return TimerSnapshot(
            mode = enumPref(KEY_MODE, defaultMode),
            phase = enumPref(KEY_PHASE, TimerPhase.IDLE),
            status = enumPref(KEY_STATUS, TimerStatus.IDLE),
            startedAt = prefs.getLong(KEY_STARTED_AT, 0L),
            endsAt = prefs.getLong(KEY_ENDS_AT, 0L),
            segmentStartedAt = prefs.getLong(KEY_SEGMENT_STARTED_AT, 0L),
            accumulatedFocusMs = prefs.getLong(KEY_ACCUMULATED_MS, 0L),
            pausedAt = prefs.getLong(KEY_PAUSED_AT, 0L),
            remainingMs = prefs.getLong(KEY_REMAINING_MS, 0L),
            plannedSeconds = prefs.getInt(KEY_PLANNED_SECONDS, 0),
            sessionId = prefs.getLong(KEY_SESSION_ID, 0L),
            presetId = prefs.getLong(KEY_PRESET_ID, 0L),
            presetName = prefs.getString(KEY_PRESET_NAME, "经典 25/5") ?: "经典 25/5",
            taskId = prefs.getLong(KEY_TASK_ID, -1L).takeIf { it > 0L },
            habitId = prefs.getLong(KEY_HABIT_ID, -1L).takeIf { it > 0L },
            taskTitle = prefs.getString(KEY_TASK_TITLE, "") ?: "",
            tag = prefs.getString(KEY_TAG, "") ?: "",
            completedFocusCount = prefs.getInt(KEY_COMPLETED_COUNT, settings.completedFocusCount),
            focusMin = prefs.getInt(KEY_FOCUS_MIN, 25),
            shortBreakMin = prefs.getInt(KEY_SHORT_BREAK_MIN, 5),
            longBreakMin = prefs.getInt(KEY_LONG_BREAK_MIN, 15),
            longBreakEvery = prefs.getInt(KEY_LONG_EVERY, 4),
            autoStartBreak = prefs.getBoolean(KEY_AUTO_BREAK, true),
            autoStartFocus = prefs.getBoolean(KEY_AUTO_FOCUS, false),
            sound = prefs.getBoolean(KEY_SOUND, true)
        )
    }

    fun write(snapshot: TimerSnapshot) {
        prefs.edit()
            .putString(KEY_MODE, snapshot.mode.name)
            .putString(KEY_PHASE, snapshot.phase.name)
            .putString(KEY_STATUS, snapshot.status.name)
            .putLong(KEY_STARTED_AT, snapshot.startedAt)
            .putLong(KEY_ENDS_AT, snapshot.endsAt)
            .putLong(KEY_SEGMENT_STARTED_AT, snapshot.segmentStartedAt)
            .putLong(KEY_ACCUMULATED_MS, snapshot.accumulatedFocusMs)
            .putLong(KEY_PAUSED_AT, snapshot.pausedAt)
            .putLong(KEY_REMAINING_MS, snapshot.remainingMs)
            .putInt(KEY_PLANNED_SECONDS, snapshot.plannedSeconds)
            .putLong(KEY_SESSION_ID, snapshot.sessionId)
            .putLong(KEY_PRESET_ID, snapshot.presetId)
            .putString(KEY_PRESET_NAME, snapshot.presetName)
            .putLong(KEY_TASK_ID, snapshot.taskId ?: -1L)
            .putLong(KEY_HABIT_ID, snapshot.habitId ?: -1L)
            .putString(KEY_TASK_TITLE, snapshot.taskTitle)
            .putString(KEY_TAG, snapshot.tag)
            .putInt(KEY_COMPLETED_COUNT, snapshot.completedFocusCount)
            .putInt(KEY_FOCUS_MIN, snapshot.focusMin)
            .putInt(KEY_SHORT_BREAK_MIN, snapshot.shortBreakMin)
            .putInt(KEY_LONG_BREAK_MIN, snapshot.longBreakMin)
            .putInt(KEY_LONG_EVERY, snapshot.longBreakEvery)
            .putBoolean(KEY_AUTO_BREAK, snapshot.autoStartBreak)
            .putBoolean(KEY_AUTO_FOCUS, snapshot.autoStartFocus)
            .putBoolean(KEY_SOUND, snapshot.sound)
            .commit()
        if (snapshot.status == TimerStatus.IDLE) {
            settings.completedFocusCount = snapshot.completedFocusCount
        }
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_PHASE = "phase"
        private const val KEY_STATUS = "status"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_ENDS_AT = "ends_at"
        private const val KEY_SEGMENT_STARTED_AT = "segment_started_at"
        private const val KEY_ACCUMULATED_MS = "accumulated_ms"
        private const val KEY_PAUSED_AT = "paused_at"
        private const val KEY_REMAINING_MS = "remaining_ms"
        private const val KEY_PLANNED_SECONDS = "planned_seconds"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_PRESET_ID = "preset_id"
        private const val KEY_PRESET_NAME = "preset_name"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_HABIT_ID = "habit_id"
        private const val KEY_TASK_TITLE = "task_title"
        private const val KEY_TAG = "tag"
        private const val KEY_COMPLETED_COUNT = "completed_count"
        private const val KEY_FOCUS_MIN = "focus_min"
        private const val KEY_SHORT_BREAK_MIN = "short_break_min"
        private const val KEY_LONG_BREAK_MIN = "long_break_min"
        private const val KEY_LONG_EVERY = "long_every"
        private const val KEY_AUTO_BREAK = "auto_break"
        private const val KEY_AUTO_FOCUS = "auto_focus"
        private const val KEY_SOUND = "sound"
    }
}
