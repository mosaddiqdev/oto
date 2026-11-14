package com.oto.stats.data

import com.oto.stats.tracking.engine.ListenResult
import com.oto.stats.tracking.engine.TrackMeta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Random

/** Prefix on the session id of every demo listen, so they can be removed without touching real ones. */
const val DEMO_SESSION_PREFIX = "demo:"

enum class DemoPreset(val label: String, val days: Int) {
    WEEK("1 week", 7),
    MONTHS_3("3 months", 90),
    YEARS_3("3 years", 1095),
}

data class DemoListen(val result: ListenResult, val tzOffsetMinutes: Int)

/**
 * Builds a believable, repeatable listening history so every Stats state can be seen and tested
 * without waiting months for real data. Pure: same inputs, same output (fixed seed).
 *
 * It has commute and evening peaks, quieter weekend mornings, a few heavy artists plus a long tail,
 * skips, replays, three apps, one quiet month and one trip in another time zone. Real artists and songs, so the demo looks like a real library.
 */
object DemoHistory {
    private const val SEED = 20_26L
    private const val SPOTIFY = "com.spotify.music"
    private const val YT_MUSIC = "com.google.android.apps.youtube.music"
    private const val POWERAMP = "com.maxmpz.audioplayer"

    private class Song(val title: String, val artist: String, val album: String?, val durationMs: Long)

    /** One artist's songs on one album: title to length in seconds (approximate). */
    private fun album(artist: String, album: String?, vararg songs: Pair<String, Int>) =
        songs.map { (title, sec) -> Song(title, artist, album, sec * 1000L) }

    /**
     * Real artists and songs, so the demo looks like a real library. Order sets the favourites:
     * early artists and early songs get the heaviest rotation, the rest form the long tail.
     * The Instant Crush rows carry a "feat." credit on purpose, to exercise artist matching.
     */
    private val catalogSongs: List<Song> = listOf(
        album("The Weeknd", "After Hours", "Blinding Lights" to 200, "Save Your Tears" to 215, "Heartless" to 202, "In Your Eyes" to 238),
        album("The Weeknd", "Starboy", "Die For You" to 260),
        album("Arijit Singh", "Aashiqui 2", "Tum Hi Ho" to 262),
        album("Arijit Singh", "Ae Dil Hai Mushkil", "Channa Mereya" to 289, "Ae Dil Hai Mushkil" to 269),
        album("Arijit Singh", "Brahm\u0101stra", "Kesariya" to 268),
        album("Arijit Singh", "Bhediya", "Apna Bana Le" to 261),
        album("Taylor Swift", "1989", "Blank Space" to 231, "Style" to 231, "Shake It Off" to 219),
        album("Taylor Swift", "folklore", "cardigan" to 239, "august" to 262),
        album("Taylor Swift", "Midnights", "Anti-Hero" to 200),
        album("Daft Punk", "Random Access Memories", "Lose Yourself to Dance" to 353, "Giorgio by Moroder" to 544),
        album("Daft Punk feat. Julian Casablancas", "Random Access Memories", "Instant Crush" to 337),
        album("Daft Punk", "Discovery", "One More Time" to 320, "Digital Love" to 301, "Harder, Better, Faster, Stronger" to 224),
        album("Kendrick Lamar", "DAMN.", "HUMBLE." to 177, "DNA." to 185),
        album("Kendrick Lamar", "good kid, m.A.A.d city", "Swimming Pools (Drank)" to 313, "Money Trees" to 386, "m.A.A.d city" to 350),
        album("Kendrick Lamar", "To Pimp a Butterfly", "Alright" to 219, "King Kunta" to 234),
        album("A. R. Rahman", "Rockstar", "Kun Faya Kun" to 473, "Tum Ho" to 248),
        album("A. R. Rahman", "Slumdog Millionaire", "Jai Ho" to 318),
        album("Radiohead", "OK Computer", "Paranoid Android" to 387, "Karma Police" to 261, "No Surprises" to 229, "Let Down" to 299),
        album("Radiohead", "In Rainbows", "Nude" to 255),
        album("Frank Ocean", "Blonde", "Nights" to 307, "Ivy" to 249, "Self Control" to 249, "Pink + White" to 184),
        album("Frank Ocean", "channel ORANGE", "Thinkin Bout You" to 200, "Super Rich Kids" to 304),
        album("Tame Impala", "Currents", "The Less I Know the Better" to 216, "Let It Happen" to 467, "Eventually" to 319),
        album("Fleetwood Mac", "Rumours", "Dreams" to 257, "Go Your Own Way" to 223, "The Chain" to 270, "Don't Stop" to 193),
        album("Arctic Monkeys", "AM", "Do I Wanna Know?" to 272, "R U Mine?" to 202, "Arabella" to 207),
        album("Billie Eilish", "WHEN WE ALL FALL ASLEEP, WHERE DO WE GO?", "bad guy" to 194, "when the party's over" to 200),
        album("Billie Eilish", "Happier Than Ever", "Happier Than Ever" to 298, "NDA" to 195),
        album("Billie Eilish", "HIT ME HARD AND SOFT", "BIRDS OF A FEATHER" to 210),
        album("Coldplay", "A Rush of Blood to the Head", "The Scientist" to 309, "Clocks" to 307),
        album("Coldplay", "Parachutes", "Yellow" to 269),
        album("Dua Lipa", "Future Nostalgia", "Levitating" to 203, "Don't Start Now" to 183, "Physical" to 194),
    ).flatten()

    private fun catalog(rnd: Random): List<Song> =
        catalogSongs.map { if (rnd.nextInt(14) == 0) Song(it.title, it.artist, null, it.durationMs) else it } // a few untagged files

    /** Heavy listening skews to the first artists and first songs of each artist. */
    private fun pick(rnd: Random, songs: List<Song>, weights: DoubleArray, total: Double): Song {
        var r = rnd.nextDouble() * total
        for (i in songs.indices) {
            r -= weights[i]
            if (r <= 0) return songs[i]
        }
        return songs.last()
    }

    fun generate(
        preset: DemoPreset,
        nowMs: Long,
        zone: ZoneId,
        homeOffsetOverride: ZoneOffset? = null,
    ): List<DemoListen> {
        val rnd = Random(SEED)
        val songs = catalog(rnd)
        val weights = DoubleArray(songs.size) { i -> 1.0 / (1 + i * 0.2) }
        val total = weights.sum()

        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val out = ArrayList<DemoListen>()
        var counter = 0

        // The quiet month and the trip sit at fixed places in history, so they exist in long presets.
        val quietFrom = today.minusDays(150)
        val quietTo = today.minusDays(120)
        val tripFrom = today.minusDays(60)
        val tripTo = today.minusDays(48)
        val tripOffset = ZoneOffset.ofHoursMinutes(9, 0)

        for (back in preset.days - 1 downTo 0) {
            val day: LocalDate = today.minusDays(back.toLong())
            val onTrip = day >= tripFrom && day <= tripTo && preset.days > 60
            val quiet = day >= quietFrom && day <= quietTo
            val weekend = day.dayOfWeek.value >= 6
            val skipDay = rnd.nextDouble() < (if (quiet) 0.8 else 0.1)
            if (skipDay) continue

            // Listening grows slowly over time, so trends are visible.
            val growth = 0.75 + 0.5 * (1.0 - back.toDouble() / preset.days.coerceAtLeast(1))
            val blocks = if (weekend) {
                listOf(11 to 4, 15 to 6, 21 to 8)
            } else {
                listOf(8 to 5, 13 to 3, 18 to 6, 22 to 9)
            }
            val offset = homeOffsetOverride ?: if (onTrip) tripOffset else zone.rules.getOffset(day.atStartOfDay(zone).toInstant())

            for ((hour, base) in blocks) {
                if (rnd.nextDouble() < 0.18) continue
                val n = (base * growth * (0.6 + rnd.nextDouble() * 0.8)).toInt().coerceAtLeast(1)
                var cursor = day.atTime(hour, rnd.nextInt(40)).toInstant(offset).toEpochMilli()
                val appRoll = rnd.nextDouble()
                val pkg = when {
                    appRoll < 0.62 -> SPOTIFY
                    appRoll < 0.94 -> YT_MUSIC
                    else -> POWERAMP
                }
                var i = 0
                while (i < n) {
                    val song = pick(rnd, songs, weights, total)
                    val replays = if (rnd.nextDouble() < 0.06) 2 else 1
                    repeat(replays) {
                        val roll = rnd.nextDouble()
                        val completion: Int
                        val skipped: Boolean
                        when {
                            roll < 0.12 -> { completion = 5 + rnd.nextInt(25); skipped = true }
                            roll < 0.30 -> { completion = 40 + rnd.nextInt(50); skipped = false }
                            else -> { completion = 90 + rnd.nextInt(11); skipped = false }
                        }
                        val played = song.durationMs * completion / 100
                        if (cursor + played >= nowMs) return@repeat
                        counter++
                        out.add(
                            DemoListen(
                                ListenResult(
                                    sessionId = "$DEMO_SESSION_PREFIX$counter",
                                    packageName = pkg,
                                    meta = TrackMeta(song.title, song.artist, song.album, song.durationMs),
                                    startedAtMs = cursor,
                                    endedAtMs = cursor + played,
                                    playedMs = played,
                                    durationMs = song.durationMs,
                                    durationEstimated = false,
                                    completionPct = completion,
                                    skipped = skipped,
                                    pauseCount = if (rnd.nextInt(8) == 0) 1 else 0,
                                    seekCount = if (rnd.nextInt(12) == 0) 1 else 0,
                                ),
                                offset.totalSeconds / 60,
                            ),
                        )
                        cursor += played + 2_000
                    }
                    i++
                }
            }
        }
        return out
    }
}
