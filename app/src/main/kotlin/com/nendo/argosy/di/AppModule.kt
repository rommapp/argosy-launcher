package com.nendo.argosy.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.preferences.dataStore
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileAccessLayerImpl
import com.nendo.argosy.hardware.LEDController
import com.nendo.argosy.hardware.OdinLEDController
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        dataStore: DataStore<Preferences>
    ): UserPreferencesRepository {
        return UserPreferencesRepository(dataStore)
    }

    @Provides
    @Singleton
    fun provideLEDController(): LEDController {
        return OdinLEDController()
    }

    @Provides
    @Singleton
    fun provideFileAccessLayer(impl: FileAccessLayerImpl): FileAccessLayer = impl
}
