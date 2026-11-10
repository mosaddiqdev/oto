package com.oto.stats.data

import androidx.room.migration.Migration

/**
 * One entry per schema change, from version N to N + 1. Example:
 *
 *     val MIGRATION_1_2 = object : Migration(1, 2) {
 *         override fun migrate(db: SupportSQLiteDatabase) {
 *             db.execSQL("ALTER TABLE tracks ADD COLUMN artworkPath TEXT")
 *         }
 *     }
 *
 * Add it to [ALL]. Simple additions can use Room's @AutoMigration instead.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
