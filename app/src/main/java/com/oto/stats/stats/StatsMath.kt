package com.oto.stats.stats

import kotlin.math.abs
import kotlin.math.roundToInt

private const val DAY_MS = 86_400_000L
private const val MINUTE_MS = 60_000L

/** Small pure helpers behind the Stats numbers. Kept free of Android so they are unit tested. */
object StatsMath {

    /**
     * The local day number (days since 1970-01-01) of a listen, using the UTC offset that was in
     * effect when it was played. Travelling later never moves an old listen to another day, and
     * totals line up with History for the same day.
     */
    fun localDay(startedAtMs: Long, tzOffsetMinutes: Int): Long =
        Math.floorDiv(startedAtMs + tzOffsetMinutes * MINUTE_MS, DAY_MS)

    /** Local hour of day (0..23) of a listen, by the same rule. */
    fun localHour(startedAtMs: Long, tzOffsetMinutes: Int): Int {
        val local = startedAtMs + tzOffsetMinutes * MINUTE_MS
        return (Math.floorMod(local, DAY_MS) / 3_600_000L).toInt()
    }

    data class Streaks(val current: Int, val longest: Int)

    /**
     * Consecutive days with listening. [activeDays] are local day numbers; [today] is the local day
     * number of now. The current streak stays alive through today even if nothing has played yet,
     * as long as yesterday was active, so it does not read zero every morning.
     */
    fun streaks(activeDays: Collection<Long>, today: Long): Streaks {
        if (activeDays.isEmpty()) return Streaks(0, 0)
        val days = activeDays.toSortedSet().toLongArray()

        var longest = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i] == days[i - 1] + 1) run + 1 else 1
            if (run > longest) longest = run
        }

        val last = days.last()
        val current = if (last == today || last == today - 1) {
            var n = 1
            var i = days.size - 1
            while (i > 0 && days[i - 1] == days[i] - 1) {
                n++
                i--
            }
            n
        } else {
            0
        }
        return Streaks(current, longest)
    }

    enum class Direction { UP, DOWN, FLAT }

    data class Delta(val percent: Int, val direction: Direction)

    /**
     * Change against the comparison window. Null (hide it) when there is nothing to compare with:
     * a previous value of zero would give a meaningless "+infinity%".
     */
    fun delta(current: Long, previous: Long): Delta? {
        if (previous <= 0L) return null
        val pct = ((current - previous) * 100.0 / previous).roundToInt()
        val dir = when {
            pct > 0 -> Direction.UP
            pct < 0 -> Direction.DOWN
            else -> Direction.FLAT
        }
        return Delta(abs(pct), dir)
    }

    /**
     * Whether a period has enough listening for patterns (hour of day, habits) to mean anything.
     * Below this, show the headline and lists but hide distributions that would mislead.
     */
    fun hasEnoughForPatterns(plays: Int, activeDays: Int): Boolean = plays >= 10 && activeDays >= 3

    /** Average per day with its denominator spelled out, so "per day" is never ambiguous. */
    data class Average(val perDayMs: Long, val overDays: Int, val activeOnly: Boolean)

    fun average(totalMs: Long, calendarDays: Int, activeDays: Int, activeOnly: Boolean): Average? {
        val days = if (activeOnly) activeDays else calendarDays
        if (days <= 0) return null
        return Average(totalMs / days, days, activeOnly)
    }
}
