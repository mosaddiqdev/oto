package com.oto.stats.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsTest {
    private fun base(
        plays: Int = 100, replays: Int = 0, skips: Int = 0, hours: List<Long>? = null,
        artist: Int = 10, fresh: Int? = null,
    ) = PersonalityInput(plays, 50, replays, skips, hours, artist, fresh)

    private fun hoursAt(vararg h: Int): List<Long> = LongArray(24).also { a -> h.forEach { a[it] += 1000 } }.toList()

    @Test fun tooLittleDataIsHonest() {
        assertEquals("Just Getting Started", Insights.personality(base(plays = 4)).name)
    }

    @Test fun nightOwlBeatsEverythingElse() {
        val p = Insights.personality(base(hours = hoursAt(1, 2, 3, 20), artist = 80))
        assertEquals("Night Owl", p.name)
        assertEquals("75% of your listening happens after midnight.", p.line)
    }

    @Test fun priorityOrder() {
        assertEquals("Early Bird", Insights.personality(base(hours = hoursAt(8, 9, 20))).name)
        assertEquals("Loyalist", Insights.personality(base(artist = 40, replays = 50)).name)
        assertEquals("On Repeat", Insights.personality(base(replays = 15)).name)
        assertEquals("Explorer", Insights.personality(base(fresh = 60)).name)
        assertEquals("Picky", Insights.personality(base(skips = 35)).name)
        assertEquals("Steady Listener", Insights.personality(base()).name)
    }

    @Test fun daypartSharesAddUp() {
        val shares = Insights.daypartShares(hoursAt(0, 7, 13, 19)).toMap()
        assertEquals(25, shares[Daypart.NIGHT])
        assertEquals(25, shares[Daypart.EVENING])
    }

    @Test fun equivalents() {
        assertEquals("Just warming up.", Insights.equivalent(10 * 60_000L))
        assertEquals("That's about one full album.", Insights.equivalent(50 * 60_000L))
        assertEquals("That's about 5 albums back to back.", Insights.equivalent(210 * 60_000L))
        assertEquals("That's 2.5 days of nonstop music.", Insights.equivalent(60 * 3_600_000L))
    }

    @Test fun oneIn() {
        assertEquals("1 in 5", Insights.oneIn(21))
        assertEquals("1 in 2", Insights.oneIn(55))
        assertNull(Insights.oneIn(3))
        assertNull(Insights.oneIn(80))
    }
}
