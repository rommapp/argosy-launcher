package com.nendo.argosy.di

import android.content.Context
import androidx.room.Room
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.migrations.MigrationRegistry
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.AppCategoryDao
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.CoreOptionOverrideDao
import com.nendo.argosy.data.local.dao.GameCoreOptionOverrideDao
import com.nendo.argosy.data.local.dao.ControllerMappingDao
import com.nendo.argosy.data.local.dao.ControllerOrderDao
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.dao.CoreVersionHistoryDao
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
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSocialSyncDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.SocialGameCacheDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.StateTombstoneDao
import com.nendo.argosy.data.local.dao.CachedLicenseDao
import com.nendo.argosy.data.local.dao.SteamAccountDao
import com.nendo.argosy.data.local.dao.SteamDownloadQueueDao
import com.nendo.argosy.data.local.dao.SteamDownloadTrackingDao
import com.nendo.argosy.data.local.dao.SteamLicenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ALauncherDatabase {
        return Room.databaseBuilder(
            context,
            ALauncherDatabase::class.java,
            "alauncher.db"
        )
            .addMigrations(*MigrationRegistry.ARRAY)
            .enableMultiInstanceInvalidation()
            .build()
    }

    @Provides
    fun providePlatformDao(database: ALauncherDatabase): PlatformDao = database.platformDao()

    @Provides
    fun provideGameDao(database: ALauncherDatabase): GameDao = database.gameDao()

    @Provides
    fun provideGameDiscDao(database: ALauncherDatabase): GameDiscDao = database.gameDiscDao()

    @Provides
    fun provideEmulatorConfigDao(database: ALauncherDatabase): EmulatorConfigDao =
        database.emulatorConfigDao()

    @Provides
    fun provideDownloadQueueDao(database: ALauncherDatabase): DownloadQueueDao =
        database.downloadQueueDao()

    @Provides
    fun provideSaveSyncDao(database: ALauncherDatabase): SaveSyncDao =
        database.saveSyncDao()

    @Provides
    fun providePendingSyncQueueDao(database: ALauncherDatabase): PendingSyncQueueDao =
        database.pendingSyncQueueDao()

    @Provides
    fun provideEmulatorSaveConfigDao(database: ALauncherDatabase): EmulatorSaveConfigDao =
        database.emulatorSaveConfigDao()

    @Provides
    fun provideEmulatorLaunchArgsDao(database: ALauncherDatabase): com.nendo.argosy.data.local.dao.EmulatorLaunchArgsDao =
        database.emulatorLaunchArgsDao()

    @Provides
    fun provideAchievementDao(database: ALauncherDatabase): AchievementDao =
        database.achievementDao()

    @Provides
    fun provideSaveCacheDao(database: ALauncherDatabase): SaveCacheDao =
        database.saveCacheDao()

    @Provides
    fun provideStateCacheDao(database: ALauncherDatabase): StateCacheDao =
        database.stateCacheDao()

    @Provides
    fun provideStateTombstoneDao(database: ALauncherDatabase): StateTombstoneDao =
        database.stateTombstoneDao()

    @Provides
    fun provideOrphanedFileDao(database: ALauncherDatabase): OrphanedFileDao =
        database.orphanedFileDao()

    @Provides
    fun provideAppCategoryDao(database: ALauncherDatabase): AppCategoryDao =
        database.appCategoryDao()

    @Provides
    fun provideFirmwareDao(database: ALauncherDatabase): FirmwareDao =
        database.firmwareDao()

    @Provides
    fun provideCollectionDao(database: ALauncherDatabase): CollectionDao =
        database.collectionDao()

    @Provides
    fun providePinnedCollectionDao(database: ALauncherDatabase): PinnedCollectionDao =
        database.pinnedCollectionDao()

    @Provides
    fun provideGameFileDao(database: ALauncherDatabase): GameFileDao =
        database.gameFileDao()

    @Provides
    fun provideCoreVersionDao(database: ALauncherDatabase): CoreVersionDao =
        database.coreVersionDao()

    @Provides
    fun provideCoreVersionHistoryDao(database: ALauncherDatabase): CoreVersionHistoryDao =
        database.coreVersionHistoryDao()

    @Provides
    fun provideControllerOrderDao(database: ALauncherDatabase): ControllerOrderDao =
        database.controllerOrderDao()

    @Provides
    fun provideControllerMappingDao(database: ALauncherDatabase): ControllerMappingDao =
        database.controllerMappingDao()

    @Provides
    fun provideHotkeyDao(database: ALauncherDatabase): HotkeyDao =
        database.hotkeyDao()

    @Provides
    fun provideCheatDao(database: ALauncherDatabase): CheatDao =
        database.cheatDao()

    @Provides
    fun providePlatformLibretroSettingsDao(database: ALauncherDatabase): PlatformLibretroSettingsDao =
        database.platformLibretroSettingsDao()

    @Provides
    fun provideEmulatorUpdateDao(database: ALauncherDatabase): EmulatorUpdateDao =
        database.emulatorUpdateDao()

    @Provides
    fun providePlaySessionDao(database: ALauncherDatabase): PlaySessionDao =
        database.playSessionDao()

    @Provides
    fun providePendingSocialSyncDao(database: ALauncherDatabase): PendingSocialSyncDao =
        database.pendingSocialSyncDao()

    @Provides
    fun providePendingConflictDao(database: ALauncherDatabase): PendingConflictDao =
        database.pendingConflictDao()

    @Provides
    fun provideTouchLayoutOverrideDao(database: ALauncherDatabase): com.nendo.argosy.data.local.dao.TouchLayoutOverrideDao =
        database.touchLayoutOverrideDao()

    @Provides
    fun provideSocialGameCacheDao(database: ALauncherDatabase): SocialGameCacheDao =
        database.socialGameCacheDao()

    @Provides
    fun provideCoreOptionOverrideDao(database: ALauncherDatabase): CoreOptionOverrideDao =
        database.coreOptionOverrideDao()

    @Provides
    fun provideGameCoreOptionOverrideDao(database: ALauncherDatabase): GameCoreOptionOverrideDao =
        database.gameCoreOptionOverrideDao()

    @Provides
    fun provideSteamAccountDao(database: ALauncherDatabase): SteamAccountDao =
        database.steamAccountDao()

    @Provides
    fun provideSteamLicenseDao(database: ALauncherDatabase): SteamLicenseDao =
        database.steamLicenseDao()

    @Provides
    fun provideCachedLicenseDao(database: ALauncherDatabase): CachedLicenseDao =
        database.cachedLicenseDao()

    @Provides
    fun provideSteamDownloadQueueDao(database: ALauncherDatabase): SteamDownloadQueueDao =
        database.steamDownloadQueueDao()

    @Provides
    fun provideSteamDownloadTrackingDao(database: ALauncherDatabase): SteamDownloadTrackingDao =
        database.steamDownloadTrackingDao()
}
