package com.nendo.argosy.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nendo.argosy.util.SearchNormalizer

object Migration_1_2 : Migration(1, 2) {
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

object Migration_2_3 : Migration(2, 3) {
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

object Migration_3_4 : Migration(3, 4) {
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

object Migration_4_5 : Migration(4, 5) {
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

object Migration_5_6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN cachedScreenshotPaths TEXT")
    }
}

object Migration_6_7 : Migration(6, 7) {
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

object Migration_7_8 : Migration(7, 8) {
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

object Migration_8_9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN completion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE games ADD COLUMN backlogged INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE games ADD COLUMN nowPlaying INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_9_10 : Migration(9, 10) {
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

object Migration_10_11 : Migration(10, 11) {
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

object Migration_11_12 : Migration(11, 12) {
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

object Migration_12_13 : Migration(12, 13) {
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

object Migration_13_14 : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN achievementCount INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_14_15 : Migration(14, 15) {
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

object Migration_15_16 : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN earnedAchievementCount INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_16_17 : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE achievements ADD COLUMN cachedBadgeUrl TEXT")
        db.execSQL("ALTER TABLE achievements ADD COLUMN cachedBadgeUrlLock TEXT")
    }
}

object Migration_17_18 : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_queue ADD COLUMN discNumber INTEGER")
    }
}

object Migration_18_19 : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN m3uPath TEXT")
    }
}

object Migration_19_20 : Migration(19, 20) {
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

object Migration_20_21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN activeSaveChannel TEXT")
    }
}

object Migration_21_22 : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_sync ADD COLUMN channelName TEXT")
        db.execSQL("DROP INDEX IF EXISTS index_save_sync_gameId_emulatorId")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_save_sync_gameId_emulatorId_channelName ON save_sync(gameId, emulatorId, channelName)")
    }
}

object Migration_22_23 : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN activeSaveTimestamp INTEGER")
    }
}

object Migration_23_24 : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN titleId TEXT")
    }
}

object Migration_24_25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platforms ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE platforms ADD COLUMN customRomPath TEXT")
    }
}

object Migration_25_26 : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platforms ADD COLUMN slug TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE platforms SET slug = id")
    }
}

object Migration_26_27 : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN platformSlug TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE games SET platformSlug = platformId")
    }
}

object Migration_27_28 : Migration(27, 28) {
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

object Migration_28_29 : Migration(28, 29) {
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

object Migration_29_30 : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN launcherSetManually INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_30_31 : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE platforms SET sortOrder = 10 WHERE id = 'steam'")
    }
}

object Migration_31_32 : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE games SET platformSlug = 'steam' WHERE platformId = 'steam'")
    }
}

object Migration_32_33 : Migration(32, 33) {
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

object Migration_33_34 : Migration(33, 34) {
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

object Migration_34_35 : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE state_cache ADD COLUMN screenshotPath TEXT")
    }
}

object Migration_35_36 : Migration(35, 36) {
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

object Migration_36_37 : Migration(36, 37) {
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

object Migration_37_38 : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_configs ADD COLUMN preferredExtension TEXT")
    }
}

object Migration_38_39 : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_discs ADD COLUMN parentRommId INTEGER")
    }
}

object Migration_39_40 : Migration(39, 40) {
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

object Migration_40_41 : Migration(40, 41) {
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

object Migration_41_42 : Migration(41, 42) {
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

object Migration_42_43 : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE collections ADD COLUMN type TEXT NOT NULL DEFAULT 'REGULAR'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_collections_type ON collections(type)")
    }
}

object Migration_43_44 : Migration(43, 44) {
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

object Migration_44_45 : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN youtubeVideoId TEXT")
    }
}

object Migration_45_46 : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN gradientColors TEXT")
    }
}

object Migration_46_47 : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_queue ADD COLUMN isMultiFileRom INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_47_48 : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN titleIdCandidates TEXT")
    }
}

object Migration_48_49 : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_cache ADD COLUMN contentHash TEXT")
        db.execSQL("ALTER TABLE save_sync ADD COLUMN lastUploadedHash TEXT")
    }
}

object Migration_49_50 : Migration(49, 50) {
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

object Migration_50_51 : Migration(50, 51) {
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

object Migration_51_52 : Migration(51, 52) {
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

object Migration_52_53 : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cheats ADD COLUMN isUserCreated INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cheats ADD COLUMN lastUsedAt INTEGER")
    }
}

object Migration_53_54 : Migration(53, 54) {
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

object Migration_54_55 : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE achievements ADD COLUMN unlockedAt INTEGER")
        db.execSQL("ALTER TABLE achievements ADD COLUMN unlockedHardcoreAt INTEGER")
    }
}

object Migration_55_56 : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN raId INTEGER")
    }
}

object Migration_56_57 : Migration(56, 57) {
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

object Migration_57_58 : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN achievementsFetchedAt INTEGER")
    }
}

object Migration_58_59 : Migration(58, 59) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN romHash TEXT")
    }
}

object Migration_59_60 : Migration(59, 60) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE state_cache ADD COLUMN rommSaveId INTEGER")
        db.execSQL("ALTER TABLE state_cache ADD COLUMN syncStatus TEXT")
        db.execSQL("ALTER TABLE state_cache ADD COLUMN serverUpdatedAt INTEGER")
        db.execSQL("ALTER TABLE state_cache ADD COLUMN lastUploadedHash TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_rommSaveId ON state_cache(rommSaveId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_syncStatus ON state_cache(syncStatus)")
    }
}

object Migration_60_61 : Migration(60, 61) {
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

object Migration_61_62 : Migration(61, 62) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_state_cache_gameId_emulatorId ON state_cache(gameId, emulatorId)")
    }
}

object Migration_62_63 : Migration(62, 63) {
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

object Migration_63_64 : Migration(63, 64) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN fileSizeBytes INTEGER DEFAULT NULL")
    }
}

object Migration_64_65 : Migration(64, 65) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN titleIdLocked INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_65_66 : Migration(65, 66) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN rommFileName TEXT")
    }
}

object Migration_66_67 : Migration(66, 67) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN activeSaveApplied INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE save_cache ADD COLUMN isRollback INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_67_68 : Migration(67, 68) {
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

object Migration_68_69 : Migration(68, 69) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN analogAsDpad INTEGER")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN dpadAsAnalog INTEGER")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN rumbleEnabled INTEGER")
    }
}

object Migration_69_70 : Migration(69, 70) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE controller_mappings ADD COLUMN platformId TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_controller_mappings_controllerPlatform ON controller_mappings(controllerId, platformId)")
    }
}

object Migration_70_71 : Migration(70, 71) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN shaderChain TEXT DEFAULT NULL")
    }
}

object Migration_71_72 : Migration(71, 72) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN frame TEXT DEFAULT NULL")
    }
}

object Migration_72_73 : Migration(72, 73) {
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

object Migration_73_74 : Migration(73, 74) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platforms ADD COLUMN fsSlug TEXT DEFAULT NULL")
    }
}

object Migration_74_75 : Migration(74, 75) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_cache ADD COLUMN needsRemoteSync INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE save_cache ADD COLUMN lastSyncedAt INTEGER")
        db.execSQL("ALTER TABLE save_cache ADD COLUMN remoteSyncError TEXT")
        db.execSQL("ALTER TABLE save_cache ADD COLUMN channelName TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_save_cache_needsRemoteSync ON save_cache(needsRemoteSync)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                gameId INTEGER NOT NULL,
                rommId INTEGER NOT NULL,
                syncType TEXT NOT NULL,
                priority INTEGER NOT NULL,
                payloadJson TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                retryCount INTEGER NOT NULL DEFAULT 0,
                maxRetries INTEGER NOT NULL DEFAULT 3,
                lastError TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_queue_priority_createdAt ON pending_sync_queue(priority, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_queue_gameId ON pending_sync_queue(gameId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_queue_status ON pending_sync_queue(status)")

        db.execSQL("DROP TABLE IF EXISTS pending_sync")
        db.execSQL("DROP TABLE IF EXISTS pending_save_sync")
        db.execSQL("DROP TABLE IF EXISTS pending_state_sync")
        db.execSQL("DROP TABLE IF EXISTS pending_achievements")
    }
}

object Migration_75_76 : Migration(75, 76) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_cache ADD COLUMN rommSaveId INTEGER")
    }
}

object Migration_76_77 : Migration(76, 77) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN pendingDeviceSyncSaveId INTEGER")
    }
}

object Migration_77_78 : Migration(77, 78) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            UPDATE save_cache
            SET channelName = note
            WHERE channelName IS NULL AND note IS NOT NULL AND isLocked = 1
        """)
    }
}

object Migration_78_79 : Migration(78, 79) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_configs ADD COLUMN useFileUri INTEGER")
    }
}

object Migration_79_80 : Migration(79, 80) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_configs ADD COLUMN displayTarget TEXT")
    }
}

object Migration_80_81 : Migration(80, 81) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN syncDirty INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_81_82 : Migration(81, 82) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS play_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                userId TEXT,
                gameId INTEGER NOT NULL,
                igdbId INTEGER,
                gameTitle TEXT NOT NULL,
                platformSlug TEXT NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                continued INTEGER NOT NULL DEFAULT 0,
                deviceId TEXT NOT NULL,
                deviceManufacturer TEXT NOT NULL,
                deviceModel TEXT NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_play_sessions_gameId ON play_sessions(gameId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_play_sessions_igdbId ON play_sessions(igdbId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_play_sessions_startTime ON play_sessions(startTime)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_play_sessions_deviceId ON play_sessions(deviceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_play_sessions_userId ON play_sessions(userId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS social_game_cache (
                igdbId INTEGER PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                coverUrl TEXT,
                platformSlug TEXT,
                releaseYear INTEGER,
                fetchedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_social_game_cache_fetchedAt ON social_game_cache(fetchedAt)")
    }
}

object Migration_82_83 : Migration(82, 83) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE achievements ADD COLUMN socialSharedAt INTEGER")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_achievements_gameId_raId ON achievements(gameId, raId)")
        val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        db.execSQL("""
            UPDATE achievements SET socialSharedAt = $cutoff
            WHERE (unlockedAt IS NOT NULL AND unlockedAt < $cutoff)
               OR (unlockedHardcoreAt IS NOT NULL AND unlockedHardcoreAt < $cutoff)
        """)
    }
}

object Migration_83_84 : Migration(83, 84) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN verifiedRaId INTEGER")
        db.execSQL("ALTER TABLE games ADD COLUMN raIdVerified INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_84_85 : Migration(84, 85) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE games SET gradientColors = NULL")
    }
}

object Migration_85_86 : Migration(85, 86) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE play_sessions ADD COLUMN activePlayMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE play_sessions ADD COLUMN standbyMs INTEGER NOT NULL DEFAULT 0")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_social_sync (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                syncType TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                occurredAt INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                retryCount INTEGER NOT NULL DEFAULT 0,
                maxRetries INTEGER NOT NULL DEFAULT 5,
                lastError TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_social_sync_status ON pending_social_sync(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_social_sync_syncType ON pending_social_sync(syncType)")
    }
}

object Migration_86_87 : Migration(86, 87) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS core_option_overrides (
                coreId TEXT NOT NULL,
                optionKey TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY (coreId, optionKey)
            )
            """
        )
    }
}

object Migration_87_88 : Migration(87, 88) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN cheatsFetchedAt INTEGER")
    }
}

object Migration_88_89 : Migration(88, 89) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN rewindSpeed INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN rewindBufferDuration INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN vsync INTEGER DEFAULT NULL")
    }
}

object Migration_89_90 : Migration(89, 90) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cheats ADD COLUMN variantRegion TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE cheats ADD COLUMN variantVersion TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN cheatsSelectedRegion TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN cheatsSelectedVersion TEXT DEFAULT NULL")
    }
}

object Migration_90_91 : Migration(90, 91) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN fastForwardEnabled INTEGER DEFAULT NULL")
    }
}

object Migration_91_92 : Migration(91, 92) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS steam_accounts")
        db.execSQL("DROP TABLE IF EXISTS steam_licenses")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS steam_accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                steamId INTEGER NOT NULL,
                username TEXT NOT NULL,
                avatarHash TEXT DEFAULT NULL,
                refreshToken TEXT NOT NULL,
                accessToken TEXT DEFAULT NULL,
                accessTokenExpiry INTEGER DEFAULT NULL,
                isActive INTEGER NOT NULL DEFAULT 0,
                lastLoginAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_steam_accounts_steamId ON steam_accounts(steamId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS steam_licenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                accountId INTEGER NOT NULL,
                packageId INTEGER NOT NULL,
                appIds TEXT NOT NULL,
                licenseType INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(accountId) REFERENCES steam_accounts(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_steam_licenses_accountId ON steam_licenses(accountId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_steam_licenses_packageId_accountId ON steam_licenses(packageId, accountId)")
    }
}

object Migration_92_93 : Migration(92, 93) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cached_licenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                license_json TEXT NOT NULL
            )
        """)
    }
}

object Migration_93_94 : Migration(93, 94) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE steam_accounts ADD COLUMN clientId INTEGER DEFAULT NULL")
    }
}

object Migration_94_95 : Migration(94, 95) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS steam_download_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                appId INTEGER NOT NULL,
                gameName TEXT NOT NULL,
                coverPath TEXT,
                installDir TEXT,
                installPath TEXT,
                totalBytes INTEGER NOT NULL,
                bytesDownloaded INTEGER NOT NULL,
                state TEXT NOT NULL,
                errorReason TEXT,
                createdAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_steam_download_queue_appId ON steam_download_queue(appId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_steam_download_queue_state ON steam_download_queue(state)")
    }
}

object Migration_95_96 : Migration(95, 96) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS steam_completed_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                appId INTEGER NOT NULL,
                depotId INTEGER NOT NULL,
                manifestId INTEGER NOT NULL,
                fileName TEXT NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_steam_completed_files_appId ON steam_completed_files(appId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_steam_completed_files_appId_depotId_fileName ON steam_completed_files(appId, depotId, fileName)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS steam_completed_depots (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                appId INTEGER NOT NULL,
                depotId INTEGER NOT NULL,
                manifestId INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_steam_completed_depots_appId_depotId ON steam_completed_depots(appId, depotId)")
    }
}

object Migration_96_97 : Migration(96, 97) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN storeEnrichStatus INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            UPDATE games SET storeEnrichStatus = 1
            WHERE source = 'STEAM' AND description IS NOT NULL
            AND screenshotPaths IS NOT NULL AND screenshotPaths != ''
        """)
    }
}

object Migration_97_98 : Migration(97, 98) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_save_config ADD COLUMN statePathPattern TEXT")
        db.execSQL("ALTER TABLE emulator_save_config ADD COLUMN isUserStateOverride INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN savePath TEXT")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN statePath TEXT")
    }
}

object Migration_98_99 : Migration(98, 99) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS emulator_launch_args (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                platformId INTEGER NOT NULL,
                emulatorId TEXT NOT NULL,
                launchMethod TEXT,
                romPathFormat TEXT,
                intentFlagsMask INTEGER,
                mimeType TEXT,
                FOREIGN KEY(platformId) REFERENCES platforms(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_emulator_launch_args_platformId_emulatorId ON emulator_launch_args(platformId, emulatorId)")
    }
}

object Migration_99_100 : Migration(99, 100) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_launch_args ADD COLUMN dataBinding TEXT")
        db.execSQL("ALTER TABLE emulator_launch_args ADD COLUMN extraBinding TEXT")
        db.execSQL("ALTER TABLE emulator_launch_args ADD COLUMN clipDataBinding TEXT")
    }
}

object Migration_100_101 : Migration(100, 101) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN activeVariantFileId INTEGER")
        db.execSQL("ALTER TABLE games ADD COLUMN lastPlayedFileId INTEGER")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS game_files_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                gameId INTEGER NOT NULL,
                rommFileId INTEGER,
                romId INTEGER NOT NULL DEFAULT 0,
                fileName TEXT NOT NULL,
                filePath TEXT NOT NULL,
                category TEXT NOT NULL,
                fileSize INTEGER NOT NULL,
                localPath TEXT,
                downloadedAt INTEGER,
                isLaunchTarget INTEGER NOT NULL DEFAULT 0,
                isMultiDisc INTEGER NOT NULL DEFAULT 0,
                m3uPath TEXT,
                FOREIGN KEY(gameId) REFERENCES games(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("""
            INSERT INTO game_files_new (id, gameId, rommFileId, romId, fileName, filePath, category, fileSize, localPath, downloadedAt)
            SELECT id, gameId, rommFileId, romId, fileName, filePath, category, fileSize, localPath, downloadedAt
            FROM game_files
        """)
        db.execSQL("DROP TABLE game_files")
        db.execSQL("ALTER TABLE game_files_new RENAME TO game_files")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_game_files_gameId ON game_files(gameId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_game_files_rommFileId ON game_files(rommFileId)")
    }
}

object Migration_101_102 : Migration(101, 102) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_files ADD COLUMN romHashPrefix TEXT")
    }
}

object Migration_102_103 : Migration(102, 103) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN steamInstallDir TEXT")
    }
}

object Migration_103_104 : Migration(103, 104) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE emulator_launch_args SET extraBinding = NULL " +
                "WHERE emulatorId IN ('nethersx2', 'aethersx2', 'duckstation') " +
                "AND extraBinding = 'FILE_PROVIDER'"
        )
    }
}

object Migration_104_105 : Migration(104, 105) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN isManagedByGn INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_105_106 : Migration(105, 106) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `games_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `platformId` INTEGER NOT NULL,
                `platformSlug` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `sortTitle` TEXT NOT NULL,
                `localPath` TEXT,
                `rommId` INTEGER,
                `rommFileName` TEXT,
                `igdbId` INTEGER,
                `raId` INTEGER,
                `steamAppId` INTEGER,
                `steamLauncher` TEXT,
                `steamInstallDir` TEXT,
                `packageName` TEXT,
                `launcherSetManually` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `coverPath` TEXT,
                `gradientColors` TEXT,
                `backgroundPath` TEXT,
                `screenshotPaths` TEXT,
                `cachedScreenshotPaths` TEXT,
                `developer` TEXT,
                `publisher` TEXT,
                `releaseYear` INTEGER,
                `genre` TEXT,
                `description` TEXT,
                `players` TEXT,
                `rating` REAL,
                `regions` TEXT,
                `languages` TEXT,
                `gameModes` TEXT,
                `franchises` TEXT,
                `userRating` INTEGER NOT NULL,
                `userDifficulty` INTEGER NOT NULL,
                `completion` INTEGER NOT NULL,
                `status` TEXT,
                `backlogged` INTEGER NOT NULL,
                `nowPlaying` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `isHidden` INTEGER NOT NULL,
                `playCount` INTEGER NOT NULL,
                `playTimeMinutes` INTEGER NOT NULL,
                `lastPlayed` INTEGER,
                `addedAt` INTEGER NOT NULL,
                `isMultiDisc` INTEGER NOT NULL,
                `lastPlayedDiscId` INTEGER,
                `m3uPath` TEXT,
                `activeVariantFileId` INTEGER,
                `lastPlayedFileId` INTEGER,
                `achievementCount` INTEGER NOT NULL,
                `earnedAchievementCount` INTEGER NOT NULL,
                `activeSaveChannel` TEXT,
                `activeSaveTimestamp` INTEGER,
                `activeSaveApplied` INTEGER NOT NULL,
                `pendingDeviceSyncSaveId` INTEGER,
                `titleId` TEXT,
                `titleIdLocked` INTEGER NOT NULL,
                `storeEnrichStatus` INTEGER NOT NULL,
                `titleIdCandidates` TEXT,
                `youtubeVideoId` TEXT,
                `cheatsFetched` INTEGER NOT NULL,
                `cheatsFetchedAt` INTEGER,
                `cheatsSelectedRegion` TEXT,
                `cheatsSelectedVersion` TEXT,
                `achievementsFetchedAt` INTEGER,
                `romHash` TEXT,
                `verifiedRaId` INTEGER,
                `raIdVerified` INTEGER NOT NULL,
                `fileSizeBytes` INTEGER,
                `syncDirty` INTEGER NOT NULL,
                FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO games_new (
                id, platformId, platformSlug, title, sortTitle, localPath,
                rommId, rommFileName, igdbId, raId, steamAppId, steamLauncher,
                steamInstallDir, packageName, launcherSetManually, source,
                coverPath, gradientColors, backgroundPath, screenshotPaths,
                cachedScreenshotPaths, developer, publisher, releaseYear,
                genre, description, players, rating, regions, languages,
                gameModes, franchises, userRating, userDifficulty, completion,
                status, backlogged, nowPlaying, isFavorite, isHidden,
                playCount, playTimeMinutes, lastPlayed, addedAt, isMultiDisc,
                lastPlayedDiscId, m3uPath, activeVariantFileId, lastPlayedFileId,
                achievementCount, earnedAchievementCount, activeSaveChannel,
                activeSaveTimestamp, activeSaveApplied, pendingDeviceSyncSaveId,
                titleId, titleIdLocked, storeEnrichStatus, titleIdCandidates,
                youtubeVideoId, cheatsFetched, cheatsFetchedAt,
                cheatsSelectedRegion, cheatsSelectedVersion, achievementsFetchedAt,
                romHash, verifiedRaId, raIdVerified, fileSizeBytes, syncDirty
            )
            SELECT
                id, platformId, platformSlug, title, sortTitle, localPath,
                rommId, rommFileName, igdbId, raId, steamAppId,
                CASE
                    WHEN isManagedByGn = 1 AND (steamLauncher IS NULL OR steamLauncher = 'native')
                        THEN 'app.gamenative'
                    ELSE steamLauncher
                END,
                steamInstallDir, packageName, launcherSetManually, source,
                coverPath, gradientColors, backgroundPath, screenshotPaths,
                cachedScreenshotPaths, developer, publisher, releaseYear,
                genre, description, players, rating, regions, languages,
                gameModes, franchises, userRating, userDifficulty, completion,
                status, backlogged, nowPlaying, isFavorite, isHidden,
                playCount, playTimeMinutes, lastPlayed, addedAt, isMultiDisc,
                lastPlayedDiscId, m3uPath, activeVariantFileId, lastPlayedFileId,
                achievementCount, earnedAchievementCount, activeSaveChannel,
                activeSaveTimestamp, activeSaveApplied, pendingDeviceSyncSaveId,
                titleId, titleIdLocked, storeEnrichStatus, titleIdCandidates,
                youtubeVideoId, cheatsFetched, cheatsFetchedAt,
                cheatsSelectedRegion, cheatsSelectedVersion, achievementsFetchedAt,
                romHash, verifiedRaId, raIdVerified, fileSizeBytes, syncDirty
            FROM games
            """.trimIndent()
        )
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
    }
}

object Migration_106_107 : Migration(106, 107) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN holdMs INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Track server-side zip corruption per save_sync row. When a download fails
 * to inflate, we record the server's file timestamp so subsequent sync
 * attempts skip the download until the server copy changes (re-upload).
 */
object Migration_107_108 : Migration(107, 108) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_sync ADD COLUMN corruptZipTimestamp TEXT")
    }
}

object Migration_108_109 : Migration(108, 109) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE games SET raIdVerified = 0 WHERE verifiedRaId IS NULL AND raIdVerified = 1")
    }
}

object Migration_109_110 : Migration(109, 110) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_save_config ADD COLUMN selectedMemcardPath TEXT")
    }
}

object Migration_110_111 : Migration(110, 111) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_conflicts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                gameId INTEGER NOT NULL,
                rommSaveId INTEGER,
                fileName TEXT NOT NULL,
                slot TEXT,
                emulator TEXT,
                localUpdatedAt INTEGER,
                serverUpdatedAt INTEGER,
                localHash TEXT,
                serverHash TEXT,
                reason TEXT NOT NULL DEFAULT '',
                discoveredAt INTEGER NOT NULL,
                dismissed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_pending_conflicts_gameId_rommSaveId ON pending_conflicts(gameId, rommSaveId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_pending_conflicts_dismissed ON pending_conflicts(dismissed)"
        )
    }
}

object Migration_111_112 : Migration(111, 112) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_sync ADD COLUMN lastSyncDeviceId TEXT")
        db.execSQL("ALTER TABLE save_sync ADD COLUMN lastSyncDeviceName TEXT")
    }
}

object Migration_112_113 : Migration(112, 113) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pending_sync_queue ADD COLUMN sessionId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_sync_queue_sessionId ON pending_sync_queue(sessionId)")
    }
}

object Migration_113_114 : Migration(113, 114) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN saveId TEXT")
        db.execSQL("UPDATE games SET saveId = titleId WHERE titleId IS NOT NULL")
    }
}

object Migration_114_115 : Migration(114, 115) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_queue ADD COLUMN gameFolderName TEXT")
    }
}

object Migration_115_116 : Migration(115, 116) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS touch_layout_overrides (
                platformSlug TEXT NOT NULL,
                orientation TEXT NOT NULL,
                schemaVersion INTEGER NOT NULL,
                layoutJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY (platformSlug, orientation)
            )
            """.trimIndent()
        )
    }
}

object Migration_116_117 : Migration(116, 117) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_sync ADD COLUMN userSelectedRestorePoint INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_117_118 : Migration(117, 118) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_sync ADD COLUMN userSelectedRestorePointAt INTEGER")
    }
}

object Migration_118_119 : Migration(118, 119) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_launch_args ADD COLUMN customExtras TEXT")
    }
}

object Migration_119_120 : Migration(119, 120) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_save_config ADD COLUMN savesBesideRom INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_120_121 : Migration(120, 121) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE save_sync ADD COLUMN localContentHash TEXT")

        db.execSQL(
            """
            UPDATE save_sync
            SET rommSaveId = COALESCE(rommSaveId, (
                    SELECT s.rommSaveId FROM save_sync s
                    WHERE s.gameId = save_sync.gameId AND s.emulatorId = save_sync.emulatorId
                      AND (s.channelName IS NULL OR s.channelName = 'argosy-latest')
                    ORDER BY COALESCE(s.lastSyncedAt, 0) DESC, s.id DESC LIMIT 1)),
                lastUploadedHash = COALESCE(lastUploadedHash, (
                    SELECT s.lastUploadedHash FROM save_sync s
                    WHERE s.gameId = save_sync.gameId AND s.emulatorId = save_sync.emulatorId
                      AND (s.channelName IS NULL OR s.channelName = 'argosy-latest')
                    ORDER BY COALESCE(s.lastSyncedAt, 0) DESC, s.id DESC LIMIT 1)),
                localSavePath = COALESCE(localSavePath, (
                    SELECT s.localSavePath FROM save_sync s
                    WHERE s.gameId = save_sync.gameId AND s.emulatorId = save_sync.emulatorId
                      AND (s.channelName IS NULL OR s.channelName = 'argosy-latest')
                    ORDER BY COALESCE(s.lastSyncedAt, 0) DESC, s.id DESC LIMIT 1))
            WHERE save_sync.channelName = 'autosave'
              AND EXISTS (SELECT 1 FROM save_sync s
                    WHERE s.gameId = save_sync.gameId AND s.emulatorId = save_sync.emulatorId
                      AND (s.channelName IS NULL OR s.channelName = 'argosy-latest'))
            """.trimIndent()
        )

        db.execSQL(
            """
            DELETE FROM save_sync
            WHERE (channelName IS NULL OR channelName = 'argosy-latest')
              AND EXISTS (SELECT 1 FROM save_sync s
                    WHERE s.gameId = save_sync.gameId AND s.emulatorId = save_sync.emulatorId
                      AND s.channelName = 'autosave')
            """.trimIndent()
        )

        db.execSQL(
            """
            UPDATE save_sync SET channelName = 'autosave'
            WHERE channelName = 'argosy-latest'
              AND NOT EXISTS (SELECT 1 FROM save_sync s
                    WHERE s.gameId = save_sync.gameId AND s.emulatorId = save_sync.emulatorId
                      AND s.channelName = 'autosave')
            """.trimIndent()
        )
    }
}

object Migration_121_122 : Migration(121, 122) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS state_tombstones (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                gameId INTEGER NOT NULL,
                rommSaveId INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_state_tombstones_rommSaveId ON state_tombstones(rommSaveId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_state_tombstones_gameId ON state_tombstones(gameId)"
        )
    }
}

object Migration_122_123 : Migration(122, 123) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE core_versions ADD COLUMN installedHash TEXT")
        db.execSQL("ALTER TABLE core_versions ADD COLUMN installedSize INTEGER")
        db.execSQL("ALTER TABLE core_versions ADD COLUMN corrupt INTEGER")
    }
}

object Migration_123_124 : Migration(123, 124) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS core_version_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                coreId TEXT NOT NULL,
                version TEXT NOT NULL,
                hash TEXT NOT NULL,
                size INTEGER NOT NULL,
                fileName TEXT NOT NULL,
                archivedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_core_version_history_coreId ON core_version_history(coreId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_core_version_history_archivedAt ON core_version_history(archivedAt)"
        )
        db.execSQL("ALTER TABLE core_versions ADD COLUMN blockedVersion TEXT")
    }
}

object Migration_124_125 : Migration(124, 125) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN coreOptionKey TEXT")
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN coreOptionDirection INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN coreOptionValuesJson TEXT")
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN coreInputRetropadId INTEGER")
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN coreInputMode TEXT NOT NULL DEFAULT 'PULSE'")
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN scopeType TEXT NOT NULL DEFAULT 'GLOBAL'")
        db.execSQL("ALTER TABLE hotkeys ADD COLUMN scopeKey TEXT")
    }
}

object Migration_125_126 : Migration(125, 126) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game_core_option_overrides (
                gameId INTEGER NOT NULL,
                coreId TEXT NOT NULL,
                optionKey TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY (gameId, coreId, optionKey),
                FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_game_core_option_overrides_gameId " +
                "ON game_core_option_overrides (gameId)"
        )
        db.execSQL("ALTER TABLE games ADD COLUMN perGameSettingsEnabled INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_126_127 : Migration(126, 127) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game_controller_mappings (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                gameId INTEGER NOT NULL,
                controllerId TEXT NOT NULL,
                controllerName TEXT NOT NULL,
                vendorId INTEGER NOT NULL,
                productId INTEGER NOT NULL,
                mappingJson TEXT NOT NULL,
                presetName TEXT,
                isAutoDetected INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY (gameId) REFERENCES games(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_game_controller_mappings_gameId " +
                "ON game_controller_mappings (gameId)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_game_controller_mappings_gameId_controllerId " +
                "ON game_controller_mappings (gameId, controllerId)"
        )
    }
}

object Migration_127_128 : Migration(127, 128) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN perGameControlsEnabled INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_128_129 : Migration(128, 129) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN searchTitle TEXT NOT NULL DEFAULT ''")
        db.query("SELECT id, title FROM games").use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val titleIdx = cursor.getColumnIndexOrThrow("title")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val title = cursor.getString(titleIdx) ?: ""
                db.execSQL(
                    "UPDATE games SET searchTitle = ? WHERE id = ?",
                    arrayOf<Any>(SearchNormalizer.normalize(title), id)
                )
            }
        }
    }
}

object Migration_129_130 : Migration(129, 130) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN audioVolume INTEGER DEFAULT NULL")
    }
}

object Migration_130_131 : Migration(130, 131) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN portraitPosition TEXT DEFAULT NULL")
    }
}

object Migration_131_132 : Migration(131, 132) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN genres TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN collections TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_games_genres ON games(genres)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_games_collections ON games(collections)")
    }
}

object Migration_132_133 : Migration(132, 133) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN boxBackPath TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN boxSpinePath TEXT DEFAULT NULL")
    }
}

object Migration_133_134 : Migration(133, 134) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `speedrun_categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`gameId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sourceLabel` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_speedrun_categories_gameId` ON `speedrun_categories` (`gameId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `speedrun_segments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`orderIndex` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `speedrun_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_speedrun_segments_categoryId` ON `speedrun_segments` (`categoryId`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `speedrun_attempts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`completed` INTEGER NOT NULL, " +
                "`finalTimeMs` INTEGER, " +
                "`splitTimesJson` TEXT NOT NULL, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `speedrun_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_speedrun_attempts_categoryId` ON `speedrun_attempts` (`categoryId`)"
        )
    }
}

object Migration_134_135 : Migration(134, 135) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `game_files` ADD COLUMN `regions` TEXT")
        db.execSQL("ALTER TABLE `game_files` ADD COLUMN `versionGroup` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_game_files_gameId_versionGroup` " +
                "ON `game_files` (`gameId`, `versionGroup`)"
        )
    }
}

object Migration_135_136 : Migration(135, 136) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `download_queue` ADD COLUMN `selectedFileIds` TEXT")
    }
}

object Migration_136_137 : Migration(136, 137) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `emulator_configs` ADD COLUMN `savePath` TEXT")
    }
}

object Migration_137_138 : Migration(137, 138) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `game_files` ADD COLUMN `trackTitle` TEXT")
        db.execSQL("ALTER TABLE `game_files` ADD COLUMN `trackNumber` INTEGER")
        db.execSQL("ALTER TABLE `game_files` ADD COLUMN `durationSeconds` REAL")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bgm_playlist` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`filePath` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "`gameFileId` INTEGER)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_bgm_playlist_filePath` ON `bgm_playlist` (`filePath`)"
        )
    }
}

object Migration_138_139 : Migration(138, 139) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bgm_playlist` ADD COLUMN `entryType` TEXT NOT NULL DEFAULT 'file'")
    }
}

object Migration_139_140 : Migration(139, 140) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bgm_playlist` ADD COLUMN `sourceEntryId` INTEGER")
    }
}

object Migration_140_141 : Migration(140, 141) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bgm_playlist` ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1")
    }
}

object Migration_141_142 : Migration(141, 142) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audio_loudness` (" +
                "`filePath` TEXT NOT NULL, " +
                "`fileKey` TEXT NOT NULL, " +
                "`meanDb` REAL NOT NULL, " +
                "`measuredAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`filePath`))"
        )
    }
}

object Migration_142_143 : Migration(142, 143) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`alternativeNames` TEXT",
            "`ageRatings` TEXT",
            "`mobyId` INTEGER",
            "`sgdbId` INTEGER",
            "`ssId` INTEGER",
            "`launchboxId` INTEGER",
            "`hasheousId` INTEGER",
            "`tgdbId` INTEGER",
            "`hltbId` INTEGER",
            "`flashpointId` TEXT",
            "`gamelistId` TEXT",
            "`libretroId` TEXT",
            "`crcHash` TEXT",
            "`md5Hash` TEXT",
            "`sha1Hash` TEXT",
            "`raHash` TEXT",
            "`manualPath` TEXT"
        ).forEach { db.execSQL("ALTER TABLE `games` ADD COLUMN $it") }

        db.execSQL("ALTER TABLE `games` ADD COLUMN `hasManual` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `games` ADD COLUMN `remoteHasSoundtrack` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `games` ADD COLUMN `isIdentified` INTEGER NOT NULL DEFAULT 1")
    }
}

object Migration_143_144 : Migration(143, 144) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `games` ADD COLUMN `originalCoverPath` TEXT")
        db.execSQL("ALTER TABLE `games` ADD COLUMN `coverSetManually` INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_144_145 : Migration(144, 145) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `games` ADD COLUMN `timeToBeatMainSec` INTEGER")
        db.execSQL("ALTER TABLE `games` ADD COLUMN `timeToBeatExtraSec` INTEGER")
        db.execSQL("ALTER TABLE `games` ADD COLUMN `timeToBeatCompletionistSec` INTEGER")
    }
}

object Migration_145_146 : Migration(145, 146) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quaypass_encounters (
                credentialFingerprint TEXT NOT NULL PRIMARY KEY,
                username TEXT NOT NULL,
                displayName TEXT,
                avatarColor TEXT,
                avatarBlobBase64 TEXT,
                greeting TEXT,
                lastGameTitle TEXT,
                lastGamePlatform TEXT,
                lastGamePlaytimeMinutes INTEGER,
                lastGameIgdbId INTEGER,
                encounteredAt INTEGER NOT NULL,
                seenByUser INTEGER NOT NULL DEFAULT 0,
                accountId TEXT,
                reported INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quaypass_encounters_encounteredAt ON quaypass_encounters(encounteredAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quaypass_encounters_seenByUser ON quaypass_encounters(seenByUser)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quaypass_daily_stats (
                date TEXT NOT NULL PRIMARY KEY,
                encounterCount INTEGER NOT NULL DEFAULT 0,
                ticketsEarned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}

object Migration_146_147 : Migration(146, 147) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emulator_configs ADD COLUMN selectedMemcardPath TEXT")
    }
}

object Migration_147_148 : Migration(147, 148) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quaypass_encounters ADD COLUMN meetCount INTEGER NOT NULL DEFAULT 1")
    }
}

object Migration_148_149 : Migration(148, 149) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quaypass_pending_reports` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`peerAccountId` TEXT NOT NULL, " +
                "`credentialBase64` TEXT NOT NULL, " +
                "`attestationBase64` TEXT NOT NULL, " +
                "`nonceBase64` TEXT NOT NULL, " +
                "`tsSecs` INTEGER NOT NULL, " +
                "`cardMessage` TEXT, " +
                "`cardIgdbId` INTEGER, " +
                "`cardAvatarPngBase64` TEXT)"
        )
    }
}

object Migration_149_150 : Migration(149, 150) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `save_ownership` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`savePath` TEXT NOT NULL, " +
                "`emulatorId` TEXT NOT NULL, " +
                "`ownerUserId` INTEGER, " +
                "`contentHash` TEXT, " +
                "`transitionState` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_save_ownership_savePath_emulatorId` " +
                "ON `save_ownership` (`savePath`, `emulatorId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_save_ownership_ownerUserId` " +
                "ON `save_ownership` (`ownerUserId`)"
        )
    }
}

object Migration_150_151 : Migration(150, 151) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `romm_accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`rommUserId` INTEGER NOT NULL, " +
                "`username` TEXT NOT NULL, " +
                "`baseUrl` TEXT NOT NULL, " +
                "`token` TEXT NOT NULL, " +
                "`deviceId` TEXT, " +
                "`deviceClientVersion` TEXT, " +
                "`avatarPath` TEXT, " +
                "`isActive` INTEGER NOT NULL, " +
                "`lastLoginAt` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_romm_accounts_rommUserId` " +
                "ON `romm_accounts` (`rommUserId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_romm_accounts_isActive` " +
                "ON `romm_accounts` (`isActive`)"
        )
    }
}

object Migration_151_152 : Migration(151, 152) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `pending_sync_queue` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL("ALTER TABLE `pending_sync_queue` ADD COLUMN `cacheId` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_sync_queue_ownerUserId` " +
                "ON `pending_sync_queue` (`ownerUserId`)"
        )

        db.execSQL("ALTER TABLE `save_cache` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_save_cache_ownerUserId` " +
                "ON `save_cache` (`ownerUserId`)"
        )

        db.execSQL("ALTER TABLE `pending_conflicts` ADD COLUMN `ownerUserId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP INDEX IF EXISTS `index_pending_conflicts_gameId_rommSaveId`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_conflicts_gameId_rommSaveId_ownerUserId` " +
                "ON `pending_conflicts` (`gameId`, `rommSaveId`, `ownerUserId`)"
        )
    }
}

object Migration_152_153 : Migration(152, 153) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `save_ownership` ADD COLUMN `gameId` INTEGER")
        db.execSQL("ALTER TABLE `save_ownership` ADD COLUMN `channelName` TEXT")
        db.execSQL("ALTER TABLE `save_ownership` ADD COLUMN `pendingOwnerUserId` INTEGER")
        db.execSQL("ALTER TABLE `save_ownership` ADD COLUMN `archivedCacheId` INTEGER")
        db.execSQL("ALTER TABLE `save_ownership` ADD COLUMN `incomingCacheId` INTEGER")
        db.execSQL("ALTER TABLE `save_ownership` ADD COLUMN `needsSync` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_save_ownership_transitionState` " +
                "ON `save_ownership` (`transitionState`)"
        )

        db.execSQL("ALTER TABLE `save_cache` ADD COLUMN `serverCurrentAtSync` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `save_cache` SET `serverCurrentAtSync` = 1 WHERE `rommSaveId` IS NOT NULL")
    }
}

object Migration_153_154 : Migration(153, 154) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_user_overlay` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`gameId` INTEGER NOT NULL, " +
                "`isMember` INTEGER NOT NULL, " +
                "`serverHidden` INTEGER NOT NULL, " +
                "`isFavorite` INTEGER NOT NULL, " +
                "`userRating` INTEGER NOT NULL, " +
                "`userDifficulty` INTEGER NOT NULL, " +
                "`completion` INTEGER NOT NULL, " +
                "`status` TEXT, " +
                "`backlogged` INTEGER NOT NULL, " +
                "`nowPlaying` INTEGER NOT NULL, " +
                "`playCount` INTEGER NOT NULL, " +
                "`playTimeMinutes` INTEGER NOT NULL, " +
                "`lastPlayed` INTEGER, " +
                "`earnedAchievementCount` INTEGER NOT NULL, " +
                "`activeSaveChannel` TEXT, " +
                "`activeSaveTimestamp` INTEGER, " +
                "`activeSaveApplied` INTEGER NOT NULL, " +
                "`pendingDeviceSyncSaveId` INTEGER, " +
                "FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_user_overlay_ownerUserId_gameId` " +
                "ON `game_user_overlay` (`ownerUserId`, `gameId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_game_user_overlay_gameId` " +
                "ON `game_user_overlay` (`gameId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_game_user_overlay_ownerUserId` " +
                "ON `game_user_overlay` (`ownerUserId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `collection_membership` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`collectionId` INTEGER NOT NULL, " +
                "`isMember` INTEGER NOT NULL, " +
                "FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_collection_membership_ownerUserId_collectionId` " +
                "ON `collection_membership` (`ownerUserId`, `collectionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_collection_membership_collectionId` " +
                "ON `collection_membership` (`collectionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_collection_membership_ownerUserId` " +
                "ON `collection_membership` (`ownerUserId`)"
        )

        db.execSQL(
            "INSERT OR IGNORE INTO `game_user_overlay` (" +
                "`ownerUserId`, `gameId`, `isMember`, `serverHidden`, `isFavorite`, `userRating`, " +
                "`userDifficulty`, `completion`, `status`, `backlogged`, `nowPlaying`, `playCount`, " +
                "`playTimeMinutes`, `lastPlayed`, `earnedAchievementCount`, `activeSaveChannel`, " +
                "`activeSaveTimestamp`, `activeSaveApplied`, `pendingDeviceSyncSaveId`) " +
                "SELECT a.rommUserId, g.id, 1, 0, g.isFavorite, g.userRating, " +
                "g.userDifficulty, g.completion, g.status, g.backlogged, g.nowPlaying, g.playCount, " +
                "g.playTimeMinutes, g.lastPlayed, g.earnedAchievementCount, g.activeSaveChannel, " +
                "g.activeSaveTimestamp, g.activeSaveApplied, g.pendingDeviceSyncSaveId " +
                "FROM `games` g CROSS JOIN " +
                "(SELECT `rommUserId` FROM `romm_accounts` WHERE `isActive` = 1 LIMIT 1) a"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `collection_membership` (`ownerUserId`, `collectionId`, `isMember`) " +
                "SELECT a.rommUserId, c.id, 1 FROM `collections` c CROSS JOIN " +
                "(SELECT `rommUserId` FROM `romm_accounts` WHERE `isActive` = 1 LIMIT 1) a"
        )
    }
}

/**
 * Moves the active-save target onto the `save_cache` row it always pointed at.
 *
 * The channel and the restore timestamp were two columns on `games` naming a cache row
 * indirectly, which a re-timestamped upload could leave dangling. The row now carries the flag
 * itself. The backfill resolves the old pointer per account, preferring the exact timestamp and
 * falling back to the newest row in the recorded channel; a pointer at a channel that holds no
 * cache row resolves to nothing, which is the same thing it meant before.
 */
object Migration_154_155 : Migration(154, 155) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `save_cache` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `save_cache` ADD COLUMN `activeSaveApplied` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `save_cache` ADD COLUMN `pendingDeviceSyncSaveId` INTEGER")

        db.execSQL(
            "UPDATE `save_cache` SET `isActive` = 1 WHERE `id` IN (" +
                "SELECT (" +
                "SELECT sc.`id` FROM `save_cache` sc " +
                "WHERE sc.`gameId` = o.`gameId` " +
                "AND (sc.`ownerUserId` IS NULL OR sc.`ownerUserId` = o.`ownerUserId`) " +
                "AND sc.`cachedAt` = o.`activeSaveTimestamp` " +
                "ORDER BY sc.`cachedAt` DESC LIMIT 1" +
                ") FROM `game_user_overlay` o WHERE o.`activeSaveTimestamp` IS NOT NULL" +
                ")"
        )

        db.execSQL(
            "UPDATE `save_cache` SET `isActive` = 1 WHERE `id` IN (" +
                "SELECT (" +
                "SELECT sc.`id` FROM `save_cache` sc " +
                "WHERE sc.`gameId` = o.`gameId` " +
                "AND (sc.`ownerUserId` IS NULL OR sc.`ownerUserId` = o.`ownerUserId`) " +
                "AND sc.`channelName` = o.`activeSaveChannel` " +
                "ORDER BY sc.`cachedAt` DESC LIMIT 1" +
                ") FROM `game_user_overlay` o WHERE o.`activeSaveChannel` IS NOT NULL " +
                "AND NOT EXISTS (SELECT 1 FROM `save_cache` a " +
                "WHERE a.`gameId` = o.`gameId` AND a.`isActive` = 1)" +
                ")"
        )

        db.execSQL(
            "UPDATE `save_cache` SET `isActive` = 1 WHERE `id` IN (" +
                "SELECT (" +
                "SELECT sc.`id` FROM `save_cache` sc " +
                "WHERE sc.`gameId` = g.`id` AND sc.`cachedAt` = g.`activeSaveTimestamp` " +
                "ORDER BY sc.`cachedAt` DESC LIMIT 1" +
                ") FROM `games` g WHERE g.`activeSaveTimestamp` IS NOT NULL " +
                "AND NOT EXISTS (SELECT 1 FROM `game_user_overlay` o WHERE o.`gameId` = g.`id`)" +
                ")"
        )

        db.execSQL(
            "UPDATE `save_cache` SET `isActive` = 1 WHERE `id` IN (" +
                "SELECT (" +
                "SELECT sc.`id` FROM `save_cache` sc " +
                "WHERE sc.`gameId` = g.`id` AND sc.`channelName` = g.`activeSaveChannel` " +
                "ORDER BY sc.`cachedAt` DESC LIMIT 1" +
                ") FROM `games` g WHERE g.`activeSaveChannel` IS NOT NULL " +
                "AND NOT EXISTS (SELECT 1 FROM `game_user_overlay` o WHERE o.`gameId` = g.`id`) " +
                "AND NOT EXISTS (SELECT 1 FROM `save_cache` a " +
                "WHERE a.`gameId` = g.`id` AND a.`isActive` = 1)" +
                ")"
        )

        db.execSQL(
            "UPDATE `save_cache` SET " +
                "`activeSaveApplied` = IFNULL((SELECT o.`activeSaveApplied` FROM `game_user_overlay` o " +
                "WHERE o.`gameId` = `save_cache`.`gameId` " +
                "AND (`save_cache`.`ownerUserId` IS NULL OR o.`ownerUserId` = `save_cache`.`ownerUserId`) " +
                "LIMIT 1), 0), " +
                "`pendingDeviceSyncSaveId` = (SELECT o.`pendingDeviceSyncSaveId` FROM `game_user_overlay` o " +
                "WHERE o.`gameId` = `save_cache`.`gameId` " +
                "AND (`save_cache`.`ownerUserId` IS NULL OR o.`ownerUserId` = `save_cache`.`ownerUserId`) " +
                "LIMIT 1) " +
                "WHERE `isActive` = 1"
        )
    }
}

/**
 * Drops the active-save columns now that `save_cache` carries them.
 *
 * `games` is the CASCADE parent of nine tables, so the rebuild runs with foreign keys off: an
 * enforced DROP TABLE on a parent performs an implicit delete and would take every child row with
 * it. Every index on the rebuilt tables is recreated by hand for the same reason a rebuild is
 * needed at all -- SQLite carries neither across the rename.
 */
object Migration_155_156 : Migration(155, 156) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `games_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `platformId` INTEGER NOT NULL, " +
                "`platformSlug` TEXT NOT NULL, `title` TEXT NOT NULL, `sortTitle` TEXT NOT NULL, " +
                "`searchTitle` TEXT NOT NULL, `localPath` TEXT, `rommId` INTEGER, " +
                "`rommFileName` TEXT, `igdbId` INTEGER, `raId` INTEGER, `steamAppId` INTEGER, " +
                "`steamLauncher` TEXT, `steamInstallDir` TEXT, `packageName` TEXT, " +
                "`launcherSetManually` INTEGER NOT NULL, `source` TEXT NOT NULL, `coverPath` TEXT, " +
                "`originalCoverPath` TEXT, `coverSetManually` INTEGER NOT NULL, " +
                "`gradientColors` TEXT, `backgroundPath` TEXT, `screenshotPaths` TEXT, " +
                "`cachedScreenshotPaths` TEXT, `boxBackPath` TEXT, `boxSpinePath` TEXT, " +
                "`developer` TEXT, `publisher` TEXT, `releaseYear` INTEGER, `genre` TEXT, " +
                "`description` TEXT, `players` TEXT, `rating` REAL, `regions` TEXT, " +
                "`languages` TEXT, `gameModes` TEXT, `franchises` TEXT, `genres` TEXT, " +
                "`collections` TEXT, `alternativeNames` TEXT, `ageRatings` TEXT, `mobyId` INTEGER, " +
                "`sgdbId` INTEGER, `ssId` INTEGER, `launchboxId` INTEGER, `hasheousId` INTEGER, " +
                "`tgdbId` INTEGER, `hltbId` INTEGER, `timeToBeatMainSec` INTEGER, " +
                "`timeToBeatExtraSec` INTEGER, `timeToBeatCompletionistSec` INTEGER, " +
                "`flashpointId` TEXT, `gamelistId` TEXT, `libretroId` TEXT, `crcHash` TEXT, " +
                "`md5Hash` TEXT, `sha1Hash` TEXT, `raHash` TEXT, `hasManual` INTEGER NOT NULL, " +
                "`manualPath` TEXT, `remoteHasSoundtrack` INTEGER NOT NULL, " +
                "`isIdentified` INTEGER NOT NULL, `userRating` INTEGER NOT NULL, " +
                "`userDifficulty` INTEGER NOT NULL, `completion` INTEGER NOT NULL, `status` TEXT, " +
                "`backlogged` INTEGER NOT NULL, `nowPlaying` INTEGER NOT NULL, " +
                "`isFavorite` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, " +
                "`playCount` INTEGER NOT NULL, `playTimeMinutes` INTEGER NOT NULL, " +
                "`lastPlayed` INTEGER, `addedAt` INTEGER NOT NULL, `isMultiDisc` INTEGER NOT NULL, " +
                "`lastPlayedDiscId` INTEGER, `m3uPath` TEXT, `activeVariantFileId` INTEGER, " +
                "`lastPlayedFileId` INTEGER, `achievementCount` INTEGER NOT NULL, " +
                "`earnedAchievementCount` INTEGER NOT NULL, `titleId` TEXT, " +
                "`titleIdLocked` INTEGER NOT NULL, `storeEnrichStatus` INTEGER NOT NULL, " +
                "`titleIdCandidates` TEXT, `saveId` TEXT, `youtubeVideoId` TEXT, " +
                "`cheatsFetched` INTEGER NOT NULL, `cheatsFetchedAt` INTEGER, " +
                "`cheatsSelectedRegion` TEXT, `cheatsSelectedVersion` TEXT, " +
                "`achievementsFetchedAt` INTEGER, `romHash` TEXT, `verifiedRaId` INTEGER, " +
                "`raIdVerified` INTEGER NOT NULL, `fileSizeBytes` INTEGER, " +
                "`perGameSettingsEnabled` INTEGER NOT NULL, " +
                "`perGameControlsEnabled` INTEGER NOT NULL, `syncDirty` INTEGER NOT NULL, " +
                "FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        val gameColumns = "`id`, `platformId`, `platformSlug`, `title`, `sortTitle`, " +
            "`searchTitle`, `localPath`, `rommId`, `rommFileName`, `igdbId`, `raId`, " +
            "`steamAppId`, `steamLauncher`, `steamInstallDir`, `packageName`, " +
            "`launcherSetManually`, `source`, `coverPath`, `originalCoverPath`, " +
            "`coverSetManually`, `gradientColors`, `backgroundPath`, `screenshotPaths`, " +
            "`cachedScreenshotPaths`, `boxBackPath`, `boxSpinePath`, `developer`, `publisher`, " +
            "`releaseYear`, `genre`, `description`, `players`, `rating`, `regions`, " +
            "`languages`, `gameModes`, `franchises`, `genres`, `collections`, " +
            "`alternativeNames`, `ageRatings`, `mobyId`, `sgdbId`, `ssId`, `launchboxId`, " +
            "`hasheousId`, `tgdbId`, `hltbId`, `timeToBeatMainSec`, `timeToBeatExtraSec`, " +
            "`timeToBeatCompletionistSec`, `flashpointId`, `gamelistId`, `libretroId`, " +
            "`crcHash`, `md5Hash`, `sha1Hash`, `raHash`, `hasManual`, `manualPath`, " +
            "`remoteHasSoundtrack`, `isIdentified`, `userRating`, `userDifficulty`, " +
            "`completion`, `status`, `backlogged`, `nowPlaying`, `isFavorite`, `isHidden`, " +
            "`playCount`, `playTimeMinutes`, `lastPlayed`, `addedAt`, `isMultiDisc`, " +
            "`lastPlayedDiscId`, `m3uPath`, `activeVariantFileId`, `lastPlayedFileId`, " +
            "`achievementCount`, `earnedAchievementCount`, `titleId`, `titleIdLocked`, " +
            "`storeEnrichStatus`, `titleIdCandidates`, `saveId`, `youtubeVideoId`, " +
            "`cheatsFetched`, `cheatsFetchedAt`, `cheatsSelectedRegion`, " +
            "`cheatsSelectedVersion`, `achievementsFetchedAt`, `romHash`, `verifiedRaId`, " +
            "`raIdVerified`, `fileSizeBytes`, `perGameSettingsEnabled`, " +
            "`perGameControlsEnabled`, `syncDirty`"

        db.execSQL("INSERT INTO `games_new` ($gameColumns) SELECT $gameColumns FROM `games`")
        db.execSQL("DROP TABLE `games`")
        db.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_genres` ON `games` (`genres`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_collections` ON `games` (`collections`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_user_overlay_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER NOT NULL, " +
                "`gameId` INTEGER NOT NULL, " +
                "`isMember` INTEGER NOT NULL, " +
                "`serverHidden` INTEGER NOT NULL, " +
                "`isFavorite` INTEGER NOT NULL, " +
                "`userRating` INTEGER NOT NULL, " +
                "`userDifficulty` INTEGER NOT NULL, " +
                "`completion` INTEGER NOT NULL, " +
                "`status` TEXT, " +
                "`backlogged` INTEGER NOT NULL, " +
                "`nowPlaying` INTEGER NOT NULL, " +
                "`playCount` INTEGER NOT NULL, " +
                "`playTimeMinutes` INTEGER NOT NULL, " +
                "`lastPlayed` INTEGER, " +
                "`earnedAchievementCount` INTEGER NOT NULL, " +
                "FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        val overlayColumns = "`id`, `ownerUserId`, `gameId`, `isMember`, `serverHidden`, " +
            "`isFavorite`, `userRating`, `userDifficulty`, `completion`, `status`, " +
            "`backlogged`, `nowPlaying`, `playCount`, `playTimeMinutes`, `lastPlayed`, " +
            "`earnedAchievementCount`"

        db.execSQL(
            "INSERT INTO `game_user_overlay_new` ($overlayColumns) " +
                "SELECT $overlayColumns FROM `game_user_overlay`"
        )
        db.execSQL("DROP TABLE `game_user_overlay`")
        db.execSQL("ALTER TABLE `game_user_overlay_new` RENAME TO `game_user_overlay`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_user_overlay_ownerUserId_gameId` " +
                "ON `game_user_overlay` (`ownerUserId`, `gameId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_game_user_overlay_gameId` " +
                "ON `game_user_overlay` (`gameId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_game_user_overlay_ownerUserId` " +
                "ON `game_user_overlay` (`ownerUserId`)"
        )

        db.execSQL("PRAGMA foreign_keys=ON")
    }
}

/**
 * Moves per-user rom hiding off `games` and onto a sparse join table.
 *
 * Row existence is the fact, so most roms are absent. The backfill resolves an owner per game the
 * way the active-save backfill did: the account holding that game's overlay row, preferring the
 * active one, then whichever account is active, and finally nothing at all -- an install that
 * predates accounts records its hidden roms against a null owner rather than losing them.
 */
object Migration_156_157 : Migration(156, 157) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_roms_hidden` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER, " +
                "`gameId` INTEGER NOT NULL, " +
                "FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_user_roms_hidden_ownerUserId_gameId` " +
                "ON `user_roms_hidden` (`ownerUserId`, `gameId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_user_roms_hidden_gameId` " +
                "ON `user_roms_hidden` (`gameId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_user_roms_hidden_ownerUserId` " +
                "ON `user_roms_hidden` (`ownerUserId`)"
        )

        db.execSQL(
            "INSERT INTO `user_roms_hidden` (`ownerUserId`, `gameId`) " +
                "SELECT COALESCE(" +
                "(SELECT o.`ownerUserId` FROM `game_user_overlay` o " +
                "JOIN `romm_accounts` a ON a.`rommUserId` = o.`ownerUserId` AND a.`isActive` = 1 " +
                "WHERE o.`gameId` = g.`id` LIMIT 1), " +
                "(SELECT o.`ownerUserId` FROM `game_user_overlay` o WHERE o.`gameId` = g.`id` LIMIT 1), " +
                "(SELECT `rommUserId` FROM `romm_accounts` WHERE `isActive` = 1 LIMIT 1)" +
                "), g.`id` " +
                "FROM `games` g WHERE g.`isHidden` = 1"
        )
    }
}

/**
 * Drops `isHidden` from `games` now that `user_roms_hidden` carries it.
 *
 * `games` is the CASCADE parent of nine tables, so the rebuild runs with foreign keys off: an
 * enforced DROP TABLE on a parent performs an implicit delete and would take every child row with
 * it, including the hidden rows written one migration earlier. Every index is recreated by hand
 * because SQLite carries none of them across the rename.
 */
object Migration_157_158 : Migration(157, 158) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `games_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `platformId` INTEGER NOT NULL, " +
                "`platformSlug` TEXT NOT NULL, `title` TEXT NOT NULL, `sortTitle` TEXT NOT NULL, " +
                "`searchTitle` TEXT NOT NULL, `localPath` TEXT, `rommId` INTEGER, " +
                "`rommFileName` TEXT, `igdbId` INTEGER, `raId` INTEGER, `steamAppId` INTEGER, " +
                "`steamLauncher` TEXT, `steamInstallDir` TEXT, `packageName` TEXT, " +
                "`launcherSetManually` INTEGER NOT NULL, `source` TEXT NOT NULL, `coverPath` TEXT, " +
                "`originalCoverPath` TEXT, `coverSetManually` INTEGER NOT NULL, " +
                "`gradientColors` TEXT, `backgroundPath` TEXT, `screenshotPaths` TEXT, " +
                "`cachedScreenshotPaths` TEXT, `boxBackPath` TEXT, `boxSpinePath` TEXT, " +
                "`developer` TEXT, `publisher` TEXT, `releaseYear` INTEGER, `genre` TEXT, " +
                "`description` TEXT, `players` TEXT, `rating` REAL, `regions` TEXT, " +
                "`languages` TEXT, `gameModes` TEXT, `franchises` TEXT, `genres` TEXT, " +
                "`collections` TEXT, `alternativeNames` TEXT, `ageRatings` TEXT, `mobyId` INTEGER, " +
                "`sgdbId` INTEGER, `ssId` INTEGER, `launchboxId` INTEGER, `hasheousId` INTEGER, " +
                "`tgdbId` INTEGER, `hltbId` INTEGER, `timeToBeatMainSec` INTEGER, " +
                "`timeToBeatExtraSec` INTEGER, `timeToBeatCompletionistSec` INTEGER, " +
                "`flashpointId` TEXT, `gamelistId` TEXT, `libretroId` TEXT, `crcHash` TEXT, " +
                "`md5Hash` TEXT, `sha1Hash` TEXT, `raHash` TEXT, `hasManual` INTEGER NOT NULL, " +
                "`manualPath` TEXT, `remoteHasSoundtrack` INTEGER NOT NULL, " +
                "`isIdentified` INTEGER NOT NULL, `userRating` INTEGER NOT NULL, " +
                "`userDifficulty` INTEGER NOT NULL, `completion` INTEGER NOT NULL, `status` TEXT, " +
                "`backlogged` INTEGER NOT NULL, `nowPlaying` INTEGER NOT NULL, " +
                "`isFavorite` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, " +
                "`playTimeMinutes` INTEGER NOT NULL, " +
                "`lastPlayed` INTEGER, `addedAt` INTEGER NOT NULL, `isMultiDisc` INTEGER NOT NULL, " +
                "`lastPlayedDiscId` INTEGER, `m3uPath` TEXT, `activeVariantFileId` INTEGER, " +
                "`lastPlayedFileId` INTEGER, `achievementCount` INTEGER NOT NULL, " +
                "`earnedAchievementCount` INTEGER NOT NULL, `titleId` TEXT, " +
                "`titleIdLocked` INTEGER NOT NULL, `storeEnrichStatus` INTEGER NOT NULL, " +
                "`titleIdCandidates` TEXT, `saveId` TEXT, `youtubeVideoId` TEXT, " +
                "`cheatsFetched` INTEGER NOT NULL, `cheatsFetchedAt` INTEGER, " +
                "`cheatsSelectedRegion` TEXT, `cheatsSelectedVersion` TEXT, " +
                "`achievementsFetchedAt` INTEGER, `romHash` TEXT, `verifiedRaId` INTEGER, " +
                "`raIdVerified` INTEGER NOT NULL, `fileSizeBytes` INTEGER, " +
                "`perGameSettingsEnabled` INTEGER NOT NULL, " +
                "`perGameControlsEnabled` INTEGER NOT NULL, `syncDirty` INTEGER NOT NULL, " +
                "FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        val gameColumns = "`id`, `platformId`, `platformSlug`, `title`, `sortTitle`, " +
            "`searchTitle`, `localPath`, `rommId`, `rommFileName`, `igdbId`, `raId`, " +
            "`steamAppId`, `steamLauncher`, `steamInstallDir`, `packageName`, " +
            "`launcherSetManually`, `source`, `coverPath`, `originalCoverPath`, " +
            "`coverSetManually`, `gradientColors`, `backgroundPath`, `screenshotPaths`, " +
            "`cachedScreenshotPaths`, `boxBackPath`, `boxSpinePath`, `developer`, `publisher`, " +
            "`releaseYear`, `genre`, `description`, `players`, `rating`, `regions`, " +
            "`languages`, `gameModes`, `franchises`, `genres`, `collections`, " +
            "`alternativeNames`, `ageRatings`, `mobyId`, `sgdbId`, `ssId`, `launchboxId`, " +
            "`hasheousId`, `tgdbId`, `hltbId`, `timeToBeatMainSec`, `timeToBeatExtraSec`, " +
            "`timeToBeatCompletionistSec`, `flashpointId`, `gamelistId`, `libretroId`, " +
            "`crcHash`, `md5Hash`, `sha1Hash`, `raHash`, `hasManual`, `manualPath`, " +
            "`remoteHasSoundtrack`, `isIdentified`, `userRating`, `userDifficulty`, " +
            "`completion`, `status`, `backlogged`, `nowPlaying`, `isFavorite`, " +
            "`playCount`, `playTimeMinutes`, `lastPlayed`, `addedAt`, `isMultiDisc`, " +
            "`lastPlayedDiscId`, `m3uPath`, `activeVariantFileId`, `lastPlayedFileId`, " +
            "`achievementCount`, `earnedAchievementCount`, `titleId`, `titleIdLocked`, " +
            "`storeEnrichStatus`, `titleIdCandidates`, `saveId`, `youtubeVideoId`, " +
            "`cheatsFetched`, `cheatsFetchedAt`, `cheatsSelectedRegion`, " +
            "`cheatsSelectedVersion`, `achievementsFetchedAt`, `romHash`, `verifiedRaId`, " +
            "`raIdVerified`, `fileSizeBytes`, `perGameSettingsEnabled`, " +
            "`perGameControlsEnabled`, `syncDirty`"

        db.execSQL("INSERT INTO `games_new` ($gameColumns) SELECT $gameColumns FROM `games`")
        db.execSQL("DROP TABLE `games`")
        db.execSQL("ALTER TABLE `games_new` RENAME TO `games`")
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_genres` ON `games` (`genres`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_games_collections` ON `games` (`collections`)")

        db.execSQL("PRAGMA foreign_keys=ON")
    }
}

/**
 * Gives the last unscoped per-user tables an owner.
 *
 * `achievements` matters most: its unique index was `(gameId, raId)` with a REPLACE insert, and
 * REPLACE deletes the conflicting row, so one account's unlock silently destroyed another's. The
 * owner has to be folded into that index rather than merely added as a column.
 *
 * `quaypass_encounters` is rebuilt because the peer fingerprint was the whole primary key, which
 * collapses one peer to one row for the entire device; the second local account to meet that peer
 * was rejected as a duplicate and lost the ticket. Existing rows belong to the active account.
 */
object Migration_158_159 : Migration(158, 159) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val activeUserId = "(SELECT `rommUserId` FROM `romm_accounts` WHERE `isActive` = 1 LIMIT 1)"

        db.execSQL("ALTER TABLE `pending_social_sync` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_social_sync_ownerUserId` " +
                "ON `pending_social_sync` (`ownerUserId`)"
        )
        db.execSQL("UPDATE `pending_social_sync` SET `ownerUserId` = $activeUserId")

        db.execSQL("ALTER TABLE `play_sessions` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_play_sessions_ownerUserId` " +
                "ON `play_sessions` (`ownerUserId`)"
        )
        db.execSQL("UPDATE `play_sessions` SET `ownerUserId` = $activeUserId")

        db.execSQL("ALTER TABLE `achievements` ADD COLUMN `ownerUserId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `achievements` SET `ownerUserId` = COALESCE($activeUserId, 0)")
        db.execSQL("DROP INDEX IF EXISTS `index_achievements_gameId_raId`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_achievements_gameId_raId_ownerUserId` " +
                "ON `achievements` (`gameId`, `raId`, `ownerUserId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_achievements_ownerUserId` " +
                "ON `achievements` (`ownerUserId`)"
        )

        db.execSQL(
            "ALTER TABLE `quaypass_pending_reports` " +
                "ADD COLUMN `localOwnerUserId` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL("UPDATE `quaypass_pending_reports` SET `localOwnerUserId` = COALESCE($activeUserId, 0)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quaypass_pending_reports_localOwnerUserId` " +
                "ON `quaypass_pending_reports` (`localOwnerUserId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `quaypass_encounters_new` (" +
                "`credentialFingerprint` TEXT NOT NULL, `username` TEXT NOT NULL, " +
                "`displayName` TEXT, `avatarColor` TEXT, `avatarBlobBase64` TEXT, " +
                "`greeting` TEXT, `lastGameTitle` TEXT, `lastGamePlatform` TEXT, " +
                "`lastGamePlaytimeMinutes` INTEGER, `lastGameIgdbId` INTEGER, " +
                "`encounteredAt` INTEGER NOT NULL, `seenByUser` INTEGER NOT NULL, " +
                "`accountId` TEXT, `reported` INTEGER NOT NULL, `meetCount` INTEGER NOT NULL, " +
                "`localOwnerUserId` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`credentialFingerprint`, `localOwnerUserId`))"
        )
        db.execSQL(
            "INSERT INTO `quaypass_encounters_new` (" +
                "`credentialFingerprint`, `username`, `displayName`, `avatarColor`, " +
                "`avatarBlobBase64`, `greeting`, `lastGameTitle`, `lastGamePlatform`, " +
                "`lastGamePlaytimeMinutes`, `lastGameIgdbId`, `encounteredAt`, `seenByUser`, " +
                "`accountId`, `reported`, `meetCount`, `localOwnerUserId`) " +
                "SELECT `credentialFingerprint`, `username`, `displayName`, `avatarColor`, " +
                "`avatarBlobBase64`, `greeting`, `lastGameTitle`, `lastGamePlatform`, " +
                "`lastGamePlaytimeMinutes`, `lastGameIgdbId`, `encounteredAt`, `seenByUser`, " +
                "`accountId`, `reported`, `meetCount`, COALESCE($activeUserId, 0) " +
                "FROM `quaypass_encounters`"
        )
        db.execSQL("DROP TABLE `quaypass_encounters`")
        db.execSQL("ALTER TABLE `quaypass_encounters_new` RENAME TO `quaypass_encounters`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quaypass_encounters_encounteredAt` " +
                "ON `quaypass_encounters` (`encounteredAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quaypass_encounters_seenByUser` " +
                "ON `quaypass_encounters` (`seenByUser`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quaypass_encounters_localOwnerUserId` " +
                "ON `quaypass_encounters` (`localOwnerUserId`)"
        )
    }
}

/**
 * Gives the last four sync tables an owner.
 *
 * Three of them carry a unique index that has to absorb the owner rather than merely gain a
 * column beside it. `state_cache` inserts with REPLACE, so one account caching a slot deletes
 * the other account's row and leaks its cache file; `state_tombstones` was unique on the server
 * save id alone, so one account's delete suppressed another's state and it resurrected on their
 * next sync; `save_sync` resolves one row per (game, emulator, channel) and would otherwise
 * collapse two accounts onto it. `download_queue` needs attribution only -- the rom file is
 * device-global and one copy serves every account.
 *
 * The columns are nullable with no SQL default because Room emits none for a Kotlin default, and
 * every existing row is backfilled to the active account. An install with no account leaves them
 * null, which the reads treat as visible to whoever signs in rather than orphaning them.
 */
object Migration_159_160 : Migration(159, 160) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val activeUserId = "(SELECT `rommUserId` FROM `romm_accounts` WHERE `isActive` = 1 LIMIT 1)"

        db.execSQL("ALTER TABLE `save_sync` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL("UPDATE `save_sync` SET `ownerUserId` = $activeUserId")
        db.execSQL("DROP INDEX IF EXISTS `index_save_sync_gameId_emulatorId_channelName`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_save_sync_gameId_emulatorId_channelName_ownerUserId` " +
                "ON `save_sync` (`gameId`, `emulatorId`, `channelName`, `ownerUserId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_save_sync_ownerUserId` " +
                "ON `save_sync` (`ownerUserId`)"
        )

        db.execSQL("ALTER TABLE `state_cache` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL("UPDATE `state_cache` SET `ownerUserId` = $activeUserId")
        db.execSQL("DROP INDEX IF EXISTS `index_state_cache_game_emu_slot_channel_core`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_state_cache_game_emu_slot_channel_core_owner` " +
                "ON `state_cache` (`gameId`, `emulatorId`, `slotNumber`, `channelName`, " +
                "`coreId`, `ownerUserId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_state_cache_ownerUserId` " +
                "ON `state_cache` (`ownerUserId`)"
        )

        db.execSQL("ALTER TABLE `state_tombstones` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL("UPDATE `state_tombstones` SET `ownerUserId` = $activeUserId")
        db.execSQL("DROP INDEX IF EXISTS `index_state_tombstones_rommSaveId`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_state_tombstones_rommSaveId_ownerUserId` " +
                "ON `state_tombstones` (`rommSaveId`, `ownerUserId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_state_tombstones_ownerUserId` " +
                "ON `state_tombstones` (`ownerUserId`)"
        )

        db.execSQL("ALTER TABLE `download_queue` ADD COLUMN `ownerUserId` INTEGER")
        db.execSQL("UPDATE `download_queue` SET `ownerUserId` = $activeUserId")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_download_queue_ownerUserId` " +
                "ON `download_queue` (`ownerUserId`)"
        )
    }
}

/**
 * Gives live save-state files the ownership record live saves already have.
 *
 * Owner-scoping the `state_cache` reads left the filesystem door open: a state file the outgoing
 * account left in the emulator's state directory is still discovered at session end, cached, and
 * uploaded under whoever is signed in. The row is keyed on the live path plus emulator, mirroring
 * `save_ownership`, and additionally carries the slot identity (`slotNumber`, `channelName`,
 * `coreId`) because states are multi-slot per game where a save is one artifact.
 *
 * No backfill: an empty table reads as unowned everywhere, which is the pre-accounts behaviour
 * (adopt once, then record). Claiming every state file on disk for the active account would need a
 * filesystem walk this migration cannot perform.
 */
object Migration_160_161 : Migration(160, 161) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `state_ownership` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`statePath` TEXT NOT NULL, " +
                "`emulatorId` TEXT NOT NULL, " +
                "`ownerUserId` INTEGER, " +
                "`contentHash` TEXT, " +
                "`transitionState` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`gameId` INTEGER, " +
                "`slotNumber` INTEGER NOT NULL DEFAULT 0, " +
                "`channelName` TEXT, " +
                "`coreId` TEXT, " +
                "`pendingOwnerUserId` INTEGER, " +
                "`archivedCacheId` INTEGER, " +
                "`incomingCacheId` INTEGER, " +
                "`needsSync` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_state_ownership_statePath_emulatorId` " +
                "ON `state_ownership` (`statePath`, `emulatorId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_state_ownership_ownerUserId` " +
                "ON `state_ownership` (`ownerUserId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_state_ownership_transitionState` " +
                "ON `state_ownership` (`transitionState`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_state_ownership_gameId` " +
                "ON `state_ownership` (`gameId`)"
        )
    }
}

/**
 * Per-port libretro controller device ids, encoded as `port:deviceId` pairs. Nullable with no SQL
 * default: absent means the core keeps the device it auto-assigns on load.
 */
object Migration_161_162 : Migration(161, 162) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `emulator_configs` ADD COLUMN `controllerTypes` TEXT")
    }
}
/**
 * Clears every Wii title id so it is re-extracted.
 *
 * sigil read the game id from offset 0 for anything that was not RVZ, so a WBFS image yielded the
 * container's own "WBFS" magic, hex-encoded to 57424653, for every game. Wii saves are keyed on
 * that id, so affected games all pointed at one save folder. Binary extraction sets titleIdLocked,
 * and the update query skips locked rows, so a fixed reader alone would never repair them.
 *
 * Wiping all Wii rows rather than only the known-bad value: a correct read is cheap to redo and
 * re-derives the same id, while any other misread we have not identified gets corrected too.
 */
object Migration_162_163 : Migration(162, 163) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE `games` SET `titleId` = NULL, `saveId` = NULL, `titleIdCandidates` = NULL, " +
                "`titleIdLocked` = 0 WHERE `platformId` IN " +
                "(SELECT `id` FROM `platforms` WHERE `slug` = 'wii')"
        )
    }
}

/**
 * Re-keys game_discs uniqueness from rommId to (gameId, discNumber).
 *
 * The unique index on rommId encoded an assumption that every disc is its own RomM rom, which
 * holds for the sibling shape and not for a folder-based multi-disc rom, where one rom owns every
 * disc file. Registering those discs put the same rommId on each row, and REPLACE-on-conflict
 * meant each insert overwrote the last until only the highest-numbered disc survived.
 *
 * A disc is identified by which game it belongs to and its number; rommId is where to fetch it
 * from and is legitimately shared. Rows orphaned by the old collapse are dropped so the next sync
 * re-registers the full set.
 *
 * Duplicate pairs have to go before the index exists, or creating it throws and Room retries the
 * whole migration on every launch - a crash loop with no way out of it from the device. Where a
 * pair is duplicated the downloaded row is kept, since it is the one pointing at a file on disk;
 * between equal candidates the oldest wins, and the next sync re-registers whatever was dropped.
 */
object Migration_163_164 : Migration(163, 164) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM `game_discs` WHERE `gameId` IN (" +
                "SELECT `gameId` FROM `game_discs` GROUP BY `gameId` HAVING COUNT(*) = 1" +
                ") AND `discNumber` > 1"
        )
        db.execSQL(
            "DELETE FROM `game_discs` WHERE `id` NOT IN (" +
                "SELECT COALESCE(" +
                "MIN(CASE WHEN `localPath` IS NOT NULL THEN `id` END), MIN(`id`)" +
                ") FROM `game_discs` GROUP BY `gameId`, `discNumber`" +
                ")"
        )
        db.execSQL("DROP INDEX IF EXISTS `index_game_discs_rommId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_discs_rommId` ON `game_discs` (`rommId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_discs_gameId_discNumber` " +
                "ON `game_discs` (`gameId`, `discNumber`)"
        )
    }
}

/**
 * A per-game mapping was stored once per controller, with nothing recording which console profile
 * it was authored against. That is only sound while a game has one profile; once the profile
 * follows the port device, the same game needs a separate mapping per device. Existing rows are
 * back-filled to the empty key, which resolves as the platform's default profile.
 */
object Migration_164_165 : Migration(164, 165) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `game_controller_mappings` ADD COLUMN `profileKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP INDEX IF EXISTS `index_game_controller_mappings_gameId_controllerId`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_controller_mappings_gameId_controllerId_profileKey` " +
                "ON `game_controller_mappings` (`gameId`, `controllerId`, `profileKey`)"
        )
    }
}

object Migration_165_166 : Migration(165, 166) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `home_tiles` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` INTEGER, " +
                "`pageIndex` INTEGER NOT NULL, " +
                "`columnIndex` INTEGER NOT NULL, " +
                "`rowIndex` INTEGER NOT NULL, " +
                "`columnSpan` INTEGER NOT NULL DEFAULT 1, " +
                "`rowSpan` INTEGER NOT NULL DEFAULT 1, " +
                "`targetType` TEXT NOT NULL, " +
                "`gameId` INTEGER, " +
                "`collectionId` INTEGER, " +
                "`virtualType` TEXT, " +
                "`virtualName` TEXT, " +
                "`packageName` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_home_tiles_ownerUserId_pageIndex` " +
                "ON `home_tiles` (`ownerUserId`, `pageIndex`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_home_tiles_ownerUserId_pageIndex_columnIndex_rowIndex` " +
                "ON `home_tiles` (`ownerUserId`, `pageIndex`, `columnIndex`, `rowIndex`)"
        )
    }
}

object Migration_168_169 : Migration(168, 169) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platforms ADD COLUMN combineContent INTEGER NOT NULL DEFAULT 0")
    }
}

object Migration_167_168 : Migration(167, 168) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN autoSaveState INTEGER")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN autoRestoreState INTEGER")
        db.execSQL("ALTER TABLE platform_libretro_settings ADD COLUMN hwCoreSaveStates INTEGER")
    }
}

object Migration_166_167 : Migration(166, 167) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DROP INDEX IF EXISTS `index_home_tiles_ownerUserId_pageIndex_columnIndex_rowIndex`"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_home_tiles_ownerUserId_pageIndex_columnIndex_rowIndex` " +
                "ON `home_tiles` (`ownerUserId`, `pageIndex`, `columnIndex`, `rowIndex`)"
        )
    }
}

/**
 * The media library: server libraries, the items in them, their tracks, watch state and downloads.
 *
 * Every table is keyed on the media server's own ids plus the owning media user, because that is
 * the identity the server hands out and the only one a row can be written under before a local id
 * exists. `ownerUserId` is the media user, not a RomM account, and is never null: none of this
 * exists before a media login, so there are no unowned rows to adopt.
 *
 * No foreign keys. The hierarchy in `media_items` is carried by `parentId` pointing at another
 * row's `itemId`, and an episode can arrive from an endpoint that answers with the episode alone,
 * with its season never synced; a constraint would refuse the row instead of storing it unresolved.
 * `media_user_data` stays unconstrained for the same reason from the other direction: a position
 * recorded offline must outlive a sync that rewrites or drops the item it belongs to.
 */
object Migration_169_170 : Migration(169, 170) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_libraries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` TEXT NOT NULL, " +
                "`libraryId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`collectionType` TEXT, " +
                "`primaryImageTag` TEXT, " +
                "`itemCount` INTEGER NOT NULL DEFAULT 0, " +
                "`displayOrder` INTEGER NOT NULL DEFAULT 0, " +
                "`lastSyncedAt` INTEGER)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_libraries_ownerUserId_libraryId` " +
                "ON `media_libraries` (`ownerUserId`, `libraryId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, " +
                "`libraryId` TEXT, " +
                "`parentId` TEXT, " +
                "`seriesId` TEXT, " +
                "`itemType` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sortName` TEXT NOT NULL, " +
                "`overview` TEXT, " +
                "`productionYear` INTEGER, " +
                "`premiereDate` INTEGER, " +
                "`dateCreated` INTEGER, " +
                "`communityRating` REAL, " +
                "`officialRating` TEXT, " +
                "`genres` TEXT, " +
                "`studios` TEXT, " +
                "`runTimeTicks` INTEGER, " +
                "`indexNumber` INTEGER, " +
                "`parentIndexNumber` INTEGER, " +
                "`seriesName` TEXT, " +
                "`childCount` INTEGER, " +
                "`primaryImageTag` TEXT, " +
                "`backdropImageTag` TEXT, " +
                "`thumbImageTag` TEXT, " +
                "`container` TEXT, " +
                "`localPath` TEXT, " +
                "`downloadQuality` TEXT, " +
                "`downloadedBytes` INTEGER, " +
                "`downloadedAt` INTEGER, " +
                "`lastSyncedAt` INTEGER)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_items_ownerUserId_itemId` " +
                "ON `media_items` (`ownerUserId`, `itemId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_items_ownerUserId_libraryId` " +
                "ON `media_items` (`ownerUserId`, `libraryId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_items_ownerUserId_parentId` " +
                "ON `media_items` (`ownerUserId`, `parentId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_items_ownerUserId_seriesId` " +
                "ON `media_items` (`ownerUserId`, `seriesId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_items_ownerUserId_sortName` " +
                "ON `media_items` (`ownerUserId`, `sortName`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_streams` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, " +
                "`mediaSourceId` TEXT NOT NULL, " +
                "`streamIndex` INTEGER NOT NULL, " +
                "`streamType` TEXT NOT NULL, " +
                "`codec` TEXT, " +
                "`language` TEXT, " +
                "`displayTitle` TEXT, " +
                "`channels` INTEGER, " +
                "`bitRate` INTEGER, " +
                "`width` INTEGER, " +
                "`height` INTEGER, " +
                "`isDefault` INTEGER NOT NULL DEFAULT 0, " +
                "`isForced` INTEGER NOT NULL DEFAULT 0, " +
                "`isExternal` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_media_streams_ownerUserId_itemId_mediaSourceId_streamIndex` " +
                "ON `media_streams` (`ownerUserId`, `itemId`, `mediaSourceId`, `streamIndex`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_user_data` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, " +
                "`playbackPositionTicks` INTEGER NOT NULL DEFAULT 0, " +
                "`playedPercentage` REAL, " +
                "`played` INTEGER NOT NULL DEFAULT 0, " +
                "`playCount` INTEGER NOT NULL DEFAULT 0, " +
                "`isFavorite` INTEGER NOT NULL DEFAULT 0, " +
                "`lastPlayedAt` INTEGER, " +
                "`needsSync` INTEGER NOT NULL DEFAULT 0, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_user_data_ownerUserId_itemId` " +
                "ON `media_user_data` (`ownerUserId`, `itemId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_user_data_ownerUserId_needsSync` " +
                "ON `media_user_data` (`ownerUserId`, `needsSync`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_download_queue` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, " +
                "`seriesId` TEXT, " +
                "`itemName` TEXT NOT NULL, " +
                "`seriesName` TEXT, " +
                "`itemType` TEXT NOT NULL, " +
                "`quality` TEXT NOT NULL, " +
                "`mediaSourceId` TEXT, " +
                "`playSessionId` TEXT, " +
                "`destinationPath` TEXT, " +
                "`tempFilePath` TEXT, " +
                "`bytesDownloaded` INTEGER NOT NULL, " +
                "`totalBytes` INTEGER NOT NULL, " +
                "`state` TEXT NOT NULL, " +
                "`errorReason` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_download_queue_ownerUserId_itemId` " +
                "ON `media_download_queue` (`ownerUserId`, `itemId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_download_queue_state` " +
                "ON `media_download_queue` (`state`)"
        )
    }
}

/**
 * Adds `media_sources`, the cache of what one PlaybackInfo answer said about a playable version -
 * its container, its size and its bitrate. Every download and every stream already learned this and
 * discarded it, which left a re-download unable to tell identical bytes from a different quality.
 *
 * No foreign key to `media_items`, matching `media_streams`: a cached answer is keyed on the server
 * item id and a sync pass that rewrites the item row is not a reason to refuse or drop it.
 */
object Migration_170_171 : Migration(170, 171) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_sources` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerUserId` TEXT NOT NULL, " +
                "`itemId` TEXT NOT NULL, " +
                "`mediaSourceId` TEXT NOT NULL, " +
                "`container` TEXT, " +
                "`sizeBytes` INTEGER, " +
                "`bitrateKbps` INTEGER, " +
                "`videoHeight` INTEGER)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_media_sources_ownerUserId_itemId_mediaSourceId` " +
                "ON `media_sources` (`ownerUserId`, `itemId`, `mediaSourceId`)"
        )
    }
}

/**
 * Gives a home tile somewhere to record the media item it points at.
 *
 * The column is added rather than the target being folded into an existing one, matching how every
 * other kind of target is stored: each keeps its own nullable column, and the type string says which
 * one to read. Existing rows carry a null here and are untouched, so a grid arranged by an earlier
 * build reads back exactly as it was written.
 */
object Migration_171_172 : Migration(171, 172) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `home_tiles` ADD COLUMN `mediaItemId` TEXT")
    }
}

/**
 * Collapses duplicate `state_cache` rows down to one per slot. The unique index spans three
 * nullable columns, and SQLite treats NULLs as distinct, so REPLACE never fired for the ordinary
 * case and every pre-launch download inserted another row for a slot it already held.
 *
 * A survivor missing a server link inherits one from a sibling first, so collapsing the group
 * cannot strand a state object on the server. Cached files are left on disk untouched.
 */
object Migration_172_173 : Migration(172, 173) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE state_cache
            SET rommSaveId = (
                SELECT sibling.rommSaveId FROM state_cache AS sibling
                WHERE sibling.gameId = state_cache.gameId
                  AND sibling.emulatorId = state_cache.emulatorId
                  AND sibling.slotNumber = state_cache.slotNumber
                  AND sibling.channelName IS state_cache.channelName
                  AND sibling.coreId IS state_cache.coreId
                  AND sibling.ownerUserId IS state_cache.ownerUserId
                  AND sibling.rommSaveId IS NOT NULL
                ORDER BY sibling.id DESC
                LIMIT 1
            )
            WHERE rommSaveId IS NULL
              AND EXISTS (
                SELECT 1 FROM state_cache AS sibling
                WHERE sibling.gameId = state_cache.gameId
                  AND sibling.emulatorId = state_cache.emulatorId
                  AND sibling.slotNumber = state_cache.slotNumber
                  AND sibling.channelName IS state_cache.channelName
                  AND sibling.coreId IS state_cache.coreId
                  AND sibling.ownerUserId IS state_cache.ownerUserId
                  AND sibling.rommSaveId IS NOT NULL
              )
            """.trimIndent()
        )

        db.execSQL(
            """
            DELETE FROM state_cache
            WHERE id NOT IN (
                SELECT MAX(id) FROM state_cache
                GROUP BY gameId, emulatorId, slotNumber, channelName, coreId, ownerUserId
            )
            """.trimIndent()
        )
    }
}

/**
 * Adds the cast and crew credited on a media title. Nothing is backfilled: credits arrive with the
 * item, so an existing library shows them from its next sync rather than needing a reset.
 */
object Migration_173_174 : Migration(173, 174) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_credits` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `ownerUserId` TEXT NOT NULL,
                `itemId` TEXT NOT NULL,
                `personId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `role` TEXT,
                `personType` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `primaryImageTag` TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_media_credits_ownerUserId_itemId_personId_personType` " +
                "ON `media_credits` (`ownerUserId`, `itemId`, `personId`, `personType`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_credits_ownerUserId_itemId` " +
                "ON `media_credits` (`ownerUserId`, `itemId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_media_credits_ownerUserId_personId` " +
                "ON `media_credits` (`ownerUserId`, `personId`)"
        )
    }
}

/**
 * Lets a home tile play something rather than only point at it: how a series tile chooses its
 * episode, the season a tile is confined to, a file on this device, and the episodes a tile was
 * given by hand. Existing tiles carry nulls and keep behaving exactly as they did.
 */
object Migration_174_175 : Migration(174, 175) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `home_tiles` ADD COLUMN `mediaPlayMode` TEXT")
        db.execSQL("ALTER TABLE `home_tiles` ADD COLUMN `mediaScopeId` TEXT")
        db.execSQL("ALTER TABLE `home_tiles` ADD COLUMN `mediaFilePath` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `home_tile_episodes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tileId` INTEGER NOT NULL,
                `itemId` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_home_tile_episodes_tileId` " +
                "ON `home_tile_episodes` (`tileId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_home_tile_episodes_tileId_itemId` " +
                "ON `home_tile_episodes` (`tileId`, `itemId`)"
        )
    }
}

/**
 * Gives a save slot somewhere to exist before anything has been saved into it.
 *
 * Slots were inferred from the saves that existed, so one that had just been created was announced
 * and then gone: the list rebuilt without it and the next save went elsewhere. Nothing is backfilled
 * here - every slot that already holds a save keeps being found the way it always was, and this
 * table only carries the ones that would otherwise have nowhere to live.
 */
object Migration_175_176 : Migration(175, 176) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `save_channels` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `ownerUserId` INTEGER,
                `gameId` INTEGER NOT NULL,
                `channelName` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_save_channels_ownerUserId_gameId_channelName` " +
                "ON `save_channels` (`ownerUserId`, `gameId`, `channelName`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_save_channels_ownerUserId_gameId` " +
                "ON `save_channels` (`ownerUserId`, `gameId`)"
        )
    }
}
