package com.oto.stats.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ArtistEntity::class,
        TrackEntity::class,
        TrackArtistEntity::class,
        ListeningEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class OtoDatabase : RoomDatabase() {
    abstract fun listeningDao(): ListeningDao

    companion object {
        fun build(context: Context): OtoDatabase =
            Room.databaseBuilder(context.applicationContext, OtoDatabase::class.java, "oto.db").build()
    }
}
