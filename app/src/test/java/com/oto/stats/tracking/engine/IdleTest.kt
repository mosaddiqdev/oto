package com.oto.stats.tracking.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleTest {

    private val results = mutableListOf<ListenResult>()
    private var elapsed = 0L
    private var ids = 0
    private val epochBase = 1_700_000_000_000L
    private val minute = 60_000L
    private val song = TrackMeta("Song", "Artist", "Album", 200_000)

    private fun clock() = Clock(elapsed, epochBase + elapsed)

    private fun tracker() =
        PlaybackTracker("com.example.player", newSessionId = { "s${ids++}" }) { results += it }

    private fun PlaybackTracker.start() {
        onMetadata(song, clock())
        onPlaybackState(PlayState.PLAYING, 0, 1f, clock())
    }

    private fun PlaybackTracker.play(seconds: Int, startPos: Long): Long {
        var pos = startPos
        var remaining = seconds
        while (remaining > 0) {
            val step = minOf(5, remaining)
            elapsed += step * 1000L
            pos += step * 1000L
            onTick(pos, 1f, clock())
            remaining -= step
        }
        return pos
    }

    @Test
    fun longPauseThenResumeStartsANewListen() {
        val t = tracker()
        t.start()
        val pos = t.play(60, 0)
        t.onPlaybackState(PlayState.PAUSED, pos, 1f, clock())
        elapsed += 40 * minute
        t.onPlaybackState(PlayState.PLAYING, pos, 1f, clock())

        // The first sitting was closed when playback resumed.
        assertEquals(1, results.size)
        assertEquals("s0", results[0].sessionId)
        assertEquals(60_000L, results[0].playedMs)

        t.play(40, pos)
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())
        assertEquals(2, results.size)
        assertEquals("s1", results[1].sessionId)
        assertEquals(40_000L, results[1].playedMs)
    }

    @Test
    fun shortPauseKeepsTheSameListen() {
        val t = tracker()
        t.start()
        val pos = t.play(30, 0)
        t.onPlaybackState(PlayState.PAUSED, pos, 1f, clock())
        elapsed += 10 * minute
        t.onPlaybackState(PlayState.PLAYING, pos, 1f, clock())
        t.play(30, pos)
        t.onPlaybackState(PlayState.STOPPED, 0, 1f, clock())

        assertEquals(1, results.size)
        assertEquals("s0", results[0].sessionId)
        assertEquals(60_000L, results[0].playedMs)
    }

    @Test
    fun idleCheckClosesAListenLeftPausedTooLong() {
        val t = tracker()
        t.start()
        val pos = t.play(60, 0)
        t.onPlaybackState(PlayState.PAUSED, pos, 1f, clock())
        elapsed += 31 * minute
        t.onIdleCheck(clock())

        assertEquals(1, results.size)
        assertEquals(60_000L, results[0].playedMs)
        assertNull(t.openSession())

        t.onIdleCheck(clock())
        assertEquals(1, results.size)
    }

    @Test
    fun idleCheckKeepsAShortPause() {
        val t = tracker()
        t.start()
        val pos = t.play(60, 0)
        t.onPlaybackState(PlayState.PAUSED, pos, 1f, clock())
        elapsed += 5 * minute
        t.onIdleCheck(clock())

        assertTrue(results.isEmpty())
        assertNotNull(t.openSession())
    }

    @Test
    fun idleCheckDoesNothingWhilePlaying() {
        val t = tracker()
        t.start()
        t.play(60, 0)
        elapsed += 45 * minute // no samples for a long time, but the player never paused
        t.onIdleCheck(clock())

        assertTrue(results.isEmpty())
        assertNotNull(t.openSession())
    }

    @Test
    fun restoredOldSessionIsClosedWhenThePlayerResumes() {
        val t = tracker()
        val twoHoursAgo = epochBase - 120 * minute
        t.restore(OpenSession("saved-1", "com.example.player", song, twoHoursAgo - 70_000, twoHoursAgo, 60_000, 1, 0))
        t.onMetadata(song, clock())
        t.onPlaybackState(PlayState.PLAYING, 90_000, 1f, clock())

        assertEquals(1, results.size)
        assertEquals("saved-1", results[0].sessionId)
        assertEquals(60_000L, results[0].playedMs)
        // A fresh listen has started for the resumed playback.
        assertEquals("s0", t.openSession()?.sessionId)
    }
}
