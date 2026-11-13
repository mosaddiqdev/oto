package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.LocalDate

/** One day of the "Showing up" card. [level] is 0 for no listening, 1..4 for how much, by quartile within the range. */
data class HeatDay(val date: LocalDate, val playedMs: Long, val plays: Int, val level: Int, val state: BarState)

data class Run(val length: Int, val end: LocalDate)

/** Pure model for the consistency card: which days had listening, how heavy, and the longest unbroken run. */
object ShowingUp {

    /** Every day from [start] to [end] inclusive. Days before tracking began or after today are not "zero", they are unknown. */
    fun build(start: LocalDate, end: LocalDate, today: LocalDate, firstTracked: LocalDate?, daily: List<DayTotal>): List<HeatDay> {
        val byDay = daily.associateBy { it.day }
        val raw = ArrayList<Triple<LocalDate, DayTotal?, BarState>>()
        var d = start
        while (!d.isAfter(end)) {
            val state = when {
                d.isAfter(today) -> BarState.FUTURE
                firstTracked != null && d.isBefore(firstTracked) -> BarState.BEFORE_TRACKING
                else -> BarState.TRACKED
            }
            raw.add(Triple(d, byDay[d.toEpochDay()], state))
            d = d.plusDays(1)
        }
        val positive = raw.filter { it.third == BarState.TRACKED }.mapNotNull { it.second?.playedMs?.takeIf { ms -> ms > 0 } }.sorted()
        fun q(p: Double) = if (positive.isEmpty()) 0L else positive[((positive.size - 1) * p).toInt()]
        val q1 = q(0.25); val q2 = q(0.5); val q3 = q(0.75)
        return raw.map { (date, row, state) ->
            val ms = if (state == BarState.TRACKED) row?.playedMs ?: 0L else 0L
            val level = when {
                ms <= 0L -> 0
                ms <= q1 -> 1
                ms <= q2 -> 2
                ms <= q3 -> 3
                else -> 4
            }
            HeatDay(date, ms, if (state == BarState.TRACKED) row?.plays ?: 0 else 0, level, state)
        }
    }

    fun elapsed(days: List<HeatDay>) = days.count { it.state == BarState.TRACKED }

    fun active(days: List<HeatDay>) = days.count { it.level > 0 }

    /** Longest stretch of consecutive days with listening; the latest one wins a tie. Null when there is none. */
    fun longestRun(days: List<HeatDay>): Run? {
        var best: Run? = null
        var len = 0
        for (d in days) {
            if (d.level > 0) {
                len++
                if (best == null || len >= best.length) best = Run(len, d.date)
            } else {
                len = 0
            }
        }
        return best
    }
}
