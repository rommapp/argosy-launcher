package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.media.MediaDirectoryManager
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.MediaStreamingBitrate
import com.nendo.argosy.data.preferences.MediaSubtitleLanguage
import com.nendo.argosy.data.preferences.MediaSubtitleMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.screens.settings.JellyfinState
import com.nendo.argosy.ui.screens.settings.MediaRelocationPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Owns the Jellyfin settings state and every preference write behind it.
 *
 * Sign-in is a two-part contract: this delegate publishes [quickConnectRequestEvent] when the user
 * asks to sign in, and the Jellyfin connection layer runs the Quick Connect exchange, reporting
 * back through [onQuickConnectStarted] and then [onSignedIn] or [onSignInFailed].
 */
class JellyfinSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val mediaDirectoryManager: MediaDirectoryManager
) {
    private val _state = MutableStateFlow(JellyfinState())
    val state: StateFlow<JellyfinState> = _state.asStateFlow()

    private val _quickConnectRequestEvent = MutableSharedFlow<String>()
    val quickConnectRequestEvent: SharedFlow<String> = _quickConnectRequestEvent.asSharedFlow()

    private val _openMediaLocationPickerEvent = MutableSharedFlow<Unit>()
    val openMediaLocationPickerEvent: SharedFlow<Unit> = _openMediaLocationPickerEvent.asSharedFlow()

    fun updateState(newState: JellyfinState) {
        _state.value = newState
    }

    fun startServerConfig(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                configuring = true,
                configUrl = it.serverUrl,
                configError = null,
                configFocusField = null
            )
        }
        onFocusReset()
    }

    fun setConfigUrl(url: String) {
        _state.update { it.copy(configUrl = url, configError = null) }
    }

    fun setConfigFocusField(field: Int?) {
        _state.update { it.copy(configFocusField = field) }
    }

    fun clearConfigFocusField() {
        _state.update { it.copy(configFocusField = null) }
    }

    fun cancelServerConfig(onFocusReset: () -> Unit) {
        _state.update { it.copy(configuring = false, configError = null, configFocusField = null) }
        onFocusReset()
    }

    fun commitServerConfig(scope: CoroutineScope, onFocusReset: () -> Unit) {
        val url = _state.value.configUrl.trim().trimEnd('/')
        if (url.isBlank()) {
            _state.update { it.copy(configError = "Enter a server address") }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update { it.copy(configError = "Address must start with http:// or https://") }
            return
        }
        scope.launch {
            preferencesRepository.setJellyfinServerUrl(url)
            _state.update {
                it.copy(
                    serverUrl = url,
                    configuring = false,
                    configUrl = url,
                    configError = null,
                    configFocusField = null
                )
            }
            onFocusReset()
        }
    }

    fun requestQuickConnect(scope: CoroutineScope) {
        val server = _state.value.serverUrl
        if (server.isBlank()) return
        scope.launch { _quickConnectRequestEvent.emit(server) }
    }

    fun onQuickConnectStarted() {
        _state.update { it.copy(quickConnectRequested = true) }
    }

    fun onSignedIn(userName: String?) {
        _state.update {
            it.copy(
                isSignedIn = true,
                userName = userName.orEmpty(),
                quickConnectRequested = false
            )
        }
    }

    fun onSignInFailed() {
        _state.update { it.copy(quickConnectRequested = false) }
    }

    fun requestSignOut() {
        _state.update { it.copy(showSignOutConfirm = true) }
    }

    fun cancelSignOut() {
        _state.update { it.copy(showSignOutConfirm = false) }
    }

    fun confirmSignOut(scope: CoroutineScope) {
        _state.update { it.copy(showSignOutConfirm = false) }
        scope.launch {
            preferencesRepository.clearJellyfinCredentials()
            _state.update {
                it.copy(isSignedIn = false, userName = "", quickConnectRequested = false)
            }
        }
    }

    fun cycleDownloadQuality(scope: CoroutineScope, direction: Int) {
        val entries = MediaDownloadQuality.entries
        val next = entries[(entries.indexOf(_state.value.downloadQuality) + direction).mod(entries.size)]
        setDownloadQuality(scope, next)
    }

    fun setDownloadQuality(scope: CoroutineScope, quality: MediaDownloadQuality) {
        scope.launch {
            preferencesRepository.setMediaDownloadQuality(quality)
            _state.update { it.copy(downloadQuality = quality) }
        }
    }

    fun cycleMaxStreamingBitrate(scope: CoroutineScope, direction: Int) {
        val entries = MediaStreamingBitrate.entries
        val next = entries[(entries.indexOf(_state.value.maxStreamingBitrate) + direction).mod(entries.size)]
        setMaxStreamingBitrate(scope, next)
    }

    fun setMaxStreamingBitrate(scope: CoroutineScope, bitrate: MediaStreamingBitrate) {
        scope.launch {
            preferencesRepository.setMediaMaxStreamingBitrate(bitrate)
            _state.update { it.copy(maxStreamingBitrate = bitrate) }
        }
    }

    fun cycleSubtitleMode(scope: CoroutineScope, direction: Int) {
        val entries = MediaSubtitleMode.entries
        val next = entries[(entries.indexOf(_state.value.subtitleMode) + direction).mod(entries.size)]
        setSubtitleMode(scope, next)
    }

    fun setSubtitleMode(scope: CoroutineScope, mode: MediaSubtitleMode) {
        scope.launch {
            preferencesRepository.setMediaSubtitleMode(mode)
            _state.update { it.copy(subtitleMode = mode) }
        }
    }

    fun cycleSubtitleLanguage(scope: CoroutineScope, direction: Int) {
        val entries = MediaSubtitleLanguage.entries
        val next = entries[(entries.indexOf(_state.value.subtitleLanguage) + direction).mod(entries.size)]
        setSubtitleLanguage(scope, next)
    }

    fun setSubtitleLanguage(scope: CoroutineScope, language: MediaSubtitleLanguage) {
        scope.launch {
            preferencesRepository.setMediaSubtitleLanguage(language)
            _state.update { it.copy(subtitleLanguage = language) }
        }
    }

    fun setBurnInImageSubtitles(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setMediaBurnInImageSubtitles(enabled)
            _state.update { it.copy(burnInImageSubtitles = enabled) }
        }
    }

    fun setSharePresence(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setShareMediaPresence(enabled)
            _state.update { it.copy(sharePresence = enabled) }
        }
    }

    fun openMediaLocationPicker(scope: CoroutineScope) {
        scope.launch { _openMediaLocationPickerEvent.emit(Unit) }
    }

    fun refreshMediaDirPath(scope: CoroutineScope) {
        scope.launch {
            val path = mediaDirectoryManager.resolveMediaDir().absolutePath
            _state.update { it.copy(mediaDirPath = path) }
        }
    }

    fun onMediaLocationSelected(scope: CoroutineScope, newPath: String) {
        scope.launch {
            val oldPath = mediaDirectoryManager.resolveMediaDir().absolutePath
            if (oldPath == newPath) return@launch
            val fileCount = mediaDirectoryManager.countFiles()
            if (fileCount > 0) {
                _state.update {
                    it.copy(pendingMediaRelocation = MediaRelocationPrompt(oldPath, newPath, fileCount))
                }
            } else {
                applyMediaLocation(scope, oldPath, newPath, moveFiles = false)
            }
        }
    }

    fun confirmMediaRelocation(scope: CoroutineScope) {
        val pending = _state.value.pendingMediaRelocation ?: return
        _state.update { it.copy(pendingMediaRelocation = null) }
        applyMediaLocation(scope, pending.oldPath, pending.newPath, moveFiles = true)
    }

    fun skipMediaRelocation(scope: CoroutineScope) {
        val pending = _state.value.pendingMediaRelocation ?: return
        _state.update { it.copy(pendingMediaRelocation = null) }
        applyMediaLocation(scope, pending.oldPath, pending.newPath, moveFiles = false)
    }

    fun cancelMediaRelocation() {
        _state.update { it.copy(pendingMediaRelocation = null) }
    }

    private fun applyMediaLocation(
        scope: CoroutineScope,
        oldPath: String,
        newPath: String,
        moveFiles: Boolean
    ) {
        scope.launch {
            if (moveFiles) {
                mediaDirectoryManager.relocate(File(oldPath), File(newPath))
            }
            preferencesRepository.setMediaStoragePath(newPath)
            _state.update { it.copy(mediaDirPath = newPath) }
        }
    }
}
