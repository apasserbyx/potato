package com.potato.focus.habits

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.potato.focus.PotatoApp
import com.potato.focus.data.HabitReminderSlot
import java.util.Calendar

object HabitReminderScheduler {
    const val ACTION_HABIT_REMINDER = "com.potato.focus.HABIT_REMINDER"
    const val EXTRA_SLOT = "habit_reminder_slot"

    private val slots = listOf(
        HabitReminderSlot.MORNING,
        HabitReminderSlot.AFTERNOON,
        HabitReminderSlot.EVENING
    )

    fun scheduleAll(context: Context) {
        val app = context.applicationContext as? PotatoApp ?: return
        val habits = app.habits.activeHabits()
        val manager = context.getSystemService(AlarmManager::class.java)

        slots.forEach { slot ->
            val needed = app.settings.habitSlotEnabled(slot) &&
                habits.any { it.reminderEnabled && it.reminderSlot == slot }
            val pending = pendingIntent(context, slot)
            if (!needed) {
                manager.cancel(pending)
                return@forEach
            }

            val minutes = app.settings.habitSlotMinutes(slot)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val triggerAt = calendar.timeInMillis
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !manager.canScheduleExactAlarms()
                ) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else {
                    manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } catch (_: Exception) {
                // The next app launch will reschedule.
            }
        }
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        slots.forEach { manager.cancel(pendingIntent(context, it)) }
    }

    private fun pendingIntent(context: Context, slot: HabitReminderSlot): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_HABIT_REMINDER
            putExtra(EXTRA_SLOT, slot.name)
        }
        return PendingIntent.getBroadcast(
            context,
            3100 + slot.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
