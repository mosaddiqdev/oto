package com.oto.stats.tracking.engine

/** What a media app says is playing. The artist is already cleaned (placeholders become "Unknown Artist"). */
data class TrackMeta(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
)

enum class PlayState { PLAYING, PAUSED, STOPPED }

/**
 * Two clocks: [elapsedMs] is monotonic and used to measure intervals,
 * [epochMs] is wall-clock time and used for timestamps.
 */
data class Clock(val elapsedMs: Long, val epochMs: Long)

/** One finished, countable listen. */
data class ListenResult(
    val sessionId: String,
    val packageName: String,
    val meta: TrackMeta,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val playedMs: Long,
    val durationMs: Long,
    val durationEstimated: Boolean,
    val completionPct: Int,
    val skipped: Boolean,
    val pauseCount: Int,
    val seekCount: Int,
)

/**
 * A listen that is still open. Saved periodically so it can be resumed or recovered
 * if the process is killed.
 */
data class OpenSession(
    val sessionId: String,
    val packageName: String,
    val meta: TrackMeta,
    val startedAtMs: Long,
    val lastActiveAtMs: Long,
    val playedMs: Long,
    val pauseCount: Int,
    val seekCount: Int,
)

/** Live view of one player, for the UI. */
data class NowPlayingSnapshot(
    val packageName: String,
    val meta: TrackMeta,
    val state: PlayState,
    val countedMs: Long,
    val countable: Boolean,
    /** SystemClock.elapsedRealtime() when this was published; lets the UI keep counting between updates. */
    val atElapsedMs: Long = 0L,
    /** The open listen this belongs to, so the UI can tell it apart from finished ones. */
    val sessionId: String? = null,
)
