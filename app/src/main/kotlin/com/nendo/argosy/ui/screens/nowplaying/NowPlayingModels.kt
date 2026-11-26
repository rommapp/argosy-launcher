package com.nendo.argosy.ui.screens.nowplaying

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.data.preferences.NowPlayingStyle
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerState
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import com.nendo.argosy.ui.components.musicplayer.TrackListCursor
import com.nendo.argosy.ui.components.nowplaying.hasQueue
import com.nendo.argosy.ui.components.nowplaying.isAudible

enum class NowPlayingFocus { WAVE, TRANSPORT, QUEUE }

enum class NowPlayingOption { STYLE, SPIN, LAYOUT }

fun visibleNowPlayingOptions(style: NowPlayingStyle): List<NowPlayingOption> =
    if (style.spins) NowPlayingOption.entries else NowPlayingOption.entries - NowPlayingOption.SPIN

data class NowPlayingOptionsUi(
    val focused: NowPlayingOption = NowPlayingOption.STYLE,
    val pickerOption: NowPlayingOption? = null,
    val pickerToken: Int = 0
)

/**
 * Focus and overlays of the Now Playing screen. [split] holds while a presentation screen shows
 * the stage: the control screen then always lists the wave, the transport and the running order,
 * whatever the layout preference.
 */
data class NowPlayingNavigation(
    val focus: NowPlayingFocus = NowPlayingFocus.TRANSPORT,
    val transport: MusicTransportButton = MusicTransportButton.PLAY_PAUSE,
    val queueFocus: Int = 0,
    val queueCursor: TrackListCursor? = null,
    val options: NowPlayingOptionsUi? = null,
    val playlistPicker: MusicPlaylistPickerState? = null,
    val showsPlaylistPermission: Boolean = false,
    val split: Boolean = false
)

data class NowPlayingUiState(
    val playback: AmbientPlaybackState = AmbientPlaybackState(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val prefs: NowPlayingPreferences = NowPlayingPreferences(),
    val navigation: NowPlayingNavigation = NowPlayingNavigation(),
    val analysis: TrackAnalysis? = null,
    val playInBackground: Boolean = false
) {
    val isAudible: Boolean get() = playback.isAudible
    val hasQueue: Boolean get() = playback.hasQueue
    val split: Boolean get() = navigation.split
    private val listsControls: Boolean get() = split || prefs.layout == NowPlayingLayout.SHELF
    val showsTransport: Boolean get() = hasQueue && listsControls
    val showsQueue: Boolean get() = listsControls && playback.tracks.isNotEmpty()
    val focus: NowPlayingFocus
        get() = when {
            navigation.focus == NowPlayingFocus.WAVE && split && hasQueue -> NowPlayingFocus.WAVE
            navigation.focus == NowPlayingFocus.QUEUE && showsQueue -> NowPlayingFocus.QUEUE
            else -> NowPlayingFocus.TRANSPORT
        }
    val queueFocus: Int
        get() = navigation.queueFocus.coerceIn(0, (playback.tracks.size - 1).coerceAtLeast(0))
    val visibleOptions: List<NowPlayingOption> get() = visibleNowPlayingOptions(prefs.style)
}

/**
 * What the presentation screen draws while Now Playing is open on the control screen. [hints]
 * is the inline hint line in display order.
 */
data class NowPlayingStageState(
    val playback: AmbientPlaybackState,
    val positionMs: Long,
    val durationMs: Long,
    val analysis: TrackAnalysis?,
    val style: NowPlayingStyle,
    val spin: Boolean,
    val playInBackground: Boolean,
    val hints: List<NowPlayingStageHint>
)

enum class NowPlayingStageHint { SKIP_SECONDS, TRACK, QUEUE, STYLE, SWAP_SCREENS }

/**
 * The inline hint line for the stage: bumper seeking unless quick navigation keeps the bumpers,
 * and the Select press as Style or, in tap-to-swap mode, as the swap.
 */
fun nowPlayingStageHints(
    hasQueue: Boolean,
    showsQueue: Boolean,
    bumpersSeek: Boolean,
    selectSwaps: Boolean
): List<NowPlayingStageHint> = buildList {
    if (hasQueue) {
        if (bumpersSeek) add(NowPlayingStageHint.SKIP_SECONDS)
        add(NowPlayingStageHint.TRACK)
    }
    if (showsQueue) add(NowPlayingStageHint.QUEUE)
    add(if (selectSwaps) NowPlayingStageHint.SWAP_SCREENS else NowPlayingStageHint.STYLE)
}

/**
 * The stage slot to publish, or null when the screen stays single-screen or nothing is queued.
 */
fun NowPlayingUiState.stageSlot(
    bumpersSeek: Boolean,
    selectSwaps: Boolean
): com.nendo.argosy.ui.dualscreen.PresentationSlot.NowPlayingStage? {
    if (!split || !hasQueue) return null
    return com.nendo.argosy.ui.dualscreen.PresentationSlot.NowPlayingStage(
        NowPlayingStageState(
            playback = playback,
            positionMs = positionMs,
            durationMs = durationMs,
            analysis = analysis,
            style = prefs.style,
            spin = prefs.spin,
            playInBackground = playInBackground,
            hints = nowPlayingStageHints(hasQueue, showsQueue, bumpersSeek, selectSwaps)
        )
    )
}
