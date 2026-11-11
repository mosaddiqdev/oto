package com.oto.stats.stats

import com.oto.stats.data.AppShareRow
import com.oto.stats.data.Headline
import com.oto.stats.data.HourBucket
import com.oto.stats.data.ListeningDao
import com.oto.stats.data.TopAlbumRow
import com.oto.stats.data.TopArtistRow
import com.oto.stats.data.TopTrackRow
import com.oto.stats.data.TrackCount
import com.oto.stats.tracking.engine.Normalize
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers

const val TOP_LIST_SIZE = 5

/** What the user picked: a kind, how many periods back, and what to rank by. */
data class StatsSelection(
    val kind: PeriodKind = PeriodKind.MONTH,
    val stepsBack: Int = 0,
    val byTime: Boolean = true,
)

/** One period's worth of numbers, ready to draw. */
data class StatsData(
    val headline: Headline,
    val uniqueArtists: Int,
    /** Same measures over the comparison window; null for All time or when it has no listening. */
    val previous: Headline?,
    val previousUniqueArtists: Int?,
    val comparisonPartial: Boolean,
    val bars: List<ActivityBar>,
    val averagePerDay: StatsMath.Average?,
    val topArtists: List<TopArtistRow>,
    val topTracks: List<TopTrackRow>,
    val topAlbums: List<TopAlbumRow>,
    /** 24 entries, played ms per local hour; null when there is too little data for patterns. */
    val hours: List<Long>?,
    /** Monday..Sunday played ms; null when there is too little data for patterns. */
    val weekdays: List<Long>?,
    val apps: List<AppShareRow>,
    val replays: Int,
    val mostReplayed: TrackCount?,
    val mostSkipped: TrackCount?,
    val activeDaysInPeriod: Int,
    val streaks: StatsMath.Streaks,
)

data class StatsUiState(
    val selection: StatsSelection = StatsSelection(),
    val periodLabel: String = "",
    val resolvedDates: String = "",
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    /** "Since Mar 2025" style note for All time. */
    val trackedSince: LocalDate? = null,
    val content: Content = Content.Loading,
) {
    sealed interface Content {
        data object Loading : Content
        /** Nothing has ever been tracked. */
        data object NoHistory : Content
        /** Something is tracked, but not in this period. */
        data object EmptyPeriod : Content
        data class Ready(val data: StatsData) : Content
    }
}

/**
 * Holds the Stats selection and turns it into screen state. Lives as long as the process, so the
 * chosen period survives tab switches and rotation.
 *
 * `flatMapLatest` means a slow query for an old selection can never overwrite a newer one, and
 * a debounced change marker refreshes the numbers while a listen is being saved.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class StatsModel(
    private val dao: ListeningDao,
    scope: CoroutineScope,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val weekStart: () -> DayOfWeek = { WeekFields.of(Locale.getDefault()).firstDayOfWeek },
) {
    private val selection = MutableStateFlow(StatsSelection())

    val state: StateFlow<StatsUiState> = selection
        .flatMapLatest { sel ->
            dao.changeMarker().distinctUntilChanged().debounce(250).map { load(sel) }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectKind(kind: PeriodKind) {
        selection.value = selection.value.copy(kind = kind, stepsBack = 0)
    }

    fun previous() {
        selection.value = selection.value.let { it.copy(stepsBack = it.stepsBack + 1) }
    }

    fun next() {
        selection.value = selection.value.let { it.copy(stepsBack = (it.stepsBack - 1).coerceAtLeast(0)) }
    }

    fun setByTime(byTime: Boolean) {
        selection.value = selection.value.copy(byTime = byTime)
    }

    private suspend fun load(sel: StatsSelection): StatsUiState {
        val z = zone()
        val ws = weekStart()
        val today = Instant.ofEpochMilli(nowMs()).atZone(z).toLocalDate()
        val firstMs = dao.firstEventAt()
        val firstDay = firstMs?.let { Instant.ofEpochMilli(it).atZone(z).toLocalDate() }

        var period = StatsPeriod.current(sel.kind, today, ws, firstDay)
        repeat(sel.stepsBack) { period = period.previous(ws) }

        val base = StatsUiState(
            selection = sel,
            periodLabel = period.label(),
            resolvedDates = period.resolvedDates(today),
            canGoPrevious = period.canGoPrevious(firstDay),
            canGoNext = sel.stepsBack > 0 && period.canGoNext(today),
            trackedSince = firstDay,
        )
        if (firstMs == null) return base.copy(content = StatsUiState.Content.NoHistory)

        val startMs = period.range.startMs(z)
        val endMs = period.range.endMs(z)
        val headline = dao.headline(startMs, endMs)
        if (headline.plays == 0) return base.copy(content = StatsUiState.Content.EmptyPeriod)

        val unknownKey = Normalize.artistKey(Normalize.UNKNOWN_ARTIST)
        val daily = dao.dailyInRange(startMs, endMs)
        val activeInPeriod = daily.size
        val enough = StatsMath.hasEnoughForPatterns(headline.plays, activeInPeriod)

        val comparison = period.comparison(today, ws)
        var previous: Headline? = null
        var previousArtists: Int? = null
        if (comparison != null) {
            val cs = comparison.startMs(z)
            val ce = comparison.endMs(z)
            val h = dao.headline(cs, ce)
            if (h.plays > 0) {
                previous = h
                previousArtists = dao.uniqueArtists(cs, ce, unknownKey)
            }
        }

        val hours = if (enough) {
            val arr = LongArray(24)
            for (b in dao.hourly(startMs, endMs)) arr[b.hour.coerceIn(0, 23)] += b.playedMs
            arr.toList()
        } else {
            null
        }
        val weekdays = if (enough) {
            val arr = LongArray(7)
            for (d in daily) arr[LocalDate.ofEpochDay(d.day).dayOfWeek.value - 1] += d.playedMs
            arr.toList()
        } else {
            null
        }

        val shownEnd = minOf(period.range.endExclusive, today.plusDays(1))
        val calendarDays = java.time.temporal.ChronoUnit.DAYS.between(
            maxOf(period.range.start, firstDay ?: period.range.start),
            shownEnd,
        ).toInt()

        val streaks = StatsMath.streaks(dao.activeDays(), today.toEpochDay())

        val data = StatsData(
            headline = headline,
            uniqueArtists = dao.uniqueArtists(startMs, endMs, unknownKey),
            previous = previous,
            previousUniqueArtists = previousArtists,
            comparisonPartial = previous != null && period.comparisonIsPartial(today, ws),
            bars = ActivityBuckets.build(sel.kind, period.range, today, firstDay, daily),
            averagePerDay = StatsMath.average(headline.playedMs, calendarDays, activeInPeriod, activeOnly = false),
            topArtists = dao.topArtists(startMs, endMs, sel.byTime, unknownKey, TOP_LIST_SIZE),
            topTracks = dao.topTracks(startMs, endMs, sel.byTime, TOP_LIST_SIZE),
            topAlbums = dao.topAlbums(startMs, endMs, sel.byTime, TOP_LIST_SIZE),
            hours = hours,
            weekdays = weekdays,
            apps = dao.appShare(startMs, endMs),
            replays = dao.replayCount(startMs, endMs),
            mostReplayed = if (enough) dao.mostReplayed(startMs, endMs) else null,
            mostSkipped = if (enough) dao.mostSkipped(startMs, endMs) else null,
            activeDaysInPeriod = activeInPeriod,
            streaks = streaks,
        )
        return base.copy(content = StatsUiState.Content.Ready(data))
    }
}
