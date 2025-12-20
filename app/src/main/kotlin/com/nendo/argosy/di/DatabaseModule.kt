package com.nendo.argosy.di

import android.content.Context
import androidx.room.Room
import com.nendo.argosy.data.local.ALauncherDatabase
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
import com.nendo.argosy.data.local.dao.OrphanedFileDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
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
                ALauncherDatabase.MIGRATION_27_28
            )
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
    fun providePendingSyncDao(database: ALauncherDatabase): PendingSyncDao =
        database.pendingSyncDao()

    @Provides
    fun provideDownloadQueueDao(database: ALauncherDatabase): DownloadQueueDao =
        database.downloadQueueDao()

    @Provides
    fun provideSaveSyncDao(database: ALauncherDatabase): SaveSyncDao =
        database.saveSyncDao()

    @Provides
    fun providePendingSaveSyncDao(database: ALauncherDatabase): PendingSaveSyncDao =
        database.pendingSaveSyncDao()

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
    fun provideOrphanedFileDao(database: ALauncherDatabase): OrphanedFileDao =
        database.orphanedFileDao()
}
