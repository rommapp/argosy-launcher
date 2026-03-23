package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
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
    private val gameDao: GameDao
) {
    private val _downloadIndicators = MutableStateFlow<Map<Long, GameDownloadIndicator>>(emptyMap())
    val downloadIndicators: StateFlow<Map<Long, GameDownloadIndicator>> = _downloadIndicators.asStateFlow()

    private val completedGameIds = mutableSetOf<Long>()
    private var lastDownloadQueueTime = 0L
    private val downloadQueueDebounceMs = 300L

    fun observeDownloadState(scope: CoroutineScope, onNewlyCompleted: suspend () -> Unit) {
        scope.launch {
            downloadManager.state.collect { downloadState ->
                val indicators = mutableMapOf<Long, GameDownloadIndicator>()

                downloadState.activeDownloads.forEach { download ->
                    val isExtracting = download.state == DownloadState.EXTRACTING
                    indicators[download.gameId] = GameDownloadIndicator(
                        isDownloading = !isExtracting,
                        isExtracting = isExtracting,
                        progress = if (isExtracting) download.extractionPercent else download.progressPercent
                    )
                }

                downloadState.queue.forEach { download ->
                    val isExtracting = download.state == DownloadState.EXTRACTING
                    val isPaused = download.state == DownloadState.PAUSED
                    val isQueued = download.state == DownloadState.QUEUED
                    if (isExtracting || isPaused || isQueued) {
                        indicators[download.gameId] = GameDownloadIndicator(
                            isDownloading = false,
                            isExtracting = isExtracting,
                            isPaused = isPaused,
                            isQueued = isQueued,
                            progress = if (isExtracting) download.extractionPercent else download.progressPercent
                        )
                    }
                }

                val newlyCompleted = downloadState.completed
                    .map { it.gameId }
                    .filter { it !in completedGameIds }

                if (newlyCompleted.isNotEmpty()) {
                    completedGameIds.addAll(newlyCompleted)
                    onNewlyCompleted()
                }

                _downloadIndicators.value = indicators
            }
        }

        // Steam downloads -- observe downloadState for pause persistence
        scope.launch {
            steamContentManager.downloadState.collect { steamState ->
                val appId = when (steamState) {
                    is SteamDownloadState.Preparing -> steamState.appId
                    is SteamDownloadState.Downloading -> steamState.appId
                    is SteamDownloadState.Moving -> steamState.appId
                    is SteamDownloadState.Paused -> steamState.appId
                    is SteamDownloadState.Completed -> steamState.appId
                    is SteamDownloadState.Failed -> steamState.appId
                    is SteamDownloadState.Idle -> null
                }
                if (appId == null) return@collect
                val game = gameDao.getBySteamAppId(appId) ?: return@collect
                val activeDl = steamContentManager.activeDownload.value
                val progress = activeDl?.progress ?: when (steamState) {
                    is SteamDownloadState.Paused -> steamState.progress
                    else -> 0f
                }
                val indicator = when (steamState) {
                    is SteamDownloadState.Preparing -> GameDownloadIndicator(isQueued = true)
                    is SteamDownloadState.Downloading -> GameDownloadIndicator(
                        isDownloading = true,
                        progress = progress
                    )
                    is SteamDownloadState.Moving -> GameDownloadIndicator(
                        isExtracting = true,
                        progress = 1f
                    )
                    is SteamDownloadState.Paused -> GameDownloadIndicator(
                        isPaused = true,
                        progress = progress
                    )
                    is SteamDownloadState.Completed -> {
                        onNewlyCompleted()
                        null
                    }
                    is SteamDownloadState.Failed, is SteamDownloadState.Idle -> null
                }
                if (indicator != null) {
                    _downloadIndicators.value = _downloadIndicators.value + (game.id to indicator)
                } else {
                    _downloadIndicators.value = _downloadIndicators.value - game.id
                }
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
