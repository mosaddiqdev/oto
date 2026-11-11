package com.oto.stats.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodKind { WEEK, MONTH, YEAR, ALL }

/** A half-open run of local calendar days: [start, endExclusive). */
data class DayRange(val start: LocalDate, val endExclusive: LocalDate) {
    val days: Long get() = ChronoUnit.DAYS.between(start, endExclusive)

    /** Epoch ms of local midnight, so DST days (23 or 25 hours) are handled by java.time. */
    fun startMs(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMs(zone: ZoneId): Long = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * The window Stats is showing: a calendar week, month or year (which can be stepped back through)
 * or everything tracked so far. Pure and clock-free; "today" and the first tracked day are passed in.
 */
class StatsPeriod private constructor(
    val kind: PeriodKind,
    val range: DayRange,
) {
    /** True when [today] falls inside this period, i.e. the period is still running. */
    fun isCurrent(today: LocalDate): Boolean = today >= range.start && today < range.endExclusive

    fun canGoNext(today: LocalDate): Boolean = kind != PeriodKind.ALL && !isCurrent(today) && range.start < today

    /** Stepping back is allowed while the earlier period still overlaps tracked history. */
    fun canGoPrevious(firstDay: LocalDate?): Boolean {
        if (kind == PeriodKind.ALL || firstDay == null) return false
        return range.start > firstDay
    }

    fun previous(weekStart: DayOfWeek): StatsPeriod = shifted(-1, weekStart)

    fun next(weekStart: DayOfWeek): StatsPeriod = shifted(1, weekStart)

    private fun shifted(steps: Long, weekStart: DayOfWeek): StatsPeriod {
        val anchor = when (kind) {
            PeriodKind.WEEK -> range.start.plusWeeks(steps)
            PeriodKind.MONTH -> range.start.plusMonths(steps)
            PeriodKind.YEAR -> range.start.plusYears(steps)
            PeriodKind.ALL -> return this
        }
        return containing(kind, anchor, weekStart, firstDay = null, today = anchor)
    }

    /**
     * The earlier window to compare against, or null for All time.
     * A running period is compared with the same number of elapsed days of the previous one, so
     * "Nov 1 to 11" is never judged against all of October.
     */
    fun comparison(today: LocalDate, weekStart: DayOfWeek): DayRange? {
        if (kind == PeriodKind.ALL) return null
        val prev = previous(weekStart).range
        if (!isCurrent(today)) return prev
        val elapsed = ChronoUnit.DAYS.between(range.start, today) + 1
        val end = minOf(prev.start.plusDays(elapsed), prev.endExclusive)
        return DayRange(prev.start, end)
    }

    /** True when the comparison covers fewer days than a whole previous period. */
    fun comparisonIsPartial(today: LocalDate, weekStart: DayOfWeek): Boolean {
        val cmp = comparison(today, weekStart) ?: return false
        return cmp.days < previous(weekStart).range.days
    }

    fun label(locale: Locale = Locale.getDefault()): String = when (kind) {
        PeriodKind.WEEK -> {
            val last = range.endExclusive.minusDays(1)
            val m1 = range.start.month.getDisplayName(TextStyle.SHORT, locale)
            val m2 = last.month.getDisplayName(TextStyle.SHORT, locale)
            if (range.start.month == last.month) "$m1 ${range.start.dayOfMonth} – ${last.dayOfMonth}"
            else "$m1 ${range.start.dayOfMonth} – $m2 ${last.dayOfMonth}"
        }
        PeriodKind.MONTH -> "${range.start.month.getDisplayName(TextStyle.FULL, locale)} ${range.start.year}"
        PeriodKind.YEAR -> range.start.year.toString()
        PeriodKind.ALL -> "All time"
    }

    /** The resolved dates, shown next to the data so the window is never ambiguous. */
    fun resolvedDates(today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val last = minOf(range.endExclusive.minusDays(1), today)
        fun fmt(d: LocalDate) =
            "${d.month.getDisplayName(TextStyle.SHORT, locale)} ${d.dayOfMonth}, ${d.year}"
        return "${fmt(range.start)} – ${fmt(last)}"
    }

    override fun equals(other: Any?) = other is StatsPeriod && other.kind == kind && other.range == range
    override fun hashCode() = 31 * kind.hashCode() + range.hashCode()
    override fun toString() = "StatsPeriod($kind, ${range.start}..${range.endExclusive})"

    companion object {
        /**
         * The period of [kind] that contains [anchor]. For ALL it runs from [firstDay]
         * (or [today] when nothing is tracked yet) through [today].
         */
        fun containing(
            kind: PeriodKind,
            anchor: LocalDate,
            weekStart: DayOfWeek,
            firstDay: LocalDate?,
            today: LocalDate,
        ): StatsPeriod = when (kind) {
            PeriodKind.WEEK -> {
                val start = anchor.with(TemporalAdjusters.previousOrSame(weekStart))
                StatsPeriod(kind, DayRange(start, start.plusWeeks(1)))
            }
            PeriodKind.MONTH -> {
                val start = anchor.withDayOfMonth(1)
                StatsPeriod(kind, DayRange(start, start.plusMonths(1)))
            }
            PeriodKind.YEAR -> {
                val start = anchor.withDayOfYear(1)
                StatsPeriod(kind, DayRange(start, start.plusYears(1)))
            }
            PeriodKind.ALL -> {
                val start = firstDay?.takeIf { it <= today } ?: today
                StatsPeriod(kind, DayRange(start, today.plusDays(1)))
            }
        }

        fun current(kind: PeriodKind, today: LocalDate, weekStart: DayOfWeek, firstDay: LocalDate?) =
            containing(kind, today, weekStart, firstDay, today)
    }
}
