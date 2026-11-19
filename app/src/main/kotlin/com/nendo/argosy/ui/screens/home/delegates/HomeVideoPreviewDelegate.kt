package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.components.GameVideoClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Where Home's preview clip is. The carousel stops at [PLAYING]; Columns goes on to [EXPANDED],
 * where the clip fills the screen, then [INFO], where the game's details sit over it, and
 * [RESTORING] while it shrinks back into the column before returning to [IDLE].
 */
enum class VideoPreviewPhase {
    IDLE,
    LOADING,
    PLAYING,
    EXPANDED,
    INFO,
    RESTORING;

    val isActive: Boolean get() = this != IDLE && this != LOADING

    val isExpanded: Boolean get() = this == EXPANDED || this == INFO
}

data class VideoPreviewState(
    val phase: VideoPreviewPhase = VideoPreviewPhase.IDLE,
    val clip: GameVideoClip? = null,
    val gameId: Long? = null,
    val muteVideoPreview: Boolean = com.nendo.argosy.ui.theme.generated.ComponentDefaults.Background.videoWallpaperMuted,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelayMs: Long = 3000L
) {
    val isVideoPreviewActive: Boolean get() = phase.isActive

    val isVideoPreviewLoading: Boolean get() = phase == VideoPreviewPhase.LOADING

    val videoPreviewId: String? get() = clip?.key
}

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

    /**
     * Quiets the launcher's music while an engaged tile is sounding. Platform audio focus does not
     * reach it, so this is the only thing that does.
     */
    fun holdForTileAudio() = ambientAudioManager.fadeOut()

    fun releaseTileAudio() = ambientAudioManager.fadeIn()

    /**
     * Hands the output to the page being shown, or takes it back. A page that carries its own sound
     * stands in for the launcher's music for as long as it is the page in view.
     */
    fun setPageOwnsAudio(pageOwnsAudio: Boolean) {
        if (pageOwnsAudio) ambientAudioManager.fadeOut() else ambientAudioManager.fadeIn()
    }

    fun startVideoPreviewLoading(gameId: Long, clip: GameVideoClip) {
        _state.update {
            it.copy(
                phase = VideoPreviewPhase.LOADING,
                clip = clip,
                gameId = gameId
            )
        }
    }

    fun activateVideoPreview(videoId: String, blocked: Boolean, isResumed: Boolean) {
        if (blocked || !isResumed) return
        if (!advance(videoId, VideoPreviewPhase.LOADING, VideoPreviewPhase.PLAYING)) return
        if (!_state.value.muteVideoPreview) {
            ambientAudioManager.fadeOut()
        }
    }

    fun beginTakeover(videoId: String): Boolean =
        advance(videoId, VideoPreviewPhase.PLAYING, VideoPreviewPhase.EXPANDED)

    fun showTakeoverInfo(videoId: String): Boolean =
        advance(videoId, VideoPreviewPhase.EXPANDED, VideoPreviewPhase.INFO)

    fun restoreTakeover(): Boolean {
        var restored = false
        _state.update { current ->
            restored = current.phase.isExpanded
            if (restored) current.copy(phase = VideoPreviewPhase.RESTORING) else current
        }
        return restored
    }

    fun finishTakeoverRestore() {
        if (_state.value.phase == VideoPreviewPhase.RESTORING) deactivateVideoPreview()
    }

    fun cancelVideoPreviewLoading() {
        _state.update { it.idle() }
        ambientAudioManager.fadeIn()
    }

    fun deactivateVideoPreview() {
        val wasActive = _state.value.phase != VideoPreviewPhase.IDLE
        _state.update { it.idle() }
        if (wasActive) {
            ambientAudioManager.fadeIn()
        }
    }

    private fun advance(videoId: String, from: VideoPreviewPhase, to: VideoPreviewPhase): Boolean {
        var advanced = false
        _state.update { current ->
            advanced = current.phase == from && current.videoPreviewId == videoId
            if (advanced) current.copy(phase = to) else current
        }
        return advanced
    }

    private fun VideoPreviewState.idle(): VideoPreviewState =
        copy(phase = VideoPreviewPhase.IDLE, clip = null, gameId = null)
}
