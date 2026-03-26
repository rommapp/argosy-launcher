package com.nendo.argosy.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloadState: DownloadQueueState = DownloadQueueState(),
    val focusedDownloadId: Long? = null,
    val maxActiveSlots: Int = 1,
    val showFailedActionDialog: Boolean = false
) {
    val activeItems: List<DownloadProgress>
        get() = buildList {
            addAll(downloadState.activeDownloads)
            val remainingSlots = maxActiveSlots - size
            if (remainingSlots > 0) {
                val pausedItems = downloadState.queue.filter { it.state == DownloadState.PAUSED }
                addAll(pausedItems.take(remainingSlots))
            }
        }

    private val activeDownloadIds: Set<Long>
        get() = activeItems.map { it.id }.toSet()

    val queuedItems: List<DownloadProgress>
        get() = downloadState.queue.filter { it.id !in activeDownloadIds }

    val completedItems: List<DownloadProgress>
        get() = downloadState.completed

    val allItems: List<DownloadProgress>
        get() = activeItems + queuedItems + completedItems

    val focusedIndex: Int
        get() = allItems.indexOfFirst { it.id == focusedDownloadId }.takeIf { it >= 0 } ?: 0

    val focusedItem: DownloadProgress?
        get() = allItems.find { it.id == focusedDownloadId } ?: allItems.firstOrNull()

    val isFocusedItemCompleted: Boolean
        get() = focusedItem?.let { it.id in completedItems.map { c -> c.id } } ?: false

    val isFocusedItemFailed: Boolean
        get() = focusedItem?.state == DownloadState.FAILED

    val canToggle: Boolean
        get() = focusedItem != null && !isFocusedItemCompleted

    val canCancel: Boolean
        get() = focusedItem != null && !isFocusedItemCompleted

    val canRemove: Boolean
        get() = focusedItem != null && isFocusedItemCompleted

    val hasFinishedItems: Boolean
        get() = completedItems.isNotEmpty()

    val toggleLabel: String
        get() = when (focusedItem?.state) {
            DownloadState.DOWNLOADING -> "Pause"
            DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE, DownloadState.FAILED -> "Resume"
            DownloadState.QUEUED -> "Pause"
            else -> "Toggle"
        }

    val confirmLabel: String
        get() = when {
            isFocusedItemFailed -> "Options"
            isFocusedItemCompleted -> "View"
            else -> toggleLabel
        }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val steamContentManager: SteamContentManager,
    private val gameDao: GameDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val state: StateFlow<DownloadQueueState> = downloadManager.state

    private val _steamDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())

    init {
        viewModelScope.launch {
            combine(
                downloadManager.state,
                preferencesRepository.preferences.map { it.maxConcurrentDownloads },
                _steamDownloads
            ) { downloadState, maxActive, steamItems ->
                Triple(downloadState, maxActive, steamItems)
            }.collect { (downloadState, maxActive, steamItems) ->
                val merged = downloadState.copy(
                    activeDownloads = downloadState.activeDownloads + steamItems.filter {
                        it.state == DownloadState.DOWNLOADING || it.state == DownloadState.EXTRACTING
                    },
                    queue = downloadState.queue + steamItems.filter {
                        it.state == DownloadState.QUEUED || it.state == DownloadState.PAUSED
                    },
                    completed = downloadState.completed + steamItems.filter {
                        it.state == DownloadState.COMPLETED
                    }
                )

                val currentFocusedId = _uiState.value.focusedDownloadId
                val allItems = buildList {
                    addAll(merged.activeDownloads)
                    addAll(merged.queue)
                    addAll(merged.completed)
                }

                val newFocusedId = when {
                    allItems.isEmpty() -> null
                    currentFocusedId != null && allItems.any { it.id == currentFocusedId } -> currentFocusedId
                    else -> allItems.firstOrNull()?.id
                }

                _uiState.value = _uiState.value.copy(
                    downloadState = merged,
                    focusedDownloadId = newFocusedId,
                    maxActiveSlots = maxActive
                )
            }
        }

        // Convert Steam downloads to DownloadProgress entries
        // Observe active download, state, and queue
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                steamContentManager.activeDownload,
                steamContentManager.downloadState,
                steamContentManager.downloadQueue
            ) { activeDl, dlState, queue -> Triple(activeDl, dlState, queue) }.collect { (activeDl, steamState, queue) ->
                val items = mutableListOf<DownloadProgress>()

                // Active/paused download
                val activeAppId: Long?
                when {
                    activeDl != null -> {
                        activeAppId = activeDl.appId
                        val (mappedState, statusMsg) = when (steamState) {
                            is SteamDownloadState.Downloading -> {
                                val depotInfo = if (steamState.totalDepots > 1) " (${steamState.currentDepot}/${steamState.totalDepots} depots)" else ""
                                DownloadState.DOWNLOADING to depotInfo.ifEmpty { null }
                            }
                            is SteamDownloadState.Preparing -> DownloadState.QUEUED to "Preparing..."
                            is SteamDownloadState.Connecting -> DownloadState.QUEUED to "Connecting to Steam..."
                            is SteamDownloadState.FetchingManifest -> DownloadState.QUEUED to "Fetching depot manifest..."
                            is SteamDownloadState.Validating -> DownloadState.EXTRACTING to (steamState.statusDetail.ifEmpty { "Validating..." })
                            is SteamDownloadState.Moving -> DownloadState.EXTRACTING to "Moving files..."
                            is SteamDownloadState.Cleaning -> DownloadState.QUEUED to "Cleaning up..."
                            is SteamDownloadState.Paused -> DownloadState.PAUSED to null
                            is SteamDownloadState.Completed -> DownloadState.COMPLETED to null
                            is SteamDownloadState.Failed -> DownloadState.FAILED to steamState.error
                            is SteamDownloadState.Idle -> DownloadState.QUEUED to null
                        }
                        val game = gameDao.getBySteamAppId(activeDl.appId)
                        items.add(DownloadProgress(
                            id = -activeDl.appId,
                            gameId = game?.id ?: 0L,
                            rommId = 0L,
                            platformSlug = "steam",
                            gameTitle = activeDl.gameName,
                            fileName = "",
                            totalBytes = activeDl.totalBytes,
                            bytesDownloaded = activeDl.bytesDownloaded,
                            state = mappedState,
                            coverPath = activeDl.coverPath,
                            bytesPerSecond = if (mappedState == DownloadState.EXTRACTING) 0L else activeDl.bytesPerSecond,
                            statusMessage = statusMsg
                        ))
                    }
                    steamState is SteamDownloadState.Paused -> {
                        activeAppId = steamState.appId
                        val game = gameDao.getBySteamAppId(steamState.appId)
                        items.add(DownloadProgress(
                            id = -steamState.appId,
                            gameId = game?.id ?: 0L,
                            rommId = 0L,
                            platformSlug = "steam",
                            gameTitle = steamState.gameName,
                            fileName = "",
                            totalBytes = 0L,
                            bytesDownloaded = 0L,
                            state = DownloadState.PAUSED,
                            coverPath = game?.coverPath
                        ))
                    }
                    else -> activeAppId = null
                }

                // Queued downloads: show as QUEUED (waiting to start)
                for (queued in queue) {
                    if (queued.appId == activeAppId) continue
                    val game = gameDao.getBySteamAppId(queued.appId)
                    items.add(DownloadProgress(
                        id = -queued.appId,
                        gameId = game?.id ?: 0L,
                        rommId = 0L,
                        platformSlug = "steam",
                        gameTitle = queued.gameName,
                        fileName = "",
                        totalBytes = 0L,
                        bytesDownloaded = 0L,
                        state = DownloadState.QUEUED,
                        coverPath = queued.coverPath
                    ))
                }

                _steamDownloads.value = items
            }
        }
    }

    private fun moveFocus(delta: Int): Boolean {
        val currentState = _uiState.value
        val items = currentState.allItems
        if (items.isEmpty()) return false

        val currentIndex = currentState.focusedIndex
        val newIndex = (currentIndex + delta).coerceIn(0, items.size - 1)

        if (newIndex != currentIndex) {
            _uiState.value = currentState.copy(focusedDownloadId = items[newIndex].id)
            return true
        }
        return false
    }

    private fun isSteamItem(item: DownloadProgress) = item.id < 0

    fun toggleFocusedItem() {
        val item = _uiState.value.focusedItem ?: return
        android.util.Log.d("DownloadsVM", "toggleFocusedItem: id=${item.id}, state=${item.state}, isSteam=${isSteamItem(item)}")
        if (isSteamItem(item)) {
            when (item.state) {
                DownloadState.DOWNLOADING -> steamContentManager.pauseDownload()
                DownloadState.PAUSED -> resumeSteamDownload(item)
                else -> android.util.Log.d("DownloadsVM", "Steam item in unhandled state: ${item.state}")
            }
            return
        }
        when (item.state) {
            DownloadState.DOWNLOADING -> downloadManager.pauseDownload(item.rommId)
            DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE, DownloadState.FAILED ->
                downloadManager.resumeDownload(item.gameId)
            DownloadState.QUEUED -> downloadManager.pauseDownload(item.rommId)
            else -> {}
        }
    }

    private fun resumeSteamDownload(item: DownloadProgress) {
        val steamAppId = -item.id
        android.util.Log.d("DownloadsVM", "resumeSteamDownload: steamAppId=$steamAppId")
        viewModelScope.launch {
            val game = gameDao.getBySteamAppId(steamAppId)
            android.util.Log.d("DownloadsVM", "resumeSteamDownload: game=${game?.title}")
            if (game == null) return@launch
            steamContentManager.queueDownloadOptimistic(steamAppId, game.title, game.coverPath)
        }
    }

    fun cancelFocusedItem() {
        val item = _uiState.value.focusedItem ?: return
        if (isSteamItem(item)) {
            steamContentManager.cancelDownload()
            return
        }
        downloadManager.cancelDownload(item.rommId)
    }

    fun cancelDownload(rommId: Long) {
        downloadManager.cancelDownload(rommId)
    }

    fun pauseDownload(rommId: Long) {
        downloadManager.pauseDownload(rommId)
    }

    fun resumeDownload(gameId: Long) {
        downloadManager.resumeDownload(gameId)
    }

    fun clearCompleted() {
        downloadManager.clearCompleted()
    }

    fun clearFinished() {
        downloadManager.clearFinished()
    }

    fun removeFromCompleted(downloadId: Long) {
        downloadManager.removeFromCompleted(downloadId)
    }

    fun retryDownload(downloadId: Long) {
        downloadManager.retryDownload(downloadId)
    }

    fun showFailedActionDialog() {
        _uiState.value = _uiState.value.copy(showFailedActionDialog = true)
    }

    fun dismissFailedActionDialog() {
        _uiState.value = _uiState.value.copy(showFailedActionDialog = false)
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onNavigateToGame: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult = if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        override fun onDown(): InputResult = if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED
        override fun onConfirm(): InputResult {
            val state = _uiState.value
            val item = state.focusedItem ?: return InputResult.UNHANDLED

            return when {
                state.isFocusedItemFailed -> {
                    showFailedActionDialog()
                    InputResult.HANDLED
                }
                state.isFocusedItemCompleted -> {
                    onNavigateToGame(item.gameId)
                    InputResult.HANDLED
                }
                state.canToggle -> {
                    toggleFocusedItem()
                    InputResult.HANDLED
                }
                else -> InputResult.UNHANDLED
            }
        }
        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
        override fun onMenu(): InputResult = InputResult.UNHANDLED
        override fun onSecondaryAction(): InputResult {
            if (_uiState.value.hasFinishedItems) {
                clearFinished()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
        override fun onContextMenu(): InputResult {
            val state = _uiState.value
            return when {
                state.canRemove -> {
                    state.focusedItem?.let { removeFromCompleted(it.id) }
                    InputResult.HANDLED
                }
                state.canCancel -> {
                    cancelFocusedItem()
                    InputResult.HANDLED
                }
                else -> InputResult.UNHANDLED
            }
        }
    }
}
