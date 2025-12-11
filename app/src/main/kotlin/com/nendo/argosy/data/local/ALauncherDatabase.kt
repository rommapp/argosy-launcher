package com.nendo.argosy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nendo.argosy.data.local.converter.Converters
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.PendingSaveSyncDao
import com.nendo.argosy.data.local.dao.PendingSyncDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSaveSyncEntity
import com.nendo.argosy.data.local.entity.PendingSyncEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity

@Database(
    entities = [
        PlatformEntity::class,
        GameEntity::class,
        EmulatorConfigEntity::class,
        PendingSyncEntity::class,
        DownloadQueueEntity::class,
        SaveSyncEntity::class,
        PendingSaveSyncEntity::class,
        EmulatorSaveConfigEntity::class,
        GameDiscEntity::class,
        AchievementEntity::class
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ALauncherDatabase : RoomDatabase() {
    abstract fun platformDao(): PlatformDao
    abstract fun gameDao(): GameDao
    abstract fun gameDiscDao(): GameDiscDao
    abstract fun emulatorConfigDao(): EmulatorConfigDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun saveSyncDao(): SaveSyncDao
    abstract fun pendingSaveSyncDao(): PendingSaveSyncDao
    abstract fun emulatorSaveConfigDao(): EmulatorSaveConfigDao
    abstract fun achievementDao(): AchievementDao

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS download_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        rommId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        gameTitle TEXT NOT NULL,
                        platformSlug TEXT NOT NULL,
                        coverPath TEXT,
                        bytesDownloaded INTEGER NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        errorReason TEXT,
                        tempFilePath TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_download_queue_gameId ON download_queue(gameId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_download_queue_state ON download_queue(state)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN completion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN backlogged INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN nowPlaying INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN steamAppId INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN steamLauncher TEXT")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_games_steamAppId
                    ON games(steamAppId) WHERE steamAppId IS NOT NULL
                    """
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS save_sync (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        rommId INTEGER NOT NULL,
                        emulatorId TEXT NOT NULL,
                        rommSaveId INTEGER,
                        localSavePath TEXT,
                        localUpdatedAt INTEGER,
                        serverUpdatedAt INTEGER,
                        lastSyncedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_save_sync_gameId_emulatorId ON save_sync(gameId, emulatorId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_save_sync_rommSaveId ON save_sync(rommSaveId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_save_sync_lastSyncedAt ON save_sync(lastSyncedAt)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_save_sync (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        rommId INTEGER NOT NULL,
                        emulatorId TEXT NOT NULL,
                        localSavePath TEXT NOT NULL,
                        action TEXT NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_save_sync_gameId ON pending_save_sync(gameId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_save_sync_createdAt ON pending_save_sync(createdAt)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS emulator_save_config (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        emulatorId TEXT NOT NULL,
                        savePathPattern TEXT NOT NULL,
                        isAutoDetected INTEGER NOT NULL,
                        isUserOverride INTEGER NOT NULL DEFAULT 0,
                        lastVerifiedAt INTEGER
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_emulator_save_config_emulatorId ON emulator_save_config(emulatorId)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN isMultiDisc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN lastPlayedDiscId INTEGER")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_discs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        discNumber INTEGER NOT NULL,
                        rommId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        localPath TEXT,
                        fileSize INTEGER NOT NULL,
                        FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_game_discs_gameId ON game_discs(gameId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_game_discs_rommId ON game_discs(rommId)")

                db.execSQL("ALTER TABLE download_queue ADD COLUMN discId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_download_queue_discId ON download_queue(discId)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS emulator_configs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        platformId TEXT,
                        gameId INTEGER,
                        packageName TEXT,
                        displayName TEXT,
                        coreName TEXT,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (platformId) REFERENCES platforms(id) ON DELETE CASCADE,
                        FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    INSERT INTO emulator_configs_new (id, platformId, gameId, packageName, displayName, coreName, isDefault)
                    SELECT id, platformId, gameId, packageName, displayName, coreName, isDefault FROM emulator_configs
                """)
                db.execSQL("DROP TABLE emulator_configs")
                db.execSQL("ALTER TABLE emulator_configs_new RENAME TO emulator_configs")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_emulator_configs_platformId ON emulator_configs(platformId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_emulator_configs_gameId ON emulator_configs(gameId)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN achievementCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        raId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        points INTEGER NOT NULL,
                        type TEXT,
                        badgeUrl TEXT,
                        badgeUrlLock TEXT,
                        isUnlocked INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_achievements_gameId ON achievements(gameId)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN earnedAchievementCount INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
