package com.example.metrotune.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PracticeDayEntity::class, PracticeSessionEntity::class,
        DrillRecordEntity::class, StringHealthEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun practiceDao(): PracticeDao
    abstract fun drillDao(): DrillDao
    abstract fun healthDao(): HealthDao
}

/**
 * Schema migrations. The legacy prefs → Room import is a DATA migration and
 * lives in PrefsImporter. Future schema changes bump the version above and
 * add a Migration here, e.g.:
 *
 *   val MIGRATION_2_3 = object : Migration(2, 3) {
 *       override fun migrate(db: SupportSQLiteDatabase) { … }
 *   }
 */
object AppMigrations {

    /** v2: additive — string_health table (per-instrument, per-pitch history). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `string_health` (" +
                    "`instrumentId` TEXT NOT NULL, " +
                    "`midi` INTEGER NOT NULL, " +
                    "`count` INTEGER NOT NULL, " +
                    "`avgCents` REAL NOT NULL, " +
                    "`lastCents` REAL NOT NULL, " +
                    "`lastAtMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`instrumentId`, `midi`))")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
