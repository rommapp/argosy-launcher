package com.nendo.argosy.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nendo.argosy.data.preferences.AccountPreferenceStoreRegistry
import com.nendo.argosy.data.preferences.AccountScopedDataStore
import com.nendo.argosy.data.preferences.dataStore
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileAccessLayerImpl
import com.nendo.argosy.data.sync.SyncNotificationCopyResources
import com.nendo.argosy.domain.usecase.sync.SyncNotificationCopy
import com.nendo.argosy.hardware.AyaneoLEDController
import com.nendo.argosy.hardware.LEDController
import com.nendo.argosy.hardware.OdinLEDController
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindFileAccessLayer(impl: FileAccessLayerImpl): FileAccessLayer

    @Binds
    @Singleton
    abstract fun bindSyncNotificationCopy(impl: SyncNotificationCopyResources): SyncNotificationCopy

    companion object {

        @Provides
        @Singleton
        fun provideMoshi(): Moshi = Moshi.Builder().build()

        @Provides
        @Singleton
        fun provideAccountPreferenceStoreRegistry(
            @ApplicationContext context: Context
        ): AccountPreferenceStoreRegistry = AccountPreferenceStoreRegistry(
            context = context,
            globalStore = context.dataStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )

        @Provides
        @Singleton
        fun provideDataStore(
            @ApplicationContext context: Context,
            registry: AccountPreferenceStoreRegistry
        ): DataStore<Preferences> = AccountScopedDataStore(context.dataStore, registry)

        @Provides
        @Singleton
        fun provideLEDController(@ApplicationContext context: Context): LEDController {
            val isAyaneo = android.os.Build.MANUFACTURER.equals("AYANEO", ignoreCase = true) ||
                android.os.Build.BRAND.equals("AYANEO", ignoreCase = true)
            return if (isAyaneo) AyaneoLEDController(context) else OdinLEDController()
        }
    }
}
