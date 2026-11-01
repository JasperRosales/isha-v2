package dev.jasper.ishav2.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import dev.jasper.ishav2.data.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AttachmentTooLargeException(val limitBytes: Long) :
    IllegalArgumentException("Attachment is larger than ${humanSize(limitBytes)}")

/** Copies picked media into app storage so chat history survives reboots. */
object FileStore {
    const val MAX_BYTES = 25L * 1024 * 1024

    fun attachmentsDir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    fun cameraDir(context: Context): File =
        File(context.cacheDir, "camera").apply { mkdirs() }

    fun newCameraFile(context: Context): File =
        File(cameraDir(context), "photo-${System.currentTimeMillis()}.jpg")

    suspend fun importUri(context: Context, uri: Uri): Part = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: mimeFromName(displayName(context, uri))
        val name = displayName(context, uri)
        val size = sizeOf(context, uri)
        if (size > MAX_BYTES) throw AttachmentTooLargeException(MAX_BYTES)

        val id = UUID.randomUUID().toString().take(8)
        if (mime.startsWith("image/") && mime != "image/gif") {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Could not read $name")
            val image = compressImage(bytes, name)
            val out = File(attachmentsDir(context), "$id.jpg")
            out.writeBytes(image)
            Part.Image(path = out.absolutePath, mime = "image/jpeg", name = name)
        } else {
            val extension = extensionFor(mime, name)
            val out = File(attachmentsDir(context), "$id.$extension")
            resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> copyLimited(input, output, MAX_BYTES) }
            } ?: throw IllegalStateException("Could not read $name")
            Part.File(path = out.absolutePath, mime = mime, name = name, size = out.length())
        }
    }

    suspend fun importCameraFile(context: Context, file: File): Part = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        if (bytes.size > MAX_BYTES) throw AttachmentTooLargeException(MAX_BYTES)
        val image = compressImage(bytes, file.name)
        val out = File(attachmentsDir(context), "${UUID.randomUUID().toString().take(8)}.jpg")
        out.writeBytes(image)
        file.delete()
        Part.Image(
            path = out.absolutePath,
            mime = "image/jpeg",
            name = "photo-${System.currentTimeMillis()}.jpg"
        )
    }

    fun delete(part: Part) {
        val path = when (part) {
            is Part.Image -> part.path
            is Part.File -> part.path
            else -> return
        }
        runCatching { File(path).takeIf { it.exists() && it.parentFile == File(path).parentFile }?.delete() }
    }

    private fun compressImage(bytes: ByteArray, name: String): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 1800) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalStateException("Could not decode $name")
        val rotated = rotateForExif(decoded, bytes)
        val scaled = scaleDown(rotated, 1600)
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 86, out)
        if (scaled !== rotated) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    private fun rotateForExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, max: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val ratio = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw AttachmentTooLargeException(limit)
            output.write(buffer, 0, read)
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return sanitize(fromProvider ?: uri.lastPathSegment ?: "attachment")
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: -1L
    }

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').take(120).ifBlank { "attachment" }

    private fun mimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    private fun extensionFor(mime: String, name: String): String {
        val fromName = name.substringAfterLast('.', "")
        if (fromName.isNotBlank() && fromName.length <= 6) return fromName
        return android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    }
}

fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
