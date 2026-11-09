package com.oto.stats.tracking.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackerTest {

    private val results = mutableListOf<ListenResult>()
    private var elapsed = 0L
    private var ids = 0
    private val epochBase = 1_700_000_000_000L

    private fun clock() = Clock(elapsed, epochBase + elapsed)

    private fun tracker(pkg: String = "com.example.player", rules: SessionRules = SessionRules()) =
        PlaybackTracker(pkg, rules, newSessionId = { "s${ids++}" }) { results += it }

    private val song = TrackMeta("Song", "Artist", "Album", 200_000)

    /** Plays for [seconds] of wall time, sampling every [stepSec]; position moves 1:1. Returns the new position. */
    private fun PlaybackTracker.play(seconds: Int, startPos: Long, stepSec: Int = 5): Long {
        var pos = startPos
        var remaining = seconds
        while (remaining > 0) {
            val step = minOf(stepSec, remaining)
            elapsed += step * 1000L
            pos += step * 1000L
            onTick(pos, 1f, clock())
            remaining -= step
        }
        return pos
    }

    private fun PlaybackTracker.start(meta: TrackMeta = song, position: Long = 0) {
        onMetadata(meta, clock())
        onPlaybackState(PlayState.PLAYING, position, 1f, clock())
    }

    private fun PlaybackTracker.stop() = onPlaybackState(PlayState.STOPPED, 0, 1f, clock())

    @Test
    fun fullPlayIsOneCompleteListen() {
        val t = tracker()
        t.start()
        t.play(200, 0)
        t.stop()

        assertEquals(1, results.size)
        val r = results[0]
        assertEquals(200_000L, r.playedMs)
        assertEquals(100, r.completionPct)
        assertFalse(r.skipped)
        assertFalse(r.durationEstimated)
        assertEquals("Song", r.meta.title)
    }

    @Test
    fun veryShortPlayIsDropped() {
        val t = tracker()
        t.start()
        t.play(10, 0)
        t.stop()
        assertTrue(results.isEmpty())
    }

    @Test
    fun partialPlayAboveMinimumIsSavedAsSkip() {
        val t = tracker()
        t.start()
        t.play(40, 0)
        t.stop()

        assertEquals(1, results.size)
        assertEquals(40_000L, results[0].playedMs)
        assertEquals(20, results[0].completionPct)
        assertTrue(results[0].skipped)
    }

    @Test
    fun pausedTimeIsNotCounted() {
        val t = tracker()
        t.start()
        val pos = t.play(30, 0)
        t.onPlaybackState(PlayState.PAUSED, pos, 1f, clock())
        elapsed += 600_000 // ten minutes paused
        t.onPlaybackState(PlayState.PLAYING, pos, 1f, clock())
        t.play(30, pos)
        t.stop()

        assertEquals(1, results.size)
        assertEquals(60_000L, results[0].playedMs)
        assertEquals(1, results[0].pauseCount)
    }

    @Test
    fun forwardSeekIsNotCounted() {
        val t = tracker()
        t.start()
        val pos = t.play(20, 0)
        elapsed += 1_000
        t.onTick(pos + 60_000, 1f, clock()) // jump ahead a minute
        t.play(20, pos + 60_000)
        t.stop()

        assertEquals(1, results.size)
        assertEquals(40_000L, results[0].playedMs)
        assertEquals(1, results[0].seekCount)
    }

    @Test
    fun restartAfterMostOfTrackStartsANewPlay() {
        val t = tracker()
        val short = TrackMeta("Loop", "Artist", null, 100_000)
        t.start(short)
        t.play(90, 0)
        elapsed += 5_000
        t.onTick(1_000, 1f, clock()) // looped back to the start
        assertEquals(1, results.size)
        assertEquals(90_000L, results[0].playedMs)

        t.play(60, 1_000)
        t.stop()
        assertEquals(2, results.size)
        assertEquals(60_000L, results[1].playedMs)
        assertTrue(results[0].sessionId != results[1].sessionId)
    }

    @Test
    fun earlyRestartIsJustASeek() {
        val t = tracker()
        t.start()
        t.play(30, 0)
        elapsed += 1_000
        t.onTick(1_000, 1f, clock()) // restarted after only 30s of 200s
        assertTrue(results.isEmpty())
        t.play(30, 1_000)
        t.stop()

        assertEquals(1, results.size)
        assertEquals(60_000L, results[0].playedMs)
    }

    @Test
    fun trackChangeClosesPreviousListen() {
        val t = tracker()
        t.start()
        t.play(50, 0)
        t.onMetadata(TrackMeta("Other", "Someone", null, 180_000), clock())

        assertEquals(1, results.size)
        assertEquals("Song", results[0].meta.title)
        assertEquals(50_000L, results[0].playedMs)
    }

    @Test
    fun spotifyAdIsIgnored() {
        val t = tracker(pkg = "com.spotify.music")
        t.start(TrackMeta("Advertisement", "Spotify", null, 30_000))
        t.play(30, 0)
        t.stop()
        assertTrue(results.isEmpty())
    }

    @Test
    fun hugeDurationIsNotMusic() {
        val t = tracker()
        t.start(TrackMeta("Long Episode", "Some Podcast", null, 45 * 60_000L))
        t.play(120, 0)
        t.stop()
        assertTrue(results.isEmpty())
    }

    @Test
    fun artistArrivingLateDoesNotSplitTheListen() {
        val t = tracker()
        t.start(TrackMeta("Song", "Unknown Artist", null, 200_000))
        val pos = t.play(10, 0)
        t.onMetadata(TrackMeta("Song", "Real Artist", "Album", 200_000), clock())
        t.play(40, pos)
        t.stop()

        assertEquals(1, results.size)
        assertEquals("Real Artist", results[0].meta.artist)
        assertEquals(50_000L, results[0].playedMs)
    }

    @Test
    fun unknownDurationUsesFallbackForCompletion() {
        val t = tracker()
        t.start(TrackMeta("Song", "Artist", null, null))
        t.play(100, 0)
        t.stop()

        assertEquals(1, results.size)
        assertEquals(100_000L, results[0].playedMs)
        assertTrue(results[0].durationEstimated)
        assertEquals(47, results[0].completionPct)
    }

    @Test
    fun shortTrackNeedsHalfNotTwentyFiveSeconds() {
        val t = tracker()
        t.start(TrackMeta("Jingle", "Artist", null, 30_000))
        t.play(20, 0)
        t.stop()

        assertEquals(1, results.size)
        assertEquals(20_000L, results[0].playedMs)
    }

    @Test
    fun stoppedThenPlayedAgainIsTwoListens() {
        val t = tracker()
        t.start()
        t.play(100, 0)
        t.stop()
        t.onPlaybackState(PlayState.PLAYING, 0, 1f, clock())
        t.play(100, 0)
        t.stop()

        assertEquals(2, results.size)
    }

    @Test
    fun shutdownFlushesOpenListen() {
        val t = tracker()
        t.start()
        t.play(60, 0)
        t.shutdown(clock())

        assertEquals(1, results.size)
        assertEquals(60_000L, results[0].playedMs)
    }

    @Test
    fun snapshotReflectsState() {
        val t = tracker()
        t.start()
        t.play(10, 0)
        val snap = t.snapshot()!!
        assertEquals(PlayState.PLAYING, snap.state)
        assertEquals(10_000L, snap.countedMs)
        assertTrue(snap.countable)
    }
}
