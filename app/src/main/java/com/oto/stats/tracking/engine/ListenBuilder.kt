package com.oto.stats.tracking.engine

/**
 * Applies the counting rules to a finished session.
 * Returns null when the listen is too short to count.
 */
internal fun buildListenResult(
    sessionId: String,
    packageName: String,
    meta: TrackMeta,
    startedAtMs: Long,
    endedAtMs: Long,
    playedMs: Long,
    pauseCount: Int,
    seekCount: Int,
    rules: SessionRules,
): ListenResult? {
    val known = meta.durationMs?.takeIf { it > 0 }
    val duration = known ?: rules.fallbackDurationMs
    val played = minOf(playedMs, (duration * rules.maxPlayedMultiple).toLong())
    if (played < rules.requiredPlayMs(known)) return null

    val completion = ((played * 100) / duration).coerceIn(0L, 100L).toInt()
    val skipped = played.toDouble() < duration * rules.skipBelowFraction

    return ListenResult(
        sessionId = sessionId,
        packageName = packageName,
        meta = meta,
        startedAtMs = startedAtMs,
        endedAtMs = maxOf(endedAtMs, startedAtMs),
        playedMs = played,
        durationMs = duration,
        durationEstimated = known == null,
        completionPct = completion,
        skipped = skipped,
        pauseCount = pauseCount,
        seekCount = seekCount,
    )
}
