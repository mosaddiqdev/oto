package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class BarUnit { DAY, MONTH, YEAR }

/** Why a bar is empty: a real zero is different from "before tracking began" or "not yet". */
enum class BarState { TRACKED, BEFORE_TRACKING, FUTURE }

data class ActivityBar(
    val start: LocalDate,
    val unit: BarUnit,
    val playedMs: Long,
    val plays: Int,
    val state: BarState,
)

/** Turns per-day totals into the bars of the activity chart for a period. Pure. */
object ActivityBuckets {

    fun unitFor(kind: PeriodKind, range: DayRange): BarUnit = when (kind) {
        PeriodKind.WEEK, PeriodKind.MONTH -> BarUnit.DAY
        PeriodKind.YEAR -> BarUnit.MONTH
        PeriodKind.ALL -> if (ChronoUnit.MONTHS.between(range.start.withDayOfMonth(1), range.endExclusive) > 36) BarUnit.YEAR else BarUnit.MONTH
    }

    private fun floor(d: LocalDate, unit: BarUnit): LocalDate = when (unit) {
        BarUnit.DAY -> d
        BarUnit.MONTH -> d.withDayOfMonth(1)
        BarUnit.YEAR -> d.withDayOfYear(1)
    }

    private fun step(d: LocalDate, unit: BarUnit): LocalDate = when (unit) {
        BarUnit.DAY -> d.plusDays(1)
        BarUnit.MONTH -> d.plusMonths(1)
        BarUnit.YEAR -> d.plusYears(1)
    }

    fun build(
        kind: PeriodKind,
        range: DayRange,
        today: LocalDate,
        firstDay: LocalDate?,
        daily: List<DayTotal>,
    ): List<ActivityBar> {
        val unit = unitFor(kind, range)
        val sums = HashMap<LocalDate, LongArray>() // [playedMs, plays]
        for (d in daily) {
            val key = floor(LocalDate.ofEpochDay(d.day), unit)
            val acc = sums.getOrPut(key) { LongArray(2) }
            acc[0] += d.playedMs
            acc[1] += d.plays.toLong()
        }
        val bars = ArrayList<ActivityBar>()
        var cursor = floor(range.start, unit)
        while (cursor < range.endExclusive) {
            val acc = sums[cursor]
            val state = when {
                cursor > today -> BarState.FUTURE
                firstDay != null && step(cursor, unit) <= firstDay -> BarState.BEFORE_TRACKING
                else -> BarState.TRACKED
            }
            bars.add(ActivityBar(cursor, unit, acc?.get(0) ?: 0L, (acc?.get(1) ?: 0L).toInt(), state))
            cursor = step(cursor, unit)
        }
        return bars
    }
}
