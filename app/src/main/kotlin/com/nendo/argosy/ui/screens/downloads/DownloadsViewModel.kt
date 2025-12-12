package com.nendo.argosy.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.preferences.UserPreferencesRepository
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
    val maxActiveSlots: Int = 1
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

    val allItems: List<DownloadProgress>
        get() = activeItems + queuedItems

    val focusedIndex: Int
        get() = allItems.indexOfFirst { it.id == focusedDownloadId }.takeIf { it >= 0 } ?: 0

    val focusedItem: DownloadProgress?
        get() = allItems.find { it.id == focusedDownloadId } ?: allItems.firstOrNull()

    val canToggle: Boolean
        get() = focusedItem != null

    val canCancel: Boolean
        get() = focusedItem != null

    val toggleLabel: String
        get() = when (focusedItem?.state) {
            DownloadState.DOWNLOADING -> "Pause"
            DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE, DownloadState.FAILED -> "Resume"
            DownloadState.QUEUED -> "Pause"
            else -> "Toggle"
        }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val state: StateFlow<DownloadQueueState> = downloadManager.state

    init {
        viewModelScope.launch {
            combine(
                downloadManager.state,
                preferencesRepository.preferences.map { it.maxConcurrentDownloads }
            ) { downloadState, maxActive ->
                downloadState to maxActive
            }.collect { (downloadState, maxActive) ->
                val currentFocusedId = _uiState.value.focusedDownloadId
                val allItems = buildList {
                    addAll(downloadState.activeDownloads)
                    addAll(downloadState.queue)
                }

                val newFocusedId = when {
                    allItems.isEmpty() -> null
                    currentFocusedId != null && allItems.any { it.id == currentFocusedId } -> currentFocusedId
                    else -> allItems.firstOrNull()?.id
                }

                _uiState.value = _uiState.value.copy(
                    downloadState = downloadState,
                    focusedDownloadId = newFocusedId,
                    maxActiveSlots = maxActive
                )
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

    fun toggleFocusedItem() {
        val item = _uiState.value.focusedItem ?: return
        when (item.state) {
            DownloadState.DOWNLOADING -> downloadManager.pauseDownload(item.rommId)
            DownloadState.PAUSED, DownloadState.WAITING_FOR_STORAGE, DownloadState.FAILED ->
                downloadManager.resumeDownload(item.gameId)
            DownloadState.QUEUED -> downloadManager.pauseDownload(item.rommId)
            else -> {}
        }
    }

    fun cancelFocusedItem() {
        val item = _uiState.value.focusedItem ?: return
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

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult = if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        override fun onDown(): InputResult = if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED
        override fun onConfirm(): InputResult {
            if (_uiState.value.canToggle) {
                toggleFocusedItem()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
        override fun onMenu(): InputResult = InputResult.UNHANDLED
        override fun onSecondaryAction(): InputResult = InputResult.UNHANDLED
        override fun onContextMenu(): InputResult {
            if (_uiState.value.canCancel) {
                cancelFocusedItem()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
    }
}
