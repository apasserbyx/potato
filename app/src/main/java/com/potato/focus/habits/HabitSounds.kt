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
        return "系统默认"
    }

    fun resolveUri(context: Context, key: String): Uri? {
        when {
            key.isBlank() || key == "default" -> {
                return Settings.System.DEFAULT_NOTIFICATION_URI
            }
            key.startsWith("system:") -> {
                return runCatching { Uri.parse(key.removePrefix("system:")) }.getOrNull()
            }
            else -> {
                val option = builtInOptions.firstOrNull { it.key == key } ?: return Settings.System.DEFAULT_NOTIFICATION_URI
                val res = option.rawRes ?: return Settings.System.DEFAULT_NOTIFICATION_URI
                return Uri.parse("android.resource://${context.packageName}/$res")
            }
        }
    }

    fun channelPart(key: String): String = when {
        key.isBlank() -> "global"
        key.startsWith("system:") -> "sys_${abs(key.hashCode())}"
        else -> key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
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
