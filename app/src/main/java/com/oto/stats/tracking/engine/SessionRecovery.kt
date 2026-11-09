package com.oto.stats.tracking.engine

/** Turns a leftover open session (from a killed process) into a finished listen, if it still counts. */
object SessionRecovery {
    /** Sessions older than this are discarded rather than recovered. */
    const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun toResult(open: OpenSession, nowEpochMs: Long, rules: SessionRules = SessionRules()): ListenResult? {
        if (nowEpochMs - open.lastActiveAtMs > MAX_AGE_MS) return null
        if (!ContentFilter.isCountable(open.meta, open.packageName, rules)) return null
        return buildListenResult(
            sessionId = open.sessionId,
            packageName = open.packageName,
            meta = open.meta,
            startedAtMs = open.startedAtMs,
            endedAtMs = open.lastActiveAtMs,
            playedMs = open.playedMs,
            pauseCount = open.pauseCount,
            seekCount = open.seekCount,
            rules = rules,
        )
    }
}
