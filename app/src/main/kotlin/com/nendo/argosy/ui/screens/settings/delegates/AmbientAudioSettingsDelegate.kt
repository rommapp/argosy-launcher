package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.local.entity.BgmPlaylistEntity
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.usecase.music.RelocateMusicLibraryUseCase
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.BgmPlaylistCoordinator
import com.nendo.argosy.ui.screens.settings.AmbientAudioState
import com.nendo.argosy.ui.screens.settings.MusicRelocationPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class AmbientAudioSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val ambientAudioManager: AmbientAudioManager,
    private val playlistCoordinator: BgmPlaylistCoordinator,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val relocateMusicLibrary: RelocateMusicLibraryUseCase
) {
    private val _state = MutableStateFlow(AmbientAudioState())
    val state: StateFlow<AmbientAudioState> = _state.asStateFlow()

    private val _openPlaylistManagerEvent = MutableSharedFlow<Unit>()
    val openPlaylistManagerEvent: SharedFlow<Unit> = _openPlaylistManagerEvent.asSharedFlow()

    private val _openMusicBrowserEvent = MutableSharedFlow<Unit>()
    val openMusicBrowserEvent: SharedFlow<Unit> = _openMusicBrowserEvent.asSharedFlow()

    private val _openAddMusicBrowserEvent = MutableSharedFlow<Unit>()
    val openAddMusicBrowserEvent: SharedFlow<Unit> = _openAddMusicBrowserEvent.asSharedFlow()

    private val _openMusicLocationPickerEvent = MutableSharedFlow<Unit>()
    val openMusicLocationPickerEvent: SharedFlow<Unit> = _openMusicLocationPickerEvent.asSharedFlow()

    fun initFlowCollection(scope: CoroutineScope) {
        scope.launch {
            ambientAudioManager.currentTrackName.collect { trackName ->
                _state.update { it.copy(currentTrackName = trackName) }
            }
        }
        scope.launch {
            playlistCoordinator.entries.collect { entries ->
                val trackCount = entries.count { it.entryType != BgmPlaylistEntity.TYPE_FOLDER && it.enabled }
                _state.update { it.copy(playlistEntryCount = trackCount) }
            }
        }
    }

    fun updateState(newState: AmbientAudioState) {
        _state.value = newState
    }

    fun setEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientAudioEnabled(enabled)
            ambientAudioManager.setEnabled(enabled)
            _state.update { it.copy(enabled = enabled) }

            if (enabled) {
                ambientAudioManager.fadeIn()
            } else {
                ambientAudioManager.fadeOut()
            }
        }
    }

    fun setVolume(scope: CoroutineScope, volume: Int) {
        scope.launch {
            preferencesRepository.setAmbientAudioVolume(volume)
            ambientAudioManager.setVolume(volume)
            _state.update { it.copy(volume = volume) }
        }
    }

    fun adjustVolume(scope: CoroutineScope, delta: Int) {
        adjustInList(_state.value.volume, VolumeLevels.AMBIENT_AUDIO, delta)?.let { setVolume(scope, it) }
    }

    fun setShuffle(scope: CoroutineScope, shuffle: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientAudioShuffle(shuffle)
            ambientAudioManager.setShuffle(shuffle)
            _state.update { it.copy(shuffle = shuffle) }
        }
    }

    fun setGameDetailTheme(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setGameDetailThemeEnabled(enabled)
            _state.update { it.copy(gameDetailThemeEnabled = enabled) }
        }
    }

    fun addPlaylistEntry(scope: CoroutineScope, path: String) {
        scope.launch {
            playlistCoordinator.addLocalPath(path)
        }
    }

    fun openPlaylistManager(scope: CoroutineScope) {
        scope.launch {
            _openPlaylistManagerEvent.emit(Unit)
        }
    }

    fun openAddMusicBrowser(scope: CoroutineScope) {
        scope.launch {
            _openAddMusicBrowserEvent.emit(Unit)
        }
    }

    fun openMusicBrowser(scope: CoroutineScope) {
        scope.launch {
            _openMusicBrowserEvent.emit(Unit)
        }
    }

    fun openMusicLocationPicker(scope: CoroutineScope) {
        scope.launch {
            _openMusicLocationPickerEvent.emit(Unit)
        }
    }

    fun refreshMusicDirPath(scope: CoroutineScope) {
        scope.launch {
            val path = musicDirectoryManager.resolveMusicDir().absolutePath
            _state.update { it.copy(musicDirPath = path) }
        }
    }

    fun onMusicLocationSelected(scope: CoroutineScope, newPath: String) {
        scope.launch {
            val oldPath = musicDirectoryManager.resolveMusicDir().absolutePath
            if (oldPath == newPath) return@launch
            val fileCount = musicDirectoryManager.countFiles()
            if (fileCount > 0) {
                _state.update {
                    it.copy(pendingMusicRelocation = MusicRelocationPrompt(oldPath, newPath, fileCount))
                }
            } else {
                applyMusicLocation(scope, oldPath, newPath, moveFiles = false)
            }
        }
    }

    fun confirmMusicRelocation(scope: CoroutineScope) {
        val pending = _state.value.pendingMusicRelocation ?: return
        _state.update { it.copy(pendingMusicRelocation = null) }
        applyMusicLocation(scope, pending.oldPath, pending.newPath, moveFiles = true)
    }

    fun skipMusicRelocation(scope: CoroutineScope) {
        val pending = _state.value.pendingMusicRelocation ?: return
        _state.update { it.copy(pendingMusicRelocation = null) }
        applyMusicLocation(scope, pending.oldPath, pending.newPath, moveFiles = false)
    }

    fun cancelMusicRelocation() {
        _state.update { it.copy(pendingMusicRelocation = null) }
    }

    private fun applyMusicLocation(
        scope: CoroutineScope,
        oldPath: String,
        newPath: String,
        moveFiles: Boolean
    ) {
        scope.launch {
            relocateMusicLibrary(oldPath, newPath, moveFiles)
            playlistCoordinator.refresh()
            _state.update { it.copy(musicDirPath = newPath) }
        }
    }
}
