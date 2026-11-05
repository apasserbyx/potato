package com.potato.focus.data

import android.content.ContentValues
import android.database.Cursor
import java.time.LocalDate

class HabitDao(private val helper: PotatoDatabase) {

    fun activeHabits(): List<Habit> =
        query("archived_at IS NULL", null)

    fun allHabits(): List<Habit> =
        query(null, null)

    fun byId(id: Long): Habit? =
        query("id = ?", arrayOf(id.toString())).firstOrNull()

    fun insert(habit: Habit): Long {
        val values = habit.toValues(includeId = false)
        return helper.writableDatabase.insert("habits", null, values)
    }

    fun update(habit: Habit) {
        helper.writableDatabase.update(
            "habits",
            habit.toValues(includeId = false),
            "id = ?",
            arrayOf(habit.id.toString())
        )
    }

    fun archive(id: Long, archivedAt: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply { put("archived_at", archivedAt) }
        helper.writableDatabase.update("habits", values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("habits", "id = ?", arrayOf(id.toString()))
    }

    private fun query(selection: String?, args: Array<String>?): List<Habit> {
        val rows = mutableListOf<Habit>()
        helper.readableDatabase.query(
            "habits", null, selection, args, null, null, "sort_order ASC, id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) rows += cursor.toHabit()
        }
        return rows
    }

    private fun Cursor.toHabit() = Habit(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        icon = getString(getColumnIndexOrThrow("icon")),
        color = normalizeHabitColor(getString(getColumnIndexOrThrow("color"))),
        type = runCatching {
            HabitType.valueOf(getString(getColumnIndexOrThrow("type")))
        }.getOrDefault(HabitType.TIMED),
        targetSeconds = getInt(getColumnIndexOrThrow("target_seconds")),
        targetCount = getInt(getColumnIndexOrThrow("target_count")),
        unit = getString(getColumnIndexOrThrow("unit")),
        weekdaysMask = getInt(getColumnIndexOrThrow("weekdays_mask")),
        reminderSlot = runCatching {
            HabitReminderSlot.valueOf(getString(getColumnIndexOrThrow("reminder_slot")))
        }.getOrDefault(HabitReminderSlot.NONE),
        reminderEnabled = getInt(getColumnIndexOrThrow("reminder_enabled")) == 1,
        note = getString(getColumnIndexOrThrow("note")),
        sortOrder = getInt(getColumnIndexOrThrow("sort_order")),
        archivedAt = getLongOrNull("archived_at"),
        createdAt = getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun Cursor.getLongOrNull(name: String): Long? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getLong(index)
    }

    private fun normalizeHabitColor(hex: String): String = when (hex.uppercase()) {
        "#E2573E" -> "#96592F"
        "#77957D" -> "#5F7550"
        "#D99A3E" -> "#8F6224"
        "#6E7FA8" -> "#7A8794"
        else -> hex
    }

    private fun Habit.toValues(includeId: Boolean): ContentValues = ContentValues().apply {
        if (includeId) put("id", id)
        put("name", name)
        put("icon", icon)
        put("color", color)
        put("type", type.name)
        put("target_seconds", targetSeconds)
        put("target_count", targetCount)
        put("unit", unit)
        put("weekdays_mask", weekdaysMask)
        put("reminder_slot", reminderSlot.name)
        put("reminder_enabled", if (reminderEnabled) 1 else 0)
        put("note", note)
        put("sort_order", sortOrder)
        put("archived_at", archivedAt)
        put("created_at", createdAt)
    }
}

class HabitLogDao(private val helper: PotatoDatabase) {

    fun logsForDate(date: LocalDate): Map<Long, HabitLog> =
        query("date = ?", arrayOf(date.toString())).associateBy { it.habitId }

    fun logsSince(start: LocalDate): List<HabitLog> =
        query("date >= ?", arrayOf(start.toString()))

    fun logsForHabit(habitId: Long): List<HabitLog> =
        query("habit_id = ?", arrayOf(habitId.toString()), "date DESC")

    fun allLogs(): List<HabitLog> = query(null, null, "date DESC")

    fun clearAll() {
        helper.writableDatabase.delete("habit_logs", null, null)
    }

    fun get(habitId: Long, date: LocalDate): HabitLog? =
        query(
            "habit_id = ? AND date = ?",
            arrayOf(habitId.toString(), date.toString())
        ).firstOrNull()

    /**
     * Adds real focused time to a timed habit for a date.
     * Returns the updated log.
     */
    fun addTimedProgress(
        habitId: Long,
        date: LocalDate,
        seconds: Int,
        targetSeconds: Int
    ): HabitLog {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = getLocked(db, habitId, date)
            val focused = (existing?.focusedSeconds ?: 0) + seconds.coerceAtLeast(0)
            val completed = focused >= targetSeconds.coerceAtLeast(1)
            val status = if (completed) HabitLogStatus.COMPLETED else HabitLogStatus.PENDING
            val log = HabitLog(
                id = existing?.id ?: 0L,
                habitId = habitId,
                date = date.toString(),
                status = status,
                focusedSeconds = focused,
                countValue = existing?.countValue ?: 0,
                source = "timer",
                completedAt = if (completed) System.currentTimeMillis() else null,
                note = existing?.note.orEmpty()
            )
            upsertLocked(db, log, existing == null)
            db.setTransactionSuccessful()
            return log
        } finally {
            db.endTransaction()
        }
    }

    fun adjustCount(
        habitId: Long,
        date: LocalDate,
        delta: Int,
        targetCount: Int
    ): HabitLog {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = getLocked(db, habitId, date)
            val newCount = ((existing?.countValue ?: 0) + delta).coerceIn(0, 9999)
            val completed = newCount >= targetCount.coerceAtLeast(1)
            val status = if (completed) HabitLogStatus.COMPLETED else HabitLogStatus.PENDING
            val log = HabitLog(
                id = existing?.id ?: 0L,
                habitId = habitId,
                date = date.toString(),
                status = status,
                focusedSeconds = existing?.focusedSeconds ?: 0,
                countValue = newCount,
                source = "manual",
                completedAt = if (completed) System.currentTimeMillis() else null,
                note = existing?.note.orEmpty()
            )
            upsertLocked(db, log, existing == null)
            db.setTransactionSuccessful()
            return log
        } finally {
            db.endTransaction()
        }
    }

    fun markCompleted(habitId: Long, date: LocalDate): HabitLog {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = getLocked(db, habitId, date)
            val log = HabitLog(
                id = existing?.id ?: 0L,
                habitId = habitId,
                date = date.toString(),
                status = HabitLogStatus.COMPLETED,
                focusedSeconds = existing?.focusedSeconds ?: 0,
                countValue = existing?.countValue ?: 0,
                source = "manual",
                completedAt = System.currentTimeMillis(),
                note = existing?.note.orEmpty()
            )
            upsertLocked(db, log, existing == null)
            db.setTransactionSuccessful()
            return log
        } finally {
            db.endTransaction()
        }
    }

    fun markSkipped(habitId: Long, date: LocalDate): HabitLog {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = getLocked(db, habitId, date)
            val log = HabitLog(
                id = existing?.id ?: 0L,
                habitId = habitId,
                date = date.toString(),
                status = HabitLogStatus.SKIPPED,
                focusedSeconds = existing?.focusedSeconds ?: 0,
                countValue = existing?.countValue ?: 0,
                source = "manual",
                completedAt = null,
                note = existing?.note.orEmpty()
            )
            upsertLocked(db, log, existing == null)
            db.setTransactionSuccessful()
            return log
        } finally {
            db.endTransaction()
        }
    }

    private fun getLocked(
        db: android.database.sqlite.SQLiteDatabase,
        habitId: Long,
        date: LocalDate
    ): HabitLog? {
        db.query(
            "habit_logs",
            null,
            "habit_id = ? AND date = ?",
            arrayOf(habitId.toString(), date.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toLog() else null
        }
    }

    private fun upsertLocked(db: android.database.sqlite.SQLiteDatabase, log: HabitLog, insert: Boolean) {
        val values = ContentValues().apply {
            put("habit_id", log.habitId)
            put("date", log.date)
            put("status", log.status.name)
            put("focused_seconds", log.focusedSeconds)
            put("count_value", log.countValue)
            put("source", log.source)
            put("completed_at", log.completedAt)
            put("note", log.note)
        }
        if (insert) {
            db.insert("habit_logs", null, values)
        } else {
            db.update(
                "habit_logs",
                values,
                "habit_id = ? AND date = ?",
                arrayOf(log.habitId.toString(), log.date)
            )
        }
    }

    private fun query(
        selection: String?,
        args: Array<String>?,
        orderBy: String = "date ASC"
    ): List<HabitLog> {
        val rows = mutableListOf<HabitLog>()
        helper.readableDatabase.query(
            "habit_logs", null, selection, args, null, null, orderBy
        ).use { cursor ->
            while (cursor.moveToNext()) rows += cursor.toLog()
        }
        return rows
    }

    private fun Cursor.toLog() = HabitLog(
        id = getLong(getColumnIndexOrThrow("id")),
        habitId = getLong(getColumnIndexOrThrow("habit_id")),
        date = getString(getColumnIndexOrThrow("date")),
        status = runCatching {
            HabitLogStatus.valueOf(getString(getColumnIndexOrThrow("status")))
        }.getOrDefault(HabitLogStatus.PENDING),
        focusedSeconds = getInt(getColumnIndexOrThrow("focused_seconds")),
        countValue = getInt(getColumnIndexOrThrow("count_value")),
        source = getString(getColumnIndexOrThrow("source")),
        completedAt = if (isNull(getColumnIndexOrThrow("completed_at"))) null
        else getLong(getColumnIndexOrThrow("completed_at")),
        note = getString(getColumnIndexOrThrow("note"))
    )
}
