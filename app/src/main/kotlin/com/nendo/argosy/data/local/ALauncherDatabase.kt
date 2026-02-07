package com.nendo.argosy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nendo.argosy.data.local.converter.Converters
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.AppCategoryDao
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.ControllerMappingDao
import com.nendo.argosy.data.local.dao.ControllerOrderDao
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.dao.HotkeyDao
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.EmulatorUpdateDao
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.OrphanedFileDao
import com.nendo.argosy.data.local.dao.PendingAchievementDao
import com.nendo.argosy.data.local.dao.PendingSaveSyncDao
import com.nendo.argosy.data.local.dao.PendingStateSyncDao
import com.nendo.argosy.data.local.dao.PendingSyncDao
import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.AppCategoryEntity
import com.nendo.argosy.data.local.entity.CheatEntity
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.ControllerMappingEntity
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.EmulatorUpdateEntity
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.OrphanedFileEntity
import com.nendo.argosy.data.local.entity.PendingAchievementEntity
import com.nendo.argosy.data.local.entity.PendingSaveSyncEntity
import com.nendo.argosy.data.local.entity.PendingStateSyncEntity
import com.nendo.argosy.data.local.entity.PendingSyncEntity
import com.nendo.argosy.data.local.entity.PinnedCollectionEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity

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
        AchievementEntity::class,
        SaveCacheEntity::class,
        StateCacheEntity::class,
        OrphanedFileEntity::class,
        AppCategoryEntity::class,
        FirmwareEntity::class,
        CollectionEntity::class,
        CollectionGameEntity::class,
        PinnedCollectionEntity::class,
        GameFileEntity::class,
        CoreVersionEntity::class,
        ControllerOrderEntity::class,
        ControllerMappingEntity::class,
        HotkeyEntity::class,
        CheatEntity::class,
        PendingAchievementEntity::class,
        PendingStateSyncEntity::class,
        PlatformLibretroSettingsEntity::class,
        EmulatorUpdateEntity::class
    ],
    version = 73,
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
    abstract fun saveCacheDao(): SaveCacheDao
    abstract fun stateCacheDao(): StateCacheDao
    abstract fun orphanedFileDao(): OrphanedFileDao
    abstract fun appCategoryDao(): AppCategoryDao
    abstract fun firmwareDao(): FirmwareDao
    abstract fun collectionDao(): CollectionDao
    abstract fun pinnedCollectionDao(): PinnedCollectionDao
    abstract fun gameFileDao(): GameFileDao
    abstract fun coreVersionDao(): CoreVersionDao
    abstract fun controllerOrderDao(): ControllerOrderDao
    abstract fun controllerMappingDao(): ControllerMappingDao
    abstract fun hotkeyDao(): HotkeyDao
    abstract fun cheatDao(): CheatDao
    abstract fun pendingAchievementDao(): PendingAchievementDao
    abstract fun pendingStateSyncDao(): PendingStateSyncDao
    abstract fun platformLibretroSettingsDao(): PlatformLibretroSettingsDao
    abstract fun emulatorUpdateDao(): EmulatorUpdateDao

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

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE achievements ADD COLUMN cachedBadgeUrl TEXT")
                db.execSQL("ALTER TABLE achievements ADD COLUMN cachedBadgeUrlLock TEXT")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_queue ADD COLUMN discNumber INTEGER")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN m3uPath TEXT")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS save_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        emulatorId TEXT NOT NULL,
                        cachedAt INTEGER NOT NULL,
                        saveSize INTEGER NOT NULL,
                        cachePath TEXT NOT NULL,
                        isLocked INTEGER NOT NULL DEFAULT 0,
                        note TEXT
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_save_cache_gameId ON save_cache(gameId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_save_cache_cachedAt ON save_cache(cachedAt)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN activeSaveChannel TEXT")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE save_sync ADD COLUMN channelName TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_save_sync_gameId_emulatorId")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_save_sync_gameId_emulatorId_channelName ON save_sync(gameId, emulatorId, channelName)")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN activeSaveTimestamp INTEGER")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN titleId TEXT")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE platforms ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE platforms ADD COLUMN customRomPath TEXT")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE platforms ADD COLUMN slug TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE platforms SET slug = id")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN platformSlug TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE games SET platformSlug = platformId")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS orphaned_files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        path TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_orphaned_files_path ON orphaned_files(path)")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN status TEXT")
                db.execSQL("""
                    UPDATE games SET status = CASE completion
                        WHEN 1 THEN 'incomplete'
                        WHEN 2 THEN 'finished'
                        WHEN 3 THEN 'completed_100'
                        ELSE NULL
                    END
                    WHERE completion > 0
                """)
                db.execSQL("ALTER TABLE pending_sync ADD COLUMN stringValue TEXT")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN launcherSetManually INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE platforms SET sortOrder = 10 WHERE id = 'steam'")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE games SET platformSlug = 'steam' WHERE platformId = 'steam'")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN packageName TEXT")
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_games_packageName
                    ON games(packageName)
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_category_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        category TEXT,
                        isGame INTEGER NOT NULL,
                        isManualOverride INTEGER NOT NULL DEFAULT 0,
                        fetchedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_app_category_cache_packageName
                    ON app_category_cache(packageName)
                """)
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS state_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        emulatorId TEXT NOT NULL,
                        slotNumber INTEGER NOT NULL,
                        channelName TEXT,
                        cachedAt INTEGER NOT NULL,
                        stateSize INTEGER NOT NULL,
                        cachePath TEXT NOT NULL,
                        coreId TEXT,
                        coreVersion TEXT,
                        isLocked INTEGER NOT NULL DEFAULT 0,
                        note TEXT
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_gameId ON state_cache(gameId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_cachedAt ON state_cache(cachedAt)")
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_state_cache_game_emu_slot_channel
                    ON state_cache(gameId, emulatorId, slotNumber, channelName)
                """)
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE state_cache ADD COLUMN screenshotPath TEXT")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE state_cache ADD COLUMN platformSlug TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS index_state_cache_game_emu_slot_channel")
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_state_cache_game_emu_slot_channel_core
                    ON state_cache(gameId, emulatorId, slotNumber, channelName, coreId)
                """)
                db.execSQL("DELETE FROM state_cache")
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `platforms_new` (`id` INTEGER NOT NULL, `slug` TEXT NOT NULL, `name` TEXT NOT NULL, `shortName` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isVisible` INTEGER NOT NULL, `logoPath` TEXT, `romExtensions` TEXT NOT NULL, `lastScanned` INTEGER, `gameCount` INTEGER NOT NULL, `syncEnabled` INTEGER NOT NULL, `customRomPath` TEXT, PRIMARY KEY(`id`))
                """)

                db.execSQL("""
                    INSERT INTO platforms_new (id, slug, name, shortName, sortOrder, isVisible, logoPath, romExtensions, lastScanned, gameCount, syncEnabled, customRomPath)
                    SELECT
                        CASE
                            WHEN id = 'android' THEN -1
                            WHEN id = 'steam' THEN -2
                            WHEN id = 'ios' THEN -3
                            ELSE CAST(id AS INTEGER)
                        END,
                        slug, name, shortName, sortOrder, isVisible, logoPath, romExtensions, lastScanned, gameCount, syncEnabled, customRomPath
                    FROM platforms
                """)

                db.execSQL("DROP TABLE platforms")
                db.execSQL("ALTER TABLE platforms_new RENAME TO platforms")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `games_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `platformId` INTEGER NOT NULL, `platformSlug` TEXT NOT NULL, `title` TEXT NOT NULL, `sortTitle` TEXT NOT NULL, `localPath` TEXT, `rommId` INTEGER, `igdbId` INTEGER, `steamAppId` INTEGER, `steamLauncher` TEXT, `packageName` TEXT, `launcherSetManually` INTEGER NOT NULL, `source` TEXT NOT NULL, `coverPath` TEXT, `backgroundPath` TEXT, `screenshotPaths` TEXT, `cachedScreenshotPaths` TEXT, `developer` TEXT, `publisher` TEXT, `releaseYear` INTEGER, `genre` TEXT, `description` TEXT, `players` TEXT, `rating` REAL, `regions` TEXT, `languages` TEXT, `gameModes` TEXT, `franchises` TEXT, `userRating` INTEGER NOT NULL, `userDifficulty` INTEGER NOT NULL, `completion` INTEGER NOT NULL, `status` TEXT, `backlogged` INTEGER NOT NULL, `nowPlaying` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `playTimeMinutes` INTEGER NOT NULL, `lastPlayed` INTEGER, `addedAt` INTEGER NOT NULL, `isMultiDisc` INTEGER NOT NULL, `lastPlayedDiscId` INTEGER, `m3uPath` TEXT, `achievementCount` INTEGER NOT NULL, `earnedAchievementCount` INTEGER NOT NULL, `activeSaveChannel` TEXT, `activeSaveTimestamp` INTEGER, `titleId` TEXT, FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
                """)

                db.execSQL("""
                    INSERT INTO games_new (
                        id, platformId, platformSlug, title, sortTitle, localPath, rommId, igdbId,
                        steamAppId, steamLauncher, packageName, launcherSetManually, source, coverPath,
                        backgroundPath, screenshotPaths, cachedScreenshotPaths, developer, publisher,
                        releaseYear, genre, description, players, rating, regions, languages, gameModes,
                        franchises, userRating, userDifficulty, completion, status, backlogged, nowPlaying,
                        isFavorite, isHidden, playCount, playTimeMinutes, lastPlayed, addedAt, isMultiDisc,
                        lastPlayedDiscId, m3uPath, achievementCount, earnedAchievementCount,
                        activeSaveChannel, activeSaveTimestamp, titleId
                    )
                    SELECT
                        id,
                        CASE
                            WHEN platformId = 'android' THEN -1
                            WHEN platformId = 'steam' THEN -2
                            WHEN platformId = 'ios' THEN -3
                            ELSE CAST(platformId AS INTEGER)
                        END,
                        platformSlug, title, sortTitle, localPath, rommId, igdbId, steamAppId, steamLauncher,
                        packageName, launcherSetManually, source, coverPath, backgroundPath, screenshotPaths,
                        cachedScreenshotPaths, developer, publisher, releaseYear, genre, description, players,
                        rating, regions, languages, gameModes, franchises, userRating, userDifficulty,
                        completion, status, backlogged, nowPlaying, isFavorite, isHidden, playCount,
                        playTimeMinutes, lastPlayed, addedAt, isMultiDisc, lastPlayedDiscId, m3uPath,
                        achievementCount, earnedAchievementCount, activeSaveChannel, activeSaveTimestamp, titleId
                    FROM games
                """)

                db.execSQL("DROP TABLE games")
                db.execSQL("ALTER TABLE games_new RENAME TO games")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_platformId` ON `games` (`platformId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_title` ON `games` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_lastPlayed` ON `games` (`lastPlayed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_source` ON `games` (`source`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_rommId` ON `games` (`rommId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_steamAppId` ON `games` (`steamAppId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_games_packageName` ON `games` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_regions` ON `games` (`regions`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_gameModes` ON `games` (`gameModes`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_franchises` ON `games` (`franchises`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `emulator_configs_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `platformId` INTEGER, `gameId` INTEGER, `packageName` TEXT, `displayName` TEXT, `coreName` TEXT, `isDefault` INTEGER NOT NULL, FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
                """)

                db.execSQL("""
                    INSERT INTO emulator_configs_new (id, platformId, gameId, packageName, displayName, coreName, isDefault)
                    SELECT
                        id,
                        CASE
                            WHEN platformId = 'android' THEN -1
                            WHEN platformId = 'steam' THEN -2
                            WHEN platformId = 'ios' THEN -3
                            WHEN platformId IS NULL THEN NULL
                            ELSE CAST(platformId AS INTEGER)
                        END,
                        gameId, packageName, displayName, coreName, isDefault
                    FROM emulator_configs
                """)

                db.execSQL("DROP TABLE emulator_configs")
                db.execSQL("ALTER TABLE emulator_configs_new RENAME TO emulator_configs")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_emulator_configs_platformId` ON `emulator_configs` (`platformId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_emulator_configs_gameId` ON `emulator_configs` (`gameId`)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emulator_configs ADD COLUMN preferredExtension TEXT")
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE game_discs ADD COLUMN parentRommId INTEGER")
            }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS firmware (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        platformId INTEGER NOT NULL,
                        platformSlug TEXT NOT NULL,
                        rommId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        md5Hash TEXT,
                        sha1Hash TEXT,
                        localPath TEXT,
                        downloadedAt INTEGER,
                        lastVerifiedAt INTEGER,
                        FOREIGN KEY (platformId) REFERENCES platforms(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_firmware_platformId_fileName ON firmware(platformId, fileName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_firmware_platformSlug ON firmware(platformSlug)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_firmware_rommId ON firmware(rommId)")
            }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        rommId INTEGER,
                        name TEXT NOT NULL,
                        description TEXT,
                        isUserCreated INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_rommId ON collections(rommId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collections_name ON collections(name)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collection_games (
                        collectionId INTEGER NOT NULL,
                        gameId INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY (collectionId, gameId),
                        FOREIGN KEY (collectionId) REFERENCES collections(id) ON DELETE CASCADE,
                        FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_games_collectionId ON collection_games(collectionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_games_gameId ON collection_games(gameId)")
            }
        }

        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pinned_collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        collectionId INTEGER,
                        virtualType TEXT,
                        virtualName TEXT,
                        displayOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pinned_collections_displayOrder ON pinned_collections(displayOrder)")
            }
        }

        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN type TEXT NOT NULL DEFAULT 'REGULAR'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collections_type ON collections(type)")
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        rommFileId INTEGER NOT NULL,
                        romId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        category TEXT NOT NULL,
                        fileSize INTEGER NOT NULL,
                        localPath TEXT,
                        downloadedAt INTEGER,
                        FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_game_files_gameId ON game_files(gameId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_game_files_rommFileId ON game_files(rommFileId)")

                db.execSQL("ALTER TABLE download_queue ADD COLUMN gameFileId INTEGER")
                db.execSQL("ALTER TABLE download_queue ADD COLUMN fileCategory TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_download_queue_gameFileId ON download_queue(gameFileId)")
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN youtubeVideoId TEXT")
            }
        }

        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN gradientColors TEXT")
            }
        }

        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_queue ADD COLUMN isMultiFileRom INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN titleIdCandidates TEXT")
            }
        }

        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE save_cache ADD COLUMN contentHash TEXT")
                db.execSQL("ALTER TABLE save_sync ADD COLUMN lastUploadedHash TEXT")
            }
        }

        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS core_versions (
                        coreId TEXT PRIMARY KEY NOT NULL,
                        installedVersion TEXT,
                        latestVersion TEXT,
                        installedAt INTEGER,
                        lastCheckedAt INTEGER,
                        updateAvailable INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS controller_order (
                        port INTEGER PRIMARY KEY NOT NULL,
                        controllerId TEXT NOT NULL,
                        controllerName TEXT NOT NULL,
                        assignedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_controller_order_controllerId ON controller_order(controllerId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS controller_mappings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        controllerId TEXT NOT NULL,
                        controllerName TEXT NOT NULL,
                        vendorId INTEGER NOT NULL,
                        productId INTEGER NOT NULL,
                        mappingJson TEXT NOT NULL,
                        presetName TEXT,
                        isAutoDetected INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_controller_mappings_controllerId ON controller_mappings(controllerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_controller_mappings_vendorProduct ON controller_mappings(vendorId, productId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS hotkeys (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        action TEXT NOT NULL,
                        buttonComboJson TEXT NOT NULL,
                        controllerId TEXT,
                        isEnabled INTEGER NOT NULL DEFAULT 1
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hotkeys_action ON hotkeys(action)")
            }
        }

        val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cheats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        cheatIndex INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        code TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cheats_gameId ON cheats(gameId)")

                db.execSQL("ALTER TABLE games ADD COLUMN cheatsFetched INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cheats ADD COLUMN isUserCreated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cheats ADD COLUMN lastUsedAt INTEGER")
            }
        }

        val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_achievements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        achievementRaId INTEGER NOT NULL,
                        forHardcoreMode INTEGER NOT NULL,
                        earnedAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_achievements_gameId ON pending_achievements(gameId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_achievements_createdAt ON pending_achievements(createdAt)")

                db.execSQL("ALTER TABLE save_cache ADD COLUMN cheatsUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE save_cache ADD COLUMN isHardcore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE save_cache ADD COLUMN slotName TEXT")
            }
        }

        val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE achievements ADD COLUMN unlockedAt INTEGER")
                db.execSQL("ALTER TABLE achievements ADD COLUMN unlockedHardcoreAt INTEGER")
            }
        }

        val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN raId INTEGER")
            }
        }

        val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("""
                    CREATE TABLE achievements_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL,
                        raId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        points INTEGER NOT NULL,
                        type TEXT,
                        badgeUrl TEXT,
                        badgeUrlLock TEXT,
                        cachedBadgeUrl TEXT,
                        cachedBadgeUrlLock TEXT,
                        unlockedAt INTEGER,
                        unlockedHardcoreAt INTEGER,
                        FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    INSERT INTO achievements_new (id, gameId, raId, title, description, points, type, badgeUrl, badgeUrlLock, cachedBadgeUrl, cachedBadgeUrlLock, unlockedAt, unlockedHardcoreAt)
                    SELECT id, gameId, raId, title, description, points, type, badgeUrl, badgeUrlLock, cachedBadgeUrl, cachedBadgeUrlLock, unlockedAt, unlockedHardcoreAt
                    FROM achievements
                """)
                db.execSQL("DROP TABLE achievements")
                db.execSQL("ALTER TABLE achievements_new RENAME TO achievements")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_achievements_gameId ON achievements(gameId)")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN achievementsFetchedAt INTEGER")
            }
        }

        val MIGRATION_58_59 = object : Migration(58, 59) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN romHash TEXT")
            }
        }

        val MIGRATION_59_60 = object : Migration(59, 60) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE state_cache ADD COLUMN rommSaveId INTEGER")
                db.execSQL("ALTER TABLE state_cache ADD COLUMN syncStatus TEXT")
                db.execSQL("ALTER TABLE state_cache ADD COLUMN serverUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE state_cache ADD COLUMN lastUploadedHash TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_rommSaveId ON state_cache(rommSaveId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_syncStatus ON state_cache(syncStatus)")
            }
        }

        val MIGRATION_60_61 = object : Migration(60, 61) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_state_sync (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        stateCacheId INTEGER NOT NULL,
                        gameId INTEGER NOT NULL,
                        rommId INTEGER NOT NULL,
                        emulatorId TEXT NOT NULL,
                        action TEXT NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_state_sync_stateCacheId ON pending_state_sync(stateCacheId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_state_sync_gameId ON pending_state_sync(gameId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_state_sync_createdAt ON pending_state_sync(createdAt)")
            }
        }

        val MIGRATION_61_62 = object : Migration(61, 62) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_gameId_emulatorId ON state_cache(gameId, emulatorId)")
            }
        }

        val MIGRATION_62_63 = object : Migration(62, 63) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM emulator_configs
                    WHERE gameId IN (SELECT id FROM games WHERE platformSlug = 'scummvm')
                    AND packageName = 'argosy.builtin.libretro'
                """)
                db.execSQL("""
                    DELETE FROM emulator_configs
                    WHERE platformId IN (SELECT id FROM platforms WHERE slug = 'scummvm')
                    AND packageName = 'argosy.builtin.libretro'
                """)
            }
        }

        val MIGRATION_63_64 = object : Migration(63, 64) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN fileSizeBytes INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_64_65 = object : Migration(64, 65) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN titleIdLocked INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_65_66 = object : Migration(65, 66) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN rommFileName TEXT")
            }
        }

        val MIGRATION_66_67 = object : Migration(66, 67) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN activeSaveApplied INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE save_cache ADD COLUMN isRollback INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_67_68 = object : Migration(67, 68) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS platform_libretro_settings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        platformId INTEGER NOT NULL,
                        shader TEXT,
                        filter TEXT,
                        aspectRatio TEXT,
                        rotation INTEGER,
                        overscanCrop INTEGER,
                        blackFrameInsertion INTEGER,
                        fastForwardSpeed INTEGER,
                        rewindEnabled INTEGER,
                        skipDuplicateFrames INTEGER,
                        lowLatencyAudio INTEGER,
                        FOREIGN KEY (platformId) REFERENCES platforms(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_platform_libretro_settings_platformId ON platform_libretro_settings(platformId)")
            }
        }

        val MIGRATION_68_69 = object : Migration(68, 69) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN analogAsDpad INTEGER")
                db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN dpadAsAnalog INTEGER")
                db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN rumbleEnabled INTEGER")
            }
        }

        val MIGRATION_69_70 = object : Migration(69, 70) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE controller_mappings ADD COLUMN platformId TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_controller_mappings_controllerPlatform ON controller_mappings(controllerId, platformId)")
            }
        }

        val MIGRATION_70_71 = object : Migration(70, 71) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN shaderChain TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_71_72 = object : Migration(71, 72) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN frame TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_72_73 = object : Migration(72, 73) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS emulator_updates (
                        emulatorId TEXT PRIMARY KEY NOT NULL,
                        latestVersion TEXT NOT NULL,
                        installedVersion TEXT,
                        downloadUrl TEXT NOT NULL,
                        assetName TEXT NOT NULL,
                        assetSize INTEGER NOT NULL,
                        checkedAt INTEGER NOT NULL,
                        installedVariant TEXT,
                        hasUpdate INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }
    }
}
