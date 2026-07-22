package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.emulator.DriverFetcherRepository
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.repository.DatabaseAdminRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.libretro.frame.FrameRegistry
import com.nendo.argosy.libretro.shader.ShaderRegistry
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.screens.settings.CachesClearTarget
import com.nendo.argosy.ui.screens.settings.StorageCachesState
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StorageCachesDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseAdminRepository: DatabaseAdminRepository,
    private val emulatorDownloadManager: EmulatorDownloadManager,
    private val driverFetcherRepository: DriverFetcherRepository,
    private val socialRepository: SocialRepository,
    private val steamContentManager: SteamContentManager,
    private val soundFeedbackManager: SoundFeedbackManager,
    private val frameRegistry: FrameRegistry,
    private val attributionRepository: StorageAttributionRepository,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(StorageCachesState())
    val state: StateFlow<StorageCachesState> = _state.asStateFlow()

    private var stagingWalkRunning = false

    fun refreshOnOpen(scope: CoroutineScope) {
        _state.update { it.copy(steamDownloadBusy = steamContentManager.hasBlockingDownloadState()) }
        refreshSteamStagingBytes(scope)
    }

    private fun refreshSteamStagingBytes(scope: CoroutineScope) {
        if (stagingWalkRunning) return
        stagingWalkRunning = true
        scope.launch(Dispatchers.IO) {
            try {
                val roots = listOf(
                    AppPaths.steamStagingRoot(context.filesDir),
                    File(context.filesDir, "steam_downloads")
                )
                val bytes = roots.sumOf { root ->
                    if (root.exists()) {
                        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else 0L
                }
                _state.update { it.copy(steamStagingBytes = bytes) }
            } finally {
                stagingWalkRunning = false
            }
        }
    }

    fun requestClear(target: CachesClearTarget, driverDownloadActive: Boolean = false) {
        if (target in _state.value.busyClears) return
        val blockedReason = when (target) {
            CachesClearTarget.EMULATOR_APKS ->
                "Wait for the emulator download to finish first".takeIf { emulatorDownloadManager.hasActiveDownload() }
            CachesClearTarget.MISC_DOWNLOADS ->
                "Wait for the driver download to finish first".takeIf { driverDownloadActive }
            CachesClearTarget.STEAM_DOWNLOADS ->
                "Cancel Steam downloads first".takeIf { steamContentManager.hasBlockingDownloadState() }
            else -> null
        }
        if (blockedReason != null) {
            _state.update { it.copy(steamDownloadBusy = steamContentManager.hasBlockingDownloadState()) }
            notificationManager.showError(blockedReason)
            return
        }
        _state.update { it.copy(pendingClear = target) }
    }

    fun cancelClear() {
        _state.update { it.copy(pendingClear = null) }
    }

    /** Runs the pending clear on IO; [onCleared] fires on success with the completed target. */
    fun confirmClear(scope: CoroutineScope, onCleared: (CachesClearTarget) -> Unit = {}) {
        val target = _state.value.pendingClear ?: return
        _state.update { it.copy(pendingClear = null, busyClears = it.busyClears + target) }
        scope.launch {
            val succeeded = withContext(Dispatchers.IO) { runClear(target) }
            _state.update { it.copy(busyClears = it.busyClears - target) }
            if (succeeded) {
                if (target == CachesClearTarget.STEAM_DOWNLOADS) refreshSteamStagingBytes(scope)
                attributionRepository.refreshOnOpen()
                onCleared(target)
            }
        }
    }

    private suspend fun runClear(target: CachesClearTarget): Boolean = when (target) {
        CachesClearTarget.IMAGE_CACHE -> {
            databaseAdminRepository.clearImageCache()
            true
        }
        CachesClearTarget.ROM_EXTRACTION -> {
            val performed = databaseAdminRepository.clearRomExtractionCache()
            if (!performed) notificationManager.showError("Cannot clear extracted ROMs while a game is running")
            performed
        }
        CachesClearTarget.SFX_CACHE -> {
            soundFeedbackManager.clearSfxCache()
            true
        }
        CachesClearTarget.EMULATOR_APKS -> {
            val performed = emulatorDownloadManager.clearApkCache()
            if (!performed) notificationManager.showError("Wait for the emulator download to finish first")
            performed
        }
        CachesClearTarget.MISC_DOWNLOADS -> {
            socialRepository.clearPresenceCovers()
            driverFetcherRepository.clearDownloadedDrivers()
            true
        }
        CachesClearTarget.SHADERS_CATALOG -> {
            ShaderRegistry(context).clearCatalog()
            attributionRepository.markDirty(StorageCategory.SHADERS_CATALOG)
            true
        }
        CachesClearTarget.FRAMES -> {
            frameRegistry.clearDownloadedFrames()
            attributionRepository.markDirty(StorageCategory.FRAMES)
            true
        }
        CachesClearTarget.STEAM_DOWNLOADS -> {
            val performed = steamContentManager.clearDownloadData()
            if (!performed) {
                _state.update { it.copy(steamDownloadBusy = true) }
                notificationManager.showError("Cancel Steam downloads first")
            }
            performed
        }
    }
}
