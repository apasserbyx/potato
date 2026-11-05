package com.potato.focus.habits

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.potato.focus.PotatoApp
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitReminderMode
import com.potato.focus.data.HabitReminderSlot
import java.util.Calendar

object HabitReminderScheduler {
    const val ACTION_HABIT_REMINDER = "com.potato.focus.HABIT_REMINDER"
    const val ACTION_HABIT_INDEPENDENT = "com.potato.focus.HABIT_INDEPENDENT"
    const val EXTRA_SLOT = "habit_reminder_slot"
    const val EXTRA_HABIT_ID = "habit_reminder_habit_id"

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
                habits.any {
                    it.reminderEnabled &&
                        it.reminderMode == HabitReminderMode.SLOT &&
                        it.reminderSlot == slot
                }
            val pending = slotIntent(context, slot)
            if (!needed) {
                manager.cancel(pending)
                return@forEach
            }
            val minutes = app.settings.habitSlotMinutes(slot)
            scheduleAt(context, pending, nextTrigger(minutes))
        }

        app.habits.allHabits().forEach { cancelIndependent(context, it.id) }
        habits.filter {
            it.reminderEnabled &&
                it.reminderMode == HabitReminderMode.INDEPENDENT &&
                it.reminderMinutes in 0..(24 * 60 - 1)
        }.forEach { habit ->
            scheduleAt(
                context,
                independentIntent(context, habit),
                nextTrigger(habit.reminderMinutes)
            )
        }
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        slots.forEach { manager.cancel(slotIntent(context, it)) }
        (context.applicationContext as? PotatoApp)?.habits?.allHabits()?.forEach {
            manager.cancel(independentIntent(context, it))
        }
    }

    private fun nextTrigger(minutesOfDay: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun scheduleAt(context: Context, pendingIntent: PendingIntent, triggerAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !manager.canScheduleExactAlarms()
            ) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: Exception) {
            // The next app launch will reschedule.
        }
    }

    private fun slotIntent(context: Context, slot: HabitReminderSlot): PendingIntent {
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

    private fun independentIntent(context: Context, habit: Habit): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_HABIT_INDEPENDENT
            data = Uri.parse("potato://habit-reminder/${habit.id}")
            putExtra(EXTRA_HABIT_ID, habit.id)
        }
        return PendingIntent.getBroadcast(
            context,
            4200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelIndependent(context: Context, habitId: Long) {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_HABIT_INDEPENDENT
            data = Uri.parse("potato://habit-reminder/$habitId")
        }
        val pending = PendingIntent.getBroadcast(
            context,
            4200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }
}
