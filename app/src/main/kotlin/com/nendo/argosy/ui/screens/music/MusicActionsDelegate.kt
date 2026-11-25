package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerState
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MusicActions"

internal suspend fun <T> musicGuarded(tag: String, what: String, block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Logger.warn(tag, "$what failed", e)
    null
}

class MusicActionsDelegate(
    private val library: MusicLibrary,
    private val playback: MusicPlayback,
    private val notifications: NotificationManager,
    private val resolver: MusicTrackResolver,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<MusicUiState>,
    private val onPlaybackStarted: () -> Unit
) {
    fun playColumn(column: MusicColumn, shuffle: Boolean) {
        val snapshot = state.value
        val grouping = snapshot.grouping
        val filter = snapshot.filter
        scope.launch {
            if (column.kind == MusicColumnKind.RECENT && !shuffle && resumeRecent(column, grouping, filter)) {
                return@launch
            }
            val tracks = musicGuarded(TAG, "column tracks") { resolver.columnTracks(column, grouping, filter) }
            if (tracks == null) {
                notifications.showError(NotificationText.Res(R.string.music_library_notice_play_failed))
                return@launch
            }
            play(column.key, column.title, if (shuffle) tracks.shuffled() else tracks, 0)
        }
    }

    private suspend fun resumeRecent(column: MusicColumn, grouping: MusicGrouping, filter: MusicSourceFilter): Boolean {
        val point = musicGuarded(TAG, "resume point") { withContext(Dispatchers.IO) { library.resumePoint() } }
            ?: return false
        val contents = musicGuarded(TAG, "contents") { resolver.contents(column, grouping, filter) } ?: return false
        val album = contents.albums.firstOrNull { it.key == point.albumKey } ?: return false
        val tracks = musicGuarded(TAG, "album tracks") { resolver.albumTracks(album, contents.tracks) }.orEmpty()
        if (tracks.isEmpty()) return false
        val queue = musicGuarded(TAG, "queue") { withContext(Dispatchers.IO) { library.queueFor(tracks) } }.orEmpty()
        val index = queue.indexOfFirst { it.identity?.trackKey == point.trackKey }
        if (index < 0) return false
        val startIndex = if (point.completed) index + 1 else index
        if (startIndex > queue.lastIndex) return false
        val startPositionMs = if (point.completed) 0L else point.positionMs
        playback.playQueue(album.key, album.title, queue, startIndex, startPositionMs)
        onPlaybackStarted()
        return true
    }

    fun playTracks(sourceId: String, label: String, tracks: List<MusicTrack>, startIndex: Int) {
        scope.launch { play(sourceId, label, tracks, startIndex) }
    }

    private suspend fun play(sourceId: String, label: String, tracks: List<MusicTrack>, startIndex: Int) {
        if (playback.play(sourceId, label, tracks, startIndex)) onPlaybackStarted()
    }

    fun openColumnOptions(column: MusicColumn) {
        state.update {
            it.copy(
                options = MusicOptionsState(
                    target = MusicOptionsTarget.Column(column),
                    title = column.title,
                    actions = withNowPlaying(COLUMN_ACTIONS, it.hasQueue)
                )
            )
        }
    }

    fun openTrackOptions(track: MusicTrack, column: MusicColumn) {
        state.update {
            it.copy(
                options = MusicOptionsState(
                    target = MusicOptionsTarget.Track(track, column),
                    title = track.title,
                    actions = withNowPlaying(TRACK_ACTIONS, it.hasQueue)
                )
            )
        }
    }

    private fun withNowPlaying(actions: List<MusicOptionAction>, hasQueue: Boolean) =
        if (hasQueue) actions + MusicOptionAction.NOW_PLAYING else actions

    fun moveOptionsFocus(delta: Int) {
        state.update { s ->
            val options = s.options ?: return@update s
            val picker = options.picker
            when {
                picker != null -> s.copy(options = options.copy(picker = picker.moved(delta)))
                else -> s.copy(
                    options = options.copy(focusIndex = (options.focusIndex + delta).mod(options.actions.size))
                )
            }
        }
    }

    fun focusOption(index: Int) {
        state.update { s ->
            val options = s.options ?: return@update s
            val picker = options.picker
            if (picker != null) {
                s.copy(options = options.copy(picker = picker.copy(focusIndex = index)))
            } else {
                s.copy(options = options.copy(focusIndex = index))
            }
        }
    }

    fun confirmOption(onOpenNowPlaying: () -> Unit, onOpenListening: () -> Unit) {
        val options = state.value.options ?: return
        if (options.isBusy) return
        val picker = options.picker
        if (picker != null) {
            picker.focused?.let { addToPlaylist(options.target, it) }
            return
        }
        when (options.actions.getOrNull(options.focusIndex)) {
            MusicOptionAction.PLAY -> playTarget(options.target, shuffle = false)
            MusicOptionAction.SHUFFLE -> playTarget(options.target, shuffle = true)
            MusicOptionAction.DOWNLOAD -> download(options.target)
            MusicOptionAction.ADD_TO_PLAYLIST -> openPlaylistPicker(options.target)
            MusicOptionAction.NOW_PLAYING -> {
                dismissOptions()
                onOpenNowPlaying()
            }
            MusicOptionAction.LISTENING -> {
                dismissOptions()
                onOpenListening()
            }
            null -> Unit
        }
    }

    fun backOptions() {
        state.update { s ->
            val options = s.options ?: return@update s
            if (options.picker != null && !options.isBusy) {
                s.copy(options = options.copy(picker = null))
            } else {
                s.copy(options = null)
            }
        }
    }

    fun dismissOptions() = state.update { it.copy(options = null) }

    private fun playTarget(target: MusicOptionsTarget, shuffle: Boolean) {
        dismissOptions()
        when (target) {
            is MusicOptionsTarget.Column -> playColumn(target.column, shuffle)
            is MusicOptionsTarget.Track -> playTracks(target.track.key, target.track.title, listOf(target.track), 0)
        }
    }

    private suspend fun tracksOf(target: MusicOptionsTarget): List<MusicTrack>? {
        val snapshot = state.value
        return when (target) {
            is MusicOptionsTarget.Column -> musicGuarded(TAG, "column tracks") {
                resolver.columnTracks(target.column, snapshot.grouping, snapshot.filter)
            }
            is MusicOptionsTarget.Track -> listOf(target.track)
        }
    }

    private fun setBusy(busy: Boolean) = state.update { s ->
        s.copy(options = s.options?.copy(isBusy = busy))
    }

    private fun download(target: MusicOptionsTarget) {
        setBusy(true)
        scope.launch {
            val tracks = tracksOf(target).orEmpty()
            val queued = tracks.isNotEmpty() &&
                musicGuarded(TAG, "download") { withContext(Dispatchers.IO) { library.download(tracks) } } != null
            dismissOptions()
            if (queued) {
                notifications.showSuccess(
                    NotificationText.Plural(R.plurals.music_library_notice_download_queued, tracks.size, listOf(tracks.size))
                )
            } else {
                notifications.showError(NotificationText.Res(R.string.music_library_notice_download_failed))
            }
        }
    }

    private fun openPlaylistPicker(target: MusicOptionsTarget) {
        val filter = state.value.filter
        state.update { s -> s.copy(options = s.options?.copy(picker = MusicPlaylistPickerState())) }
        scope.launch {
            val sources = when (target) {
                is MusicOptionsTarget.Column -> target.column.sources
                is MusicOptionsTarget.Track -> setOf(target.track.source)
            }
            val playlists = musicGuarded(TAG, "playlists") { withContext(Dispatchers.IO) { library.playlists(filter) } }
                .orEmpty()
                .filter { it.writable && it.source in sources }
            state.update { s ->
                val options = s.options ?: return@update s
                if (options.picker == null) return@update s
                s.copy(options = options.copy(picker = MusicPlaylistPickerState(playlists = playlists, isLoading = false)))
            }
        }
    }

    private fun addToPlaylist(target: MusicOptionsTarget, playlist: MusicPlaylist) {
        setBusy(true)
        scope.launch {
            val tracks = tracksOf(target).orEmpty().filter { it.source == playlist.source }
            val result = if (tracks.isEmpty()) {
                null
            } else {
                musicGuarded(TAG, "add to playlist") {
                    withContext(Dispatchers.IO) { library.addToPlaylist(playlist, tracks) }
                }
            }
            dismissOptions()
            if (result?.isSuccess == true) {
                notifications.showSuccess(NotificationText.Res(R.string.music_library_notice_added, listOf(playlist.name)))
            } else {
                notifications.showError(NotificationText.Res(R.string.music_library_notice_add_failed))
            }
        }
    }

    fun openFilter() {
        state.update { s ->
            val sources = s.sources.orEmpty()
            val options = MusicSourceFilter.entries.filter { filter ->
                filter == MusicSourceFilter.ALL || sources.any(filter::admits)
            }
            s.copy(filterPicker = MusicFilterState(options, options.indexOf(s.filter).coerceAtLeast(0)))
        }
    }

    fun moveFilterFocus(delta: Int) = state.update { s ->
        val picker = s.filterPicker ?: return@update s
        s.copy(filterPicker = picker.copy(focusIndex = (picker.focusIndex + delta).mod(picker.options.size)))
    }

    fun focusFilter(index: Int) = state.update { s ->
        val picker = s.filterPicker ?: return@update s
        s.copy(filterPicker = picker.copy(focusIndex = index.coerceIn(0, picker.options.lastIndex)))
    }

    fun dismissFilterWithChoice(): MusicSourceFilter? {
        val picker = state.value.filterPicker ?: return null
        dismissFilter()
        return picker.options.getOrNull(picker.focusIndex)
    }

    fun dismissFilter() = state.update { it.copy(filterPicker = null) }

    private companion object {
        val COLUMN_ACTIONS = listOf(
            MusicOptionAction.PLAY,
            MusicOptionAction.SHUFFLE,
            MusicOptionAction.DOWNLOAD,
            MusicOptionAction.ADD_TO_PLAYLIST,
            MusicOptionAction.LISTENING
        )
        val TRACK_ACTIONS = listOf(MusicOptionAction.DOWNLOAD, MusicOptionAction.ADD_TO_PLAYLIST)
    }
}
