package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import com.oto.stats.data.TrackCount
import java.time.LocalDate

/** A single-day best. [allTimeBest] is true when nothing in your whole history beats it. */
data class DayRecord(val value: Long, val day: LocalDate, val allTimeBest: Boolean)

data class RunRecord(val length: Int, val start: LocalDate, val end: LocalDate, val allTimeBest: Boolean)

/** Personal bests inside a window, each checked against your whole history. Pure. */
data class Records(
    val biggestDay: DayRecord?,
    val mostPlaysDay: DayRecord?,
    val longestRun: RunRecord?,
    val mostReplayed: TrackCount?,
) {
    /** True when at least one record here is also the best you have ever had. */
    val anyAllTimeBest: Boolean
        get() = biggestDay?.allTimeBest == true || mostPlaysDay?.allTimeBest == true || longestRun?.allTimeBest == true

    companion object {
        private fun bestBy(days: List<DayTotal>, key: (DayTotal) -> Long): DayTotal? =
            days.filter { key(it) > 0 }.maxWithOrNull(compareBy<DayTotal> { key(it) }.thenBy { it.day }) // later day wins a tie

        /** Longest run of consecutive days with listening, the latest winning ties. Runs of one day are not a streak. */
        fun longestRun(days: List<DayTotal>): Triple<Int, Long, Long>? {
            val sorted = days.filter { it.playedMs > 0 }.map { it.day }.distinct().sorted()
            var best: Triple<Int, Long, Long>? = null
            var runStart = 0
            for (i in sorted.indices) {
                if (i > 0 && sorted[i] != sorted[i - 1] + 1) runStart = i
                val len = i - runStart + 1
                if (len >= 2 && (best == null || len >= best.first)) best = Triple(len, sorted[runStart], sorted[i])
            }
            return best
        }

        fun compute(daily: List<DayTotal>, allDaily: List<DayTotal>, mostReplayed: TrackCount?): Records {
            val bigAll = allDaily.maxOfOrNull { it.playedMs } ?: 0L
            val playsAll = allDaily.maxOfOrNull { it.plays } ?: 0
            val runAll = longestRun(allDaily)?.first ?: 0
            val big = bestBy(daily) { it.playedMs }?.let { DayRecord(it.playedMs, LocalDate.ofEpochDay(it.day), it.playedMs >= bigAll) }
            val plays = bestBy(daily) { it.plays.toLong() }?.let { DayRecord(it.plays.toLong(), LocalDate.ofEpochDay(it.day), it.plays >= playsAll) }
            val run = longestRun(daily)?.let { (len, s, e) -> RunRecord(len, LocalDate.ofEpochDay(s), LocalDate.ofEpochDay(e), len >= runAll) }
            return Records(big, plays, run, mostReplayed?.takeIf { it.total >= 2 })
        }
    }
}
