package com.nendo.argosy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nendo.argosy.data.local.converter.Converters
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.AppCategoryDao
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.CoreOptionOverrideDao
import com.nendo.argosy.data.local.dao.ControllerMappingDao
import com.nendo.argosy.data.local.dao.ControllerOrderDao
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.dao.HotkeyDao
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorLaunchArgsDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.EmulatorUpdateDao
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.OrphanedFileDao
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
import com.nendo.argosy.data.local.dao.CachedLicenseDao
import com.nendo.argosy.data.local.dao.SteamAccountDao
import com.nendo.argosy.data.local.dao.SteamDownloadQueueDao
import com.nendo.argosy.data.local.dao.SteamDownloadTrackingDao
import com.nendo.argosy.data.local.dao.SteamLicenseDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.AppCategoryEntity
import com.nendo.argosy.data.local.entity.CheatEntity
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.CachedLicenseEntity
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import com.nendo.argosy.data.local.entity.ControllerMappingEntity
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.EmulatorUpdateEntity
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.OrphanedFileEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.PinnedCollectionEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.PendingSocialSyncEntity
import com.nendo.argosy.data.local.entity.SocialGameCacheEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.local.entity.SteamAccountEntity
import com.nendo.argosy.data.local.entity.SteamCompletedDepotEntity
import com.nendo.argosy.data.local.entity.SteamCompletedFileEntity
import com.nendo.argosy.data.local.entity.SteamDownloadQueueEntity
import com.nendo.argosy.data.local.entity.SteamLicenseEntity

@Database(
    entities = [
        PlatformEntity::class,
        GameEntity::class,
        EmulatorConfigEntity::class,
        DownloadQueueEntity::class,
        SaveSyncEntity::class,
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
        PlatformLibretroSettingsEntity::class,
        EmulatorUpdateEntity::class,
        PendingSyncQueueEntity::class,
        PlaySessionEntity::class,
        SocialGameCacheEntity::class,
        PendingSocialSyncEntity::class,
        CoreOptionOverrideEntity::class,
        SteamAccountEntity::class,
        SteamLicenseEntity::class,
        SteamDownloadQueueEntity::class,
        CachedLicenseEntity::class,
        SteamCompletedFileEntity::class,
        SteamCompletedDepotEntity::class,
        EmulatorLaunchArgsEntity::class
    ],
    version = 110,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ALauncherDatabase : RoomDatabase() {
    abstract fun platformDao(): PlatformDao
    abstract fun gameDao(): GameDao
    abstract fun gameDiscDao(): GameDiscDao
    abstract fun emulatorConfigDao(): EmulatorConfigDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun saveSyncDao(): SaveSyncDao
    abstract fun pendingSyncQueueDao(): PendingSyncQueueDao
    abstract fun emulatorSaveConfigDao(): EmulatorSaveConfigDao
    abstract fun emulatorLaunchArgsDao(): EmulatorLaunchArgsDao
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
    abstract fun platformLibretroSettingsDao(): PlatformLibretroSettingsDao
    abstract fun emulatorUpdateDao(): EmulatorUpdateDao
    abstract fun playSessionDao(): PlaySessionDao
    abstract fun pendingSocialSyncDao(): PendingSocialSyncDao
    abstract fun socialGameCacheDao(): SocialGameCacheDao
    abstract fun coreOptionOverrideDao(): CoreOptionOverrideDao
    abstract fun steamAccountDao(): SteamAccountDao
    abstract fun steamLicenseDao(): SteamLicenseDao
    abstract fun cachedLicenseDao(): CachedLicenseDao
    abstract fun steamDownloadQueueDao(): SteamDownloadQueueDao
    abstract fun steamDownloadTrackingDao(): SteamDownloadTrackingDao

}
