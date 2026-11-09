package com.oto.stats.tracking.engine

/** Text normalisation used for matching tracks and artists. */
object Normalize {
    const val UNKNOWN_ARTIST = "Unknown Artist"

    private val whitespace = Regex("\\s+")
    private val topicSuffix = Regex("\\s*-\\s*topic$", RegexOption.IGNORE_CASE)
    private val placeholders = setOf("", "<unknown>", "unknown", "unknown artist", "n/a", "null")

    /** Lowercased, trimmed, single-spaced. */
    fun key(s: String): String = whitespace.replace(s.trim(), " ").lowercase()

    /** Cleans a raw artist string: strips YouTube's " - Topic" suffix and maps placeholders to [UNKNOWN_ARTIST]. */
    fun cleanArtist(raw: String?): String {
        val trimmed = topicSuffix.replace(raw?.trim().orEmpty(), "").trim()
        return if (trimmed.lowercase() in placeholders) UNKNOWN_ARTIST else trimmed
    }

    fun isUnknown(artist: String): Boolean = artist == UNKNOWN_ARTIST

    fun artistKey(artist: String): String = key(topicSuffix.replace(artist.trim(), ""))

    /** Stable identity for a track: same title and artist text means the same track. */
    fun trackIdentity(title: String, artist: String): String = key(title) + "|" + artistKey(artist)
}
