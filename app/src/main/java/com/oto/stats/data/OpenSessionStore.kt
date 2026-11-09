package com.oto.stats.data

import android.content.Context
import com.oto.stats.tracking.engine.OpenSession
import com.oto.stats.tracking.engine.TrackMeta
import org.json.JSONObject

/**
 * Keeps listens that are still in progress, so a killed process loses at most a few seconds.
 * One entry per player app.
 */
class OpenSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("oto_open_sessions", Context.MODE_PRIVATE)

    fun all(): List<OpenSession> =
        prefs.all.values.mapNotNull { value -> (value as? String)?.let { decode(it) } }

    /** Replaces everything stored with [sessions]. */
    fun replaceAll(sessions: List<OpenSession>) {
        val editor = prefs.edit().clear()
        for (session in sessions) {
            editor.putString(session.packageName, encode(session))
        }
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encode(s: OpenSession): String = JSONObject()
        .put("id", s.sessionId)
        .put("pkg", s.packageName)
        .put("title", s.meta.title)
        .put("artist", s.meta.artist)
        .put("album", s.meta.album ?: JSONObject.NULL)
        .put("duration", s.meta.durationMs ?: JSONObject.NULL)
        .put("started", s.startedAtMs)
        .put("lastActive", s.lastActiveAtMs)
        .put("played", s.playedMs)
        .put("pauses", s.pauseCount)
        .put("seeks", s.seekCount)
        .toString()

    private fun decode(json: String): OpenSession? = runCatching {
        val o = JSONObject(json)
        OpenSession(
            sessionId = o.getString("id"),
            packageName = o.getString("pkg"),
            meta = TrackMeta(
                title = o.getString("title"),
                artist = o.getString("artist"),
                album = if (o.isNull("album")) null else o.getString("album"),
                durationMs = if (o.isNull("duration")) null else o.getLong("duration"),
            ),
            startedAtMs = o.getLong("started"),
            lastActiveAtMs = o.getLong("lastActive"),
            playedMs = o.getLong("played"),
            pauseCount = o.getInt("pauses"),
            seekCount = o.getInt("seeks"),
        )
    }.getOrNull()
}
