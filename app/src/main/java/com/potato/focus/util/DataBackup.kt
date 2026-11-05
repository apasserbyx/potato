package com.potato.focus.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.potato.focus.PotatoApp
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitIconType
import com.potato.focus.data.HabitLog
import com.potato.focus.data.HabitLogStatus
import com.potato.focus.data.HabitReminderMode
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.HabitType
import com.potato.focus.data.Preset
import com.potato.focus.data.SessionMode
import com.potato.focus.data.SessionStatus
import com.potato.focus.data.TaskItem
import com.potato.focus.habits.HabitReminderScheduler
import com.potato.focus.habits.HabitSounds
import com.potato.focus.timer.TimerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupPreview(
    val zipFile: File,
    val exportedAt: Long,
    val sessions: Int,
    val tasks: Int,
    val presets: Int,
    val habits: Int,
    val habitLogs: Int,
    val icons: Int,
    val sounds: Int
)

object DataBackup {
    private const val FORMAT_VERSION = 3
    private const val MAX_BACKUP_BYTES = 100L * 1024 * 1024
    private const val MAX_JSON_BYTES = 20L * 1024 * 1024
    private const val MAX_SOUND_BYTES = 5L * 1024 * 1024
    private const val MAX_ICON_BYTES = 8L * 1024 * 1024

    suspend fun export(context: Context): Intent? = withContext(Dispatchers.IO) {
        runCatching {
            val app = context.applicationContext as PotatoApp
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val staleBefore = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            dir.listFiles()?.forEach { old ->
                if (old.lastModified() < staleBefore) old.delete()
            }
            val file = File(dir, "Potato-backup-${System.currentTimeMillis()}.zip")
            val sessions = app.sessions.allSessionsIncludingDeleted()
            val tasks = app.tasks.allTasksIncludingDeleted()
            val presets = app.presets.allIncludingDeleted()
            val habits = app.habits.allHabitsIncludingDeleted()
            val habitLogs = app.habitLogs.allLogsIncludingDeleted()

            val soundAssets = linkedMapOf<String, String>()
            val soundKeys = buildSet {
                add(app.settings.habitSoundKey)
                add(app.settings.timerFocusSoundKey)
                add(app.settings.timerBreakSoundKey)
                habits.forEach { add(it.soundKey) }
            }.filter { it.startsWith("media:") || it.startsWith("system:") }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
                soundKeys.forEachIndexed { index, key ->
                    val uri = HabitSounds.resolveUri(context, key) ?: return@forEachIndexed
                    val mime = context.contentResolver.getType(uri) ?: mimeFromKey(key)
                    val ext = extensionForMime(mime)
                    val assetPath = "sounds/sound_$index.$ext"
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull() ?: return@forEachIndexed
                    if (bytes.size > 5 * 1024 * 1024) return@forEachIndexed
                    zip.putNextEntry(ZipEntry(assetPath))
                    zip.write(bytes)
                    zip.closeEntry()
                    soundAssets[key] = assetPath
                }

                val iconAssets = mutableMapOf<Long, String>()
                habits.filter { it.iconType == HabitIconType.IMAGE && it.iconImagePath.isNotBlank() }
                    .forEach { habit ->
                        val iconFile = File(habit.iconImagePath)
                        if (!iconFile.exists()) return@forEach
                        val ext = iconFile.extension.ifBlank { "webp" }
                        val assetPath = "icons/habit_${habit.id}.$ext"
                        zip.putNextEntry(ZipEntry(assetPath))
                        iconFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        iconAssets[habit.id] = assetPath
                    }

                val root = JSONObject().apply {
                    put("app", "Potato")
                    put("format", FORMAT_VERSION)
                    put("exportedAt", System.currentTimeMillis())
                    put("settings", settingsToJson(app))
                    put("sessions", JSONArray().apply {
                        sessions.forEach { put(sessionToJson(it)) }
                    })
                    put("tasks", JSONArray().apply {
                        tasks.forEach { put(taskToJson(it)) }
                    })
                    put("presets", JSONArray().apply {
                        presets.forEach { put(presetToJson(it)) }
                    })
                    put("habits", JSONArray().apply {
                        habits.forEach { put(habitToJson(it, iconAssets[it.id])) }
                    })
                    put("habitLogs", JSONArray().apply {
                        habitLogs.forEach { put(habitLogToJson(it)) }
                    })
                    put("soundAssets", JSONObject().apply {
                        soundAssets.forEach { (key, path) -> put(key, path) }
                    })
                }
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Potato 数据备份")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(send, "导出 Potato 备份")
        }.getOrNull()
    }

    suspend fun inspect(context: Context, uri: Uri): BackupPreview? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "imports").apply { mkdirs() }
            val local = File(dir, "potato-import-${System.currentTimeMillis()}.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                local.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BACKUP_BYTES) {
                            local.delete()
                            return@runCatching null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return@runCatching null
            val root = readBackupJson(local) ?: return@runCatching null
            if (root.optString("app") != "Potato") return@runCatching null
            if (root.optInt("format", 1) > FORMAT_VERSION) return@runCatching null
            val zip = ZipFile(local)
            val icons = zip.entries().asSequence().count { it.name.startsWith("icons/") && !it.isDirectory }
            val sounds = zip.entries().asSequence().count { it.name.startsWith("sounds/") && !it.isDirectory }
            zip.close()
            BackupPreview(
                zipFile = local,
                exportedAt = root.optLong("exportedAt"),
                sessions = activeCount(root.optJSONArray("sessions")),
                tasks = activeCount(root.optJSONArray("tasks")),
                presets = activeCount(root.optJSONArray("presets")),
                habits = activeCount(root.optJSONArray("habits")),
                habitLogs = activeCount(root.optJSONArray("habitLogs")),
                icons = icons,
                sounds = sounds
            )
        }.getOrNull()
    }

    suspend fun restore(context: Context, preview: BackupPreview): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (TimerStore(context).read().isActive) return@runCatching false
            val app = context.applicationContext as PotatoApp
            val root = readBackupJson(preview.zipFile) ?: return@runCatching false
            if (root.optString("app") != "Potato") return@runCatching false
            if (root.optInt("format", 1) > FORMAT_VERSION) return@runCatching false
            val zip = ZipFile(preview.zipFile)
            try {
                val soundMapping = mutableMapOf<String, String>()
                val soundAssets = root.optJSONObject("soundAssets") ?: JSONObject()
                soundAssets.keys().forEach { oldKey ->
                    val assetPath = soundAssets.optString(oldKey)
                    val entry = zip.getEntry(assetPath) ?: return@forEach
                    if (entry.size > MAX_SOUND_BYTES) return@forEach
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val mime = mimeFromAsset(assetPath)
                    val newKey = CustomSoundStorage.importBytes(
                        context,
                        assetPath.substringAfterLast('/'),
                        mime,
                        bytes
                    )
                    if (newKey != null) soundMapping[oldKey] = newKey
                }

                val iconMapping = mutableMapOf<String, String>()
                val iconDir = File(context.filesDir, "habit_icons").apply { mkdirs() }
                val habitsArray = root.optJSONArray("habits") ?: JSONArray()
                for (i in 0 until habitsArray.length()) {
                    val habitObj = habitsArray.optJSONObject(i) ?: continue
                    val assetPath = if (habitObj.isNull("iconAsset")) {
                        ""
                    } else {
                        habitObj.optString("iconAsset", "")
                    }
                    if (assetPath.isBlank()) continue
                    val entry = zip.getEntry(assetPath) ?: continue
                    if (entry.size > MAX_ICON_BYTES) continue
                    val ext = assetPath.substringAfterLast('.', "webp")
                    val target = File(iconDir, "restored_${System.currentTimeMillis()}_$i.$ext")
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    iconMapping[assetPath] = target.absolutePath
                }

                val settingsMap = jsonToSettings(root.optJSONObject("settings"))
                    .filterKeys { it != "habit_channel_generation" }
                    .mapValues { (key, value) ->
                        if (value is String && key in soundSettingsKeys) {
                            soundMapping[value] ?: when (key) {
                                "timer_break_sound" -> "bell"
                                else -> fallbackSound(value)
                            }
                        } else {
                            value
                        }
                    }

                val db = app.database.writableDatabase
                db.beginTransaction()
                try {
                    db.delete("sessions", null, null)
                    db.delete("tasks", null, null)
                    db.delete("presets", null, null)
                    db.delete("habits", null, null)
                    db.delete("habit_logs", null, null)

                    root.optJSONArray("sessions")?.let { rows ->
                        for (i in 0 until rows.length()) {
                            val o = rows.optJSONObject(i) ?: continue
                            val status = o.optString("status", "COMPLETED")
                            db.insert("sessions", null, ContentValues().apply {
                                put("id", o.optLong("id"))
                                put("mode", o.optString("mode", "FOCUS"))
                                put("status", if (status == "RUNNING") "PARTIAL" else status)
                                put("started_at", o.optLong("startedAt"))
                                put("ended_at", if (o.isNull("endedAt")) null else o.optLong("endedAt"))
                                put("planned_seconds", o.optInt("plannedSeconds"))
                                put("focused_seconds", o.optInt("focusedSeconds"))
                                put("paused_seconds", o.optInt("pausedSeconds"))
                                put("preset_id", nullableLong(o, "presetId"))
                                put("task_id", nullableLong(o, "taskId"))
                                put("habit_id", nullableLong(o, "habitId"))
                                put("task_title", o.optString("taskTitle"))
                                put("tag", o.optString("tag"))
                                put("mood", nullableInt(o, "mood"))
                                put("note", o.optString("note"))
                                put("source", o.optString("source", "timer"))
                                put("edited_at", nullableLong(o, "editedAt"))
                                put("sync_id", syncIdOf(o))
                                put(
                                    "updated_at",
                                    updatedAtOf(
                                        o,
                                        o.optLong("endedAt", o.optLong("startedAt"))
                                    )
                                )
                                put("deleted_at", nullableLong(o, "deletedAt"))
                            })
                        }
                    }

                    root.optJSONArray("tasks")?.let { rows ->
                        for (i in 0 until rows.length()) {
                            val o = rows.optJSONObject(i) ?: continue
                            db.insert("tasks", null, ContentValues().apply {
                                put("id", o.optLong("id"))
                                put("title", o.optString("title"))
                                put("estimate_pomodoros", o.optInt("estimatePomodoros", 1))
                                put("tag", o.optString("tag"))
                                put("due_date", if (o.isNull("dueDate")) null else o.optString("dueDate"))
                                put("completed_at", nullableLong(o, "completedAt"))
                                put("created_at", o.optLong("createdAt"))
                                put("sync_id", syncIdOf(o))
                                put("updated_at", updatedAtOf(o, o.optLong("createdAt")))
                                put("deleted_at", nullableLong(o, "deletedAt"))
                            })
                        }
                    }

                    root.optJSONArray("presets")?.let { rows ->
                        for (i in 0 until rows.length()) {
                            val o = rows.optJSONObject(i) ?: continue
                            db.insert("presets", null, ContentValues().apply {
                                put("id", o.optLong("id"))
                                put("name", o.optString("name"))
                                put("focus_min", o.optInt("focusMin", 25))
                                put("short_break_min", o.optInt("shortBreakMin", 5))
                                put("long_break_min", o.optInt("longBreakMin", 15))
                                put("long_break_every", o.optInt("longBreakEvery", 4))
                                put("auto_start_break", if (o.optBoolean("autoStartBreak", true)) 1 else 0)
                                put("auto_start_focus", if (o.optBoolean("autoStartFocus", false)) 1 else 0)
                                put("sound", if (o.optBoolean("sound", true)) 1 else 0)
                                put("is_default", if (o.optBoolean("isDefault", i == 0)) 1 else 0)
                                put("sort_order", o.optInt("sortOrder", i))
                                put("sync_id", syncIdOf(o))
                                put("updated_at", updatedAtOf(o, System.currentTimeMillis()))
                                put("deleted_at", nullableLong(o, "deletedAt"))
                            })
                        }
                    }

                    habitsArray.let { rows ->
                        for (i in 0 until rows.length()) {
                            val o = rows.optJSONObject(i) ?: continue
                            val iconAsset = if (o.isNull("iconAsset")) "" else o.optString("iconAsset", "")
                            val oldSound = o.optString("soundKey", "")
                            val newSound = if (soundMapping.containsKey(oldSound)) {
                                soundMapping[oldSound]
                            } else if (oldSound.startsWith("media:") || oldSound.startsWith("system:")) {
                                fallbackSound(oldSound)
                            } else {
                                oldSound
                            }
                            db.insert("habits", null, ContentValues().apply {
                                put("id", o.optLong("id"))
                                put("name", o.optString("name"))
                                put("icon", o.optString("icon"))
                                put("color", o.optString("color"))
                                put("type", o.optString("type", "TIMED"))
                                put("target_seconds", o.optInt("targetSeconds"))
                                put("target_count", o.optInt("targetCount"))
                                put("unit", o.optString("unit"))
                                put("weekdays_mask", o.optInt("weekdaysMask", 127))
                                put("reminder_slot", o.optString("reminderSlot", "NONE"))
                                put("reminder_enabled", if (o.optBoolean("reminderEnabled", true)) 1 else 0)
                                put("reminder_mode", o.optString("reminderMode", "SLOT"))
                                put("reminder_minutes", o.optInt("reminderMinutes", -1))
                                put("sound_key", newSound)
                                val iconType = o.optString("iconType", "EMOJI")
                                val restoredPath = iconMapping[iconAsset]
                                put(
                                    "icon_type",
                                    if (iconType == "IMAGE" && restoredPath == null) "BUILTIN" else iconType
                                )
                                put("icon_image_path", restoredPath ?: "")
                                if (iconType == "IMAGE" && restoredPath == null) {
                                    put("icon", "book")
                                }
                                put("note", o.optString("note"))
                                put("sort_order", o.optInt("sortOrder", i))
                                put("archived_at", nullableLong(o, "archivedAt"))
                                put("created_at", o.optLong("createdAt"))
                                put("sync_id", syncIdOf(o))
                                put("updated_at", updatedAtOf(o, o.optLong("createdAt")))
                                put("deleted_at", nullableLong(o, "deletedAt"))
                            })
                        }
                    }

                    root.optJSONArray("habitLogs")?.let { rows ->
                        for (i in 0 until rows.length()) {
                            val o = rows.optJSONObject(i) ?: continue
                            db.insert("habit_logs", null, ContentValues().apply {
                                put("id", o.optLong("id"))
                                put("habit_id", o.optLong("habitId"))
                                put("date", o.optString("date"))
                                put("status", o.optString("status", "PENDING"))
                                put("focused_seconds", o.optInt("focusedSeconds"))
                                put("count_value", o.optInt("countValue"))
                                put("source", o.optString("source", "timer"))
                                put("completed_at", nullableLong(o, "completedAt"))
                                put("note", o.optString("note"))
                                put("sync_id", syncIdOf(o))
                                put(
                                    "updated_at",
                                    updatedAtOf(
                                        o,
                                        o.optLong("completedAt", System.currentTimeMillis())
                                    )
                                )
                                put("deleted_at", nullableLong(o, "deletedAt"))
                            })
                        }
                    }

                    if ((root.optJSONArray("presets")?.length() ?: 0) == 0) {
                        db.insert("presets", null, ContentValues().apply {
                            put("name", "经典 25/5")
                            put("focus_min", 25)
                            put("short_break_min", 5)
                            put("long_break_min", 15)
                            put("long_break_every", 4)
                            put("auto_start_break", 1)
                            put("auto_start_focus", 0)
                            put("sound", 1)
                            put("is_default", 1)
                            put("sort_order", 0)
                            put("sync_id", UUID.randomUUID().toString())
                            put("updated_at", System.currentTimeMillis())
                        })
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                if (settingsMap.isNotEmpty()) {
                    app.settings.restorePreferences(settingsMap)
                }
                app.settings.habitChannelGeneration =
                    (System.currentTimeMillis() / 1000L % 1_000_000L).toInt() + 1
                context.getSharedPreferences("habit_snoozes", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                HabitReminderScheduler.scheduleAll(context)
                true
            } finally {
                zip.close()
            }
        }.getOrDefault(false)
    }

    private val soundSettingsKeys = setOf(
        "habit_sound_key",
        "timer_focus_sound",
        "timer_break_sound"
    )

    private fun fallbackSound(oldKey: String): String =
        if (oldKey.startsWith("media:") || oldKey.startsWith("system:")) "chime" else oldKey

    private fun readBackupJson(file: File): JSONObject? {
        val zip = ZipFile(file)
        return try {
            val entry = zip.getEntry("backup.json") ?: return null
            if (entry.size > MAX_JSON_BYTES) return null
            val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            JSONObject(text)
        } catch (_: Exception) {
            null
        } finally {
            zip.close()
        }
    }

    private fun settingsToJson(app: PotatoApp): JSONObject = JSONObject().apply {
        app.settings.exportPreferences().forEach { (key, value) ->
            if (key == "habit_channel_generation") return@forEach
            val entry = JSONObject()
            when (value) {
                is Boolean -> { entry.put("type", "b"); entry.put("value", value) }
                is Int -> { entry.put("type", "i"); entry.put("value", value) }
                is Long -> { entry.put("type", "l"); entry.put("value", value) }
                is Float -> { entry.put("type", "f"); entry.put("value", value) }
                is String -> { entry.put("type", "s"); entry.put("value", value) }
                else -> return@forEach
            }
            put(key, entry)
        }
    }

    private fun jsonToSettings(json: JSONObject?): Map<String, Any> {
        if (json == null) return emptyMap()
        val result = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            val entry = json.optJSONObject(key) ?: return@forEach
            when (entry.optString("type")) {
                "b" -> result[key] = entry.optBoolean("value")
                "i" -> result[key] = entry.optInt("value")
                "l" -> result[key] = entry.optLong("value")
                "f" -> result[key] = entry.optDouble("value").toFloat()
                "s" -> result[key] = entry.optString("value")
            }
        }
        return result
    }

    private fun mimeFromKey(key: String): String = when {
        key.endsWith(".wav") -> "audio/wav"
        key.endsWith(".ogg") -> "audio/ogg"
        key.endsWith(".m4a") -> "audio/mp4"
        else -> "audio/mpeg"
    }

    private fun mimeFromAsset(path: String): String = mimeFromKey(path)

    private fun extensionForMime(mime: String): String = when (mime) {
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/ogg" -> "ogg"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
        else -> "mp3"
    }

    private fun nullableLong(o: JSONObject, key: String): Long? =
        if (o.isNull(key)) null else o.optLong(key)

    private fun nullableInt(o: JSONObject, key: String): Int? =
        if (o.isNull(key)) null else o.optInt(key)

    private fun syncIdOf(o: JSONObject): String =
        o.optString("syncId", "").ifBlank { UUID.randomUUID().toString() }

    private fun updatedAtOf(o: JSONObject, fallback: Long): Long =
        o.optLong("updatedAt", 0L).takeIf { it > 0L } ?: fallback

    private fun activeCount(rows: JSONArray?): Int {
        if (rows == null) return 0
        var count = 0
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (row.optLong("deletedAt", 0L) == 0L) count++
        }
        return count
    }

    private fun sessionToJson(session: com.potato.focus.data.FocusSession) = JSONObject().apply {
        put("id", session.id)
        put("mode", session.mode.name)
        put("status", session.status.name)
        put("startedAt", session.startedAt)
        put("endedAt", session.endedAt ?: JSONObject.NULL)
        put("plannedSeconds", session.plannedSeconds)
        put("focusedSeconds", session.focusedSeconds)
        put("pausedSeconds", session.pausedSeconds)
        put("presetId", session.presetId ?: JSONObject.NULL)
        put("taskId", session.taskId ?: JSONObject.NULL)
        put("habitId", session.habitId ?: JSONObject.NULL)
        put("taskTitle", session.taskTitle)
        put("tag", session.tag)
        put("mood", session.mood ?: JSONObject.NULL)
        put("note", session.note)
        put("source", session.source)
        put("editedAt", session.editedAt ?: JSONObject.NULL)
        put("syncId", session.syncId)
        put("updatedAt", session.updatedAt)
        put("deletedAt", session.deletedAt ?: JSONObject.NULL)
    }

    private fun taskToJson(task: TaskItem) = JSONObject().apply {
        put("id", task.id)
        put("title", task.title)
        put("estimatePomodoros", task.estimatePomodoros)
        put("tag", task.tag)
        put("dueDate", task.dueDate ?: JSONObject.NULL)
        put("completedAt", task.completedAt ?: JSONObject.NULL)
        put("createdAt", task.createdAt)
        put("syncId", task.syncId)
        put("updatedAt", task.updatedAt)
        put("deletedAt", task.deletedAt ?: JSONObject.NULL)
    }

    private fun presetToJson(preset: Preset) = JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("focusMin", preset.focusMin)
        put("shortBreakMin", preset.shortBreakMin)
        put("longBreakMin", preset.longBreakMin)
        put("longBreakEvery", preset.longBreakEvery)
        put("autoStartBreak", preset.autoStartBreak)
        put("autoStartFocus", preset.autoStartFocus)
        put("sound", preset.sound)
        put("isDefault", preset.isDefault)
        put("sortOrder", preset.sortOrder)
        put("syncId", preset.syncId)
        put("updatedAt", preset.updatedAt)
        put("deletedAt", preset.deletedAt ?: JSONObject.NULL)
    }

    private fun habitToJson(habit: Habit, iconAsset: String?) = JSONObject().apply {
        put("id", habit.id)
        put("name", habit.name)
        put("icon", habit.icon)
        put("color", habit.color)
        put("type", habit.type.name)
        put("targetSeconds", habit.targetSeconds)
        put("targetCount", habit.targetCount)
        put("unit", habit.unit)
        put("weekdaysMask", habit.weekdaysMask)
        put("reminderSlot", habit.reminderSlot.name)
        put("reminderEnabled", habit.reminderEnabled)
        put("reminderMode", habit.reminderMode.name)
        put("reminderMinutes", habit.reminderMinutes)
        put("soundKey", habit.soundKey)
        put("iconType", habit.iconType.name)
        put("iconAsset", iconAsset ?: JSONObject.NULL)
        put("note", habit.note)
        put("sortOrder", habit.sortOrder)
        put("archivedAt", habit.archivedAt ?: JSONObject.NULL)
        put("createdAt", habit.createdAt)
        put("syncId", habit.syncId)
        put("updatedAt", habit.updatedAt)
        put("deletedAt", habit.deletedAt ?: JSONObject.NULL)
    }

    private fun habitLogToJson(log: HabitLog) = JSONObject().apply {
        put("id", log.id)
        put("habitId", log.habitId)
        put("date", log.date)
        put("status", log.status.name)
        put("focusedSeconds", log.focusedSeconds)
        put("countValue", log.countValue)
        put("source", log.source)
        put("completedAt", log.completedAt ?: JSONObject.NULL)
        put("note", log.note)
        put("syncId", log.syncId)
        put("updatedAt", log.updatedAt)
        put("deletedAt", log.deletedAt ?: JSONObject.NULL)
    }
}
