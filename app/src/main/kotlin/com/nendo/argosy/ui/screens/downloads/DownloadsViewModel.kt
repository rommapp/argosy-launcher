package com.nendo.argosy.ui.screens.downloads

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.download.MediaDownloadManager
import com.nendo.argosy.data.download.MediaDownloadProgress
import com.nendo.argosy.data.download.MediaDownloadState
import com.nendo.argosy.data.download.QueuedMediaDownload
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadGroup(val items: List<DownloadProgress>) {
    val primary: DownloadProgress
        get() = items.maxByOrNull { it.totalBytes } ?: items.first()

    val isGroup: Boolean get() = items.size > 1

    /** One synthesized record per game: summed bytes, dominant state, n-of-m label. */
    fun aggregate(context: Context): DownloadProgress {
        if (!isGroup) return primary
        val state = when {
            items.any { it.state == DownloadState.DOWNLOADING } -> DownloadState.DOWNLOADING
            items.any { it.state == DownloadState.RETRYING } -> DownloadState.RETRYING
            items.any { it.state == DownloadState.WAITING_FOR_NETWORK } -> DownloadState.WAITING_FOR_NETWORK
            items.any { it.state == DownloadState.EXTRACTING } -> DownloadState.EXTRACTING
            items.any { it.state == DownloadState.MOVING } -> DownloadState.MOVING
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
            statusMessage = context.resources.getQuantityString(
                R.plurals.downloads_group_progress,
                items.size,
                done,
                items.size
            )
        )
    }
}

data class DownloadsUiState(
    val downloadState: DownloadQueueState = DownloadQueueState(),
    val focusedDownloadId: Long? = null,
    val maxActiveSlots: Int = 1,
    val showFailedActionDialog: Boolean = false,
    val isTouchMode: Boolean = false
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

    /**
     * Position of the focused group in the rendered list, counting the section headers above it.
     */
    val focusedListIndex: Int
        get() {
            val active = activeGroups.size
            val queued = queuedGroups.size
            val index = focusedIndex
            var headers = 0
            if (active > 0) headers++
            if (index >= active && queued > 0) headers++
            if (index >= active + queued) headers++
            return index + headers
        }

    val focusedItem: DownloadProgress?
        get() = focusedGroup?.primary

    val showTouchControls: Boolean
        get() = isTouchMode && focusedItem != null

    val isFocusedItemCompleted: Boolean
        get() = focusedItem?.let { it.id in completedItems.map { c -> c.id } } ?: false

    val isFocusedItemFailed: Boolean
        get() = focusedItem?.state == DownloadState.FAILED

    val canToggle: Boolean
        get() = focusedItem?.let { !isFocusedItemCompleted && it.state in TOGGLEABLE_STATES } ?: false

    val canCancel: Boolean
        get() = focusedItem != null && !isFocusedItemCompleted

    val canRemove: Boolean
        get() = focusedItem != null && isFocusedItemCompleted

    val canView: Boolean
        get() = focusedItem?.let {
            isFocusedItemCompleted && !isFocusedItemFailed && it.platformSlug != MEDIA_SLUG
        } ?: false

    val hasFinishedItems: Boolean
        get() = completedItems.isNotEmpty()

    val canStartAll: Boolean
        get() = downloadState.hasPaused

    @get:StringRes
    val toggleLabelRes: Int
        get() = when (focusedItem?.state) {
            DownloadState.DOWNLOADING, DownloadState.RETRYING,
            DownloadState.WAITING_FOR_NETWORK -> R.string.downloads_action_pause
            DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE -> R.string.downloads_action_resume
            DownloadState.QUEUED -> R.string.downloads_action_pause
            else -> R.string.downloads_action_toggle
        }

    @get:StringRes
    val confirmLabelRes: Int
        get() = when {
            isFocusedItemFailed -> R.string.downloads_action_options
            isFocusedItemCompleted -> R.string.downloads_action_view
            else -> toggleLabelRes
        }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val mediaDownloadManager: MediaDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val state: StateFlow<DownloadQueueState> = downloadManager.state

    private val _mediaDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())

    private var mediaItemIdsByRowId: Map<Long, String> = emptyMap()

    init {
        viewModelScope.launch {
            combine(
                downloadManager.state,
                preferencesRepository.preferences.map { it.maxConcurrentDownloads },
                _mediaDownloads
            ) { downloadState, maxActive, mediaItems ->
                MergedSources(downloadState, maxActive, mediaItems)
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

                _uiState.update {
                    it.copy(
                        downloadState = merged,
                        focusedDownloadId = newFocusedId,
                        maxActiveSlots = maxActive,
                        isTouchMode = it.isTouchMode && newFocusedId == currentFocusedId
                    )
                }
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
            _uiState.update { it.copy(focusedDownloadId = groups[newIndex].primary.id, isTouchMode = false) }
            return true
        }
        return false
    }

    private fun exitTouchMode() {
        if (_uiState.value.isTouchMode) {
            _uiState.update { it.copy(isTouchMode = false) }
        }
    }

    fun handleRowTap(downloadId: Long) {
        _uiState.update {
            val sameRow = it.focusedDownloadId == downloadId
            it.copy(focusedDownloadId = downloadId, isTouchMode = !(sameRow && it.isTouchMode))
        }
    }

    private fun isMediaItem(item: DownloadProgress) = item.platformSlug == MEDIA_SLUG

    fun openFocusedGame(onNavigateToGame: (Long) -> Unit) {
        val item = _uiState.value.focusedItem ?: return
        if (!isMediaItem(item)) onNavigateToGame(item.gameId)
    }

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
            when (item.state) {
                DownloadState.DOWNLOADING, DownloadState.RETRYING,
                DownloadState.WAITING_FOR_NETWORK -> downloadManager.pauseDownload(item.rommId)
                DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE ->
                    downloadManager.resumeDownload(item.gameId)
                DownloadState.FAILED -> downloadManager.retryDownload(item.id)
                DownloadState.QUEUED -> downloadManager.pauseDownload(item.rommId)
                else -> {}
            }
        }
    }

    fun cancelFocusedItem() {
        val group = _uiState.value.focusedGroup ?: return
        for (item in group.items) {
            when {
                isMediaItem(item) ->
                    mediaItemIdsByRowId[item.id]?.let { mediaDownloadManager.cancelDownload(it) }
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
        mediaDownloadManager.clearCompletedDownloads()
    }

    fun clearFinished() {
        downloadManager.clearFinished()
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

    fun startAllPaused() {
        downloadManager.resumeAllPaused()
    }

    fun showFailedActionDialog() {
        _uiState.update { it.copy(showFailedActionDialog = true) }
    }

    fun dismissFailedActionDialog() {
        _uiState.update { it.copy(showFailedActionDialog = false) }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onNavigateToGame: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            exitTouchMode()
            return if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        }
        override fun onDown(): InputResult {
            exitTouchMode()
            return if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
        }
        override fun onLeft(): InputResult {
            exitTouchMode()
            return InputResult.UNHANDLED
        }
        override fun onRight(): InputResult {
            exitTouchMode()
            return InputResult.UNHANDLED
        }
        override fun onConfirm(): InputResult {
            exitTouchMode()
            val state = _uiState.value
            if (state.focusedItem == null) return InputResult.UNHANDLED

            return when {
                state.isFocusedItemFailed -> {
                    showFailedActionDialog()
                    InputResult.HANDLED
                }
                state.isFocusedItemCompleted -> {
                    openFocusedGame(onNavigateToGame)
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
            exitTouchMode()
            onBack()
            return InputResult.HANDLED
        }
        override fun onMenu(): InputResult {
            exitTouchMode()
            return InputResult.UNHANDLED
        }
        override fun onSecondaryAction(): InputResult {
            exitTouchMode()
            if (_uiState.value.hasFinishedItems) {
                clearFinished()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
        override fun onNextTrigger(): InputResult {
            exitTouchMode()
            if (!_uiState.value.canStartAll) return InputResult.UNHANDLED
            startAllPaused()
            return InputResult.HANDLED
        }
        override fun onContextMenu(): InputResult {
            exitTouchMode()
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

private val TOGGLEABLE_STATES = setOf(
    DownloadState.DOWNLOADING,
    DownloadState.RETRYING,
    DownloadState.WAITING_FOR_NETWORK,
    DownloadState.QUEUED,
    DownloadState.PAUSED,
    DownloadState.WAITING_FOR_STORAGE
)

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
