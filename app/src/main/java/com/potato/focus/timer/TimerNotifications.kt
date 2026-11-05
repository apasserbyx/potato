package com.potato.focus.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.potato.focus.MainActivity
import com.potato.focus.R
import com.potato.focus.util.TimeFormat

object TimerNotifications {
    const val CHANNEL_ONGOING = "timer_ongoing"
    const val CHANNEL_END = "timer_end"
    const val CHANNEL_END_APP = "timer_end_app"
    const val CHANNEL_REMINDER = "reminder"

    const val ID_ONGOING = 1001
    const val ID_FOCUS_END = 1002
    const val ID_BREAK_END = 1003
    const val ID_HABIT_END = 1004

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "计时进行中",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "锁屏和通知栏里的倒计时"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val end = NotificationChannel(
            CHANNEL_END,
            "计时结束",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "专注结束和休息结束提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 100, 180)
        }
        val endApp = NotificationChannel(
            CHANNEL_END_APP,
            "计时结束 · App 播放",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "专注完成和休息结束，由 App 直接播放提示音"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 100, 180)
        }
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "节奏提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "每日目标与专注时段提醒"
        }
        manager.createNotificationChannels(listOf(ongoing, end, endApp, reminder))
    }

    fun ongoing(context: Context, snapshot: TimerSnapshot): android.app.Notification {
        val now = System.currentTimeMillis()
        val remaining = TimerMath.remainingMillis(snapshot, now)
        val elapsed = TimerMath.elapsedFocusMillis(snapshot, now)
        val title = when {
            snapshot.isBreak -> if (snapshot.phase == TimerPhase.LONG_BREAK) "长休息" else "休息一下"
            snapshot.isHabit -> if (snapshot.status == TimerStatus.PAUSED) "习惯已暂停" else "习惯中"
            snapshot.isStopwatch -> if (snapshot.status == TimerStatus.PAUSED) "正计时已暂停" else "正计时中"
            snapshot.status == TimerStatus.PAUSED -> "专注已暂停"
            else -> "专注中"
        }
        val task = snapshot.taskTitle.ifBlank {
            if (snapshot.isBreak) "站起来，看看远处" else "自由专注"
        }
        val text = when {
            snapshot.status == TimerStatus.PAUSED && snapshot.isStopwatch ->
                "已用 ${TimeFormat.durationText(elapsed / 1000)} · 已暂停"
            snapshot.status == TimerStatus.PAUSED ->
                "剩余 ${TimeFormat.timerText((snapshot.remainingMs + 999) / 1000)} · 已暂停"
            snapshot.isStopwatch ->
                "已专注 ${TimeFormat.timerText(elapsed / 1000)}"
            snapshot.isBreak ->
                "休息 ${TimeFormat.timerText((remaining + 999) / 1000)} 后开始下一颗"
            else ->
                "剩余 ${TimeFormat.timerText((remaining + 999) / 1000)} · 预计 ${TimeFormat.clockTime(snapshot.endsAt)} 结束"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (snapshot.taskTitle.isNotBlank()) "$title · ${snapshot.taskTitle}" else title)
            .setContentText(text)
            .setContentIntent(openApp(context, 90))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(snapshot.status == TimerStatus.RUNNING)

        if (snapshot.status == TimerStatus.RUNNING) {
            if (snapshot.isStopwatch) {
                builder.setWhen(System.currentTimeMillis() - elapsed)
                builder.setUsesChronometer(true)
            } else {
                builder.setWhen(snapshot.endsAt)
                builder.setUsesChronometer(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            }
        } else {
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        }

        if (snapshot.status == TimerStatus.RUNNING) {
            builder.addAction(0, "暂停", serviceAction(context, TimerService.ACTION_PAUSE, 100))
        } else if (snapshot.status == TimerStatus.PAUSED) {
            builder.addAction(0, "继续", serviceAction(context, TimerService.ACTION_RESUME, 101))
        }
        if (snapshot.isBreak) {
            builder.addAction(0, "跳过休息", serviceAction(context, TimerService.ACTION_SKIP_BREAK, 103))
        } else {
            builder.addAction(0, "结束", serviceAction(context, TimerService.ACTION_STOP, 102))
        }
        return builder.build()
    }

    fun update(context: Context, snapshot: TimerSnapshot) {
        if (!snapshot.isActive) return
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_ONGOING, ongoing(context, snapshot))
        }
    }

    fun postFocusComplete(context: Context, snapshot: TimerSnapshot, nextPhase: TimerPhase) {
        val task = snapshot.taskTitle.ifBlank { "自由专注" }
        val breakMinutes = if (nextPhase == TimerPhase.LONG_BREAK) snapshot.longBreakMin else snapshot.shortBreakMin
        val text = if (snapshot.autoStartBreak) {
            "已记录 ${snapshot.focusMin} 分钟 · $task。休息 $breakMinutes 分钟吧。"
        } else {
            "已记录 ${snapshot.focusMin} 分钟 · $task。站起来伸个懒腰吧。"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_END_APP)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("这一颗，熟了。")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, 91))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_FOCUS_END, builder.build())
        }
    }

    fun postHabitComplete(context: Context, habitName: String) {
        val name = habitName.ifBlank { "习惯" }
        val builder = NotificationCompat.Builder(context, CHANNEL_END)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("今天的$name，完成了。")
            .setContentText("习惯已打卡，明天继续。")
            .setContentIntent(openApp(context, 93, tab = 1))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_HABIT_END, builder.build())
        }
    }

    fun postBreakComplete(context: Context, snapshot: TimerSnapshot) {
        val text = if (snapshot.autoStartFocus) {
            "下一颗已经开始了。"
        } else {
            "准备好下一颗了吗？点一下就开始。"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_END_APP)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("休息结束")
            .setContentText(text)
            .setContentIntent(openApp(context, 92))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (!snapshot.autoStartFocus) {
            builder.addAction(
                0,
                "开始下一颗",
                serviceAction(context, TimerService.ACTION_START_NEXT, 104)
            )
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_BREAK_END, builder.build())
        }
    }

    fun cancelOngoing(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ONGOING)
    }

    fun cancelEndNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_FOCUS_END)
        NotificationManagerCompat.from(context).cancel(ID_BREAK_END)
    }

    private fun openApp(context: Context, requestCode: Int, tab: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TAB, tab)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
