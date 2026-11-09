package com.oto.stats.tracking.engine

/** Decides whether what a player reports should be counted as music. */
object ContentFilter {

    private val spotifyPackages = setOf("com.spotify.music", "com.spotify.lite")

    fun isCountable(meta: TrackMeta, packageName: String, rules: SessionRules): Boolean {
        if (meta.title.isBlank()) return false
        val duration = meta.durationMs
        if (duration != null && duration > 0) {
            if (duration < rules.minTrackDurationMs || duration > rules.maxTrackDurationMs) return false
        }
        return !looksLikeAd(meta, packageName)
    }

    /** Heuristic: ads usually carry a generic title or the service name as the artist. */
    private fun looksLikeAd(meta: TrackMeta, packageName: String): Boolean {
        val title = meta.title.trim().lowercase()
        val artist = meta.artist.trim().lowercase()
        if (title == "advertisement" || artist == "advertisement") return true
        if (packageName in spotifyPackages) {
            return title == "spotify" || artist == "spotify"
        }
        return false
    }
}
