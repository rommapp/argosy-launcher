package com.nendo.argosy.ui.screens.nowplaying

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.data.preferences.NowPlayingStyle
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerState
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import com.nendo.argosy.ui.components.nowplaying.hasQueue
import com.nendo.argosy.ui.components.nowplaying.isAudible

enum class NowPlayingFocus { TRANSPORT, QUEUE }

enum class NowPlayingOption { STYLE, SPIN, LAYOUT }

fun visibleNowPlayingOptions(style: NowPlayingStyle): List<NowPlayingOption> =
    if (style.spins) NowPlayingOption.entries else NowPlayingOption.entries - NowPlayingOption.SPIN

data class NowPlayingOptionsUi(
    val focused: NowPlayingOption = NowPlayingOption.STYLE,
    val pickerOption: NowPlayingOption? = null,
    val pickerToken: Int = 0
)

data class NowPlayingNavigation(
    val focus: NowPlayingFocus = NowPlayingFocus.TRANSPORT,
    val transport: MusicTransportButton = MusicTransportButton.PLAY_PAUSE,
    val queueFocus: Int = 0,
    val options: NowPlayingOptionsUi? = null,
    val playlistPicker: MusicPlaylistPickerState? = null,
    val addingToPlaylist: Boolean = false
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
    val showsQueue: Boolean get() = prefs.layout == NowPlayingLayout.SHELF && playback.tracks.isNotEmpty()
    val focus: NowPlayingFocus
        get() = if (showsQueue) navigation.focus else NowPlayingFocus.TRANSPORT
    val queueFocus: Int
        get() = navigation.queueFocus.coerceIn(0, (playback.tracks.size - 1).coerceAtLeast(0))
    val visibleOptions: List<NowPlayingOption> get() = visibleNowPlayingOptions(prefs.style)
}
