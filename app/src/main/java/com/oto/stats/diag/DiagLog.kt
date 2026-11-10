package com.oto.stats.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small on-device log, kept across restarts, so a failure on a real phone can be read
 * afterwards (copy it from the debug screen). Records what the app saw and did,
 * never notification content.
 */
object DiagLog {
    private const val MAX_LINES = 300
    private const val MAX_FILE_BYTES = 120_000L

    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor()
    private val formatter = DateTimeFormatter
        .ofPattern("d MMM HH:mm:ss", Locale.US)
        .withZone(ZoneId.systemDefault())

    private var file: File? = null
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, "oto-diagnostics.log")
            file = f
            _lines.value = runCatching { f.readLines() }.getOrDefault(emptyList()).takeLast(MAX_LINES)
        }
    }

    fun log(tag: String, message: String) {
        val line = "${formatter.format(Instant.now())} [$tag] $message"
        Log.d("Oto", line)
        val target = synchronized(lock) {
            _lines.value = (_lines.value + line).takeLast(MAX_LINES)
            file
        }
        if (target == null) return
        writer.execute {
            runCatching {
                target.appendText(line + "\n")
                if (target.length() > MAX_FILE_BYTES) {
                    target.writeText(_lines.value.joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    fun clear() {
        val target = synchronized(lock) {
            _lines.value = emptyList()
            file
        }
        writer.execute { runCatching { target?.writeText("") } }
    }

    fun exportText(header: String): String = buildString {
        appendLine(header)
        _lines.value.forEach { appendLine(it) }
    }

    /** "45s", "12 min", "3 h" */
    fun ago(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 90 -> "${seconds}s"
            seconds < 90 * 60 -> "${seconds / 60} min"
            else -> "${seconds / 3600} h"
        }
    }
}
