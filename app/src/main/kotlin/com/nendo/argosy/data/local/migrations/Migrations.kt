package com.nendo.argosy.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
