package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.ui.common.appId
import com.nendo.argosy.ui.common.toIndicator
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeDownloadDelegate @Inject constructor(
    private val downloadManager: DownloadManager,
    private val gameActions: GameActionsDelegate,
    private val apkInstallManager: ApkInstallManager,
    private val notificationManager: NotificationManager,
    private val steamContentManager: SteamContentManager,
    private val gameRepository: GameRepository
) {
    private val rommIndicators = MutableStateFlow<Map<Long, GameDownloadIndicator>>(emptyMap())
    private val steamIndicators = MutableStateFlow<Map<Long, GameDownloadIndicator>>(emptyMap())
    private val _downloadIndicators = MutableStateFlow<Map<Long, GameDownloadIndicator>>(emptyMap())
    val downloadIndicators: StateFlow<Map<Long, GameDownloadIndicator>> = _downloadIndicators.asStateFlow()

    private val completedGameIds = mutableSetOf<Long>()
    private var lastDownloadQueueTime = 0L
    private val downloadQueueDebounceMs = 300L

    private fun mergeIndicators() {
        _downloadIndicators.value = rommIndicators.value + steamIndicators.value
    }

    fun observeDownloadState(scope: CoroutineScope, onNewlyCompleted: suspend () -> Unit) {
        scope.launch {
            downloadManager.state.collect { downloadState ->
                val indicators = mutableMapOf<Long, GameDownloadIndicator>()

                downloadState.activeDownloads.forEach { download ->
                    indicators[download.gameId] = when (download.state) {
                        DownloadState.EXTRACTING -> GameDownloadIndicator(isExtracting = true, progress = download.extractionPercent)
                        else -> GameDownloadIndicator(isDownloading = true, progress = download.progressPercent)
                    }
                }

                downloadState.queue.forEach { download ->
                    if (download.gameId in indicators) return@forEach
                    val indicator = download.state.toIndicator(
                        download.progressPercent, download.extractionPercent
                    )
                    if (indicator.isActive) {
                        indicators[download.gameId] = indicator
                    }
                }

                val newlyCompleted = downloadState.completed
                    .map { it.gameId }
                    .filter { it !in completedGameIds }

                if (newlyCompleted.isNotEmpty()) {
                    completedGameIds.addAll(newlyCompleted)
                    onNewlyCompleted()
                }

                rommIndicators.value = indicators
                mergeIndicators()
            }
        }

        scope.launch {
            steamContentManager.downloadState.collect { steamState ->
                if (steamState is SteamDownloadState.Idle) {
                    steamIndicators.value = emptyMap()
                    mergeIndicators()
                    return@collect
                }
                val appId = steamState.appId ?: return@collect
                val game = gameRepository.getBySteamAppId(appId) ?: return@collect
                val activeDl = steamContentManager.activeDownload.value
                val progress = activeDl?.progress ?: when (steamState) {
                    is SteamDownloadState.Paused -> steamState.progress
                    else -> 0f
                }
                val indicator = steamState.toIndicator(progress)
                if (indicator != null) {
                    steamIndicators.value = mapOf(game.id to indicator)
                } else {
                    if (steamState is SteamDownloadState.Completed) onNewlyCompleted()
                    steamIndicators.value = emptyMap()
                }
                mergeIndicators()
            }
        }
    }

    fun queueDownload(scope: CoroutineScope, gameId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastDownloadQueueTime < downloadQueueDebounceMs) return
        lastDownloadQueueTime = now

        scope.launch {
            when (val result = gameActions.queueDownload(gameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.AlreadyDownloaded -> {
                    notificationManager.showSuccess("Game already downloaded")
                }
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} discs")
                }
                is DownloadResult.Error -> notificationManager.showError(result.message)
                is DownloadResult.ExtractionFailed -> {
                    notificationManager.showError("Extraction failed. Open game details to retry.")
                }
            }
        }
    }

    fun installApk(scope: CoroutineScope, gameId: Long) {
        scope.launch {
            val success = apkInstallManager.installApkForGame(gameId)
            if (!success) {
                notificationManager.showError("Could not install APK")
            }
        }
    }

    fun resumeDownload(gameId: Long) {
        downloadManager.resumeDownload(gameId)
    }

    fun deleteLocalFile(scope: CoroutineScope, gameId: Long, onComplete: suspend () -> Unit) {
        scope.launch {
            gameActions.deleteLocalFile(gameId)
            notificationManager.showSuccess("Download deleted")
            onComplete()
        }
    }
}
