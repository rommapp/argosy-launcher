package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.media.MediaDirectoryManager
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.preferences.MediaAudioLanguage
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
 * What a password sign-in needs to reach the connection layer. The delegate never holds the
 * credentials past the request: they travel with the event and the form is cleared behind them.
 */
data class JellyfinPasswordSignInRequest(
    val serverUrl: String,
    val username: String,
    val password: String
) {
    override fun toString(): String =
        "JellyfinPasswordSignInRequest(serverUrl=$serverUrl, username=$username)"
}

/**
 * Owns the Jellyfin settings state and every preference write behind it.
 *
 * Sign-in is a two-part contract: this delegate publishes [quickConnectRequestEvent] or
 * [passwordSignInRequestEvent] when the user asks to sign in, and the Jellyfin connection layer
 * runs the exchange, reporting back through [onQuickConnectStarted] and then [onSignedIn] or
 * [onSignInFailed].
 */
class JellyfinSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val mediaDirectoryManager: MediaDirectoryManager,
    private val mediaRepository: MediaRepository
) {
    private val _state = MutableStateFlow(JellyfinState())
    val state: StateFlow<JellyfinState> = _state.asStateFlow()

    private val _quickConnectRequestEvent = MutableSharedFlow<String>()
    val quickConnectRequestEvent: SharedFlow<String> = _quickConnectRequestEvent.asSharedFlow()

    private val _passwordSignInRequestEvent = MutableSharedFlow<JellyfinPasswordSignInRequest>()
    val passwordSignInRequestEvent: SharedFlow<JellyfinPasswordSignInRequest> =
        _passwordSignInRequestEvent.asSharedFlow()

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

    fun commitServerConfig(scope: CoroutineScope, onFocusReset: () -> Unit, onSaved: () -> Unit) {
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
                    configFocusField = null,
                    signInError = null,
                    passwordFallbackOffered = false
                )
            }
            onFocusReset()
            onSaved()
        }
    }

    /**
     * Sends the user down whichever sign-in path the server actually offers. Quick Connect is the
     * default because a controller has no keyboard; the password form is reached only when the
     * server has reported Quick Connect switched off.
     */
    fun requestSignIn(scope: CoroutineScope, onFocusReset: () -> Unit) {
        if (_state.value.serverUrl.isBlank()) return
        if (_state.value.quickConnectAvailable) requestQuickConnect(scope) else showLoginForm(onFocusReset)
    }

    fun requestQuickConnect(scope: CoroutineScope) {
        val server = _state.value.serverUrl
        if (server.isBlank()) return
        _state.update {
            it.copy(quickConnectRequested = true, quickConnectCode = "", signInError = null)
        }
        scope.launch { _quickConnectRequestEvent.emit(server) }
    }

    fun onQuickConnectStarted(code: String) {
        _state.update { it.copy(quickConnectRequested = true, quickConnectCode = code) }
    }

    fun cancelSignIn() {
        _state.update {
            it.copy(
                quickConnectRequested = false,
                quickConnectCode = "",
                isSigningIn = false,
                signInError = null
            )
        }
    }

    /**
     * Records what the server reported about itself. Availability is only ever narrowed by a live
     * answer: while nothing has been asked, Quick Connect stays on offer so the keyboard-free path
     * is not withheld on the strength of a question that was never put.
     */
    fun onServerCapabilities(connected: Boolean, supportsQuickConnect: Boolean) {
        val available = !connected || supportsQuickConnect
        _state.update {
            it.copy(
                quickConnectAvailable = available,
                passwordFallbackOffered = it.passwordFallbackOffered || !available
            )
        }
    }

    fun showLoginForm(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                showLoginForm = true,
                loginUsername = "",
                loginPassword = "",
                loginFocusField = null,
                quickConnectRequested = false,
                quickConnectCode = "",
                isSigningIn = false
            )
        }
        onFocusReset()
    }

    fun hideLoginForm(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                showLoginForm = false,
                loginUsername = "",
                loginPassword = "",
                loginFocusField = null,
                isSigningIn = false,
                signInError = null,
                passwordFallbackOffered = true
            )
        }
        onFocusReset()
    }

    fun setLoginUsername(username: String) {
        _state.update { it.copy(loginUsername = username, signInError = null) }
    }

    fun setLoginPassword(password: String) {
        _state.update { it.copy(loginPassword = password, signInError = null) }
    }

    fun setLoginFocusField(field: Int?) {
        _state.update { it.copy(loginFocusField = field) }
    }

    fun clearLoginFocusField() {
        _state.update { it.copy(loginFocusField = null) }
    }

    fun submitPasswordSignIn(scope: CoroutineScope) {
        val state = _state.value
        if (state.serverUrl.isBlank()) return
        if (state.loginUsername.isBlank() || state.loginPassword.isBlank()) {
            _state.update { it.copy(signInError = "Enter your username and password") }
            return
        }
        if (state.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, signInError = null) }
        scope.launch {
            _passwordSignInRequestEvent.emit(
                JellyfinPasswordSignInRequest(state.serverUrl, state.loginUsername, state.loginPassword)
            )
        }
    }

    fun onSignedIn(userName: String?) {
        _state.update {
            it.copy(
                isSignedIn = true,
                userName = userName.orEmpty(),
                quickConnectRequested = false,
                quickConnectCode = "",
                showLoginForm = false,
                loginUsername = "",
                loginPassword = "",
                loginFocusField = null,
                isSigningIn = false,
                signInError = null,
                passwordFallbackOffered = false
            )
        }
    }

    /**
     * A failure before any code was issued means the Quick Connect exchange never started, which is
     * what a server with it switched off looks like from here - so the password form opens carrying
     * the reason. A failure after a code was issued is an expiry or a refusal of that one attempt,
     * and the user stays where they are with the reason on screen to try again.
     */
    fun onSignInFailed(reason: String, onFocusReset: () -> Unit) {
        val state = _state.value
        val fallBackToPassword = !state.showLoginForm && state.quickConnectCode.isBlank()
        _state.update {
            it.copy(
                quickConnectRequested = false,
                quickConnectCode = "",
                isSigningIn = false,
                signInError = reason,
                passwordFallbackOffered = true,
                showLoginForm = it.showLoginForm || fallBackToPassword,
                loginUsername = if (fallBackToPassword) "" else it.loginUsername,
                loginPassword = if (fallBackToPassword) "" else it.loginPassword
            )
        }
        if (fallBackToPassword) onFocusReset()
    }

    fun requestSignOut() {
        _state.update { it.copy(showSignOutConfirm = true) }
    }

    fun cancelSignOut() {
        _state.update { it.copy(showSignOutConfirm = false) }
    }

    /**
     * [onSignOut] is the connection layer dropping the session, which clears the stored credentials
     * as part of forgetting the server. Doing it there rather than here keeps one authority over
     * what "signed out" means to the rest of the app.
     */
    fun confirmSignOut(scope: CoroutineScope, onSignOut: suspend () -> Unit) {
        _state.update { it.copy(showSignOutConfirm = false) }
        scope.launch {
            onSignOut()
            _state.update {
                it.copy(
                    isSignedIn = false,
                    userName = "",
                    quickConnectRequested = false,
                    quickConnectCode = "",
                    signInError = null,
                    passwordFallbackOffered = false
                )
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

    fun cycleAudioLanguage(scope: CoroutineScope, direction: Int) {
        val entries = MediaAudioLanguage.entries
        val next = entries[(entries.indexOf(_state.value.audioLanguage) + direction).mod(entries.size)]
        setAudioLanguage(scope, next)
    }

    fun setAudioLanguage(scope: CoroutineScope, language: MediaAudioLanguage) {
        scope.launch {
            preferencesRepository.setMediaAudioLanguage(language)
            _state.update { it.copy(audioLanguage = language) }
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
                mediaRepository.repointDownloads(oldPath, newPath)
            }
            preferencesRepository.setMediaStoragePath(newPath)
            _state.update { it.copy(mediaDirPath = newPath) }
        }
    }
}
