package com.nendo.argosy.ui.screens.musicbrowser

import com.nendo.argosy.data.remote.romm.RomMMusicFacet
import com.nendo.argosy.domain.usecase.music.LocalMusicTrackState

enum class MusicBrowserMode { BGM, SFX }

data class MusicTrackUi(
    val romFileId: Long,
    val romId: Long,
    val title: String,
    val artistAlbum: String?,
    val durationLabel: String?,
    val fileName: String,
    val streamUrl: String,
    val platformName: String,
    val gameName: String,
    val trackNumber: Int?,
    val disc: Int?,
    val trackTitle: String?
)

data class GameGroup(
    val romId: Long,
    val gameName: String,
    val platformName: String,
    val coverPath: String?,
    val startIndex: Int,
    val tracks: List<MusicTrackUi>
)

enum class FacetPickerStage { CHOOSER, VALUES }

data class FacetPickerUi(
    val stage: FacetPickerStage,
    val facet: RomMMusicFacet? = null,
    val title: String = "",
    val options: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val focusIndex: Int = 0
)

const val SFX_DURATION_MIN_SECONDS = 1
const val SFX_DURATION_MAX_SECONDS = 5
const val SFX_DURATION_DEFAULT_SECONDS = 3
const val DURATION_FILTER_OPTION = "Max Duration"

data class MusicBrowserState(
    val mode: MusicBrowserMode = MusicBrowserMode.BGM,
    val tracks: List<MusicTrackUi> = emptyList(),
    val groups: List<GameGroup> = emptyList(),
    val coversByRomId: Map<Long, String> = emptyMap(),
    val total: Int = 0,
    val localByRomFileId: Map<Long, LocalMusicTrackState> = emptyMap(),
    val downloadingIds: Set<Long> = emptySet(),
    val playlistPaths: Set<String> = emptySet(),
    val playlistFileIds: Set<Long> = emptySet(),
    val playlistPathByFileId: Map<Long, String> = emptyMap(),
    val searchQuery: String = "",
    val artistFilter: String? = null,
    val albumFilter: String? = null,
    val genreFilter: String? = null,
    val sfxMaxDuration: Int = SFX_DURATION_DEFAULT_SECONDS,
    val focusedIndex: Int = -1,
    val showKeyboard: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isUnsupported: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val notice: String? = null,
    val previewingId: Long? = null,
    val facetPicker: FacetPickerUi? = null
) {
    val hasMore: Boolean get() = tracks.size < total
    val hasActiveFilters: Boolean get() = artistFilter != null || albumFilter != null || genreFilter != null

    fun isDownloaded(track: MusicTrackUi): Boolean = localByRomFileId.containsKey(track.romFileId)

    fun isInPlaylist(track: MusicTrackUi): Boolean {
        val local = localByRomFileId[track.romFileId] ?: return false
        if (local.localPath in playlistPaths) return true
        return local.gameFileId?.let { it in playlistFileIds } == true
    }

    fun groupIndexOf(flatIndex: Int): Int {
        if (flatIndex < 0) return -1
        return groups.indexOfLast { it.startIndex <= flatIndex }
    }
}
