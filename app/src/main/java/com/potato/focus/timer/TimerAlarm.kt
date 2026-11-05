package com.potato.focus.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class TimerAlarm(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun schedule(atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) return
        val pending = alarmIntent()
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !manager.canScheduleExactAlarms() -> {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
                }
                else -> manager.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } catch (_: Exception) {
            // The foreground service remains the primary timer. Alarm is a backup.
        }
    }

    fun cancel() {
        manager.cancel(alarmIntent())
    }

    private fun alarmIntent(): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = ACTION_TIMER_DUE
        }
        return PendingIntent.getBroadcast(
            context,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_TIMER_DUE = "com.potato.focus.TIMER_DUE"
    }
}
