package com.potato.focus.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PotatoDatabase(context: Context) : SQLiteOpenHelper(
    context,
    "potato.db",
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              mode TEXT NOT NULL,
              status TEXT NOT NULL,
              started_at INTEGER NOT NULL,
              ended_at INTEGER,
              planned_seconds INTEGER NOT NULL DEFAULT 0,
              focused_seconds INTEGER NOT NULL DEFAULT 0,
              paused_seconds INTEGER NOT NULL DEFAULT 0,
              preset_id INTEGER,
              task_id INTEGER,
              habit_id INTEGER,
              task_title TEXT NOT NULL DEFAULT '',
              tag TEXT NOT NULL DEFAULT '',
              mood INTEGER,
              note TEXT NOT NULL DEFAULT '',
              source TEXT NOT NULL DEFAULT 'timer',
              edited_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_sessions_started ON sessions(started_at)")

        db.execSQL(
            """
            CREATE TABLE tasks (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              estimate_pomodoros INTEGER NOT NULL DEFAULT 1,
              tag TEXT NOT NULL DEFAULT '',
              due_date TEXT,
              completed_at INTEGER,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE presets (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              focus_min INTEGER NOT NULL,
              short_break_min INTEGER NOT NULL,
              long_break_min INTEGER NOT NULL,
              long_break_every INTEGER NOT NULL,
              auto_start_break INTEGER NOT NULL DEFAULT 1,
              auto_start_focus INTEGER NOT NULL DEFAULT 0,
              sound INTEGER NOT NULL DEFAULT 1,
              is_default INTEGER NOT NULL DEFAULT 0,
              sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE settings (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            )
            """.trimIndent()
        )

        createHabitTables(db)
        seedPresets(db)
    }

    private fun createHabitTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE habits (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              icon TEXT NOT NULL DEFAULT '',
              color TEXT NOT NULL DEFAULT '',
              type TEXT NOT NULL,
              target_seconds INTEGER NOT NULL DEFAULT 0,
              target_count INTEGER NOT NULL DEFAULT 0,
              unit TEXT NOT NULL DEFAULT '',
              weekdays_mask INTEGER NOT NULL DEFAULT 127,
              reminder_slot TEXT NOT NULL DEFAULT 'NONE',
              reminder_enabled INTEGER NOT NULL DEFAULT 1,
              note TEXT NOT NULL DEFAULT '',
              sort_order INTEGER NOT NULL DEFAULT 0,
              archived_at INTEGER,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE habit_logs (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              habit_id INTEGER NOT NULL,
              date TEXT NOT NULL,
              status TEXT NOT NULL,
              focused_seconds INTEGER NOT NULL DEFAULT 0,
              count_value INTEGER NOT NULL DEFAULT 0,
              source TEXT NOT NULL DEFAULT 'timer',
              completed_at INTEGER,
              note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX idx_habit_log_date ON habit_logs(habit_id, date)")
    }

    private fun seedPresets(db: SQLiteDatabase) {
        val presets = listOf(
            arrayOf("经典 25/5", 25, 5, 15, 4, 1, 0, 1, 1, 0),
            arrayOf("深工作 50/10", 50, 10, 30, 3, 1, 0, 1, 0, 1),
            arrayOf("轻量 15/3", 15, 3, 10, 4, 1, 0, 1, 0, 2)
        )
        presets.forEach { row ->
            val values = ContentValues().apply {
                put("name", row[0] as String)
                put("focus_min", row[1] as Int)
                put("short_break_min", row[2] as Int)
                put("long_break_min", row[3] as Int)
                put("long_break_every", row[4] as Int)
                put("auto_start_break", row[5] as Int)
                put("auto_start_focus", row[6] as Int)
                put("sound", row[7] as Int)
                put("is_default", row[8] as Int)
                put("sort_order", row[9] as Int)
            }
            db.insert("presets", null, values)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN habit_id INTEGER")
            createHabitTables(db)
        }
    }

    companion object {
        const val DATABASE_VERSION = 2
    }
}

class SessionDao(private val helper: PotatoDatabase) {

    fun insertRunning(
        mode: SessionMode,
        plannedSeconds: Int,
        presetId: Long?,
        task: TaskItem?,
        startedAt: Long,
        habitId: Long? = null
    ): Long = insertRunningRaw(
        mode = mode,
        plannedSeconds = plannedSeconds,
        presetId = presetId,
        taskId = task?.id,
        habitId = habitId,
        taskTitle = task?.title.orEmpty(),
        tag = task?.tag.orEmpty(),
        startedAt = startedAt
    )

    fun insertRunningRaw(
        mode: SessionMode,
        plannedSeconds: Int,
        presetId: Long?,
        taskId: Long?,
        taskTitle: String,
        tag: String,
        startedAt: Long,
        habitId: Long? = null
    ): Long {
        val values = ContentValues().apply {
            put("mode", mode.name)
            put("status", SessionStatus.RUNNING.name)
            put("started_at", startedAt)
            put("planned_seconds", plannedSeconds)
            put("preset_id", presetId)
            put("task_id", taskId)
            put("habit_id", habitId)
            put("task_title", taskTitle)
            put("tag", tag)
            put("source", "timer")
        }
        return helper.writableDatabase.insert("sessions", null, values)
    }

    fun updateProgress(id: Long, focusedSeconds: Int, pausedSeconds: Int) {
        val values = ContentValues().apply {
            put("focused_seconds", focusedSeconds)
            put("paused_seconds", pausedSeconds)
        }
        helper.writableDatabase.update("sessions", values, "id = ?", arrayOf(id.toString()))
    }

    fun finish(
        id: Long,
        status: SessionStatus,
        endedAt: Long,
        focusedSeconds: Int,
        pausedSeconds: Int,
        mood: Int? = null,
        note: String = ""
    ) {
        val values = ContentValues().apply {
            put("status", status.name)
            put("ended_at", endedAt)
            put("focused_seconds", focusedSeconds)
            put("paused_seconds", pausedSeconds)
            put("mood", mood)
            put("note", note)
        }
        helper.writableDatabase.update("sessions", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateSession(session: FocusSession) {
        val values = ContentValues().apply {
            put("mode", session.mode.name)
            put("status", session.status.name)
            put("started_at", session.startedAt)
            put("ended_at", session.endedAt)
            put("planned_seconds", session.plannedSeconds)
            put("focused_seconds", session.focusedSeconds)
            put("paused_seconds", session.pausedSeconds)
            put("task_id", session.taskId)
            put("habit_id", session.habitId)
            put("task_title", session.taskTitle)
            put("tag", session.tag)
            put("mood", session.mood)
            put("note", session.note)
            put("edited_at", System.currentTimeMillis())
        }
        helper.writableDatabase.update("sessions", values, "id = ?", arrayOf(session.id.toString()))
    }

    fun sessionsSince(startMillis: Long): List<FocusSession> =
        query("started_at >= ?", arrayOf(startMillis.toString()), "started_at DESC")

    fun allSessions(): List<FocusSession> =
        query(null, null, "started_at DESC")

    fun recent(limit: Int): List<FocusSession> =
        query(null, null, "started_at DESC", limit)

    fun deleteSession(id: Long) {
        helper.writableDatabase.delete("sessions", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        helper.writableDatabase.delete("sessions", null, null)
    }

    private fun query(
        selection: String?,
        args: Array<String>?,
        orderBy: String,
        limit: Int? = null
    ): List<FocusSession> {
        val rows = mutableListOf<FocusSession>()
        val cursor = helper.readableDatabase.query(
            "sessions",
            null,
            selection,
            args,
            null,
            null,
            orderBy,
            limit?.toString()
        )
        cursor.use {
            while (it.moveToNext()) rows += it.toSession()
        }
        return rows
    }

    private fun Cursor.toSession() = FocusSession(
        id = getLong(getColumnIndexOrThrow("id")),
        mode = runCatching {
            SessionMode.valueOf(getString(getColumnIndexOrThrow("mode")))
        }.getOrDefault(SessionMode.FOCUS),
        status = runCatching {
            SessionStatus.valueOf(getString(getColumnIndexOrThrow("status")))
        }.getOrDefault(SessionStatus.COMPLETED),
        startedAt = getLong(getColumnIndexOrThrow("started_at")),
        endedAt = getLongOrNull("ended_at"),
        plannedSeconds = getInt(getColumnIndexOrThrow("planned_seconds")),
        focusedSeconds = getInt(getColumnIndexOrThrow("focused_seconds")),
        pausedSeconds = getInt(getColumnIndexOrThrow("paused_seconds")),
        presetId = getLongOrNull("preset_id"),
        taskId = getLongOrNull("task_id"),
        habitId = getLongOrNull("habit_id"),
        taskTitle = getString(getColumnIndexOrThrow("task_title")),
        tag = getString(getColumnIndexOrThrow("tag")),
        mood = getIntOrNull("mood"),
        note = getString(getColumnIndexOrThrow("note")),
        source = getString(getColumnIndexOrThrow("source")),
        editedAt = getLongOrNull("edited_at")
    )

    private fun Cursor.getLongOrNull(name: String): Long? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.getIntOrNull(name: String): Int? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getInt(index)
    }
}

class TaskDao(private val helper: PotatoDatabase) {

    fun activeTasks(): List<TaskItem> =
        query("completed_at IS NULL", null, "created_at DESC")

    fun allTasks(): List<TaskItem> =
        query(null, null, "created_at DESC")

    fun add(title: String, estimate: Int, tag: String): Long {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("estimate_pomodoros", estimate.coerceIn(1, 20))
            put("tag", tag)
            put("created_at", System.currentTimeMillis())
        }
        return helper.writableDatabase.insert("tasks", null, values)
    }

    fun setCompleted(id: Long, completed: Boolean) {
        val values = ContentValues().apply {
            put("completed_at", if (completed) System.currentTimeMillis() else null)
        }
        helper.writableDatabase.update("tasks", values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("tasks", "id = ?", arrayOf(id.toString()))
    }

    private fun query(selection: String?, args: Array<String>?, orderBy: String): List<TaskItem> {
        val rows = mutableListOf<TaskItem>()
        helper.readableDatabase.query(
            "tasks", null, selection, args, null, null, orderBy
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += TaskItem(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    estimatePomodoros = cursor.getInt(cursor.getColumnIndexOrThrow("estimate_pomodoros")),
                    tag = cursor.getString(cursor.getColumnIndexOrThrow("tag")),
                    dueDate = cursor.getString(cursor.getColumnIndexOrThrow("due_date")),
                    completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null
                    else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return rows
    }
}

class PresetDao(private val helper: PotatoDatabase) {

    fun all(): List<Preset> = query(null, null)

    fun default(): Preset? = query("is_default = 1", null).firstOrNull() ?: all().firstOrNull()

    fun byId(id: Long): Preset? = query("id = ?", arrayOf(id.toString())).firstOrNull()

    fun insert(preset: Preset): Long {
        val values = preset.toValues(includeId = false)
        return helper.writableDatabase.insert("presets", null, values)
    }

    fun update(preset: Preset) {
        helper.writableDatabase.update(
            "presets",
            preset.toValues(includeId = false),
            "id = ?",
            arrayOf(preset.id.toString())
        )
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("presets", "id = ?", arrayOf(id.toString()))
    }

    fun setDefault(id: Long) {
        helper.writableDatabase.beginTransaction()
        try {
            helper.writableDatabase.execSQL("UPDATE presets SET is_default = 0")
            helper.writableDatabase.execSQL("UPDATE presets SET is_default = 1 WHERE id = $id")
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    private fun query(selection: String?, args: Array<String>?): List<Preset> {
        val rows = mutableListOf<Preset>()
        helper.readableDatabase.query(
            "presets", null, selection, args, null, null, "sort_order ASC, id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += Preset(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    focusMin = cursor.getInt(cursor.getColumnIndexOrThrow("focus_min")),
                    shortBreakMin = cursor.getInt(cursor.getColumnIndexOrThrow("short_break_min")),
                    longBreakMin = cursor.getInt(cursor.getColumnIndexOrThrow("long_break_min")),
                    longBreakEvery = cursor.getInt(cursor.getColumnIndexOrThrow("long_break_every")),
                    autoStartBreak = cursor.getInt(cursor.getColumnIndexOrThrow("auto_start_break")) == 1,
                    autoStartFocus = cursor.getInt(cursor.getColumnIndexOrThrow("auto_start_focus")) == 1,
                    sound = cursor.getInt(cursor.getColumnIndexOrThrow("sound")) == 1,
                    isDefault = cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1,
                    sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order"))
                )
            }
        }
        return rows
    }

    private fun Preset.toValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("name", name)
        put("focus_min", focusMin)
        put("short_break_min", shortBreakMin)
        put("long_break_min", longBreakMin)
        put("long_break_every", longBreakEvery)
        put("auto_start_break", if (autoStartBreak) 1 else 0)
        put("auto_start_focus", if (autoStartFocus) 1 else 0)
        put("sound", if (sound) 1 else 0)
        put("is_default", if (isDefault) 1 else 0)
        put("sort_order", sortOrder)
    }
}
