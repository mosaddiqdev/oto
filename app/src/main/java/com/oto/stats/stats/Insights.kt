package com.oto.stats.stats

import java.util.Locale

enum class Daypart(val label: String, val hours: IntRange) {
    NIGHT("Night", 0..5),
    MORNING("Morning", 6..11),
    AFTERNOON("Afternoon", 12..17),
    EVENING("Evening", 18..23),
}

/** A name for how you listened this period, and one honest line backing it with a number. */
data class Personality(val name: String, val line: String)

data class PersonalityInput(
    val plays: Int,
    val uniqueTracks: Int,
    val replays: Int,
    val skips: Int,
    /** 24 played-ms entries; null when there is too little data for patterns. */
    val hours: List<Long>?,
    /** Share of listening time that went to the top artist, 0..100. */
    val topArtistPercent: Int,
    /** Share of tracks that were first heard in this period, 0..100; null when meaningless. */
    val newTrackPercent: Int?,
    /** Names for the evidence line, so it can say who or what instead of "a single artist". All optional. */
    val topArtistName: String? = null,
    val repeatedTitle: String? = null,
    val skippedTitle: String? = null,
    val newTracks: Int? = null,
)

/** Pure copy and rules behind the Stats "moments". Every sentence is backed by a computed number. */
object Insights {

    /** Percent of listening time in each part of the day, summing to about 100. */
    fun daypartShares(hours: List<Long>): List<Pair<Daypart, Int>> {
        val total = hours.sum().coerceAtLeast(1L)
        return Daypart.entries.map { part -> part to (part.hours.sumOf { hours[it] } * 100 / total).toInt() }
    }

    fun personality(i: PersonalityInput): Personality {
        if (i.plays < 10) return Personality("Just Getting Started", "A few more days and your habits will show.")
        val shares = i.hours?.let { daypartShares(it) }
        val night = shares?.first { it.first == Daypart.NIGHT }?.second ?: 0
        val morning = shares?.first { it.first == Daypart.MORNING }?.second ?: 0
        val replayRate = i.replays * 100 / i.plays
        val skipRate = i.skips * 100 / i.plays

        return when {
            night >= 30 -> Personality("Night Owl", "$night% of your listening happens after midnight.")
            morning >= 35 -> Personality("Early Bird", "$morning% of it happens before noon.")
            i.topArtistPercent >= 35 -> Personality(
                "Loyalist",
                if (i.topArtistName != null) "${i.topArtistPercent}% of your listening time went to ${i.topArtistName}." else "${i.topArtistPercent}% of your time went to a single artist.",
            )
            replayRate >= 12 -> Personality(
                "On Repeat",
                "You came back to the same songs $replayRate% of the time" + (i.repeatedTitle?.let { ", most of all \u201C$it\u201D." } ?: "."),
            )
            (i.newTrackPercent ?: 0) >= 50 -> Personality(
                "Explorer",
                if (i.newTracks != null) "${i.newTrackPercent}% of what you played was new to you: ${i.newTracks} new tracks." else "${i.newTrackPercent}% of what you played was new to you.",
            )
            skipRate >= 30 -> Personality(
                "Picky",
                "You skipped $skipRate% of what came on" + (i.skippedTitle?.let { ", most often \u201C$it\u201D." } ?: "."),
            )
            else -> Personality(
                "Steady Listener",
                if (i.topArtistName != null && i.topArtistPercent > 0) "No habit dominates. Your top artist, ${i.topArtistName}, took ${i.topArtistPercent}% of your time." else "No single habit dominates your listening.",
            )
        }
    }

    /** Makes a duration tangible. Albums are counted at 42 minutes, a typical full album. */
    fun equivalent(totalMs: Long): String {
        val minutes = totalMs / 60_000
        val hours = minutes / 60.0
        return when {
            hours >= 24 * 365 -> String.format(Locale.US, "That's %.1f years of nonstop music.", hours / (24 * 365))
            hours >= 48 -> String.format(Locale.US, "That's %.1f days of nonstop music.", hours / 24)
            minutes >= 84 -> "That's about ${minutes / 42} albums back to back."
            minutes >= 42 -> "That's about one full album."
            else -> "Just warming up."
        }
    }

    /** "1 in 5" style framing of a percentage, which reads faster than "21%". */
    fun oneIn(percent: Int): String? {
        if (percent < 8 || percent > 60) return null
        val n = Math.round(100.0 / percent).toInt().coerceAtLeast(2)
        return "1 in $n"
    }
}
