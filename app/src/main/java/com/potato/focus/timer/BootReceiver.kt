package com.potato.focus.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.potato.focus.habits.HabitReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in supportedActions) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HabitReminderScheduler.scheduleAll(context)
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    val snapshot = TimerStore(context).read()
                    if (!snapshot.isActive) return@launch
                    if (snapshot.isStopwatch) {
                        TimerService.start(context)
                        return@launch
                    }
                    if (TimerMath.isDue(snapshot, System.currentTimeMillis())) {
                        TimerController(context).completeDue()
                    } else {
                        TimerAlarm(context).schedule(snapshot.endsAt)
                    }
                    if (TimerStore(context).read().isActive) {
                        TimerService.start(context)
                    }
                }
            } catch (_: Exception) {
                // The next app launch still reconciles the persisted timer.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED
        )
    }
}
