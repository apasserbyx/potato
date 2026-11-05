package com.potato.focus.habits

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.potato.focus.PotatoApp
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.HabitStats
import com.potato.focus.data.isScheduledOn
import com.potato.focus.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != HabitReminderScheduler.ACTION_HABIT_REMINDER) return
        val slot = runCatching {
            HabitReminderSlot.valueOf(
                intent.getStringExtra(HabitReminderScheduler.EXTRA_SLOT) ?: return
            )
        }.getOrNull() ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? PotatoApp ?: return@launch
                val today = TimeFormat.localDate(System.currentTimeMillis())
                val logs = app.habitLogs.logsForDate(today)
                val incomplete = app.habits.activeHabits()
                    .filter { it.reminderEnabled && it.reminderSlot == slot && it.isScheduledOn(today) }
                    .filter {
                        val log = logs[it.id]
                        !HabitStats.isCompleted(log) && !HabitStats.isSkipped(log)
                    }
                if (incomplete.isNotEmpty()) {
                    HabitNotifications.postSummary(context, slot, incomplete)
                }
                HabitReminderScheduler.scheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
