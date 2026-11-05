package com.potato.focus.data

enum class SessionMode {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
    OPEN,
    HABIT
}

enum class SessionStatus {
    RUNNING,
    COMPLETED,
    PARTIAL,
    DISCARDED
}

data class FocusSession(
    val id: Long,
    val mode: SessionMode,
    val status: SessionStatus,
    val startedAt: Long,
    val endedAt: Long?,
    val plannedSeconds: Int,
    val focusedSeconds: Int,
    val pausedSeconds: Int,
    val presetId: Long?,
    val taskId: Long?,
    val habitId: Long?,
    val taskTitle: String,
    val tag: String,
    val mood: Int?,
    val note: String,
    val source: String,
    val editedAt: Long?
) {
    val isFocus: Boolean
        get() = mode == SessionMode.FOCUS || mode == SessionMode.OPEN || mode == SessionMode.HABIT

    val durationMinutes: Int
        get() = (focusedSeconds + 30) / 60
}

data class TaskItem(
    val id: Long,
    val title: String,
    val estimatePomodoros: Int,
    val tag: String,
    val dueDate: String?,
    val completedAt: Long?,
    val createdAt: Long
)

data class Preset(
    val id: Long,
    val name: String,
    val focusMin: Int,
    val shortBreakMin: Int,
    val longBreakMin: Int,
    val longBreakEvery: Int,
    val autoStartBreak: Boolean,
    val autoStartFocus: Boolean,
    val sound: Boolean,
    val isDefault: Boolean,
    val sortOrder: Int
)

enum class HabitType {
    TIMED,
    COUNT
}

enum class HabitReminderSlot {
    MORNING,
    AFTERNOON,
    EVENING,
    NONE
}

enum class HabitReminderMode {
    SLOT,
    INDEPENDENT
}

enum class HabitIconType {
    EMOJI,
    BUILTIN,
    IMAGE
}

enum class HabitAlertLevel {
    GENTLE,
    STANDARD,
    STRONG
}

enum class HabitLogStatus {
    PENDING,
    COMPLETED,
    SKIPPED
}

data class Habit(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val type: HabitType,
    val targetSeconds: Int,
    val targetCount: Int,
    val unit: String,
    val weekdaysMask: Int,
    val reminderSlot: HabitReminderSlot,
    val reminderEnabled: Boolean,
    val reminderMode: HabitReminderMode = HabitReminderMode.SLOT,
    val reminderMinutes: Int = -1,
    val soundKey: String = "",
    val iconType: HabitIconType = HabitIconType.EMOJI,
    val iconImagePath: String = "",
    val note: String,
    val sortOrder: Int,
    val archivedAt: Long?,
    val createdAt: Long
)

data class HabitLog(
    val id: Long,
    val habitId: Long,
    val date: String,
    val status: HabitLogStatus,
    val focusedSeconds: Int,
    val countValue: Int,
    val source: String,
    val completedAt: Long?,
    val note: String
)

fun Habit.isScheduledOn(date: java.time.LocalDate): Boolean {
    val bit = 1 shl (date.dayOfWeek.value - 1)
    return weekdaysMask and bit != 0
}

