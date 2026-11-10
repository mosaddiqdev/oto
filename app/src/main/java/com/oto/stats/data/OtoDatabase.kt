package com.oto.stats.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Bump this only together with a migration in [Migrations] and the new schema file that Room
 * writes to app/schemas (commit it). A unit test and CI both fail if either is missing.
 */
const val OTO_DB_VERSION = 1

@Database(
    entities = [
        ArtistEntity::class,
        TrackEntity::class,
        TrackArtistEntity::class,
        ListeningEventEntity::class,
    ],
    version = OTO_DB_VERSION,
    exportSchema = true,
)
abstract class OtoDatabase : RoomDatabase() {
    abstract fun listeningDao(): ListeningDao

    companion object {
        /**
         * Never add fallbackToDestructiveMigration() here: it would silently delete the user's
         * listening history on a schema change. Write a migration instead.
         */
        fun build(context: Context): OtoDatabase =
            Room.databaseBuilder(context.applicationContext, OtoDatabase::class.java, "oto.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
