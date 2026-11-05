package com.potato.focus.habits

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitAlertLevel
import com.potato.focus.data.SettingsStore

data class HabitChannelStatus(
    val appNotificationsEnabled: Boolean,
    val channelEnabled: Boolean,
    val soundEnabled: Boolean,
    val channelId: String
)

object HabitNotificationChannels {

    fun ensureChannel(
        context: Context,
        soundKey: String,
        level: HabitAlertLevel
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "habit_reminder"
        val id = channelId(soundKey, level)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) != null) return id

        val importance = when (level) {
            HabitAlertLevel.GENTLE -> NotificationManager.IMPORTANCE_LOW
            HabitAlertLevel.STANDARD -> NotificationManager.IMPORTANCE_DEFAULT
            HabitAlertLevel.STRONG -> NotificationManager.IMPORTANCE_HIGH
        }
        val levelLabel = when (level) {
            HabitAlertLevel.GENTLE -> "轻柔"
            HabitAlertLevel.STANDARD -> "标准"
            HabitAlertLevel.STRONG -> "醒目"
        }
        val channel = NotificationChannel(
            id,
            "习惯提醒 · $levelLabel · ${HabitSounds.label(soundKey)}",
            importance
        ).apply {
            description = "Potato 习惯提醒，每个时段每天最多一次"
            setShowBadge(true)
            if (level == HabitAlertLevel.GENTLE) {
                setSound(null, null)
                enableVibration(false)
            } else {
                val uri = HabitSounds.resolveUri(context, soundKey)
                if (uri != null) {
                    setSound(
                        uri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 100, 180)
            }
        }
        manager.createNotificationChannel(channel)
        return id
    }

    fun globalSettings(context: Context): Pair<String, HabitAlertLevel> {
        val settings = SettingsStore(context)
        val level = runCatching {
            HabitAlertLevel.valueOf(settings.habitAlertLevel)
        }.getOrDefault(HabitAlertLevel.STANDARD)
        return settings.habitSoundKey to level
    }

    fun globalChannel(context: Context): String {
        val (sound, level) = globalSettings(context)
        return ensureChannel(context, sound, level)
    }

    fun habitChannel(context: Context, habit: Habit): String {
        val (globalSound, level) = globalSettings(context)
        val sound = habit.soundKey.ifBlank { globalSound }
        return ensureChannel(context, sound, level)
    }

    fun status(context: Context): HabitChannelStatus {
        val (sound, level) = globalSettings(context)
        val id = channelId(sound, level)
        val appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return HabitChannelStatus(appEnabled, true, true, id)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(id)
        val channelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        val soundEnabled = level == HabitAlertLevel.GENTLE || channel == null || channel.sound != null
        return HabitChannelStatus(appEnabled, channelEnabled, soundEnabled, id)
    }

    private fun channelId(soundKey: String, level: HabitAlertLevel): String =
        "habit_reminder_v2_${HabitSounds.channelPart(soundKey)}_${level.name.lowercase()}"
}
