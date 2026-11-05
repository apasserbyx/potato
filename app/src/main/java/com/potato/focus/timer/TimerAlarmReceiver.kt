package com.potato.focus.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TimerAlarm.ACTION_TIMER_DUE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val controller = TimerController(context)
                controller.completeDue()
                if (TimerStore(context).read().isActive) {
                    TimerService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
