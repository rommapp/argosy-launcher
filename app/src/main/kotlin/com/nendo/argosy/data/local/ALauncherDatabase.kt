package com.nendo.argosy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nendo.argosy.data.local.converter.Converters
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncEntity
import com.nendo.argosy.data.local.entity.PlatformEntity

@Database(
    entities = [
        PlatformEntity::class,
        GameEntity::class,
        EmulatorConfigEntity::class,
        PendingSyncEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ALauncherDatabase : RoomDatabase() {
    abstract fun platformDao(): PlatformDao
    abstract fun gameDao(): GameDao
    abstract fun emulatorConfigDao(): EmulatorConfigDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN igdbId INTEGER")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_games_igdbId
                    ON games(igdbId) WHERE igdbId IS NOT NULL
                    """
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_games_igdbId")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_games_igdbId_platformId
                    ON games(igdbId, platformId) WHERE igdbId IS NOT NULL
                    """
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_games_igdbId_platformId")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_games_rommId
                    ON games(rommId) WHERE rommId IS NOT NULL
                    """
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN regions TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN languages TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN gameModes TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN franchises TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_regions ON games(regions)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_gameModes ON games(gameModes)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_franchises ON games(franchises)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN cachedScreenshotPaths TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_sync (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        rommId INTEGER NOT NULL,
                        syncType TEXT NOT NULL,
                        value INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
