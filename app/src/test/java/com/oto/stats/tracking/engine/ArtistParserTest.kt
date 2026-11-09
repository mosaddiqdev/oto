package com.oto.stats.tracking.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistParserTest {

    @Test
    fun singleArtistStaysSingle() {
        assertEquals(listOf("Daft Punk"), ArtistParser.parse("Daft Punk"))
    }

    @Test
    fun splitsFeaturing() {
        assertEquals(listOf("Artist A", "Artist B"), ArtistParser.parse("Artist A feat. Artist B"))
        assertEquals(listOf("Artist A", "Artist B"), ArtistParser.parse("Artist A ft. Artist B"))
        assertEquals(listOf("Artist A", "Artist B"), ArtistParser.parse("Artist A (feat. Artist B)"))
    }

    @Test
    fun splitsCommasAndAmpersands() {
        assertEquals(listOf("A", "B", "C"), ArtistParser.parse("A, B & C"))
    }

    @Test
    fun splitsTimesAndX() {
        assertEquals(listOf("Karan Aujla", "Ikky"), ArtistParser.parse("Karan Aujla x Ikky"))
        assertEquals(listOf("A", "B"), ArtistParser.parse("A × B"))
    }

    @Test
    fun keepsKnownBandNamesTogether() {
        assertEquals(listOf("Tyler, The Creator"), ArtistParser.parse("Tyler, The Creator"))
        assertEquals(listOf("Simon & Garfunkel"), ArtistParser.parse("Simon & Garfunkel"))
        assertEquals(listOf("AC/DC"), ArtistParser.parse("AC/DC"))
        assertEquals(listOf("Earth, Wind & Fire"), ArtistParser.parse("Earth, Wind & Fire"))
    }

    @Test
    fun protectedNameCanSitNextToOthers() {
        assertEquals(
            listOf("Tyler, The Creator", "Kali Uchis"),
            ArtistParser.parse("Tyler, The Creator, Kali Uchis"),
        )
    }

    @Test
    fun extraKeepTogetherNamesAreRespected() {
        assertEquals(listOf("Foo & Bar"), ArtistParser.parse("Foo & Bar", listOf("foo & bar")))
    }

    @Test
    fun unknownStaysUnknown() {
        assertEquals(listOf("Unknown Artist"), ArtistParser.parse("<unknown>"))
        assertEquals(listOf("Unknown Artist"), ArtistParser.parse(""))
    }

    @Test
    fun removesDuplicatesAndTopicSuffix() {
        assertEquals(listOf("A"), ArtistParser.parse("A; a"))
        assertEquals(listOf("Some Artist"), ArtistParser.parse("Some Artist - Topic"))
    }
}
