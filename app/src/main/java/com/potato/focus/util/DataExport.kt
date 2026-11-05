package com.potato.focus.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.potato.focus.data.FocusSession
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitLog
import com.potato.focus.data.Preset
import com.potato.focus.data.TaskItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DataExport {

    fun createShareIntent(
        context: Context,
        sessions: List<FocusSession>,
        tasks: List<TaskItem>,
        presets: List<Preset>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>
    ): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "potato-backup-${System.currentTimeMillis()}.json")
        val root = JSONObject().apply {
            put("app", "Potato")
            put("schema", 2)
            put("exportedAt", System.currentTimeMillis())
            put("sessions", JSONArray().apply {
                sessions.forEach { session ->
                    put(JSONObject().apply {
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
                        put("taskTitle", session.taskTitle)
                        put("tag", session.tag)
                        put("mood", session.mood ?: JSONObject.NULL)
                        put("note", session.note)
                        put("source", session.source)
                        put("editedAt", session.editedAt ?: JSONObject.NULL)
                    })
                }
            })
            put("tasks", JSONArray().apply {
                tasks.forEach { task ->
                    put(JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("estimatePomodoros", task.estimatePomodoros)
                        put("tag", task.tag)
                        put("dueDate", task.dueDate ?: JSONObject.NULL)
                        put("completedAt", task.completedAt ?: JSONObject.NULL)
                        put("createdAt", task.createdAt)
                    })
                }
            })
            put("presets", JSONArray().apply {
                presets.forEach { preset ->
                    put(JSONObject().apply {
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
                    })
                }
            })
            put("habits", JSONArray().apply {
                habits.forEach { habit ->
                    put(JSONObject().apply {
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
                        put("note", habit.note)
                        put("sortOrder", habit.sortOrder)
                        put("archivedAt", habit.archivedAt ?: JSONObject.NULL)
                        put("createdAt", habit.createdAt)
                    })
                }
            })
            put("habitLogs", JSONArray().apply {
                habitLogs.forEach { log ->
                    put(JSONObject().apply {
                        put("id", log.id)
                        put("habitId", log.habitId)
                        put("date", log.date)
                        put("status", log.status.name)
                        put("focusedSeconds", log.focusedSeconds)
                        put("countValue", log.countValue)
                        put("source", log.source)
                        put("completedAt", log.completedAt ?: JSONObject.NULL)
                        put("note", log.note)
                    })
                }
            })
        }
        file.writeText(root.toString(2))
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Potato 数据备份")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "导出 Potato 数据").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
