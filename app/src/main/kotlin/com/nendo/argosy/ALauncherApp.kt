package com.nendo.argosy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.launcher.GameNativeStoreSync
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.cheats.CheatsDownloadObserver
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.sync.SaveSyncDownloadObserver
import com.nendo.argosy.data.update.ApkInstallManager
import android.os.SystemClock
import com.nendo.argosy.data.download.DownloadServiceController
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
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val NEVER_REFRESHED = -1L
private const val FOREGROUND_STEAM_REFRESH_INTERVAL_MS = 60 * 60 * 1000L

@HiltAndroidApp
class ArgosyApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appScope = SafeCoroutineScope(Dispatchers.IO, "ArgosyApp")

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var lidSleepGuard: com.nendo.argosy.hardware.LidSleepGuard

    @Inject
    lateinit var backgroundGames: com.nendo.argosy.data.emulator.background.BackgroundGames

    @Inject
    lateinit var userCertStore: com.nendo.argosy.data.remote.ssl.UserCertStore

    @Inject
    lateinit var rommConnectionManager: com.nendo.argosy.data.remote.romm.RomMConnectionManager

    @Inject
    lateinit var socialSyncCoordinator: com.nendo.argosy.data.sync.SocialSyncCoordinator

    @Inject
    lateinit var saveSyncDownloadObserver: SaveSyncDownloadObserver

    @Inject
    lateinit var cheatsDownloadObserver: CheatsDownloadObserver

    @Inject
    lateinit var titleIdDownloadObserver: TitleIdDownloadObserver

    @Inject
    lateinit var platformDao: PlatformDao

    @Inject
    lateinit var biosRepository: com.nendo.argosy.data.repository.BiosRepository

    @Inject
    lateinit var appPreferencesRepository:
        com.nendo.argosy.data.preferences.AppPreferencesRepository

    @Inject
    lateinit var gameDao: GameDao

    @Inject
    lateinit var activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository

    @Inject
    lateinit var libraryDefaultPlatformMigration: com.nendo.argosy.data.repository.LibraryDefaultPlatformMigration

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
    lateinit var quayPassService: com.nendo.argosy.data.quaypass.QuayPassService

    @Inject
    lateinit var quayPassCredentialManager: com.nendo.argosy.data.quaypass.QuayPassCredentialManager

    @Suppress("unused")
    @Inject
    lateinit var quayPassAvatarSyncCoordinator: com.nendo.argosy.data.quaypass.QuayPassAvatarSyncCoordinator

    @Suppress("unused")
    @Inject
    lateinit var quayPassMessageSyncCoordinator: com.nendo.argosy.data.quaypass.QuayPassMessageSyncCoordinator

    @Suppress("unused")
    @Inject
    lateinit var quayPassFriendRequestQueueCoordinator: com.nendo.argosy.data.quaypass.QuayPassFriendRequestQueueCoordinator

    @Suppress("unused")
    @Inject
    lateinit var quayPassEncounterReporter: com.nendo.argosy.data.quaypass.QuayPassEncounterReporter

    @Suppress("unused")
    @Inject
    lateinit var quayPassWalletCoordinator: com.nendo.argosy.data.quaypass.QuayPassWalletCoordinator

    @Inject
    lateinit var accountSwitchCoordinator: com.nendo.argosy.data.sync.AccountSwitchCoordinator

    @Inject
    lateinit var pushInstallCoordinator: com.nendo.argosy.data.install.PushInstallCoordinator

    @Inject
    lateinit var siblingGroupRepository: com.nendo.argosy.data.repository.SiblingGroupRepository

    @Inject
    lateinit var siblingGroupDownloadObserver: com.nendo.argosy.data.repository.SiblingGroupDownloadObserver

    @Inject
    lateinit var raPatchDownloadObserver: com.nendo.argosy.data.repository.RAPatchDownloadObserver

    @Inject
    lateinit var raRepository: com.nendo.argosy.data.repository.RetroAchievementsRepository

    @Inject
    lateinit var variantFileCleanup: com.nendo.argosy.data.emulator.VariantFileCleanup

    @Inject
    lateinit var lockScreenArtManager: com.nendo.argosy.data.wallpaper.LockScreenArtManager

    @Inject
    lateinit var iconPackRepository: com.nendo.argosy.data.iconpack.IconPackRepository

    @Inject
    lateinit var homeAppAvailability: com.nendo.argosy.util.HomeAppAvailability

    @Inject
    lateinit var appsRepository: AppsRepository

    @Inject
    lateinit var gameNativeStoreSync: GameNativeStoreSync

    private val lastForegroundSteamRefreshAt = AtomicLong(NEVER_REFRESHED)

    private val foregroundObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
        override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
            appScope.launch { quayPassCredentialManager.refreshIfNeeded() }
            appScope.launch { refreshSteamInstallationsIfStale(SystemClock.elapsedRealtime()) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { userCertStore.initialize() }
        appScope.launch { com.nendo.argosy.util.RootShell.isAvailable }
        appScope.launch { socialSyncCoordinator.discardQueueWithoutSocialAccount() }
        appScope.launch { variantFileCleanup.runOnce() }
        appScope.launch { homeAppAvailability.applyAtStartup() }
        UpdateCheckWorker.schedule(this)
        SaveSyncWorker.schedule(this)
        SocialSyncWorker.schedule(this)
        CoreUpdateCheckWorker.schedule(this)
        com.nendo.argosy.data.quaypass.QuayPassCredentialRefreshWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        appScope.launch { observeSteamPackageChanges() }
        saveSyncDownloadObserver.start()
        cheatsDownloadObserver.start()
        titleIdDownloadObserver.start()
        downloadServiceController.start()
        syncServiceController.start()
        pushInstallCoordinator.start()
        siblingGroupRepository.start()
        lidSleepGuard.start()
        backgroundGames.start()
        siblingGroupDownloadObserver.start()
        raPatchDownloadObserver.start()
        appScope.launch {
            if (raRepository.isLoggedIn()) {
                com.nendo.argosy.data.sync.RAPatchBackfillWorker.schedule(this@ArgosyApp)
            }
        }
        lockScreenArtManager.start()
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
            compatCoreCache.evictStale()
        }
        appScope.launch {
            coreCrashController.runBootDetection()
            playSessionTracker.checkOrphanedSession()
        }
        appScope.launch { accountSwitchCoordinator.resumeIfInterrupted() }
        appScope.launch { activeSaveRepository.resetAllActiveSaveApplied() }
        appScope.launch { libraryDefaultPlatformMigration.run() }
        appScope.launch { deleteRetiredSteamDownloadDirs() }
        appScope.launch { biosRepository.purgeRetiredFirmware() }
        syncPlatformSortOrders()
    }

    private fun deleteRetiredSteamDownloadDirs() {
        AppPaths.retiredSteamDownloadDirs(filesDir)
            .filter { it.exists() }
            .forEach { dir ->
                if (!dir.deleteRecursively()) {
                    Logger.warn("ArgosyApp", "Could not fully remove retired Steam download dir ${dir.name}")
                }
            }
    }

    internal suspend fun refreshSteamInstallationsIfStale(now: Long) {
        val last = lastForegroundSteamRefreshAt.get()
        if (last != NEVER_REFRESHED && now - last < FOREGROUND_STEAM_REFRESH_INTERVAL_MS) return
        if (!lastForegroundSteamRefreshAt.compareAndSet(last, now)) return
        try {
            gameNativeStoreSync.refreshSteamInstallations()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error("ArgosyApp", "Failed to refresh Steam installations on foreground", e)
        }
    }

    internal suspend fun observeSteamPackageChanges() {
        appsRepository.packageChanges.collect { packageName ->
            if (SteamLaunchers.getByPackage(packageName) != null) {
                try {
                    gameNativeStoreSync.refreshSteamInstallations()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.error("ArgosyApp", "Failed to refresh Steam installations after package change", e)
                }
            }
        }
    }

    /**
     * Brings stored platform order back in line with the built-in one, unless the user has arranged
     * it themselves, in which case their order is what should survive a relaunch.
     */
    private fun syncPlatformSortOrders() {
        appScope.launch {
            if (appPreferencesRepository.isPlatformOrderCustomised()) return@launch
            PlatformDefinitions.getAll().forEach { def ->
                val platform = platformDao.getBySlug(def.slug) ?: return@forEach
                if (platform.sortOrder != def.sortOrder) {
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
            .addInterceptor(
                com.nendo.argosy.data.remote.romm.RomMImageAuthInterceptor(rommConnectionManager)
            )
            .withUserCertTrust(userCertStore)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(AppIconFetcher.Factory(packageManager, iconPackRepository))
                add(coil.decode.SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
