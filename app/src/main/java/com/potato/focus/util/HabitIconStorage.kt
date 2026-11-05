package com.potato.focus.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object HabitIconStorage {

    suspend fun saveFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "habit_icons").apply { mkdirs() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            var width = bounds.outWidth
            var height = bounds.outHeight
            while (width / 2 >= 1024 && height / 2 >= 1024) {
                sample *= 2
                width /= 2
                height /= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val source = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            val side = minOf(source.width, source.height)
            val x = (source.width - side) / 2
            val y = (source.height - side) / 2
            val square = Bitmap.createBitmap(source, x, y, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
            if (square !== scaled) square.recycle()
            if (source !== square && source !== scaled) source.recycle()

            val file = File(dir, "habit_${UUID.randomUUID()}.webp")
            file.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.WEBP, 86, out)
            }
            scaled.recycle()
            file.absolutePath
        }.getOrNull()
    }

    fun delete(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
