package com.nendo.argosy.ui.components.resumeplayer

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import com.nendo.argosy.ui.components.musicplayer.TrackListCursor
import com.nendo.argosy.ui.components.nowplaying.hasQueue

enum class ResumePlayerFocus {
    PAGE,
    CARD,
    PANEL
}

enum class ResumePlayerButton(val transport: MusicTransportButton?) {
    PREVIOUS(MusicTransportButton.PREVIOUS),
    PLAY_PAUSE(MusicTransportButton.PLAY_PAUSE),
    NEXT(MusicTransportButton.NEXT),
    QUEUE(null)
}

val ResumePlayerTransportButtons: List<MusicTransportButton> = ResumePlayerButton.entries.mapNotNull { it.transport }

data class ResumeStagePreferences(
    val nowPlaying: NowPlayingPreferences = NowPlayingPreferences(),
    val playInBackground: Boolean = false
)

data class ResumePlayerUiState(
    val playback: AmbientPlaybackState = AmbientPlaybackState(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val analysis: TrackAnalysis? = null,
    val focus: ResumePlayerFocus = ResumePlayerFocus.PAGE,
    val button: ResumePlayerButton = ResumePlayerButton.PLAY_PAUSE,
    val listFocus: Int = 0,
    val cursor: TrackListCursor? = null,
    val hasGames: Boolean = false,
    val canOpenNowPlaying: Boolean = false,
    val stage: ResumeStagePreferences = ResumeStagePreferences(),
    val panelOpen: Boolean = false
) {
    val showsCard: Boolean get() = playback.hasQueue
    val cardFocused: Boolean get() = focus == ResumePlayerFocus.CARD
    val listFocused: Boolean get() = focus == ResumePlayerFocus.PANEL
    val collapseArmed: Boolean get() = panelOpen && cardFocused
    val holdsFocus: Boolean get() = focus != ResumePlayerFocus.PAGE

    val buttons: List<ResumePlayerButton> get() = ResumePlayerButton.entries
}

fun upFromGamesEntersCard(alongCards: Boolean, focusedIndex: Int): Boolean = alongCards || focusedIndex == 0
