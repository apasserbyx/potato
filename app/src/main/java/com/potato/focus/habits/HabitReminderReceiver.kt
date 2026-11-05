package com.potato.focus.habits

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.potato.focus.PotatoApp
import com.potato.focus.data.HabitReminderMode
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.HabitStats
import com.potato.focus.data.isScheduledOn
import com.potato.focus.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != HabitReminderScheduler.ACTION_HABIT_REMINDER &&
            action != HabitReminderScheduler.ACTION_HABIT_INDEPENDENT
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? PotatoApp ?: return@launch
                val today = TimeFormat.localDate(System.currentTimeMillis())
                val logs = app.habitLogs.logsForDate(today)

                if (action == HabitReminderScheduler.ACTION_HABIT_INDEPENDENT) {
                    val habitId = intent.getLongExtra(HabitReminderScheduler.EXTRA_HABIT_ID, -1L)
                    val habit = habitId.takeIf { it > 0 }?.let { app.habits.byId(it) }
                    val log = habit?.let { logs[it.id] }
                    if (habit != null &&
                        habit.reminderEnabled &&
                        habit.isScheduledOn(today) &&
                        !HabitStats.isCompleted(log) &&
                        !HabitStats.isSkipped(log)
                    ) {
                        HabitNotifications.postHabit(context, habit, log)
                    }
                } else {
                    val slot = runCatching {
                        HabitReminderSlot.valueOf(
                            intent.getStringExtra(HabitReminderScheduler.EXTRA_SLOT) ?: ""
                        )
                    }.getOrNull()
                    if (slot != null) {
                        val incomplete = app.habits.activeHabits()
                            .filter {
                                it.reminderEnabled &&
                                    it.reminderMode == HabitReminderMode.SLOT &&
                                    it.reminderSlot == slot &&
                                    it.isScheduledOn(today)
                            }
                            .filter {
                                val log = logs[it.id]
                                !HabitStats.isCompleted(log) && !HabitStats.isSkipped(log)
                            }
                        if (incomplete.isNotEmpty()) {
                            HabitNotifications.postSummary(context, slot, incomplete)
                        }
                    }
                }
                HabitReminderScheduler.scheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
