package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How this period stacks up against the user's own earlier periods of the same kind.
 * There is no population to rank against, so this is the honest version of "top 1%":
 * "your 2nd biggest month of 14".
 */
object PeriodRank {
    data class Result(val rank: Int, val of: Int, val sameElapsed: Boolean)

    /** Needs at least this many periods with listening, or a rank means nothing. */
    private const val MIN_PERIODS = 3

    fun compute(
        daily: List<DayTotal>,
        period: StatsPeriod,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Result? {
        if (period.kind == PeriodKind.ALL || daily.isEmpty()) return null
        val running = period.isCurrent(today)
        // A running period is judged by the same number of elapsed days in every other period.
        val elapsed = if (running) ChronoUnit.DAYS.between(period.range.start, today) + 1 else Long.MAX_VALUE

        val totals = HashMap<LocalDate, Long>()
        for (d in daily) {
            val date = LocalDate.ofEpochDay(d.day)
            val start = StatsPeriod.containing(period.kind, date, weekStart, null, today).range.start
            if (ChronoUnit.DAYS.between(start, date) >= elapsed) continue
            totals[start] = (totals[start] ?: 0L) + d.playedMs
        }

        val mine = totals[period.range.start] ?: return null
        val all = totals.filterValues { it > 0 }
        if (mine <= 0 || all.size < MIN_PERIODS) return null
        val better = all.count { (start, ms) -> start != period.range.start && ms > mine }
        return Result(rank = better + 1, of = all.size, sameElapsed = running)
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /** "Your 2nd biggest month of 14." / "Your biggest week of 9." / "Your 3rd best start to a month of 6." */
    fun sentence(r: Result, kind: PeriodKind): String {
        val unit = when (kind) {
            PeriodKind.WEEK -> "week"
            PeriodKind.MONTH -> "month"
            PeriodKind.YEAR -> "year"
            PeriodKind.ALL -> return ""
        }
        return when {
            r.sameElapsed && r.rank == 1 -> "Your best start to a $unit of ${r.of}."
            r.sameElapsed -> "Your ${ordinal(r.rank)} best start to a $unit of ${r.of}."
            r.rank == 1 -> "Your biggest $unit of ${r.of}."
            else -> "Your ${ordinal(r.rank)} biggest $unit of ${r.of}."
        }
    }
}
