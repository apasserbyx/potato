package com.potato.focus.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: TimerController
    private lateinit var store: TimerStore
    private var loopJob: Job? = null
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        controller = TimerController(this)
        store = TimerStore(this)
        TimerNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_STOP -> controller.stopCurrent()
            ACTION_SKIP_BREAK -> controller.skipBreak()
            ACTION_START_NEXT -> controller.startNextFocus()
        }

        val snapshot = store.read()
        if (!snapshot.isActive) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        startForegroundSafely(snapshot)
        startLoop()
        return START_STICKY
    }

    private fun startForegroundSafely(snapshot: TimerSnapshot) {
        val notification = TimerNotifications.ongoing(this, snapshot)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    TimerNotifications.ID_ONGOING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(TimerNotifications.ID_ONGOING, notification)
            }
            foregroundStarted = true
        } catch (_: Exception) {
            // If the system rejects the foreground service, keep the persisted
            // timer and let AlarmManager / the next app launch reconcile it.
            stopSelf()
        }
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                val snapshot = store.read()
                if (!snapshot.isActive) {
                    stopForegroundAndSelf()
                    break
                }
                if (TimerMath.isDue(snapshot, System.currentTimeMillis())) {
                    controller.completeDue()
                }
                val updated = store.read()
                if (!updated.isActive) {
                    stopForegroundAndSelf()
                    break
                }
                TimerNotifications.update(this@TimerService, updated)
                delay(1_000L)
            }
        }
    }

    private fun stopForegroundAndSelf() {
        loopJob?.cancel()
        loopJob = null
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PAUSE = "com.potato.focus.action.PAUSE"
        const val ACTION_RESUME = "com.potato.focus.action.RESUME"
        const val ACTION_STOP = "com.potato.focus.action.STOP"
        const val ACTION_SKIP_BREAK = "com.potato.focus.action.SKIP_BREAK"
        const val ACTION_START_NEXT = "com.potato.focus.action.START_NEXT"

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
