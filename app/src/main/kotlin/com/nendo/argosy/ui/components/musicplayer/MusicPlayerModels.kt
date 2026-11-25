package com.nendo.argosy.ui.components.musicplayer

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.components.nowplaying.hasQueue
import com.nendo.argosy.ui.components.nowplaying.isAudible
import com.nendo.argosy.ui.screens.settings.delegates.VolumeLevels
import com.nendo.argosy.ui.screens.settings.delegates.levelIndexIn

enum class MusicPlayerRow {
    TRANSPORT,
    NOW_PLAYING,
    VOLUME,
    SOURCES,
    TRACKS
}

enum class MusicTransportButton {
    ADD_TO_PLAYLIST,
    SHUFFLE,
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    REPEAT,
    BACKGROUND
}

fun transportButtons(withPlaylist: Boolean): List<MusicTransportButton> =
    if (withPlaylist) MusicTransportButton.entries else MusicTransportButton.entries - MusicTransportButton.ADD_TO_PLAYLIST

fun List<MusicTransportButton>.stepFrom(current: MusicTransportButton, delta: Int): MusicTransportButton =
    this[(indexOf(current).coerceAtLeast(0) + delta).mod(size)]

enum class MusicBrowseKind {
    PLAYLISTS,
    SOUNDTRACKS
}

enum class MusicBrowseStatus {
    LOADING,
    READY,
    FAILED
}

enum class MusicBrowseNotice {
    OFFLINE,
    LOCAL_ONLY,
    SERVER_FAILED,
    NO_PLAYABLE_TRACKS,
    SIGN_IN_FOR_PLAYLISTS
}

data class MusicBrowseRowUi(
    val selection: MusicSelection,
    val title: String?,
    val ownerName: String?,
    val platformName: String?,
    val coverPath: String?,
    val trackCount: Int?
) {
    val key: String get() = selection.id
    val isLauncher: Boolean get() = selection is MusicSelection.Launcher
}

data class MusicBrowseUi(
    val kind: MusicBrowseKind,
    val query: String = "",
    val rows: List<MusicBrowseRowUi> = emptyList(),
    val status: MusicBrowseStatus = MusicBrowseStatus.LOADING,
    val focusIndex: Int = 0,
    val showKeyboard: Boolean = false,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val notice: MusicBrowseNotice? = null,
    val pendingSelectionId: String? = null
) {
    val itemCount: Int
        get() = when (status) {
            MusicBrowseStatus.READY -> rows.size
            MusicBrowseStatus.FAILED -> 1
            MusicBrowseStatus.LOADING -> 0
        }

    val isSearchFocused: Boolean get() = focusIndex == SEARCH_FOCUS_INDEX

    companion object {
        const val SEARCH_FOCUS_INDEX = -1
    }
}

data class MusicPlayerUiState(
    val playback: AmbientPlaybackState = AmbientPlaybackState(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val launcherEnabled: Boolean = false,
    val volumeLevel: Int = 0,
    val playInBackground: Boolean = false,
    val nowPlaying: NowPlayingPreferences = NowPlayingPreferences(),
    val focusedRow: MusicPlayerRow = MusicPlayerRow.TRANSPORT,
    val transportButton: MusicTransportButton = MusicTransportButton.PLAY_PAUSE,
    val sourceButton: MusicBrowseKind = MusicBrowseKind.PLAYLISTS,
    val trackFocus: Int = 0,
    val browse: MusicBrowseUi? = null,
    val analysis: TrackAnalysis? = null
) {
    val isAudible: Boolean get() = playback.isAudible
    val hasQueue: Boolean get() = playback.hasQueue
    val volumeFraction: Float
        get() = (levelIndexIn(volumeLevel, VolumeLevels.AMBIENT_AUDIO) + 1).toFloat() /
            VolumeLevels.AMBIENT_AUDIO.size
}
