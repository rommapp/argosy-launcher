package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.model.FilePickerRow
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.ui.common.toDownloadStatus
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.gamedetail.ExtractionFailedInfo
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.gamedetail.LaunchEvent
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
import java.io.File
import javax.inject.Inject

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

private val RESETTABLE_STATUSES = setOf(
    GameDownloadStatus.QUEUED,
    GameDownloadStatus.WAITING_FOR_STORAGE,
    GameDownloadStatus.DOWNLOADING,
    GameDownloadStatus.EXTRACTING,
    GameDownloadStatus.PAUSED,
    GameDownloadStatus.FAILED
)

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
    private val gameRepository: com.nendo.argosy.data.repository.GameRepository,
    private val steamContentManager: SteamContentManager,
    private val gameFileDao: com.nendo.argosy.data.local.dao.GameFileDao,
    private val filePickerFlow: com.nendo.argosy.domain.usecase.download.FilePickerFlowUseCase
) {
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private val _launchEvents = MutableSharedFlow<LaunchEvent>()
    val launchEvents: SharedFlow<LaunchEvent> = _launchEvents.asSharedFlow()

    private var trackedDownloadGameId: Long = 0L

    fun reset() {
        trackedDownloadGameId = 0L
        _state.value = DownloadUiState()
    }

    fun updateDownloadStatus(status: GameDownloadStatus, progress: Float) {
        _state.update { it.copy(downloadStatus = status, downloadProgress = progress) }
    }

    fun updateDownloadSize(sizeBytes: Long?) {
        _state.update { it.copy(downloadSizeBytes = sizeBytes) }
    }

    fun observeDownloads(scope: CoroutineScope, gameIdProvider: () -> Long, onCompleted: (Long) -> Unit) {
        // RomM downloads
        scope.launch {
            downloadManager.state.collect { queueState ->
                val gameId = gameIdProvider()
                if (gameId == 0L) return@collect

                val activeDownload = queueState.activeDownloads.find { it.gameId == gameId }
                val queued = queueState.queue.find { it.gameId == gameId }
                val completed = queueState.completed.filter { it.gameId == gameId }.maxByOrNull { it.id }

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
                        GameDownloadStatus.WAITING_FOR_STORAGE to queued.progressPercent
                    }
                    queued != null -> {
                        GameDownloadStatus.QUEUED to 0f
                    }
                    completed?.state == DownloadState.COMPLETED -> {
                        onCompleted(gameId)
                        GameDownloadStatus.DOWNLOADED to 1f
                    }
                    completed?.state == DownloadState.FAILED -> {
                        GameDownloadStatus.FAILED to 0f
                    }
                    else -> {
                        val currentStatus = _state.value.downloadStatus
                        when {
                            trackedDownloadGameId == gameId && currentStatus in RESETTABLE_STATUSES -> {
                                GameDownloadStatus.NOT_DOWNLOADED to 0f
                            }
                            currentStatus != GameDownloadStatus.NOT_DOWNLOADED -> {
                                currentStatus to _state.value.downloadProgress
                            }
                            else -> null
                        }
                    }
                }

                trackedDownloadGameId = if (
                    activeDownload != null || queued != null || completed?.state == DownloadState.FAILED
                ) gameId else 0L

                if (result != null) {
                    val (status, progress) = result
                    _state.update { it.copy(downloadStatus = status, downloadProgress = progress) }
                }
            }
        }

        // Steam downloads
        scope.launch {
            steamContentManager.activeDownload.collect { steamDownload ->
                val gameId = gameIdProvider()
                if (gameId == 0L) return@collect

                val game = gameRepository.getById(gameId) ?: return@collect
                val steamAppId = game.steamAppId

                if (steamDownload == null || steamAppId == null || steamDownload.appId != steamAppId) {
                    return@collect
                }

                val result = steamDownload.state.toDownloadStatus(steamDownload.progress)
                    ?: return@collect
                if (steamDownload.state is SteamDownloadState.Completed) onCompleted(gameId)

                val (status, progress) = result
                _state.update { it.copy(downloadStatus = status, downloadProgress = progress) }
            }
        }
    }

    suspend fun buildFilePickerRows(gameId: Long): Triple<List<FilePickerRow>, Set<Long>, Set<Long>>? {
        val setup = filePickerFlow.buildRows(gameId) ?: return null
        return Triple(setup.rows, setup.preselectedFileIds, setup.preselectedVersionIds)
    }

    suspend fun buildManageRows(gameId: Long): Triple<List<FilePickerRow>, Set<Long>, Set<Long>>? {
        val setup = filePickerFlow.buildManageRows(gameId) ?: return null
        return Triple(setup.rows, setup.preselectedFileIds, setup.preselectedVersionIds)
    }

    fun applyManagedFiles(
        scope: CoroutineScope,
        gameId: Long,
        rows: List<FilePickerRow>,
        selected: Set<Long>
    ) {
        scope.launch {
            val (added, removed) = filePickerFlow.applyManagedSelection(gameId, rows, selected)
            val parts = buildList {
                if (added > 0) add("$added queued")
                if (removed > 0) add("$removed removed")
            }
            if (parts.isNotEmpty()) notificationManager.showSuccess(parts.joinToString(", "))
        }
    }

    fun downloadWithSelection(
        scope: CoroutineScope,
        gameId: Long,
        selectedFileIds: Set<Long>,
        selectedVersionIds: Set<Long>
    ) {
        scope.launch {
            val (queued, errors) = filePickerFlow.downloadSelection(gameId, selectedFileIds, selectedVersionIds)
            errors.forEach { notificationManager.showError(it) }
            if (queued > 1) notificationManager.showSuccess("Queued $queued downloads")
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

    fun resumeDownload(scope: CoroutineScope, gameId: Long) {
        scope.launch {
            val game = gameRepository.getById(gameId)
            val steamAppId = game?.steamAppId
            if (game != null && steamAppId != null && steamContentManager.activeDownload.value?.appId == steamAppId) {
                steamContentManager.queueDownloadOptimistic(steamAppId, game.title, game.coverPath)
            } else {
                downloadManager.resumeDownload(gameId)
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
                        details.screenshotUrls.firstOrNull()?.let { url ->
                            imageCacheManager.queueBackgroundCacheByGameId(url, gameId, game.title)
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
