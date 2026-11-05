package com.potato.focus

import android.app.Application
import com.potato.focus.data.HabitDao
import com.potato.focus.data.HabitLogDao
import com.potato.focus.data.PotatoDatabase
import com.potato.focus.data.PresetDao
import com.potato.focus.data.SessionDao
import com.potato.focus.data.SettingsStore
import com.potato.focus.data.TaskDao
import com.potato.focus.habits.HabitNotifications
import com.potato.focus.habits.HabitReminderScheduler
import com.potato.focus.timer.TimerNotifications

class PotatoApp : Application() {
    lateinit var database: PotatoDatabase
        private set
    lateinit var sessions: SessionDao
        private set
    lateinit var tasks: TaskDao
        private set
    lateinit var presets: PresetDao
        private set
    lateinit var habits: HabitDao
        private set
    lateinit var habitLogs: HabitLogDao
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        database = PotatoDatabase(this)
        sessions = SessionDao(database)
        tasks = TaskDao(database)
        presets = PresetDao(database)
        habits = HabitDao(database)
        habitLogs = HabitLogDao(database)
        settings = SettingsStore(this)
        TimerNotifications.createChannels(this)
        HabitNotifications.createChannel(this)
        HabitReminderScheduler.scheduleAll(this)
    }
}
