package com.oto.stats.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Where a detail page can go. Encoded to a string so the back stack survives rotation and process death.
 * A page can carry the Stats window it was opened from ([startMs], [endMs], [label]); without one it opens on All time.
 */
sealed class Route {
    abstract val window: Window?

    data class Window(val startMs: Long, val endMs: Long, val label: String)

    data class Track(val id: Long, override val window: Window? = null) : Route()

    data class Artist(val id: Long, override val window: Window? = null) : Route()

    fun encode(): String {
        val w = window?.let { ":${it.startMs}:${it.endMs}:${URLEncoder.encode(it.label, "UTF-8")}" }.orEmpty()
        return when (this) {
            is Track -> "track:$id$w"
            is Artist -> "artist:$id$w"
        }
    }

    companion object {
        fun decode(s: String): Route? = runCatching {
            val p = s.split(":")
            val window = if (p.size >= 5) Window(p[2].toLong(), p[3].toLong(), URLDecoder.decode(p[4], "UTF-8")) else null
            when (p[0]) {
                "track" -> Track(p[1].toLong(), window)
                "artist" -> Artist(p[1].toLong(), window)
                else -> null
            }
        }.getOrNull()
    }
}

/** Opens a detail page from anywhere in the UI, without passing callbacks down every layer. */
class Navigator(val open: (Route) -> Unit, val back: () -> Unit)

val LocalNavigator = staticCompositionLocalOf<Navigator?> { null }
