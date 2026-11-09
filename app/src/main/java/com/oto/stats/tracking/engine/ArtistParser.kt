package com.oto.stats.tracking.engine

/**
 * Splits a credit string like "A feat. B, C & D" into individual artists.
 *
 * Commas and ampersands are ambiguous ("Tyler, The Creator", "Simon & Garfunkel"),
 * so known band names are protected first. The list can be extended by the user later.
 */
object ArtistParser {
    private const val MAX_ARTISTS = 8

    private val builtInKeepTogether = listOf(
        "tyler, the creator",
        "earth, wind & fire",
        "simon & garfunkel",
        "hall & oates",
        "ac/dc",
        "florence + the machine",
        "brooks & dunn",
        "crosby, stills & nash",
        "crosby, stills, nash & young",
        "emerson, lake & palmer",
        "chase & status",
        "mumford & sons",
        "years & years",
        "belle & sebastian",
        "kool & the gang",
        "sly & the family stone",
        "echo & the bunnymen",
        "bob marley & the wailers",
        "tom petty & the heartbreakers",
    )

    private val featRegex = Regex("""\s*[(\[]?\s*\b(?:feat|ft|featuring)\b\.?\s+""", RegexOption.IGNORE_CASE)
    private val separator = Regex("""\s*;\s*|,\s+|\s+&\s+|\s+×\s+|\s+x\s+|\s+/\s+""", RegexOption.IGNORE_CASE)
    private val tokenRegex = Regex("(\\d+)")

    fun parse(raw: String, extraKeepTogether: Collection<String> = emptyList()): List<String> {
        val cleaned = Normalize.cleanArtist(raw)
        if (Normalize.isUnknown(cleaned)) return listOf(cleaned)

        // Protect band names that contain separators.
        val protectedNames = mutableListOf<String>()
        var work = cleaned
        val keep = (builtInKeepTogether + extraKeepTogether.map { Normalize.key(it) })
            .distinct()
            .sortedByDescending { it.length }
        for (name in keep) {
            val pattern = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE,
            )
            work = pattern.replace(work) { match ->
                protectedNames += match.value
                "${protectedNames.lastIndex}"
            }
        }

        work = featRegex.replace(work, ";")

        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (piece in separator.split(work)) {
            val restored = tokenRegex.replace(piece) { protectedNames[it.groupValues[1].toInt()] }
            val part = restored.trim()
                .trim { it == '(' || it == ')' || it == '[' || it == ']' }
                .trim()
            if (part.isEmpty()) continue
            if (result.size < MAX_ARTISTS && seen.add(Normalize.artistKey(part))) {
                result.add(part)
            }
        }
        return if (result.isEmpty()) listOf(cleaned) else result
    }
}
