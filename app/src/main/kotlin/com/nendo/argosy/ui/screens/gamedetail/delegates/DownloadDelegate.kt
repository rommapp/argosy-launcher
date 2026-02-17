package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.gamedetail.ExtractionFailedInfo
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.gamedetail.LaunchEvent
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadUiState(
    val downloadStatus: GameDownloadStatus = GameDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val downloadSizeBytes: Long? = null,
    val isRefreshingGameData: Boolean = false,
    val showExtractionFailedPrompt: Boolean = false,
    val extractionFailedInfo: ExtractionFailedInfo? = null,
    val extractionPromptFocusIndex: Int = 0,
    val showMissingDiscPrompt: Boolean = false,
    val missingDiscNumbers: List<Int> = emptyList()
)

@Singleton
class DownloadDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val gameActions: GameActionsDelegate,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager,
    private val romMRepository: RomMRepository,
    private val apkInstallManager: ApkInstallManager,
    private val playStoreService: PlayStoreService,
    private val imageCacheManager: com.nendo.argosy.data.cache.ImageCacheManager,
    private val gameRepository: com.nendo.argosy.data.repository.GameRepository
) {
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private val _launchEvents = MutableSharedFlow<LaunchEvent>()
    val launchEvents: SharedFlow<LaunchEvent> = _launchEvents.asSharedFlow()

    fun reset() {
        _state.value = DownloadUiState()
    }

    fun updateDownloadStatus(status: GameDownloadStatus, progress: Float) {
        _state.update { it.copy(downloadStatus = status, downloadProgress = progress) }
    }

    fun updateDownloadSize(sizeBytes: Long?) {
        _state.update { it.copy(downloadSizeBytes = sizeBytes) }
    }

    fun observeDownloads(scope: CoroutineScope, gameIdProvider: () -> Long, onCompleted: (Long) -> Unit) {
        scope.launch {
            downloadManager.state.collect { queueState ->
                val gameId = gameIdProvider()
                if (gameId == 0L) return@collect

                val activeDownload = queueState.activeDownloads.find { it.gameId == gameId }
                val queued = queueState.queue.find { it.gameId == gameId }
                val completed = queueState.completed.find { it.gameId == gameId }

                val result: Pair<GameDownloadStatus, Float>? = when {
                    activeDownload?.state == DownloadState.EXTRACTING -> {
                        GameDownloadStatus.EXTRACTING to activeDownload.extractionPercent
                    }
                    activeDownload != null -> {
                        GameDownloadStatus.DOWNLOADING to activeDownload.progressPercent
                    }
                    queued?.state == DownloadState.EXTRACTING -> {
                        GameDownloadStatus.EXTRACTING to queued.extractionPercent
                    }
                    queued?.state == DownloadState.PAUSED -> {
                        GameDownloadStatus.PAUSED to queued.progressPercent
                    }
                    queued?.state == DownloadState.WAITING_FOR_STORAGE -> {
                        GameDownloadStatus.WAITING_FOR_STORAGE to 0f
                    }
                    queued != null -> {
                        GameDownloadStatus.QUEUED to 0f
                    }
                    completed?.state == DownloadState.COMPLETED -> {
                        onCompleted(gameId)
                        GameDownloadStatus.DOWNLOADED to 1f
                    }
                    completed?.state == DownloadState.FAILED -> {
                        GameDownloadStatus.NOT_DOWNLOADED to 0f
                    }
                    else -> {
                        val currentStatus = _state.value.downloadStatus
                        if (currentStatus != GameDownloadStatus.NOT_DOWNLOADED) {
                            currentStatus to _state.value.downloadProgress
                        } else {
                            null
                        }
                    }
                }

                if (result != null) {
                    val (status, progress) = result
                    _state.update { it.copy(downloadStatus = status, downloadProgress = progress) }
                }
            }
        }
    }

    fun downloadGame(scope: CoroutineScope, gameId: Long, pageLoadTime: Long, pageLoadDebounceMs: Long) {
        val now = System.currentTimeMillis()
        if (now - pageLoadTime < pageLoadDebounceMs) return
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
                    _state.update {
                        it.copy(
                            showExtractionFailedPrompt = true,
                            extractionFailedInfo = ExtractionFailedInfo(
                                gameId = result.gameId,
                                fileName = result.fileName,
                                errorReason = result.errorReason
                            ),
                            extractionPromptFocusIndex = 0
                        )
                    }
                    soundManager.play(SoundType.OPEN_MODAL)
                }
            }
        }
    }

    fun dismissExtractionPrompt() {
        _state.update {
            it.copy(
                showExtractionFailedPrompt = false,
                extractionFailedInfo = null,
                extractionPromptFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveExtractionPromptFocus(delta: Int) {
        _state.update { state ->
            val newIndex = (state.extractionPromptFocusIndex + delta).coerceIn(0, 1)
            state.copy(extractionPromptFocusIndex = newIndex)
        }
    }

    fun confirmExtractionPromptSelection(scope: CoroutineScope) {
        val info = _state.value.extractionFailedInfo ?: return
        val focusIndex = _state.value.extractionPromptFocusIndex

        dismissExtractionPrompt()

        scope.launch {
            when (focusIndex) {
                0 -> {
                    when (val result = gameActions.retryExtraction(info.gameId)) {
                        is DownloadResult.Queued -> notificationManager.showSuccess("Extraction succeeded")
                        is DownloadResult.Error -> notificationManager.showError(result.message)
                        else -> { }
                    }
                }
                1 -> {
                    when (val result = gameActions.redownload(info.gameId)) {
                        is DownloadResult.Queued -> notificationManager.showSuccess("Redownload started")
                        is DownloadResult.Error -> notificationManager.showError(result.message)
                        else -> { }
                    }
                }
            }
        }
    }

    fun dismissMissingDiscPrompt() {
        _state.update { it.copy(showMissingDiscPrompt = false, missingDiscNumbers = emptyList()) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun showMissingDiscPrompt(missingDiscNumbers: List<Int>) {
        _state.update {
            it.copy(
                showMissingDiscPrompt = true,
                missingDiscNumbers = missingDiscNumbers
            )
        }
    }

    fun repairAndPlay(scope: CoroutineScope, gameId: Long) {
        scope.launch {
            _state.update { it.copy(showMissingDiscPrompt = false, missingDiscNumbers = emptyList()) }

            when (val result = gameActions.repairMissingDiscs(gameId)) {
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} missing discs")
                }
                is DownloadResult.Queued -> { }
                is DownloadResult.AlreadyDownloaded -> { }
                is DownloadResult.Error -> notificationManager.showError(result.message)
                is DownloadResult.ExtractionFailed -> { }
            }
        }
    }

    fun downloadUpdateFile(
        scope: CoroutineScope,
        gameId: Long,
        file: UpdateFileUi,
        gameTitle: String,
        platformSlug: String,
        coverPath: String?
    ) {
        val gameFileId = file.gameFileId ?: return
        val rommFileId = file.rommFileId ?: return

        scope.launch {
            downloadManager.enqueueGameFileDownload(
                gameId = gameId,
                gameFileId = gameFileId,
                rommFileId = rommFileId,
                fileName = file.fileName,
                category = file.type.name.lowercase(),
                gameTitle = gameTitle,
                platformSlug = platformSlug,
                coverPath = coverPath,
                expectedSizeBytes = file.sizeBytes
            )
            notificationManager.showSuccess("Download queued: ${file.fileName}")
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

    fun deleteLocalFile(scope: CoroutineScope, gameId: Long, isSteamGame: Boolean, onGameDeleted: () -> Unit) {
        scope.launch {
            gameActions.deleteLocalFile(gameId)
            if (isSteamGame) {
                notificationManager.showSuccess("Game removed")
                _launchEvents.emit(LaunchEvent.NavigateBack)
            } else {
                notificationManager.showSuccess("Download deleted")
                onGameDeleted()
            }
        }
    }

    fun uninstallAndroidApp(scope: CoroutineScope, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        scope.launch {
            _launchEvents.emit(LaunchEvent.LaunchIntent(intent))
        }
    }

    fun refreshGameData(scope: CoroutineScope, gameId: Long, onSuccess: () -> Unit) {
        if (_state.value.isRefreshingGameData) return
        scope.launch {
            _state.update { it.copy(isRefreshingGameData = true) }
            when (val result = gameActions.refreshGameData(gameId)) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    onSuccess()
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _state.update { it.copy(isRefreshingGameData = false) }
        }
    }

    fun refreshAndroidAppData(scope: CoroutineScope, gameId: Long, packageName: String, onSuccess: () -> Unit) {
        if (_state.value.isRefreshingGameData) return

        scope.launch {
            _state.update { it.copy(isRefreshingGameData = true) }
            try {
                val details = playStoreService.getAppDetails(packageName).getOrNull()
                if (details != null) {
                    val game = gameRepository.getById(gameId)
                    if (game != null) {
                        val updated = game.copy(
                            description = details.description ?: game.description,
                            developer = details.developer ?: game.developer,
                            genre = details.genre ?: game.genre,
                            rating = details.ratingPercent ?: game.rating,
                            screenshotPaths = details.screenshotUrls.takeIf { it.isNotEmpty() }?.joinToString(",") ?: game.screenshotPaths,
                            backgroundPath = details.screenshotUrls.firstOrNull() ?: game.backgroundPath
                        )
                        gameRepository.update(updated)

                        details.coverUrl?.let { url ->
                            imageCacheManager.queueCoverCacheByGameId(url, gameId)
                        }
                        if (details.screenshotUrls.isNotEmpty()) {
                            imageCacheManager.queueScreenshotCacheByGameId(gameId, details.screenshotUrls)
                        }

                        notificationManager.showSuccess("Game data refreshed")
                        onSuccess()
                    }
                } else {
                    notificationManager.showError("Could not fetch app data")
                }
            } catch (e: Exception) {
                notificationManager.showError("Failed to refresh: ${e.message}")
            }
            _state.update { it.copy(isRefreshingGameData = false) }
        }
    }

    fun refreshDownloadSizeInBackground(scope: CoroutineScope, rommId: Long, gameId: Long) {
        scope.launch {
            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> {
                    val rom = result.data
                    val mainFile = rom.files
                        ?.filter { it.category == null && !it.fileName.startsWith(".") }
                        ?.maxByOrNull { it.fileSizeBytes }
                    val sizeBytes = mainFile?.fileSizeBytes ?: rom.fileSize
                    if (sizeBytes > 0) {
                        gameRepository.updateFileSize(gameId, sizeBytes)
                        _state.update { state ->
                            state.copy(downloadSizeBytes = sizeBytes)
                        }
                    }
                }
                else -> { }
            }
        }
    }
}
