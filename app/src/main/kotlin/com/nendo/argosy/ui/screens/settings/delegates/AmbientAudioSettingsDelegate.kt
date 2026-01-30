package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.screens.settings.AmbientAudioState
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val ambientAudioManager: AmbientAudioManager
) {
    private val _state = MutableStateFlow(AmbientAudioState())
    val state: StateFlow<AmbientAudioState> = _state.asStateFlow()

    private val _openAudioFilePickerEvent = MutableSharedFlow<Unit>()
    val openAudioFilePickerEvent: SharedFlow<Unit> = _openAudioFilePickerEvent.asSharedFlow()

    private val _openAudioFileBrowserEvent = MutableSharedFlow<Unit>()
    val openAudioFileBrowserEvent: SharedFlow<Unit> = _openAudioFileBrowserEvent.asSharedFlow()

    fun updateState(newState: AmbientAudioState) {
        _state.value = newState
    }

    fun setEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientAudioEnabled(enabled)
            ambientAudioManager.setEnabled(enabled)
            _state.update { it.copy(enabled = enabled) }

            if (enabled && _state.value.audioUri != null) {
                ambientAudioManager.fadeIn()
            } else if (!enabled) {
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

    fun openFilePicker(scope: CoroutineScope) {
        scope.launch {
            _openAudioFilePickerEvent.emit(Unit)
        }
    }

    fun openFileBrowser(scope: CoroutineScope) {
        scope.launch {
            _openAudioFileBrowserEvent.emit(Unit)
        }
    }

    fun setAudioUri(scope: CoroutineScope, uri: String?) {
        scope.launch {
            preferencesRepository.setAmbientAudioUri(uri)
            ambientAudioManager.setAudioUri(uri)

            val fileName = uri?.let { extractFileName(it) }
            _state.update { it.copy(audioUri = uri, audioFileName = fileName) }

            if (_state.value.enabled && uri != null) {
                ambientAudioManager.fadeIn()
            }
        }
    }

    fun clearAudioFile(scope: CoroutineScope) {
        setAudioUri(scope, null)
    }

    fun setAudioFilePath(scope: CoroutineScope, path: String?) {
        scope.launch {
            preferencesRepository.setAmbientAudioUri(path)
            ambientAudioManager.setAudioUri(path)

            val fileName = path?.substringAfterLast("/")
            _state.update { it.copy(audioUri = path, audioFileName = fileName) }

            if (_state.value.enabled && path != null) {
                ambientAudioManager.fadeIn()
            }
        }
    }

    private fun extractFileName(uriString: String): String? {
        if (uriString.startsWith("/")) {
            return uriString.substringAfterLast("/")
        }
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            uriString.substringAfterLast("/").substringBefore("?")
        }
    }
}
