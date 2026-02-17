package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.audio.AmbientAudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoPreviewState(
    val isVideoPreviewActive: Boolean = false,
    val videoPreviewId: String? = null,
    val isVideoPreviewLoading: Boolean = false,
    val muteVideoPreview: Boolean = false,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelayMs: Long = 3000L
)

class HomeVideoPreviewDelegate @Inject constructor(
    private val ambientAudioManager: AmbientAudioManager
) {
    private val _state = MutableStateFlow(VideoPreviewState())
    val state: StateFlow<VideoPreviewState> = _state.asStateFlow()

    fun updateFromPreferences(
        muteVideoPreview: Boolean,
        videoWallpaperEnabled: Boolean,
        videoWallpaperDelaySeconds: Int
    ) {
        val wasMuted = _state.value.muteVideoPreview
        _state.update {
            it.copy(
                muteVideoPreview = muteVideoPreview,
                videoWallpaperEnabled = videoWallpaperEnabled,
                videoWallpaperDelayMs = videoWallpaperDelaySeconds * 1000L
            )
        }

        if (_state.value.isVideoPreviewActive && wasMuted != muteVideoPreview) {
            if (muteVideoPreview) {
                ambientAudioManager.fadeIn()
            } else {
                ambientAudioManager.fadeOut()
            }
        }
    }

    fun startVideoPreviewLoading(videoId: String) {
        _state.update {
            it.copy(
                isVideoPreviewLoading = true,
                videoPreviewId = videoId
            )
        }
    }

    fun activateVideoPreview() {
        _state.update {
            it.copy(
                isVideoPreviewActive = true,
                isVideoPreviewLoading = false
            )
        }
        if (!_state.value.muteVideoPreview) {
            ambientAudioManager.fadeOut()
        }
    }

    fun cancelVideoPreviewLoading() {
        _state.update {
            it.copy(
                isVideoPreviewLoading = false,
                videoPreviewId = null
            )
        }
        ambientAudioManager.fadeIn()
    }

    fun deactivateVideoPreview() {
        val wasActive = _state.value.isVideoPreviewActive || _state.value.isVideoPreviewLoading
        _state.update {
            it.copy(
                isVideoPreviewActive = false,
                isVideoPreviewLoading = false,
                videoPreviewId = null
            )
        }
        if (wasActive) {
            ambientAudioManager.fadeIn()
        }
    }
}
