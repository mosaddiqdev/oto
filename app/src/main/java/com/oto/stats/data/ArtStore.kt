package com.oto.stats.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small cover thumbnails kept on the phone, one per track, so lists can show covers after the
 * player is gone. About 12 KB each. Keyed by the same identity the database uses for a track.
 */
class ArtStore(context: Context) {
    private val dir = File(context.filesDir, "art").apply { mkdirs() }
    private val memory = LruCache<String, ImageBitmap>(64)
    private val known = Collections.synchronizedSet(HashSet<String>())

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.jpg")
    }

    fun isKnown(key: String): Boolean = known.contains(key)

    /** Already decoded and in memory, so a list row can draw it on its first frame. */
    fun peek(key: String): ImageBitmap? = memory.get(key)

    suspend fun load(key: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(key)?.let { return@withContext it }
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { memory.put(key, it) }
    }

    /** Writes the thumbnail unless one exists. Call off the main thread. */
    fun saveIfMissing(key: String, source: Bitmap) {
        if (!known.add(key)) return
        val file = fileFor(key)
        if (file.exists()) return
        try {
            val longest = maxOf(source.width, source.height)
            val scale = if (longest > THUMB_PX) THUMB_PX.toFloat() / longest else 1f
            val thumb = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                source
            }
            val tmp = File(dir, file.name + ".tmp")
            tmp.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (e: Exception) {
            known.remove(key)
        }
    }

    /** True when a lookup for [key] failed recently, so we do not ask the network again every launch. */
    fun recentlyMissed(key: String, days: Int = 14): Boolean {
        val f = File(fileFor(key).path + ".none")
        return f.exists() && System.currentTimeMillis() - f.lastModified() < days * 86_400_000L
    }

    fun markMissed(key: String) {
        runCatching { File(fileFor(key).path + ".none").apply { writeText("") ; setLastModified(System.currentTimeMillis()) } }
    }

    fun hasFile(key: String): Boolean = fileFor(key).exists()

    private companion object {
        const val THUMB_PX = 300
    }
}
