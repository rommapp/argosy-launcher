package com.nendo.argosy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.data.sync.SaveSyncDownloadObserver
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.data.download.DownloadServiceController
import com.nendo.argosy.data.sync.SaveSyncWorker
import com.nendo.argosy.data.update.UpdateCheckWorker
import com.nendo.argosy.ui.coil.AppIconFetcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ArgosyApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var saveSyncDownloadObserver: SaveSyncDownloadObserver

    @Inject
    lateinit var platformDao: PlatformDao

    @Inject
    lateinit var apkInstallManager: ApkInstallManager

    @Inject
    lateinit var downloadServiceController: DownloadServiceController

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
        SaveSyncWorker.schedule(this)
        saveSyncDownloadObserver.start()
        downloadServiceController.start()
        syncPlatformSortOrders()
    }

    private fun syncPlatformSortOrders() {
        appScope.launch {
            PlatformDefinitions.getAll().forEach { def ->
                platformDao.getBySlug(def.slug)?.let { platform ->
                    platformDao.updateSortOrder(platform.id, def.sortOrder)
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        val trustUserCerts = runBlocking {
            userPreferencesRepository.preferences.first().trustUserCertificates
        }

        val okHttpClient = OkHttpClient.Builder()
            .withUserCertTrust(trustUserCerts)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(AppIconFetcher.Factory(packageManager))
            }
            .crossfade(true)
            .build()
    }
}
