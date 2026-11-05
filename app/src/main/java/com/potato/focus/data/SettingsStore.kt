package com.potato.focus.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("potato_settings", Context.MODE_PRIVATE)

    var dailyGoalMinutes: Int
        get() = prefs.getInt(KEY_DAILY_GOAL, 120)
        set(value) = prefs.edit().putInt(KEY_DAILY_GOAL, value.coerceIn(15, 720)).apply()

    var selectedPresetId: Long
        get() = prefs.getLong(KEY_PRESET_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_PRESET_ID, value).apply()

    var defaultMode: String
        get() = prefs.getString(KEY_DEFAULT_MODE, "POMODORO") ?: "POMODORO"
        set(value) = prefs.edit().putString(KEY_DEFAULT_MODE, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var vibration: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    var selectedTaskId: Long
        get() = prefs.getLong(KEY_TASK_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_TASK_ID, value).apply()

    var selectedTaskTitle: String
        get() = prefs.getString(KEY_TASK_TITLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TASK_TITLE, value).apply()

    var selectedTaskTag: String
        get() = prefs.getString(KEY_TASK_TAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TASK_TAG, value).apply()

    var completedFocusCount: Int
        get() = prefs.getInt(KEY_COMPLETED_FOCUS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_COMPLETED_FOCUS_COUNT, value).apply()

    var notificationPrompted: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPTED, value).apply()

    var openHourReminder: Boolean
        get() = prefs.getBoolean(KEY_OPEN_HOUR_REMINDER, false)
        set(value) = prefs.edit().putBoolean(KEY_OPEN_HOUR_REMINDER, value).apply()

    var lastOpenReminderAt: Long
        get() = prefs.getLong(KEY_LAST_OPEN_REMINDER, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_OPEN_REMINDER, value).apply()

    var habitSoundKey: String
        get() = prefs.getString(KEY_HABIT_SOUND, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_HABIT_SOUND, value).apply()

    var habitAlertLevel: String
        get() = prefs.getString(KEY_HABIT_ALERT_LEVEL, HabitAlertLevel.STANDARD.name)
            ?: HabitAlertLevel.STANDARD.name
        set(value) = prefs.edit().putString(KEY_HABIT_ALERT_LEVEL, value).apply()

    var habitFollowUpEnabled: Boolean
        get() = prefs.getBoolean(KEY_HABIT_FOLLOW_UP, false)
        set(value) = prefs.edit().putBoolean(KEY_HABIT_FOLLOW_UP, value).apply()

    fun habitSlotMinutes(slot: HabitReminderSlot): Int = when (slot) {
        HabitReminderSlot.MORNING -> prefs.getInt(KEY_HABIT_MORNING, 8 * 60 + 30)
        HabitReminderSlot.AFTERNOON -> prefs.getInt(KEY_HABIT_AFTERNOON, 14 * 60)
        HabitReminderSlot.EVENING -> prefs.getInt(KEY_HABIT_EVENING, 20 * 60 + 30)
        HabitReminderSlot.NONE -> 0
    }

    fun setHabitSlotMinutes(slot: HabitReminderSlot, minutes: Int) {
        val key = when (slot) {
            HabitReminderSlot.MORNING -> KEY_HABIT_MORNING
            HabitReminderSlot.AFTERNOON -> KEY_HABIT_AFTERNOON
            HabitReminderSlot.EVENING -> KEY_HABIT_EVENING
            HabitReminderSlot.NONE -> return
        }
        prefs.edit().putInt(key, minutes.coerceIn(0, 23 * 60 + 59)).apply()
    }

    fun habitSlotEnabled(slot: HabitReminderSlot): Boolean = when (slot) {
        HabitReminderSlot.MORNING -> prefs.getBoolean(KEY_HABIT_MORNING_ENABLED, true)
        HabitReminderSlot.AFTERNOON -> prefs.getBoolean(KEY_HABIT_AFTERNOON_ENABLED, true)
        HabitReminderSlot.EVENING -> prefs.getBoolean(KEY_HABIT_EVENING_ENABLED, true)
        HabitReminderSlot.NONE -> false
    }

    fun setHabitSlotEnabled(slot: HabitReminderSlot, enabled: Boolean) {
        val key = when (slot) {
            HabitReminderSlot.MORNING -> KEY_HABIT_MORNING_ENABLED
            HabitReminderSlot.AFTERNOON -> KEY_HABIT_AFTERNOON_ENABLED
            HabitReminderSlot.EVENING -> KEY_HABIT_EVENING_ENABLED
            HabitReminderSlot.NONE -> return
        }
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun selectTask(task: TaskItem?) {
        prefs.edit()
            .putLong(KEY_TASK_ID, task?.id ?: -1L)
            .putString(KEY_TASK_TITLE, task?.title ?: "")
            .putString(KEY_TASK_TAG, task?.tag ?: "")
            .apply()
    }

    companion object {
        private const val KEY_DAILY_GOAL = "daily_goal_minutes"
        private const val KEY_PRESET_ID = "selected_preset_id"
        private const val KEY_DEFAULT_MODE = "default_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_TASK_ID = "selected_task_id"
        private const val KEY_TASK_TITLE = "selected_task_title"
        private const val KEY_TASK_TAG = "selected_task_tag"
        private const val KEY_COMPLETED_FOCUS_COUNT = "completed_focus_count"
        private const val KEY_NOTIFICATION_PROMPTED = "notification_prompted"
        private const val KEY_OPEN_HOUR_REMINDER = "open_hour_reminder"
        private const val KEY_LAST_OPEN_REMINDER = "last_open_reminder"
        private const val KEY_HABIT_MORNING = "habit_morning_minutes"
        private const val KEY_HABIT_AFTERNOON = "habit_afternoon_minutes"
        private const val KEY_HABIT_EVENING = "habit_evening_minutes"
        private const val KEY_HABIT_MORNING_ENABLED = "habit_morning_enabled"
        private const val KEY_HABIT_AFTERNOON_ENABLED = "habit_afternoon_enabled"
        private const val KEY_HABIT_EVENING_ENABLED = "habit_evening_enabled"
        private const val KEY_HABIT_SOUND = "habit_sound_key"
        private const val KEY_HABIT_ALERT_LEVEL = "habit_alert_level"
        private const val KEY_HABIT_FOLLOW_UP = "habit_follow_up_enabled"
    }
}
