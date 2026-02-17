package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.gamedetail.ScreenshotPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ScreenshotState(
    val focusedScreenshotIndex: Int = 0,
    val showScreenshotViewer: Boolean = false,
    val viewerScreenshotIndex: Int = 0
)

@Singleton
class ScreenshotDelegate @Inject constructor(
    private val imageCacheManager: ImageCacheManager,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(ScreenshotState())
    val state: StateFlow<ScreenshotState> = _state.asStateFlow()

    fun reset() {
        _state.value = ScreenshotState()
    }

    fun setFocusedScreenshotIndex(index: Int) {
        _state.update { it.copy(focusedScreenshotIndex = index) }
    }

    fun moveScreenshotFocus(delta: Int, screenshots: List<ScreenshotPair>) {
        if (screenshots.isEmpty()) return
        _state.update { state ->
            val newIndex = (state.focusedScreenshotIndex + delta).mod(screenshots.size)
            state.copy(focusedScreenshotIndex = newIndex)
        }
    }

    fun openScreenshotViewer(screenshots: List<ScreenshotPair>, index: Int? = null) {
        if (screenshots.isEmpty()) return
        val viewerIndex = index ?: _state.value.focusedScreenshotIndex
        _state.update {
            it.copy(
                showScreenshotViewer = true,
                viewerScreenshotIndex = viewerIndex.coerceIn(0, screenshots.size - 1)
            )
        }
    }

    fun closeScreenshotViewer() {
        _state.update { state ->
            state.copy(
                showScreenshotViewer = false,
                focusedScreenshotIndex = state.viewerScreenshotIndex
            )
        }
    }

    fun moveViewerIndex(delta: Int, screenshots: List<ScreenshotPair>) {
        if (screenshots.isEmpty()) return
        _state.update { state ->
            val newIndex = (state.viewerScreenshotIndex + delta).mod(screenshots.size)
            state.copy(viewerScreenshotIndex = newIndex)
        }
    }

    fun setCurrentScreenshotAsBackground(
        scope: CoroutineScope,
        gameId: Long,
        screenshots: List<ScreenshotPair>,
        onSuccess: () -> Unit
    ) {
        val screenshot = screenshots.getOrNull(_state.value.viewerScreenshotIndex) ?: return
        val screenshotPath = screenshot.cachedPath ?: screenshot.remoteUrl

        scope.launch {
            val success = imageCacheManager.setScreenshotAsBackground(gameId, screenshotPath)
            if (success) {
                notificationManager.showSuccess("Background updated")
                onSuccess()
            } else {
                notificationManager.showError("Failed to set background")
            }
        }
    }
}
