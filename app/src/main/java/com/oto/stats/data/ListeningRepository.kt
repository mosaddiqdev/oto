package com.oto.stats.data

import com.oto.stats.diag.DiagLog
import com.oto.stats.tracking.engine.ArtistParser
import com.oto.stats.tracking.engine.ListenResult
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListeningRepository(
    private val db: OtoDatabase,
    scope: CoroutineScope,
) {
    private val dao = db.listeningDao()

    /**
     * Listens are written one at a time, in the order they were submitted, so an early
     * progress update can never overwrite a later final result.
     */
    private val queue = Channel<ListenResult>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (result in queue) {
                try {
                    if (save(result)) {
                        DiagLog.log(
                            "db",
                            "Saved listen: ${result.meta.title} · ${formatMs(result.playedMs)} (${result.completionPct}%)",
                        )
                    }
                } catch (e: Exception) {
                    DiagLog.log("db", "Save failed: ${e.javaClass.simpleName} ${e.message?.take(100)}")
                }
            }
        }
    }

    /** Queues a listen (new, in progress, or final). Safe to call from any thread. */
    fun submit(result: ListenResult) {
        queue.trySend(result)
    }

    private suspend fun save(result: ListenResult): Boolean {
        val artists = ArtistParser.parse(result.meta.artist)
        val tzOffsetMinutes = TimeZone.getDefault().getOffset(result.startedAtMs) / 60_000
        return dao.recordListen(result, artists, tzOffsetMinutes)
    }

    fun recentEvents(limit: Int = 50): Flow<List<EventRow>> = dao.recentEvents(limit)

    fun totals(): Flow<Totals> = dao.totals()

    suspend fun clearAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}
