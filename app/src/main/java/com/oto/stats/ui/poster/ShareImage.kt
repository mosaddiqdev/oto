package com.oto.stats.ui.poster

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves a poster as a PNG in the cache and opens the system share sheet for it. */
object ShareImage {
    suspend fun share(context: Context, bitmap: Bitmap, name: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { if (it.lastModified() < System.currentTimeMillis() - 86_400_000L) it.delete() }
            val safe = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "oto" }
            File(dir, "oto-$safe.png").also { f ->
                f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share your stats").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
