package com.potato.focus.habits

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.potato.focus.data.HabitReminderSlot

class HabitSnoozeStore(context: Context) {
    private val prefs = context.getSharedPreferences("habit_snoozes", Context.MODE_PRIVATE)

    fun putHabit(habitId: Long, triggerAt: Long) {
        prefs.edit().putLong("habit_$habitId", triggerAt).apply()
    }

    fun putSlot(slot: HabitReminderSlot, triggerAt: Long) {
        prefs.edit().putLong("slot_${slot.name}", triggerAt).apply()
    }

    fun removeHabit(habitId: Long) {
        prefs.edit().remove("habit_$habitId").apply()
    }

    fun removeSlot(slot: HabitReminderSlot) {
        prefs.edit().remove("slot_${slot.name}").apply()
    }

    fun habitSnoozes(): List<Pair<Long, Long>> =
        prefs.all.entries
            .filter { it.key.startsWith("habit_") && it.value is Long }
            .mapNotNull { entry ->
                val id = entry.key.removePrefix("habit_").toLongOrNull() ?: return@mapNotNull null
                id to (entry.value as Long)
            }

    fun slotSnoozes(): List<Pair<HabitReminderSlot, Long>> =
        prefs.all.entries
            .filter { it.key.startsWith("slot_") && it.value is Long }
            .mapNotNull { entry ->
                val slot = runCatching {
                    HabitReminderSlot.valueOf(entry.key.removePrefix("slot_"))
                }.getOrNull() ?: return@mapNotNull null
                slot to (entry.value as Long)
            }
}

object HabitSnoozeScheduler {
    fun scheduleHabit(context: Context, habitId: Long, delayMinutes: Int = 10) {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        HabitSnoozeStore(context).putHabit(habitId, triggerAt)
        schedule(
            context,
            fireIntent(context, HabitActionReceiver.ACTION_FIRE_HABIT, habitId = habitId),
            triggerAt
        )
    }

    fun scheduleSlot(context: Context, slot: HabitReminderSlot, delayMinutes: Int = 15) {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        HabitSnoozeStore(context).putSlot(slot, triggerAt)
        schedule(
            context,
            fireIntent(context, HabitActionReceiver.ACTION_FIRE_SLOT, slot = slot),
            triggerAt
        )
    }

    fun reschedulePersisted(context: Context) {
        val store = HabitSnoozeStore(context)
        val now = System.currentTimeMillis()
        store.habitSnoozes().forEach { (habitId, at) ->
            schedule(
                context,
                fireIntent(context, HabitActionReceiver.ACTION_FIRE_HABIT, habitId = habitId),
                maxOf(at, now + 1_000L)
            )
        }
        store.slotSnoozes().forEach { (slot, at) ->
            schedule(
                context,
                fireIntent(context, HabitActionReceiver.ACTION_FIRE_SLOT, slot = slot),
                maxOf(at, now + 1_000L)
            )
        }
    }

    private fun fireIntent(
        context: Context,
        action: String,
        habitId: Long? = null,
        slot: HabitReminderSlot? = null
    ): PendingIntent {
        val suffix = habitId?.toString() ?: slot?.name ?: "none"
        val intent = Intent(context, HabitActionReceiver::class.java).apply {
            setAction(action)
            data = Uri.parse("potato://snooze/$action/$suffix")
            habitId?.let { putExtra(HabitActionReceiver.EXTRA_HABIT_ID, it) }
            slot?.let { putExtra(HabitActionReceiver.EXTRA_SLOT, it.name) }
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(context: Context, pendingIntent: PendingIntent, triggerAt: Long) {
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
            // Snooze can be rebuilt from SharedPreferences on next launch.
        }
    }
}
