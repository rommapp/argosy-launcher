package com.nendo.argosy.ui.components.music

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.ui.screens.music.musicGuarded
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PlaylistPicker"

interface MusicPlaylistAddition {
    suspend fun addTo(playlists: MusicPlaylists, group: MusicPlaylistGroup): MusicPlaylistResult
    suspend fun createNamed(playlists: MusicPlaylists, name: String): MusicPlaylistResult
}

/**
 * The add-to-playlist picker shared by every screen that offers it. The host owns where the
 * [MusicPlaylistPickerState] lives: [picker] reads it and [updatePicker] replaces it atomically,
 * null meaning closed. [addition] is read at write time, null when there is nothing to add.
 * [onFinished] runs after the picker closes itself on a finished write.
 */
class MusicPlaylistPickerFlow(
    private val playlists: MusicPlaylists,
    private val notifications: NotificationManager,
    private val scope: CoroutineScope,
    private val picker: () -> MusicPlaylistPickerState?,
    private val updatePicker: ((MusicPlaylistPickerState?) -> MusicPlaylistPickerState?) -> Unit,
    private val addition: () -> MusicPlaylistAddition?,
    private val onFinished: () -> Unit,
    private val onNeedsPermission: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private var loadJob: Job? = null

    fun open() {
        val canAdd = addition() != null
        updatePicker { MusicPlaylistPickerState(canAdd = canAdd, isLoading = canAdd) }
        if (canAdd) load()
    }

    fun refresh() {
        if (picker()?.canAdd == true) load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val groups = musicGuarded(TAG, "playlist groups") {
                withContext(io) { playlists.groups(MusicSourceFilter.ALL) }
            }.orEmpty().filter { it.owned }
            updatePicker { current ->
                current?.copy(groups = groups, isLoading = false)?.let { it.focusedAt(it.focusIndex) }
            }
        }
    }

    fun move(delta: Int) = updatePicker { it?.takeIf { p -> p.ownsInput }?.moved(delta) ?: it }

    fun focus(index: Int) = updatePicker { it?.focusedAt(index) }

    fun confirm() {
        val current = picker() ?: return
        if (current.isBusy || !current.ownsInput) return
        if (current.isNewPlaylistFocused) {
            updatePicker { it?.copy(isNaming = true) }
            return
        }
        current.focused?.let(::addTo)
    }

    fun cancelNaming() = updatePicker { current ->
        if (current == null || current.isBusy) current else current.copy(isNaming = false)
    }

    fun submitName(name: String) {
        val trimmed = name.trim()
        val current = picker() ?: return
        if (trimmed.isEmpty() || current.isBusy) return
        write(trimmed) { it.createNamed(playlists, trimmed) }
    }

    fun confirmConflict() {
        val current = picker() ?: return
        val existing = current.conflict ?: return
        if (current.isBusy) return
        addTo(existing)
    }

    fun dismissConflict() = updatePicker { current ->
        if (current == null || current.isBusy) current else current.copy(conflict = null)
    }

    private fun addTo(group: MusicPlaylistGroup) = write(group.name) { it.addTo(playlists, group) }

    private fun write(name: String, block: suspend (MusicPlaylistAddition) -> MusicPlaylistResult) {
        val target = addition()
        if (target == null) {
            finish()
            notifications.showError(NotificationText.Res(R.string.music_library_notice_add_failed))
            return
        }
        updatePicker { it?.copy(isBusy = true) }
        scope.launch {
            val result = musicGuarded(TAG, "playlist write") { withContext(io) { block(target) } }
            when (result) {
                is MusicPlaylistResult.Done -> {
                    finish()
                    val shown = result.group?.name ?: name
                    notifications.showSuccess(NotificationText.Res(R.string.music_library_notice_added, listOf(shown)))
                }
                is MusicPlaylistResult.NameTaken -> updatePicker {
                    it?.copy(isBusy = false, isNaming = false, conflict = result.existing)
                }
                MusicPlaylistResult.NeedsRommPermission -> {
                    finish()
                    onNeedsPermission()
                }
                MusicPlaylistResult.Failed, null -> {
                    finish()
                    notifications.showError(NotificationText.Res(R.string.music_library_notice_add_failed))
                }
            }
        }
    }

    private fun finish() {
        loadJob?.cancel()
        updatePicker { null }
        onFinished()
    }
}
