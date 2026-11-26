package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicColumnsPage
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerState
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack

const val MUSIC_SETTINGS_SECTION = "navidrome"

/**
 * The Music screen. [position] is the strip cursor, unbounded while the strip [wraps] and the
 * focused index when it does not. [sources] is null until the configured sources are first known.
 */
data class MusicPlayingUi(
    val sourceId: String? = null,
    val trackId: String? = null,
    val isPlaying: Boolean = false
)

data class MusicStackUi(
    val columnKey: String,
    val albums: List<MusicAlbum>,
    val index: Int
) {
    val front: MusicAlbum? get() = albums.getOrNull(index)
}

data class MusicUiState(
    val sources: Set<MusicSourceKind>? = null,
    val grouping: MusicGrouping = MusicGrouping.ARTISTS,
    val filter: MusicSourceFilter = MusicSourceFilter.ALL,
    val page: MusicColumnsPage? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val position: Int = 0,
    val slotCount: Int = 1,
    val contents: MusicContentsUiState? = null,
    val options: MusicOptionsState? = null,
    val filterPicker: MusicFilterState? = null,
    val playlistDialog: MusicPlaylistDialog? = null,
    val showsPlaylistPermission: Boolean = false,
    val hasQueue: Boolean = false,
    val playing: MusicPlayingUi = MusicPlayingUi(),
    val stack: MusicStackUi? = null,
    val jumpLabel: String? = null,
    val showsJumpLabel: Boolean = false
) {
    val focusedStack: MusicStackUi? get() = stack?.takeIf { it.columnKey == focusedColumn?.key }
    val canFlip: Boolean
        get() = focusedColumn?.let { it.kind != MusicColumnKind.PLAYLIST && it.albumCount > 1 } == true
    val columns: List<MusicColumn> get() = page?.columns.orEmpty()
    val wraps: Boolean get() = columns.size > slotCount
    val focusIndex: Int
        get() = when {
            columns.isEmpty() -> 0
            wraps -> position.mod(columns.size)
            else -> position.coerceIn(0, columns.lastIndex)
        }
    val focusedColumn: MusicColumn? get() = columns.getOrNull(focusIndex)
    val hasNoSources: Boolean get() = sources?.isEmpty() == true
    val showsColumns: Boolean get() = !hasNoSources && !loadFailed && columns.isNotEmpty()
}

/**
 * The second level opened on a column. Album columns browse [albums] as a crate and list the
 * focused album's [tracks]; a [isFlat] column lists its tracks directly. A non-null [moveGroup]
 * is move mode: the focused track is lifted and up/down reorders it within that playlist.
 */
data class MusicContentsUiState(
    val column: MusicColumn,
    val albums: List<MusicAlbum> = emptyList(),
    val tracks: List<MusicTrack> = emptyList(),
    val albumIndex: Int = 0,
    val trackIndex: Int = 0,
    val isLoading: Boolean = true,
    val tracksLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val moveGroup: MusicPlaylistGroup? = null
) {
    val isFlat: Boolean get() = column.kind == MusicColumnKind.PLAYLIST || albums.isEmpty()
    val focusedAlbum: MusicAlbum? get() = if (isFlat) null else albums.getOrNull(albumIndex)
    val focusedTrack: MusicTrack? get() = tracks.getOrNull(trackIndex)
    val isMoving: Boolean get() = moveGroup != null
    val canPlayColumn: Boolean get() = !isFlat && !isLoading && !loadFailed

    fun followingPlaying(previousId: String?, playingId: String?, listFocused: Boolean): MusicContentsUiState {
        if (!listFocused || isMoving || previousId == null || playingId == null || previousId == playingId) return this
        if (focusedTrack?.key != previousId) return this
        val after = tracks.indices.firstOrNull { it > trackIndex && tracks[it].key == playingId }
        val target = after ?: tracks.indexOfFirst { it.key == playingId }.takeIf { it >= 0 } ?: return this
        return copy(trackIndex = target)
    }
}

enum class MusicOptionAction {
    PLAY,
    SHUFFLE,
    DOWNLOAD,
    ADD_TO_PLAYLIST,
    NOW_PLAYING,
    LISTENING,
    RENAME_PLAYLIST,
    REORDER_PLAYLIST,
    RESET_PLAYLIST_ORDER,
    DELETE_PLAYLIST,
    REMOVE_FROM_PLAYLIST
}

sealed interface MusicOptionsTarget {
    data class Column(val column: MusicColumn) : MusicOptionsTarget
    data class Track(val track: MusicTrack, val column: MusicColumn, val index: Int) : MusicOptionsTarget
}

data class MusicOptionsState(
    val target: MusicOptionsTarget,
    val title: String,
    val actions: List<MusicOptionAction>,
    val focusIndex: Int = 0,
    val picker: MusicPlaylistPickerState? = null,
    val isBusy: Boolean = false,
    val group: MusicPlaylistGroup? = null
) {
    val ownsInput: Boolean get() = picker?.ownsInput != false
}

sealed interface MusicPlaylistDialog {
    val group: MusicPlaylistGroup
    val isBusy: Boolean

    data class Rename(override val group: MusicPlaylistGroup, override val isBusy: Boolean = false) : MusicPlaylistDialog
    data class Delete(override val group: MusicPlaylistGroup, override val isBusy: Boolean = false) : MusicPlaylistDialog
}

data class MusicFilterState(
    val options: List<MusicSourceFilter>,
    val focusIndex: Int
)
