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
 * skips, replays, three apps, one quiet month and one trip in another time zone. All names are made up.
 */
object DemoHistory {
    private const val SEED = 20_26L
    private const val SPOTIFY = "com.spotify.music"
    private const val YT_MUSIC = "com.google.android.apps.youtube.music"
    private const val POWERAMP = "com.maxmpz.audioplayer"

    private class Song(val title: String, val artist: String, val album: String?, val durationMs: Long)

    private val artists = listOf(
        "Hollow Lanterns", "Marlo Vance", "The Quiet Engines", "Seren Okafor", "Paper Satellites",
        "Dune Arcade", "Iris & the Tide", "Kobalt Kids", "Lumen Choir", "Nadia Brandt feat. Oren Vale",
        "Static Orchard", "Vesper Lane", "Tomo Hayashi", "Glass Meridian", "Ashen Coast",
    )
    private val titleA = listOf(
        "Blue", "Midnight", "Slow", "Paper", "Electric", "Hollow", "Golden", "Distant", "Velvet", "Neon",
        "Quiet", "Broken", "Summer", "Winter", "Open", "Last", "Faded", "Rising", "Silver", "Wild",
    )
    private val titleB = listOf(
        "Signals", "Harbor", "Echoes", "Gardens", "Static", "Weather", "Machines", "Letters", "Rivers", "Hours",
        "Windows", "Orbit", "Parade", "Shadows", "Lights", "Season", "Maps", "Fever", "Tides", "Rooms",
    )

    private fun catalog(rnd: Random): List<Song> {
        val used = HashSet<String>()
        val songs = ArrayList<Song>()
        for ((ai, artist) in artists.withIndex()) {
            val albums = listOf("${titleB[(ai * 3) % titleB.size]} Theory", "${titleA[(ai * 5) % titleA.size]} Season")
            val count = 6 + rnd.nextInt(5)
            var made = 0
            while (made < count) {
                val title = "${titleA[rnd.nextInt(titleA.size)]} ${titleB[rnd.nextInt(titleB.size)]}"
                if (!used.add("$title|$artist")) continue
                val album = if (rnd.nextInt(10) == 0) null else albums[rnd.nextInt(albums.size)]
                songs.add(Song(title, artist, album, (150_000L + rnd.nextInt(130_000))))
                made++
            }
        }
        return songs
    }

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
        val weights = DoubleArray(songs.size) { i -> 1.0 / (1 + i * 0.35) }
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
