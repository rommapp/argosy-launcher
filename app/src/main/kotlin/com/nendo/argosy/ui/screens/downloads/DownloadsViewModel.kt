package com.nendo.argosy.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.download.MediaDownloadManager
import com.nendo.argosy.data.download.MediaDownloadProgress
import com.nendo.argosy.data.download.MediaDownloadState
import com.nendo.argosy.data.download.QueuedMediaDownload
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.QueuedSteamDownload
import com.nendo.argosy.data.steam.SteamDownloadProgress
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

data class DownloadGroup(val items: List<DownloadProgress>) {
    val primary: DownloadProgress
        get() = items.maxByOrNull { it.totalBytes } ?: items.first()

    val isGroup: Boolean get() = items.size > 1

    /** One synthesized record per game: summed bytes, dominant state, n-of-m label. */
    val aggregate: DownloadProgress
        get() {
            if (!isGroup) return primary
            val state = when {
                items.any { it.state == DownloadState.DOWNLOADING } -> DownloadState.DOWNLOADING
                items.any { it.state == DownloadState.EXTRACTING } -> DownloadState.EXTRACTING
                items.any { it.state == DownloadState.QUEUED } -> DownloadState.QUEUED
                items.any { it.state == DownloadState.PAUSED } -> DownloadState.PAUSED
                items.any { it.state == DownloadState.WAITING_FOR_STORAGE } -> DownloadState.WAITING_FOR_STORAGE
                items.any { it.state == DownloadState.FAILED } -> DownloadState.FAILED
                else -> DownloadState.COMPLETED
            }
            val done = items.count { it.state == DownloadState.COMPLETED }
            return primary.copy(
                bytesDownloaded = items.sumOf { it.bytesDownloaded },
                totalBytes = items.sumOf { it.totalBytes },
                bytesPerSecond = items.sumOf { it.bytesPerSecond },
                state = state,
                statusMessage = "$done of ${items.size} files"
            )
        }
}

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

    val allGroups: List<DownloadGroup>
        get() {
            val byKey = LinkedHashMap<Long, MutableList<DownloadProgress>>()
            allItems.forEach { p ->
                byKey.getOrPut(if (p.gameId > 0) p.gameId else -p.id) { mutableListOf() }.add(p)
            }
            return byKey.values.map { DownloadGroup(it.toList()) }
        }

    val activeGroups: List<DownloadGroup>
        get() = allGroups.filter { g -> g.items.any { it.id in activeDownloadIds } }

    val queuedGroups: List<DownloadGroup>
        get() {
            val queuedIds = queuedItems.map { it.id }.toSet()
            return allGroups.filter { g ->
                g.items.none { it.id in activeDownloadIds } && g.items.any { it.id in queuedIds }
            }
        }

    val completedGroups: List<DownloadGroup>
        get() {
            val shown = (activeGroups + queuedGroups).toSet()
            return allGroups.filter { it !in shown }
        }

    val orderedGroups: List<DownloadGroup>
        get() = activeGroups + queuedGroups + completedGroups

    val focusedGroup: DownloadGroup?
        get() = orderedGroups.firstOrNull { g -> g.items.any { it.id == focusedDownloadId } }
            ?: orderedGroups.firstOrNull()

    val focusedIndex: Int
        get() = orderedGroups.indexOfFirst { g -> g.items.any { it.id == focusedDownloadId } }
            .takeIf { it >= 0 } ?: 0

    val focusedItem: DownloadProgress?
        get() = focusedGroup?.primary

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
    private val mediaDownloadManager: MediaDownloadManager,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val state: StateFlow<DownloadQueueState> = downloadManager.state

    private val _steamDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())
    private val _mediaDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())

    private var mediaItemIdsByRowId: Map<Long, String> = emptyMap()

    init {
        viewModelScope.launch {
            combine(
                downloadManager.state,
                preferencesRepository.preferences.map { it.maxConcurrentDownloads },
                _steamDownloads,
                _mediaDownloads
            ) { downloadState, maxActive, steamItems, mediaItems ->
                MergedSources(downloadState, maxActive, steamItems + mediaItems)
            }.collect { (downloadState, maxActive, externalItems) ->
                val merged = downloadState.copy(
                    activeDownloads = downloadState.activeDownloads + externalItems.filter {
                        it.state == DownloadState.DOWNLOADING || it.state == DownloadState.EXTRACTING
                    },
                    queue = downloadState.queue + externalItems.filter {
                        it.state == DownloadState.QUEUED || it.state == DownloadState.PAUSED
                    },
                    completed = downloadState.completed + externalItems.filter {
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
        // Observe active download, state, queue, and completed
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                steamContentManager.activeDownload,
                steamContentManager.downloadState,
                steamContentManager.downloadQueue,
                steamContentManager.completedDownloads
            ) { activeDl, dlState, queue, completed ->
                arrayOf(activeDl, dlState, queue, completed)
            }.collect { values ->
                @Suppress("UNCHECKED_CAST")
                val activeDl = values[0] as SteamDownloadProgress?
                val steamState = values[1] as SteamDownloadState
                val queue = values[2] as List<QueuedSteamDownload>
                val completed = values[3] as List<SteamDownloadProgress>

                val items = mutableListOf<DownloadProgress>()

                // Active/paused download
                // Skip stale activeDownload when state has moved to Completed/Idle --
                // completed entries are handled by the completedDownloads flow.
                val activeAppId: Long?
                val isStaleActive = activeDl != null &&
                    (steamState is SteamDownloadState.Completed || steamState is SteamDownloadState.Idle)
                when {
                    activeDl != null && !isStaleActive -> {
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
                        val game = gameRepository.getBySteamAppId(activeDl.appId)
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
                        val game = gameRepository.getBySteamAppId(steamState.appId)
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
                    val game = gameRepository.getBySteamAppId(queued.appId)
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

                // Completed Steam downloads
                for (dl in completed) {
                    if (dl.appId == activeAppId) continue
                    val game = gameRepository.getBySteamAppId(dl.appId)
                    items.add(DownloadProgress(
                        id = -dl.appId,
                        gameId = game?.id ?: 0L,
                        rommId = 0L,
                        platformSlug = "steam",
                        gameTitle = dl.gameName,
                        fileName = "",
                        totalBytes = dl.totalBytes,
                        bytesDownloaded = dl.totalBytes,
                        state = DownloadState.COMPLETED,
                        coverPath = dl.coverPath
                    ))
                }

                _steamDownloads.value = items
            }
        }

        viewModelScope.launch {
            combine(
                mediaDownloadManager.activeDownload,
                mediaDownloadManager.downloadState,
                mediaDownloadManager.downloadQueue,
                mediaDownloadManager.completedDownloads
            ) { active, mediaState, queue, completed ->
                MediaSources(active, mediaState, queue, completed)
            }.collect { sources ->
                val items = mutableListOf<DownloadProgress>()
                val ids = mutableMapOf<Long, String>()

                val active = sources.active
                val isStaleActive = active != null &&
                    (sources.state is MediaDownloadState.Completed || sources.state is MediaDownloadState.Idle)
                if (active != null && !isStaleActive) {
                    items.add(active.toDownloadProgress())
                    ids[mediaRowId(active.itemId)] = active.itemId
                }

                for (queued in sources.queue) {
                    if (queued.itemId == active?.itemId) continue
                    items.add(queued.toDownloadProgress())
                    ids[mediaRowId(queued.itemId)] = queued.itemId
                }

                for (done in sources.completed) {
                    if (done.itemId == active?.itemId && !isStaleActive) continue
                    items.add(done.toDownloadProgress())
                    ids[mediaRowId(done.itemId)] = done.itemId
                }

                mediaItemIdsByRowId = ids
                _mediaDownloads.value = items
            }
        }
    }

    private data class MergedSources(
        val downloadState: DownloadQueueState,
        val maxActive: Int,
        val externalItems: List<DownloadProgress>
    )

    private data class MediaSources(
        val active: MediaDownloadProgress?,
        val state: MediaDownloadState,
        val queue: List<QueuedMediaDownload>,
        val completed: List<MediaDownloadProgress>
    )

    private fun moveFocus(delta: Int): Boolean {
        val currentState = _uiState.value
        val groups = currentState.orderedGroups
        if (groups.isEmpty()) return false

        val currentIndex = currentState.focusedIndex
        val newIndex = (currentIndex + delta).coerceIn(0, groups.size - 1)

        if (newIndex != currentIndex) {
            _uiState.value = currentState.copy(focusedDownloadId = groups[newIndex].primary.id)
            return true
        }
        return false
    }

    private fun isMediaItem(item: DownloadProgress) = item.platformSlug == MEDIA_SLUG

    private fun isSteamItem(item: DownloadProgress) = !isMediaItem(item) && item.id < 0

    fun toggleFocusedItem() {
        val group = _uiState.value.focusedGroup ?: return
        for (item in group.items) {
            if (isMediaItem(item)) {
                val itemId = mediaItemIdsByRowId[item.id] ?: continue
                when (item.state) {
                    DownloadState.DOWNLOADING -> mediaDownloadManager.pauseActiveDownload()
                    DownloadState.PAUSED, DownloadState.FAILED -> mediaDownloadManager.resumeDownload(itemId)
                    else -> Unit
                }
                continue
            }
            if (isSteamItem(item)) {
                when (item.state) {
                    DownloadState.DOWNLOADING -> steamContentManager.pauseDownload()
                    DownloadState.PAUSED -> resumeSteamDownload(item)
                    else -> {}
                }
                continue
            }
            when (item.state) {
                DownloadState.DOWNLOADING -> downloadManager.pauseDownload(item.rommId)
                DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE, DownloadState.FAILED ->
                    downloadManager.resumeDownload(item.gameId)
                DownloadState.QUEUED -> downloadManager.pauseDownload(item.rommId)
                else -> {}
            }
        }
    }

    private fun resumeSteamDownload(item: DownloadProgress) {
        val steamAppId = -item.id
        android.util.Log.d("DownloadsVM", "resumeSteamDownload: steamAppId=$steamAppId")
        viewModelScope.launch {
            val game = gameRepository.getBySteamAppId(steamAppId)
            android.util.Log.d("DownloadsVM", "resumeSteamDownload: game=${game?.title}")
            if (game == null) return@launch
            steamContentManager.queueDownloadOptimistic(steamAppId, game.title, game.coverPath)
        }
    }

    fun cancelFocusedItem() {
        val group = _uiState.value.focusedGroup ?: return
        for (item in group.items) {
            when {
                isMediaItem(item) ->
                    mediaItemIdsByRowId[item.id]?.let { mediaDownloadManager.cancelDownload(it) }
                isSteamItem(item) -> steamContentManager.cancelDownload()
                else -> downloadManager.cancelDownload(item.rommId)
            }
        }
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
        steamContentManager.clearCompletedDownloads()
        mediaDownloadManager.clearCompletedDownloads()
    }

    fun clearFinished() {
        downloadManager.clearFinished()
        steamContentManager.clearCompletedDownloads()
        mediaDownloadManager.clearCompletedDownloads()
    }

    fun removeFromCompleted(downloadId: Long) {
        val mediaItemId = mediaItemIdsByRowId[downloadId]
        if (mediaItemId != null) {
            mediaDownloadManager.removeFromCompleted(mediaItemId)
            return
        }
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
                    if (!isMediaItem(item)) onNavigateToGame(item.gameId)
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

private const val MEDIA_SLUG = "media"

/**
 * Media rows share one list with rom and Steam rows, which is keyed on a Long. Steam already claims
 * the small negatives with its own app ids, so media is placed far below them: an item id is a
 * server GUID with no numeric form of its own, and the two spaces must not meet.
 */
private const val MEDIA_ROW_ID_BASE = -1_000_000_000_000L

private fun mediaRowId(itemId: String): Long =
    MEDIA_ROW_ID_BASE - (itemId.hashCode().toLong() and Int.MAX_VALUE.toLong())

private fun MediaDownloadProgress.toDownloadProgress(): DownloadProgress {
    val (mappedState, status) = when (val current = state) {
        is MediaDownloadState.Preparing -> DownloadState.QUEUED to current.detail
        is MediaDownloadState.Downloading -> DownloadState.DOWNLOADING to quality.displayName
        is MediaDownloadState.Paused -> DownloadState.PAUSED to current.reason
        is MediaDownloadState.Completed -> DownloadState.COMPLETED to quality.displayName
        is MediaDownloadState.Failed -> DownloadState.FAILED to current.error
        MediaDownloadState.Idle -> DownloadState.QUEUED to null
    }
    return DownloadProgress(
        id = mediaRowId(itemId),
        gameId = 0L,
        rommId = 0L,
        platformSlug = MEDIA_SLUG,
        gameTitle = displayTitle,
        fileName = "",
        totalBytes = totalBytes,
        bytesDownloaded = bytesDownloaded,
        state = mappedState,
        coverPath = posterUrl,
        bytesPerSecond = bytesPerSecond,
        statusMessage = status
    )
}

private fun QueuedMediaDownload.toDownloadProgress(): DownloadProgress = DownloadProgress(
    id = mediaRowId(itemId),
    gameId = 0L,
    rommId = 0L,
    platformSlug = MEDIA_SLUG,
    gameTitle = if (seriesName.isNullOrBlank()) itemName else "$seriesName - $itemName",
    fileName = "",
    totalBytes = 0L,
    bytesDownloaded = 0L,
    state = DownloadState.QUEUED,
    coverPath = posterUrl,
    statusMessage = quality.displayName
)
