package com.nendo.argosy.di

import android.content.Context
import androidx.room.Room
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncDao
import com.nendo.argosy.data.local.dao.PlatformDao
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
                ALauncherDatabase.MIGRATION_6_7
            )
            .build()
    }

    @Provides
    fun providePlatformDao(database: ALauncherDatabase): PlatformDao = database.platformDao()

    @Provides
    fun provideGameDao(database: ALauncherDatabase): GameDao = database.gameDao()

    @Provides
    fun provideEmulatorConfigDao(database: ALauncherDatabase): EmulatorConfigDao =
        database.emulatorConfigDao()

    @Provides
    fun providePendingSyncDao(database: ALauncherDatabase): PendingSyncDao =
        database.pendingSyncDao()
}
