package com.oto.stats.stats

import com.oto.stats.data.ArtistRef
import com.oto.stats.data.DayTotal
import com.oto.stats.data.ListeningDao
import com.oto.stats.data.TrackEntity
import com.oto.stats.data.TrackListen
import com.oto.stats.data.TrackStats
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Small facts derived from a track's own days and hours. Pure. */
data class TrackInsights(
    /** Local hour (0..23) with the most plays; null when there are none. */
    val favouriteHour: Int?,
    val daysPlayed: Int,
    /** The day with the most plays; the latest wins a tie. */
    val peakDay: DayTotal?,
    /** Longest run of consecutive days with a play (2 or more), as length, first day, last day. */
    val run: Triple<Int, Long, Long>?,
) {
    companion object {
        fun of(daily: List<DayTotal>, hourPlays: List<Int>): TrackInsights {
            val peak = daily.filter { it.plays > 0 }.maxWithOrNull(compareBy<DayTotal> { it.plays }.thenBy { it.day })
            val hour = hourPlays.withIndex().filter { it.value > 0 }.maxWithOrNull(compareBy<IndexedValue<Int>> { it.value }.thenByDescending { it.index })?.index
            return TrackInsights(hour, daily.count { it.plays > 0 }, peak, Records.longestRun(daily))
        }
    }
}

/** Everything the Track page shows, for one window. */
data class TrackDetail(
    val track: TrackEntity,
    val artists: List<ArtistRef>,
    val stats: TrackStats,
    /** 1 is the most played track in the window. */
    val rank: Int,
    val tracksPlayed: Int,
    val daily: List<DayTotal>,
    val listens: List<TrackListen>,
    val insights: TrackInsights,
)

/** Loads detail pages from the local listening history, reloading when it changes. */
@OptIn(FlowPreview::class)
class DetailModel(private val dao: ListeningDao) {

    fun track(id: Long, startMs: Long, endMs: Long): Flow<TrackDetail?> =
        dao.changeMarker().distinctUntilChanged().debounce(250).map { loadTrack(id, startMs, endMs) }

    private suspend fun loadTrack(id: Long, startMs: Long, endMs: Long): TrackDetail? {
        val track = dao.trackById(id) ?: return null
        val daily = dao.trackDaily(id, startMs, endMs)
        val hours = IntArray(24)
        for (b in dao.trackHourly(id, startMs, endMs)) hours[b.hour.coerceIn(0, 23)] += b.plays
        return TrackDetail(
            track = track,
            artists = dao.artistsOfTrack(id),
            stats = dao.trackStats(id, startMs, endMs),
            rank = dao.trackRank(id, startMs, endMs),
            tracksPlayed = dao.tracksPlayed(startMs, endMs),
            daily = daily,
            listens = dao.trackListens(id, startMs, endMs, 10),
            insights = TrackInsights.of(daily, hours.toList()),
        )
    }
}
