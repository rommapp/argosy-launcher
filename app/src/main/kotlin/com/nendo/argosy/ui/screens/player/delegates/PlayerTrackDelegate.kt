package com.nendo.argosy.ui.screens.player.delegates

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nendo.argosy.ui.screens.player.NegotiatedPlayback
import com.nendo.argosy.ui.screens.player.PlayerEngine
import com.nendo.argosy.ui.screens.player.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Chooses which audio and subtitle streams are heard and seen.
 *
 * Two mechanisms, and which one applies is decided by what the server sent rather than by
 * preference. A direct-played container carries every track, so a change is a selection the player
 * makes locally and the stream is untouched. A transcode carries only the tracks the server was
 * asked to produce, so a change there means asking again - and an image subtitle always does,
 * because it can only be shown by having the server draw it into the picture.
 */
@OptIn(UnstableApi::class)
class PlayerTrackDelegate(
    private val state: MutableStateFlow<PlayerUiState>,
    private val engine: PlayerEngine,
    private val playerOf: () -> ExoPlayer?,
    private val playbackOf: () -> NegotiatedPlayback?,
    private val reload: (audioStreamIndex: Int?, subtitleStreamIndex: Int?) -> Unit,
    private val closeOverlay: () -> Unit
) {

    fun selectAudioTrack(index: Int) {
        val track = state.value.audioTracks.getOrNull(index) ?: return
        closeOverlay()
        state.update { it.copy(selectedAudioStreamIndex = track.streamIndex) }
        val playback = playbackOf() ?: return
        if (playback.isTranscode) {
            reload(track.streamIndex, state.value.selectedSubtitleStreamIndex)
        } else {
            applySelections()
        }
    }

    /**
     * With burn-in off, an image subtitle cannot be shown at all. Selecting one is refused out loud
     * rather than accepted into a state where the track reads as chosen and nothing appears on
     * screen; the list stays open on the switch that would make it work.
     */
    fun selectSubtitleTrack(index: Int) {
        val current = state.value
        val track = if (index < 0) null else current.subtitleTracks.getOrNull(index)
        if (track != null && !track.isTextSubtitle && !current.burnInImageSubtitles) {
            state.update {
                it.copy(
                    overlayIndex = it.burnInRowIndex,
                    subtitleNotice = "${track.label} is a picture, not text. It can only be shown " +
                        "by having the server draw it into the video."
                )
            }
            return
        }
        closeOverlay()
        state.update {
            it.copy(selectedSubtitleStreamIndex = track?.streamIndex, subtitleNotice = null)
        }
        if (track != null && !track.isTextSubtitle) {
            reload(state.value.selectedAudioStreamIndex, track.streamIndex)
        } else {
            applySelections()
        }
    }

    /**
     * Burn-in is a choice about this playback; the stored preference only decided where this
     * playback started.
     *
     * Turning it off while a burned-in subtitle is showing would leave the picture carrying a track
     * the state says is gone, and turning it on is what a selected image subtitle was waiting for -
     * both need the stream renegotiated. With no image subtitle in play the switch changes nothing
     * about the current stream, so nothing is torn down to honour it.
     */
    fun toggleBurnIn() {
        val current = state.value
        val enabled = !current.burnInImageSubtitles
        val selected = current.subtitleTracks.firstOrNull {
            it.streamIndex == current.selectedSubtitleStreamIndex
        }
        val burnedInTrackActive = selected != null && !selected.isTextSubtitle
        state.update { it.copy(burnInImageSubtitles = enabled, subtitleNotice = null) }
        if (!burnedInTrackActive) return
        val keepSubtitle = if (enabled) selected?.streamIndex else null
        if (!enabled) state.update { it.copy(selectedSubtitleStreamIndex = null) }
        closeOverlay()
        reload(state.value.selectedAudioStreamIndex, keepSubtitle)
    }

    /**
     * Pushes the current choices at the player. Called again whenever the track list changes,
     * because a selection made before the tracks were known has nothing to attach itself to.
     */
    fun applySelections() {
        val player = playerOf() ?: return
        val playback = playbackOf() ?: return
        val current = state.value
        val audioOrdinal = playback.audioTracks
            .firstOrNull { it.streamIndex == current.selectedAudioStreamIndex }
            ?.ordinal
        val subtitleStreamIndex = current.selectedSubtitleStreamIndex?.takeIf { index ->
            current.subtitleTracks.any { it.streamIndex == index && it.isTextSubtitle }
        }
        engine.applySelections(player, audioOrdinal, subtitleStreamIndex)
    }
}
