package com.potato.focus.habits

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.potato.focus.MainActivity
import com.potato.focus.R
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.HabitStats

object HabitNotifications {
    const val CHANNEL_HABIT = "habit_reminder"
    private const val ID_BASE = 3000

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_HABIT,
            "习惯提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "早上、下午、晚上的习惯汇总提醒"
        }
        manager.createNotificationChannel(channel)
    }

    fun postSummary(
        context: Context,
        slot: HabitReminderSlot,
        habits: List<Habit>
    ) {
        if (habits.isEmpty()) return
        createChannel(context)
        val slotLabel = when (slot) {
            HabitReminderSlot.MORNING -> "早上"
            HabitReminderSlot.AFTERNOON -> "下午"
            HabitReminderSlot.EVENING -> "晚上"
            HabitReminderSlot.NONE -> "今天"
        }
        val lines = habits.map { habit ->
            val target = HabitStats.targetText(habit)
            "${habit.name} · $target"
        }
        val summary = lines.joinToString(" · ")
        val builder = NotificationCompat.Builder(context, CHANNEL_HABIT)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${slotLabel}还有 ${habits.size} 个习惯")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openHabits(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ID_BASE + slot.ordinal, builder.build())
        }
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
}
