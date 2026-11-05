package com.potato.focus.habits

import android.content.Context
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.RawRes
import com.potato.focus.R
import kotlin.math.abs

data class HabitSoundOption(
    val key: String,
    val label: String,
    @RawRes val rawRes: Int? = null
)

object HabitSounds {
    val builtInOptions = listOf(
        HabitSoundOption("default", "系统默认"),
        HabitSoundOption("chime", "土豆叮", R.raw.habit_chime),
        HabitSoundOption("wood", "木鱼轻响", R.raw.habit_wood),
        HabitSoundOption("bell", "轻铃", R.raw.habit_bell)
    )

    private var currentPlayer: MediaPlayer? = null
    private var currentRingtone: Ringtone? = null

    fun label(key: String): String {
        if (key.isBlank()) return "跟随全局"
        builtInOptions.firstOrNull { it.key == key }?.let { return it.label }
        if (key.startsWith("system:")) return "系统铃声"
        if (key.startsWith("media:")) return "自定义音频"
        return "系统默认"
    }

    fun resolveUri(context: Context, key: String): Uri? {
        when {
            key.isBlank() || key == "default" -> {
                return Settings.System.DEFAULT_NOTIFICATION_URI ?: fallbackUri(context)
            }
            key.startsWith("system:") -> {
                return runCatching { Uri.parse(key.removePrefix("system:")) }.getOrNull()
            }
            key.startsWith("media:") -> {
                return runCatching { Uri.parse(key.removePrefix("media:")) }.getOrNull()
            }
            else -> {
                val option = builtInOptions.firstOrNull { it.key == key }
                    ?: return Settings.System.DEFAULT_NOTIFICATION_URI ?: fallbackUri(context)
                val res = option.rawRes
                    ?: return Settings.System.DEFAULT_NOTIFICATION_URI ?: fallbackUri(context)
                return Uri.parse("android.resource://${context.packageName}/$res")
            }
        }
    }

    fun fallbackUri(context: Context): Uri =
        Uri.parse("android.resource://${context.packageName}/${R.raw.habit_chime}")

    fun channelPart(key: String): String = when {
        key.isBlank() -> "global"
        key.startsWith("system:") -> "sys_${abs(key.hashCode())}"
        key.startsWith("media:") -> "media_${abs(key.hashCode())}"
        else -> key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    fun playReminder(context: Context, key: String) {
        if (key.isBlank()) return
        val audioManager = context.getSystemService(android.media.AudioManager::class.java)
        if (audioManager.ringerMode == android.media.AudioManager.RINGER_MODE_SILENT) return
        if (audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) return
        if (audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) <= 0) return
        val notificationManager =
            context.getSystemService(android.app.NotificationManager::class.java)
        if (notificationManager.isNotificationPolicyAccessGranted &&
            notificationManager.currentInterruptionFilter !=
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        ) {
            return
        }
        stopPreview()
        val option = builtInOptions.firstOrNull { it.key == key }
        if (option?.rawRes != null) {
            runCatching {
                currentPlayer = MediaPlayer.create(context, option.rawRes).apply {
                    setOnCompletionListener { stopPreview() }
                    start()
                }
            }
            return
        }
        val uri = resolveUri(context, key) ?: return
        runCatching {
            currentPlayer = MediaPlayer.create(context, uri)?.apply {
                setOnCompletionListener { stopPreview() }
                start()
            }
        }
    }

    fun preview(context: Context, key: String) {
        stopPreview()
        val option = builtInOptions.firstOrNull { it.key == key }
        if (option?.rawRes != null) {
            runCatching {
                currentPlayer = MediaPlayer.create(context, option.rawRes).apply {
                    setOnCompletionListener { stopPreview() }
                    start()
                }
            }
            return
        }
        val uri = resolveUri(context, key) ?: return
        if (key.startsWith("media:")) {
            runCatching {
                currentPlayer = MediaPlayer.create(context, uri)?.apply {
                    setOnCompletionListener { stopPreview() }
                    start()
                }
            }
            return
        }
        runCatching {
            currentRingtone = RingtoneManager.getRingtone(context, uri)?.apply {
                play()
            }
        }
    }

    fun stopPreview() {
        runCatching { currentPlayer?.stop() }
        runCatching { currentPlayer?.release() }
        currentPlayer = null
        runCatching { currentRingtone?.stop() }
        currentRingtone = null
    }
}
