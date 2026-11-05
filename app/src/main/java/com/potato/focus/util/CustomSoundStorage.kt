package com.potato.focus.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CustomSoundStorage {
    private const val MAX_BYTES = 5 * 1024 * 1024L

    suspend fun importBytes(
        context: Context,
        fileName: String,
        mime: String,
        bytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!mime.startsWith("audio/")) return@runCatching null
            if (bytes.size > MAX_BYTES) return@runCatching null
            val safeName = fileName.substringAfterLast('/').ifBlank {
                "Potato-${System.currentTimeMillis()}.mp3"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_NOTIFICATIONS + "/Potato"
                    )
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                val itemUri = resolver.insert(collection, values) ?: return@runCatching null
                resolver.openOutputStream(itemUri)?.use { it.write(bytes) }
                    ?: return@runCatching null
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                return@runCatching "media:$itemUri"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_NOTIFICATIONS
                    ),
                    "Potato"
                ).apply { mkdirs() }
                val file = File(dir, safeName)
                file.writeBytes(bytes)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mime),
                    null
                )
                return@runCatching "media:${Uri.fromFile(file)}"
            }
        }.getOrNull()
    }

    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "audio/mpeg"
            if (!mime.startsWith("audio/")) return@runCatching null

            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                } ?: -1L
            if (size > MAX_BYTES) return@runCatching null

            val extension = when (mime) {
                "audio/mpeg" -> "mp3"
                "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
                "audio/ogg" -> "ogg"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/aac" -> "aac"
                else -> "mp3"
            }
            val fileName = "Potato-${System.currentTimeMillis()}.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime)
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_NOTIFICATIONS + "/Potato"
                    )
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                val itemUri = resolver.insert(collection, values) ?: return@runCatching null
                resolver.openInputStream(uri)?.use { input ->
                    resolver.openOutputStream(itemUri)?.use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching null
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                return@runCatching "media:$itemUri"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_NOTIFICATIONS
                    ),
                    "Potato"
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mime),
                    null
                )
                return@runCatching "media:${Uri.fromFile(file)}"
            }
        }.getOrNull()
    }
}
