package com.nendo.argosy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.cheats.CheatsDownloadObserver
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.sync.SaveSyncDownloadObserver
import com.nendo.argosy.data.update.ApkInstallManager
import android.content.Intent
import com.nendo.argosy.data.download.DownloadServiceController
import com.nendo.argosy.data.steam.SteamAuthManager
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.SteamService
import com.nendo.argosy.data.sync.SaveSyncWorker
import com.nendo.argosy.data.sync.SocialSyncWorker
import com.nendo.argosy.data.sync.SyncServiceController
import com.nendo.argosy.data.update.UpdateCheckWorker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository
import com.nendo.argosy.libretro.CoreCrashController
import com.nendo.argosy.libretro.CoreUpdateCheckWorker
import com.nendo.argosy.libretro.CompatCoreCache
import com.nendo.argosy.libretro.LibretroBuildbot
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.ui.coil.AppIconFetcher
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ArgosyApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appScope = SafeCoroutineScope(Dispatchers.IO, "ArgosyApp")

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var saveSyncDownloadObserver: SaveSyncDownloadObserver

    @Inject
    lateinit var cheatsDownloadObserver: CheatsDownloadObserver

    @Inject
    lateinit var titleIdDownloadObserver: TitleIdDownloadObserver

    @Inject
    lateinit var platformDao: PlatformDao

    @Inject
    lateinit var gameDao: GameDao

    @Inject
    lateinit var apkInstallManager: ApkInstallManager

    @Inject
    lateinit var downloadServiceController: DownloadServiceController

    @Inject
    lateinit var syncServiceController: SyncServiceController

    @Inject
    lateinit var coreManager: LibretroCoreManager

    @Inject
    lateinit var compatCoreCache: CompatCoreCache

    @Inject
    lateinit var playSessionTracker: PlaySessionTracker

    @Inject
    lateinit var coreCrashController: CoreCrashController

    @Inject
    lateinit var builtinPrefs: BuiltinEmulatorPreferencesRepository

    @Inject
    lateinit var steamAuthManager: SteamAuthManager

    @Inject
    lateinit var steamContentManager: SteamContentManager

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
        SaveSyncWorker.schedule(this)
        SocialSyncWorker.schedule(this)
        CoreUpdateCheckWorker.schedule(this)
        saveSyncDownloadObserver.start()
        cheatsDownloadObserver.start()
        titleIdDownloadObserver.start()
        downloadServiceController.start()
        syncServiceController.start()
        appScope.launch {
            val storedOverride = builtinPrefs.getArchitectureOverride().first()
            if (storedOverride != null) {
                if (LibretroBuildbot.isAbiCompatibleWithProcess(storedOverride)) {
                    LibretroBuildbot.abiOverride = storedOverride
                } else {
                    android.util.Log.w("ALauncherApp", "Stored ABI override '$storedOverride' is incompatible with process bitness; clearing")
                    builtinPrefs.setArchitectureOverride(null)
                }
            }
            coreManager.migrateAbiIfNeeded()
            coreManager.checkAndUpdateCoresIfDue()
            compatCoreCache.evictStale()
        }
        appScope.launch {
            coreCrashController.runBootDetection()
            playSessionTracker.checkOrphanedSession()
        }
        appScope.launch { gameDao.resetAllActiveSaveApplied() }
        appScope.launch { autoConnectSteam() }
        appScope.launch { steamContentManager.discoverLocalSteamGames() }
        syncPlatformSortOrders()
    }

    private suspend fun autoConnectSteam() {
        val account = steamAuthManager.getActiveAccount() ?: return
        val intent = Intent(this, SteamService::class.java).apply {
            putExtra(SteamService.EXTRA_AUTO_CONNECT, true)
        }
        startService(intent)
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
        val okHttpClient = OkHttpClient.Builder()
            .withUserCertTrust(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(AppIconFetcher.Factory(packageManager))
                add(coil.decode.SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
