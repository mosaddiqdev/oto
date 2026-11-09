package com.oto.stats.data

import com.oto.stats.tracking.engine.ArtistParser
import com.oto.stats.tracking.engine.ListenResult
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ListeningRepository(private val db: OtoDatabase) {
    private val dao = db.listeningDao()

    /** Saves a finished listen. Returns false if it was a duplicate. */
    suspend fun record(result: ListenResult): Boolean {
        val artists = ArtistParser.parse(result.meta.artist)
        val tzOffsetMinutes = TimeZone.getDefault().getOffset(result.startedAtMs) / 60_000
        return dao.recordListen(result, artists, tzOffsetMinutes)
    }

    fun recentEvents(limit: Int = 50): Flow<List<EventRow>> = dao.recentEvents(limit)

    fun totals(): Flow<Totals> = dao.totals()

    suspend fun clearAll() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
