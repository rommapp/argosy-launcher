package com.nendo.argosy.di

import android.content.Context
import androidx.room.Room
import com.nendo.argosy.data.local.ALauncherDatabase
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
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
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
            .addMigrations(
                ALauncherDatabase.MIGRATION_1_2,
                ALauncherDatabase.MIGRATION_2_3,
                ALauncherDatabase.MIGRATION_3_4,
                ALauncherDatabase.MIGRATION_4_5,
                ALauncherDatabase.MIGRATION_5_6,
                ALauncherDatabase.MIGRATION_6_7,
                ALauncherDatabase.MIGRATION_7_8,
                ALauncherDatabase.MIGRATION_8_9,
                ALauncherDatabase.MIGRATION_9_10,
                ALauncherDatabase.MIGRATION_10_11,
                ALauncherDatabase.MIGRATION_11_12,
                ALauncherDatabase.MIGRATION_12_13,
                ALauncherDatabase.MIGRATION_13_14,
                ALauncherDatabase.MIGRATION_14_15,
                ALauncherDatabase.MIGRATION_15_16,
                ALauncherDatabase.MIGRATION_16_17,
                ALauncherDatabase.MIGRATION_17_18,
                ALauncherDatabase.MIGRATION_18_19,
                ALauncherDatabase.MIGRATION_19_20,
                ALauncherDatabase.MIGRATION_20_21,
                ALauncherDatabase.MIGRATION_21_22,
                ALauncherDatabase.MIGRATION_22_23,
                ALauncherDatabase.MIGRATION_23_24,
                ALauncherDatabase.MIGRATION_24_25,
                ALauncherDatabase.MIGRATION_25_26,
                ALauncherDatabase.MIGRATION_26_27,
                ALauncherDatabase.MIGRATION_27_28,
                ALauncherDatabase.MIGRATION_28_29,
                ALauncherDatabase.MIGRATION_29_30,
                ALauncherDatabase.MIGRATION_30_31,
                ALauncherDatabase.MIGRATION_31_32,
                ALauncherDatabase.MIGRATION_32_33,
                ALauncherDatabase.MIGRATION_33_34,
                ALauncherDatabase.MIGRATION_34_35,
                ALauncherDatabase.MIGRATION_35_36,
                ALauncherDatabase.MIGRATION_36_37,
                ALauncherDatabase.MIGRATION_37_38,
                ALauncherDatabase.MIGRATION_38_39,
                ALauncherDatabase.MIGRATION_39_40,
                ALauncherDatabase.MIGRATION_40_41,
                ALauncherDatabase.MIGRATION_41_42,
                ALauncherDatabase.MIGRATION_42_43,
                ALauncherDatabase.MIGRATION_43_44,
                ALauncherDatabase.MIGRATION_44_45,
                ALauncherDatabase.MIGRATION_45_46,
                ALauncherDatabase.MIGRATION_46_47,
                ALauncherDatabase.MIGRATION_47_48,
                ALauncherDatabase.MIGRATION_48_49,
                ALauncherDatabase.MIGRATION_49_50,
                ALauncherDatabase.MIGRATION_50_51,
                ALauncherDatabase.MIGRATION_51_52,
                ALauncherDatabase.MIGRATION_52_53,
                ALauncherDatabase.MIGRATION_53_54,
                ALauncherDatabase.MIGRATION_54_55,
                ALauncherDatabase.MIGRATION_55_56,
                ALauncherDatabase.MIGRATION_56_57,
                ALauncherDatabase.MIGRATION_57_58,
                ALauncherDatabase.MIGRATION_58_59,
                ALauncherDatabase.MIGRATION_59_60,
                ALauncherDatabase.MIGRATION_60_61,
                ALauncherDatabase.MIGRATION_61_62,
                ALauncherDatabase.MIGRATION_62_63,
                ALauncherDatabase.MIGRATION_63_64,
                ALauncherDatabase.MIGRATION_64_65,
                ALauncherDatabase.MIGRATION_65_66,
                ALauncherDatabase.MIGRATION_66_67,
                ALauncherDatabase.MIGRATION_67_68,
                ALauncherDatabase.MIGRATION_68_69,
                ALauncherDatabase.MIGRATION_69_70,
                ALauncherDatabase.MIGRATION_70_71,
                ALauncherDatabase.MIGRATION_71_72,
                ALauncherDatabase.MIGRATION_72_73,
                ALauncherDatabase.MIGRATION_73_74,
                ALauncherDatabase.MIGRATION_74_75
            )
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
    fun provideAchievementDao(database: ALauncherDatabase): AchievementDao =
        database.achievementDao()

    @Provides
    fun provideSaveCacheDao(database: ALauncherDatabase): SaveCacheDao =
        database.saveCacheDao()

    @Provides
    fun provideStateCacheDao(database: ALauncherDatabase): StateCacheDao =
        database.stateCacheDao()

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
}
