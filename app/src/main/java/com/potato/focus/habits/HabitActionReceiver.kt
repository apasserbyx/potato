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

class HabitActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? PotatoApp ?: return@launch
                val today = TimeFormat.localDate(System.currentTimeMillis())
                val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
                val slot = runCatching {
                    HabitReminderSlot.valueOf(intent.getStringExtra(EXTRA_SLOT) ?: "")
                }.getOrNull()
                when (action) {
                    ACTION_SNOOZE_HABIT -> {
                        if (habitId > 0) {
                            HabitSnoozeScheduler.scheduleHabit(context, habitId)
                            HabitNotifications.cancelHabit(context, habitId)
                        }
                    }
                    ACTION_SNOOZE_SLOT -> {
                        if (slot != null) {
                            HabitSnoozeScheduler.scheduleSlot(context, slot)
                            HabitNotifications.cancelSummary(context, slot)
                        }
                    }
                    ACTION_SKIP_HABIT -> {
                        if (habitId > 0) {
                            app.habitLogs.markSkipped(habitId, today)
                            HabitNotifications.cancelHabit(context, habitId)
                            HabitEvents.notifyChanged()
                        }
                    }
                    ACTION_COMPLETE_HABIT -> {
                        if (habitId > 0) {
                            app.habitLogs.markCompleted(habitId, today)
                            HabitNotifications.cancelHabit(context, habitId)
                            HabitEvents.notifyChanged()
                        }
                    }
                    ACTION_INCREMENT_HABIT -> {
                        val habit = habitId.takeIf { it > 0 }?.let { app.habits.byId(it) }
                        if (habit != null) {
                            app.habitLogs.adjustCount(
                                habit.id,
                                today,
                                1,
                                habit.targetCount
                            )
                            HabitNotifications.cancelHabit(context, habit.id)
                            HabitEvents.notifyChanged()
                        }
                    }
                    ACTION_FIRE_HABIT -> {
                        if (habitId > 0) {
                            HabitSnoozeStore(context).removeHabit(habitId)
                            val habit = app.habits.byId(habitId)
                            val log = app.habitLogs.get(habitId, today)
                            if (habit != null &&
                                habit.reminderEnabled &&
                                habit.isScheduledOn(today) &&
                                !HabitStats.isCompleted(log) &&
                                !HabitStats.isSkipped(log)
                            ) {
                                HabitNotifications.postHabit(context, habit, log)
                            }
                        }
                    }
                    ACTION_FIRE_SLOT -> {
                        if (slot != null) {
                            HabitSnoozeStore(context).removeSlot(slot)
                            val logs = app.habitLogs.logsForDate(today)
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
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_SLOT = "habit_slot"

        const val ACTION_SNOOZE_HABIT = "com.potato.focus.habits.SNOOZE_HABIT"
        const val ACTION_SNOOZE_SLOT = "com.potato.focus.habits.SNOOZE_SLOT"
        const val ACTION_SKIP_HABIT = "com.potato.focus.habits.SKIP_HABIT"
        const val ACTION_COMPLETE_HABIT = "com.potato.focus.habits.COMPLETE_HABIT"
        const val ACTION_INCREMENT_HABIT = "com.potato.focus.habits.INCREMENT_HABIT"
        const val ACTION_FIRE_HABIT = "com.potato.focus.habits.FIRE_HABIT"
        const val ACTION_FIRE_SLOT = "com.potato.focus.habits.FIRE_SLOT"
    }
}
