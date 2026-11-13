package com.oto.stats.stats

import com.oto.stats.data.DayTotal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SkyColumn(val value: Long, val lit: Boolean)

/**
 * Your days as a skyline: one column per day, or per few days when there are too many to draw. A column keeps the
 * PEAK of its days (so a record is never averaged away) and is lit if any of its days is lit. Days with no
 * listening are zero, not missing. Pure.
 */
object Skyline {
    fun build(
        daily: List<DayTotal>,
        start: LocalDate,
        end: LocalDate,
        maxColumns: Int,
        value: (DayTotal) -> Long,
        lit: (LocalDate) -> Boolean,
    ): List<SkyColumn> {
        if (end.isBefore(start) || maxColumns <= 0) return emptyList()
        val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        val per = ((days + maxColumns - 1) / maxColumns).coerceAtLeast(1)
        val byDay = daily.associateBy { it.day }
        val out = ArrayList<SkyColumn>((days + per - 1) / per)
        var i = 0
        while (i < days) {
            var peak = 0L
            var isLit = false
            for (k in i until minOf(i + per, days)) {
                val date = start.plusDays(k.toLong())
                byDay[date.toEpochDay()]?.let { peak = maxOf(peak, value(it)) }
                if (lit(date)) isLit = true
            }
            out.add(SkyColumn(peak, isLit))
            i += per
        }
        return out
    }
}
