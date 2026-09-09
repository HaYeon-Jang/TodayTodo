package com.example.todaytodo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** App-owned copies keep diary photos available after the original is moved or deleted. */
internal class DiaryPhotos(private val context: Context) {
    private val directory get() = File(context.filesDir, "diary_photos").apply { mkdirs() }

    fun file(id: String): File {
        validateId(id)
        return File(directory, id)
    }

    fun importPhoto(uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "읽을 수 없는 사진이에요" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3200) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("사진을 열 수 없어요")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull()
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
            val scale = (1600f / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
            postScale(scale, scale)
        }
        var normalized: Bitmap? = null
        try {
            normalized = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val bytes = ByteArrayOutputStream().use {
                check(normalized.compress(Bitmap.CompressFormat.JPEG, 85, it))
                it.toByteArray()
            }
            require(bytes.size <= MAX_BYTES)
            val id = digest(bytes) + ".jpg"
            write(id, bytes)
            return id
        } finally {
            if (normalized !== decoded) normalized?.recycle()
            decoded.recycle()
        }
    }

    fun thumbnail(id: String): Bitmap? {
        val path = file(id).absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1000) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun backup(ids: List<String>): JSONObject = JSONObject().apply {
        ids.distinct().forEach { id -> put(id, Base64.encodeToString(file(id).readBytes(), Base64.NO_WRAP)) }
    }

    fun removeUnused(ids: Set<String>) {
        directory.listFiles()?.filter { it.name.matches(Regex("[a-f0-9]{64}\\.jpg")) && it.name !in ids }
            ?.forEach { it.delete() }
    }

    fun restore(images: JSONObject, ids: List<String>) {
        ids.distinct().forEach { id ->
            validateId(id)
            val encoded = images.getString(id)
            require(encoded.length <= MAX_BYTES * 4 / 3 + 4)
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.size <= MAX_BYTES && digest(bytes) + ".jpg" == id)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..1600 && bounds.outHeight in 1..1600)
            write(id, bytes)
        }
    }

    private fun write(id: String, bytes: ByteArray) {
        val target = AtomicFile(file(id))
        val stream = target.startWrite()
        try {
            stream.write(bytes)
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024
        fun validateId(id: String) { require(id.matches(Regex("[a-f0-9]{64}\\.jpg"))) }
        private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
