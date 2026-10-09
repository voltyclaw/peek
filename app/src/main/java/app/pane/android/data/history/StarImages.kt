package app.pane.android.data.history

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

internal fun interface StarImageCompressor {
    /** JPEG (or already-small) bytes at most [maxBytes], or empty when the bytes are not an image. */
    fun compress(source: ByteArray, maxBytes: Int): ByteArray
}

internal data class PinnedStarFiles(
    val thumbPath: String?,
    val profilePath: String?,
    val totalBytes: Int,
)

internal interface StarImageStore {
    fun pin(canonicalUrl: String, thumbnail: ByteArray?, profile: ByteArray?): PinnedStarFiles
    fun unpin(thumbPath: String?, profilePath: String?)
    fun read(path: String?): ByteArray? = null
    fun restore(path: String?, bytes: ByteArray) = Unit
}

/** Thumbnail and profile photo together stay at or under 60 KB. No video. */
internal object StarImageBudget {
    const val MAX_TOTAL_BYTES = 60 * 1024
    private const val THUMB_SHARE = 45 * 1024
    private const val PROFILE_SHARE = 15 * 1024

    fun allowances(hasThumb: Boolean, hasProfile: Boolean): Pair<Int, Int> = when {
        hasThumb && hasProfile -> THUMB_SHARE to PROFILE_SHARE
        hasThumb -> MAX_TOTAL_BYTES to 0
        hasProfile -> 0 to MAX_TOTAL_BYTES
        else -> 0 to 0
    }
}

/**
 * Writes starred images under `filesDir/stars/`. Paths stored on the row are relative to that directory.
 * WebP encode is API 30; minSdk 26 stores JPEG.
 */
internal class FileStarImageStore(
    private val root: File,
    private val compressor: StarImageCompressor,
) : StarImageStore {
    override fun pin(canonicalUrl: String, thumbnail: ByteArray?, profile: ByteArray?): PinnedStarFiles {
        val thumbSource = thumbnail?.takeIf { it.isNotEmpty() }
        val profileSource = profile?.takeIf { it.isNotEmpty() }
        val (thumbMax, profileMax) = StarImageBudget.allowances(
            hasThumb = thumbSource != null,
            hasProfile = profileSource != null,
        )
        val thumb = fit(thumbSource, thumbMax)
        val profileBytes = fit(profileSource, profileMax)
        val folder = folderName(canonicalUrl)
        val thumbPath = thumb?.let { write(folder, "thumb.jpg", it) }
        val profilePath = profileBytes?.let { write(folder, "pfp.jpg", it) }
        val total = (thumb?.size ?: 0) + (profileBytes?.size ?: 0)
        check(total <= StarImageBudget.MAX_TOTAL_BYTES)
        return PinnedStarFiles(thumbPath, profilePath, total)
    }

    override fun unpin(thumbPath: String?, profilePath: String?) {
        listOfNotNull(thumbPath, profilePath).forEach { relative ->
            resolve(relative)?.takeIf { it.isFile }?.delete()
        }
    }

    override fun read(path: String?): ByteArray? =
        path?.let(::resolve)?.takeIf { it.isFile }?.readBytes()

    override fun restore(path: String?, bytes: ByteArray) {
        val relative = path ?: return
        val dest = resolve(relative) ?: return
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
    }

    private fun fit(source: ByteArray?, maxBytes: Int): ByteArray? {
        if (source == null || maxBytes <= 0) return null
        val compressed = compressor.compress(source, maxBytes)
        return compressed.takeIf { it.isNotEmpty() && it.size <= maxBytes }
    }

    private fun write(folder: String, name: String, bytes: ByteArray): String {
        val relative = "$folder/$name"
        val dest = File(root, relative)
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return relative
    }

    private fun resolve(relative: String): File? {
        if (relative.isBlank() || relative.contains("..")) return null
        val file = File(root, relative)
        val rootPath = root.canonicalFile.path
        val filePath = file.canonicalFile.path
        if (filePath != rootPath && !filePath.startsWith(rootPath + File.separator)) return null
        return file
    }

    private fun folderName(url: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal class AndroidStarImageCompressor : StarImageCompressor {
    override fun compress(source: ByteArray, maxBytes: Int): ByteArray {
        if (maxBytes <= 0 || source.isEmpty()) return ByteArray(0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ByteArray(0)
        var sample = 1
        val edge = max(bounds.outWidth, bounds.outHeight)
        while (edge / sample > MAX_EDGE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            source,
            0,
            source.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return ByteArray(0)
        val scaled = scaleDown(decoded, MAX_EDGE)
        if (scaled !== decoded) decoded.recycle()
        var quality = 80
        var bytes = jpeg(scaled, quality)
        while (bytes.size > maxBytes && quality > 20) {
            quality -= 15
            bytes = jpeg(scaled, quality)
        }
        var current = scaled
        while (bytes.size > maxBytes && minOf(current.width, current.height) > 32) {
            val next = scaleDown(current, max(current.width, current.height) / 2)
            if (next === current) break
            if (current !== scaled) current.recycle()
            current = next
            bytes = jpeg(current, 40)
        }
        if (current !== scaled) current.recycle()
        scaled.recycle()
        return if (bytes.size <= maxBytes) bytes else ByteArray(0)
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(bitmap.width, bitmap.height)
        if (edge <= maxEdge || edge == 0) return bitmap
        val scale = maxEdge.toFloat() / edge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private companion object {
        const val MAX_EDGE = 512
    }
}
