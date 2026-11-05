package com.potato.focus.habits

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.potato.focus.MainActivity
import com.potato.focus.R
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitAlertLevel
import com.potato.focus.data.HabitIconType
import com.potato.focus.data.HabitLog
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.HabitStats
import com.potato.focus.data.SettingsStore

object HabitNotifications {
    private const val ID_SUMMARY_BASE = 3000
    private const val ID_HABIT_BASE = 4000
    private const val ID_TEST = 3999

    fun postSummary(
        context: Context,
        slot: HabitReminderSlot,
        habits: List<Habit>
    ) {
        if (habits.isEmpty()) return
        val settings = SettingsStore(context)
        val level = runCatching {
            HabitAlertLevel.valueOf(settings.habitAlertLevel)
        }.getOrDefault(HabitAlertLevel.STANDARD)
        val channel = HabitNotificationChannels.globalChannel(context)
        val slotLabel = when (slot) {
            HabitReminderSlot.MORNING -> "早上"
            HabitReminderSlot.AFTERNOON -> "下午"
            HabitReminderSlot.EVENING -> "晚上"
            HabitReminderSlot.NONE -> "今天"
        }
        val lines = habits.map { habit ->
            "${habit.name} · ${HabitStats.targetText(habit)}"
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${slotLabel}还有 ${habits.size} 个习惯")
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openHabits(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(priorityFor(level))
            .addAction(0, "稍后 15 分钟", snoozeSlotIntent(context, slot))
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ID_SUMMARY_BASE + slot.ordinal, builder.build())
        }
    }

    fun postHabit(
        context: Context,
        habit: Habit,
        log: HabitLog?
    ) {
        val channel = HabitNotificationChannels.habitChannel(context, habit)
        val settings = SettingsStore(context)
        val level = runCatching {
            HabitAlertLevel.valueOf(settings.habitAlertLevel)
        }.getOrDefault(HabitAlertLevel.STANDARD)
        val text = when (habit.type) {
            com.potato.focus.data.HabitType.TIMED -> {
                val done = log?.focusedSeconds ?: 0
                val remaining = (habit.targetSeconds - done).coerceAtLeast(0) / 60
                if (done > 0) "还差 $remaining 分钟 · ${HabitStats.targetText(habit)}"
                else HabitStats.targetText(habit)
            }
            com.potato.focus.data.HabitType.COUNT -> {
                val count = log?.countValue ?: 0
                "$count / ${habit.targetCount} ${habit.unit.ifBlank { "次" }}"
            }
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(habit.name)
            .setContentText(text)
            .setContentIntent(openHabits(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(priorityFor(level))

        habitLargeIcon(context, habit)?.let { builder.setLargeIcon(it) }

        if (habit.type == com.potato.focus.data.HabitType.COUNT) {
            builder.addAction(0, "＋1", incrementIntent(context, habit.id))
        } else {
            builder.addAction(0, "完成", completeIntent(context, habit.id))
        }
        builder.addAction(0, "稍后 10 分钟", snoozeHabitIntent(context, habit.id))
        builder.addAction(0, "跳过", skipIntent(context, habit.id))

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(habit.id), builder.build())
        }
    }

    fun postTest(context: Context) {
        val channel = HabitNotificationChannels.globalChannel(context)
        val settings = SettingsStore(context)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Potato 习惯提醒测试")
            .setContentText("当前提示音：${HabitSounds.label(settings.habitSoundKey)}")
            .setContentIntent(openHabits(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_TEST, builder.build())
        }
    }

    fun cancelSummary(context: Context, slot: HabitReminderSlot) {
        NotificationManagerCompat.from(context).cancel(ID_SUMMARY_BASE + slot.ordinal)
    }

    fun cancelHabit(context: Context, habitId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(habitId))
    }

    fun notificationId(habitId: Long): Int =
        ID_HABIT_BASE + (habitId % 100000L).toInt()

    private fun priorityFor(level: HabitAlertLevel): Int = when (level) {
        HabitAlertLevel.GENTLE -> NotificationCompat.PRIORITY_LOW
        HabitAlertLevel.STANDARD -> NotificationCompat.PRIORITY_DEFAULT
        HabitAlertLevel.STRONG -> NotificationCompat.PRIORITY_HIGH
    }

    private fun habitLargeIcon(context: Context, habit: Habit): android.graphics.Bitmap? {
        if (habit.iconType != HabitIconType.IMAGE || habit.iconImagePath.isBlank()) return null
        return runCatching { BitmapFactory.decodeFile(habit.iconImagePath) }.getOrNull()
    }

    private fun openHabits(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TAB, 1)
        }
        return PendingIntent.getActivity(
            context,
            3200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(
        context: Context,
        action: String,
        habitId: Long? = null,
        slot: HabitReminderSlot? = null
    ): PendingIntent {
        val suffix = habitId?.toString() ?: slot?.name ?: "none"
        val intent = Intent(context, HabitActionReceiver::class.java).apply {
            setAction(action)
            data = Uri.parse("potato://habit-action/$action/$suffix")
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

    private fun snoozeHabitIntent(context: Context, habitId: Long): PendingIntent =
        actionIntent(context, HabitActionReceiver.ACTION_SNOOZE_HABIT, habitId = habitId)

    private fun snoozeSlotIntent(context: Context, slot: HabitReminderSlot): PendingIntent =
        actionIntent(context, HabitActionReceiver.ACTION_SNOOZE_SLOT, slot = slot)

    private fun skipIntent(context: Context, habitId: Long): PendingIntent =
        actionIntent(context, HabitActionReceiver.ACTION_SKIP_HABIT, habitId = habitId)

    private fun completeIntent(context: Context, habitId: Long): PendingIntent =
        actionIntent(context, HabitActionReceiver.ACTION_COMPLETE_HABIT, habitId = habitId)

    private fun incrementIntent(context: Context, habitId: Long): PendingIntent =
        actionIntent(context, HabitActionReceiver.ACTION_INCREMENT_HABIT, habitId = habitId)
}
