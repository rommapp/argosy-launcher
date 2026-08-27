package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationProgress
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.download.MediaDownloadManager
import com.nendo.argosy.data.media.MediaDirectoryManager
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncProgress
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncResult
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.preferences.MediaAudioLanguage
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.MediaStreamingQuality
import com.nendo.argosy.data.preferences.MediaSubtitleLanguage
import com.nendo.argosy.data.preferences.MediaSubtitleMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.screens.settings.JellyfinState
import com.nendo.argosy.ui.screens.settings.MediaRelocationPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

private const val MEDIA_SYNC_NOTIFICATION_KEY = "jellyfin_library_sync"

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
    private val mediaRepository: MediaRepository,
    private val mediaDownloadManager: MediaDownloadManager,
    private val notificationManager: NotificationManager,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(JellyfinState())
    val state: StateFlow<JellyfinState> = _state.asStateFlow()

    val librarySyncProgress: StateFlow<JellyfinSyncProgress> = mediaRepository.syncProgress

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
            _state.update {
                it.copy(configError = context.getString(R.string.settings_jellyfin_delegate_config_error_empty))
            }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update {
                it.copy(configError = context.getString(R.string.settings_jellyfin_delegate_config_error_scheme))
            }
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
            _state.update {
                it.copy(signInError = context.getString(R.string.settings_jellyfin_delegate_signin_error_empty))
            }
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

    /**
     * Runs the pull the user asked for. It outlives the screen it was started from: the pass is
     * uncancellable and reports through the persistent notification, so walking away from settings
     * neither stops it nor leaves a progress bar that never finishes.
     */
    fun syncLibrary(scope: CoroutineScope) {
        val current = _state.value
        if (current.isSyncingLibrary || !current.isSignedIn) return
        _state.update { it.copy(isSyncingLibrary = true, librarySyncError = null) }
        notificationManager.showPersistent(
            title = NotificationText.Res(R.string.notif_jellyfin_settings_sync_title),
            subtitle = NotificationText.Res(R.string.notif_jellyfin_settings_sync_starting),
            key = MEDIA_SYNC_NOTIFICATION_KEY,
            progress = NotificationProgress(0, 0)
        )
        scope.launch {
            withContext(NonCancellable) {
                val progressJob = launch {
                    mediaRepository.syncProgress.collect { progress ->
                        if (progress.isSyncing && progress.currentLibrary.isNotBlank()) {
                            notificationManager.updatePersistent(
                                key = MEDIA_SYNC_NOTIFICATION_KEY,
                                subtitle = NotificationText.Raw(progress.currentLibrary),
                                progress = NotificationProgress(
                                    progress.librariesDone + 1,
                                    progress.librariesTotal
                                )
                            )
                        }
                    }
                }
                val outcome = mediaRepository.refreshLibraries()
                progressJob.cancel()
                finishLibrarySync(outcome)
            }
        }
    }

    private fun finishLibrarySync(outcome: JellyfinResult<JellyfinSyncResult>) {
        when (outcome) {
            is JellyfinResult.Success -> {
                val failure = outcome.data.errors.firstOrNull()
                notificationManager.completePersistent(
                    key = MEDIA_SYNC_NOTIFICATION_KEY,
                    title = if (failure == null) {
                        NotificationText.Res(R.string.notif_jellyfin_settings_sync_up_to_date)
                    } else {
                        NotificationText.Res(R.string.notif_jellyfin_settings_sync_partial)
                    },
                    subtitle = failure?.let { NotificationText.Raw(it) }
                        ?: NotificationText.Raw(describeSyncCounts(outcome.data)),
                    type = if (failure == null) NotificationType.SUCCESS else NotificationType.ERROR
                )
                _state.update {
                    it.copy(
                        isSyncingLibrary = false,
                        lastLibrarySync = Instant.now(),
                        librarySyncError = failure
                    )
                }
            }
            is JellyfinResult.Error -> {
                notificationManager.completePersistent(
                    key = MEDIA_SYNC_NOTIFICATION_KEY,
                    title = NotificationText.Res(R.string.notif_jellyfin_settings_sync_failed_title),
                    subtitle = NotificationText.Raw(outcome.message),
                    type = NotificationType.ERROR
                )
                _state.update {
                    it.copy(isSyncingLibrary = false, librarySyncError = outcome.message)
                }
            }
        }
    }

    /**
     * The item count is what the pass persisted, not what was new to it, so it is reported as a
     * size rather than as an addition.
     */
    private fun describeSyncCounts(result: JellyfinSyncResult): String {
        val libraries = context.resources.getQuantityString(
            R.plurals.notif_jellyfin_settings_sync_libraries_count,
            result.librariesSynced,
            result.librariesSynced
        )
        val titles = context.resources.getQuantityString(
            R.plurals.notif_jellyfin_settings_sync_titles_count,
            result.itemsAdded,
            result.itemsAdded
        )
        return context.getString(R.string.notif_jellyfin_settings_sync_counts_summary, libraries, titles)
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

    fun cycleStreamingQuality(scope: CoroutineScope, direction: Int) {
        val entries = MediaStreamingQuality.entries
        val next = entries[(entries.indexOf(_state.value.streamingQuality) + direction).mod(entries.size)]
        setStreamingQuality(scope, next)
    }

    fun setStreamingQuality(scope: CoroutineScope, quality: MediaStreamingQuality) {
        scope.launch {
            preferencesRepository.setMediaStreamingQuality(quality)
            _state.update { it.copy(streamingQuality = quality) }
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

    fun setConfirmPlayerExit(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setMediaConfirmPlayerExit(enabled)
            _state.update { it.copy(confirmPlayerExit = enabled) }
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
        if (mediaDownloadManager.hasBlockingDownloadState()) {
            notificationManager.showError(
                NotificationText.Res(R.string.notif_jellyfin_settings_relocate_blocked_media_download)
            )
            return
        }
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
                mediaDirectoryManager.underRelocationLock {
                    mediaDirectoryManager.relocate(File(oldPath), File(newPath))
                    mediaRepository.repointDownloads(oldPath, newPath)
                }
            }
            preferencesRepository.setMediaStoragePath(newPath)
            _state.update { it.copy(mediaDirPath = newPath) }
        }
    }
}
