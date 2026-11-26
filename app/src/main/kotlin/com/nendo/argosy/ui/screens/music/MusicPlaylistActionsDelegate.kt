package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.ui.components.music.MusicPlaylistAddition
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "MusicPlaylistActions"
private const val PLAYLIST_COLUMN_PREFIX = "playlist:"

private class MusicTargetAddition(
    private val target: MusicOptionsTarget,
    private val resolver: MusicTrackResolver,
    private val grouping: MusicGrouping,
    private val filter: MusicSourceFilter
) : MusicPlaylistAddition {
    private suspend fun tracks(): List<MusicTrack> = when (target) {
        is MusicOptionsTarget.Column -> resolver.columnTracks(target.column, grouping, filter)
        is MusicOptionsTarget.Track -> listOf(target.track)
    }

    override suspend fun addTo(playlists: MusicPlaylists, group: MusicPlaylistGroup): MusicPlaylistResult {
        val tracks = tracks()
        return if (tracks.isEmpty()) MusicPlaylistResult.Failed else playlists.addTracks(group, tracks)
    }

    override suspend fun createNamed(playlists: MusicPlaylists, name: String): MusicPlaylistResult {
        val tracks = tracks()
        return if (tracks.isEmpty()) MusicPlaylistResult.Failed else playlists.create(name, tracks)
    }
}

internal val MusicOptionsTarget.column: MusicColumn
    get() = when (this) {
        is MusicOptionsTarget.Column -> column
        is MusicOptionsTarget.Track -> column
    }

internal fun <T> List<T>.movedItem(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

class MusicPlaylistActionsDelegate(
    private val playlists: MusicPlaylists,
    private val notifications: NotificationManager,
    private val resolver: MusicTrackResolver,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<MusicUiState>,
    private val onOpenForReorder: (MusicColumn, MusicPlaylistGroup) -> Unit,
    private val onReloadContents: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val moveLock = Mutex()

    val picker = MusicPlaylistPickerFlow(
        playlists = playlists,
        notifications = notifications,
        scope = scope,
        picker = { state.value.options?.picker },
        updatePicker = { transform ->
            state.update { s ->
                val options = s.options ?: return@update s
                s.copy(options = options.copy(picker = transform(options.picker)))
            }
        },
        addition = ::currentAddition,
        onFinished = { state.update { it.copy(options = null) } },
        onNeedsPermission = ::showPermission,
        io = io
    )

    private fun currentAddition(): MusicPlaylistAddition? {
        val snapshot = state.value
        val target = snapshot.options?.target ?: return null
        return MusicTargetAddition(target, resolver, snapshot.grouping, snapshot.filter)
    }

    fun onPlaylistsChanged() = picker.refresh()

    fun attachManagement(target: MusicOptionsTarget) {
        val column = target.column
        if (column.kind != MusicColumnKind.PLAYLIST) return
        val key = column.key.removePrefix(PLAYLIST_COLUMN_PREFIX)
        scope.launch {
            val group = musicGuarded(TAG, "playlist group") { withContext(io) { playlists.group(key) } }
            if (group == null || !group.owned) return@launch
            state.update { s ->
                val options = s.options?.takeIf { it.target == target && it.group == null } ?: return@update s
                s.copy(options = options.copy(group = group, actions = options.actions + managementActions(target, group)))
            }
        }
    }

    private fun managementActions(target: MusicOptionsTarget, group: MusicPlaylistGroup): List<MusicOptionAction> =
        when (target) {
            is MusicOptionsTarget.Column -> buildList {
                add(MusicOptionAction.RENAME_PLAYLIST)
                if (group.trackCount > 1) add(MusicOptionAction.REORDER_PLAYLIST)
                if (group.hasCustomOrder) add(MusicOptionAction.RESET_PLAYLIST_ORDER)
                add(MusicOptionAction.DELETE_PLAYLIST)
            }
            is MusicOptionsTarget.Track -> buildList {
                if (group.trackCount > 1) add(MusicOptionAction.REORDER_PLAYLIST)
                add(MusicOptionAction.REMOVE_FROM_PLAYLIST)
            }
        }

    fun confirm(action: MusicOptionAction, options: MusicOptionsState): Boolean {
        val group = options.group ?: return false
        when (action) {
            MusicOptionAction.RENAME_PLAYLIST -> openDialog(MusicPlaylistDialog.Rename(group))
            MusicOptionAction.DELETE_PLAYLIST -> openDialog(MusicPlaylistDialog.Delete(group))
            MusicOptionAction.REORDER_PLAYLIST -> startReorder(options.target, group)
            MusicOptionAction.RESET_PLAYLIST_ORDER -> resetOrder(group)
            MusicOptionAction.REMOVE_FROM_PLAYLIST -> remove(options.target, group)
            else -> return false
        }
        return true
    }

    private fun openDialog(dialog: MusicPlaylistDialog) =
        state.update { it.copy(options = null, playlistDialog = dialog) }

    fun dismissDialog() = state.update { s ->
        if (s.playlistDialog?.isBusy == true) s else s.copy(playlistDialog = null)
    }

    private fun closeDialog() = state.update { it.copy(playlistDialog = null) }

    private fun setDialogBusy(busy: Boolean) = state.update { s ->
        val dialog = when (val current = s.playlistDialog) {
            is MusicPlaylistDialog.Rename -> current.copy(isBusy = busy)
            is MusicPlaylistDialog.Delete -> current.copy(isBusy = busy)
            null -> null
        }
        s.copy(playlistDialog = dialog)
    }

    private fun showPermission() = state.update { it.copy(showsPlaylistPermission = true) }

    fun dismissPermission() = state.update { it.copy(showsPlaylistPermission = false) }

    fun submitRename(name: String) {
        val dialog = state.value.playlistDialog as? MusicPlaylistDialog.Rename ?: return
        if (dialog.isBusy) return
        if (name == dialog.group.name) {
            closeDialog()
            return
        }
        setDialogBusy(true)
        scope.launch {
            val result = musicGuarded(TAG, "rename") { withContext(io) { playlists.rename(dialog.group, name) } }
            when (result) {
                is MusicPlaylistResult.Done -> {
                    closeDialog()
                    val shown = result.group?.name ?: name
                    notifications.showSuccess(NotificationText.Res(R.string.music_library_notice_renamed, listOf(shown)))
                }
                is MusicPlaylistResult.NameTaken -> {
                    setDialogBusy(false)
                    notifications.showError(NotificationText.Res(R.string.music_library_notice_name_taken, listOf(name)))
                }
                MusicPlaylistResult.NeedsRommPermission -> {
                    closeDialog()
                    showPermission()
                }
                MusicPlaylistResult.Failed, null -> {
                    closeDialog()
                    notifications.showError(NotificationText.Res(R.string.music_library_notice_rename_failed))
                }
            }
        }
    }

    fun confirmDelete() {
        val dialog = state.value.playlistDialog as? MusicPlaylistDialog.Delete ?: return
        if (dialog.isBusy) return
        setDialogBusy(true)
        scope.launch {
            val result = musicGuarded(TAG, "delete") { withContext(io) { playlists.delete(dialog.group) } }
            closeDialog()
            when (result) {
                is MusicPlaylistResult.Done -> notifications.showSuccess(
                    NotificationText.Res(R.string.music_library_notice_deleted, listOf(dialog.group.name))
                )
                MusicPlaylistResult.NeedsRommPermission -> showPermission()
                is MusicPlaylistResult.NameTaken, MusicPlaylistResult.Failed, null -> notifications.showError(
                    NotificationText.Res(R.string.music_library_notice_delete_failed)
                )
            }
        }
    }

    private fun resetOrder(group: MusicPlaylistGroup) {
        state.update { it.copy(options = null) }
        scope.launch {
            val reset = musicGuarded(TAG, "reset order") {
                withContext(io) { playlists.resetOrder(group) }
                true
            } == true
            if (reset) {
                notifications.showSuccess(NotificationText.Res(R.string.music_library_notice_order_reset, listOf(group.name)))
            } else {
                notifications.showError(NotificationText.Res(R.string.music_library_notice_order_reset_failed))
            }
        }
    }

    private fun startReorder(target: MusicOptionsTarget, group: MusicPlaylistGroup) {
        when (target) {
            is MusicOptionsTarget.Column -> {
                state.update { it.copy(options = null) }
                onOpenForReorder(target.column, group)
            }
            is MusicOptionsTarget.Track -> state.update { s ->
                val contents = s.contents?.takeIf { it.column.key == target.column.key }
                s.copy(
                    options = null,
                    contents = contents?.copy(moveGroup = group, trackIndex = target.index) ?: s.contents
                )
            }
        }
    }

    fun moveFocused(delta: Int): Boolean {
        val contents = state.value.contents ?: return false
        val group = contents.moveGroup ?: return false
        val from = contents.trackIndex
        val to = from + delta
        if (from !in contents.tracks.indices || to !in contents.tracks.indices) return false
        val columnKey = contents.column.key
        var applied = false
        state.update { s ->
            applied = false
            val current = s.contents
                ?.takeIf { it.column.key == columnKey && it.trackIndex == from && it.isMoving }
                ?: return@update s
            applied = true
            s.copy(contents = current.copy(tracks = current.tracks.movedItem(from, to), trackIndex = to))
        }
        if (!applied) return false
        scope.launch {
            val result = moveLock.withLock {
                val latest = state.value.contents?.takeIf { it.column.key == columnKey }?.moveGroup ?: group
                musicGuarded(TAG, "move") { withContext(io) { playlists.move(latest, from, to) } }
            }
            onMoved(columnKey, result)
        }
        return true
    }

    private fun onMoved(columnKey: String, result: MusicPlaylistResult?) {
        when (result) {
            is MusicPlaylistResult.Done -> {
                val updated = result.group ?: return
                state.update { s ->
                    val contents = s.contents?.takeIf { it.column.key == columnKey && it.isMoving } ?: return@update s
                    s.copy(contents = contents.copy(moveGroup = updated))
                }
            }
            MusicPlaylistResult.NeedsRommPermission -> {
                endMove()
                showPermission()
                onReloadContents()
            }
            is MusicPlaylistResult.NameTaken, MusicPlaylistResult.Failed, null -> {
                endMove()
                notifications.showError(NotificationText.Res(R.string.music_library_notice_move_failed))
                onReloadContents()
            }
        }
    }

    fun endMove(): Boolean {
        if (state.value.contents?.isMoving != true) return false
        state.update { s -> s.copy(contents = s.contents?.copy(moveGroup = null)) }
        return true
    }

    private fun remove(target: MusicOptionsTarget, group: MusicPlaylistGroup) {
        val trackTarget = target as? MusicOptionsTarget.Track ?: return
        state.update { it.copy(options = null) }
        scope.launch {
            val result = musicGuarded(TAG, "remove") {
                withContext(io) {
                    val entry = playlists.entries(group)
                        .getOrNull(trackTarget.index)
                        ?.takeIf { it.track.key == trackTarget.track.key }
                    if (entry == null) MusicPlaylistResult.Failed else playlists.remove(group, entry)
                }
            }
            when (result) {
                is MusicPlaylistResult.Done -> {
                    dropTrack(trackTarget)
                    notifications.showSuccess(NotificationText.Res(R.string.music_library_notice_removed, listOf(group.name)))
                }
                MusicPlaylistResult.NeedsRommPermission -> showPermission()
                is MusicPlaylistResult.NameTaken, MusicPlaylistResult.Failed, null -> notifications.showError(
                    NotificationText.Res(R.string.music_library_notice_remove_failed)
                )
            }
        }
    }

    private fun dropTrack(target: MusicOptionsTarget.Track) = state.update { s ->
        val contents = s.contents?.takeIf { it.column.key == target.column.key } ?: return@update s
        if (contents.tracks.getOrNull(target.index)?.key != target.track.key) return@update s
        val tracks = contents.tracks.filterIndexed { index, _ -> index != target.index }
        s.copy(contents = contents.copy(tracks = tracks, trackIndex = contents.trackIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))))
    }
}
