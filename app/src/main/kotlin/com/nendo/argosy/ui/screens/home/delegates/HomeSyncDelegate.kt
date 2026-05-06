package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.steam.SteamAuthManager
import com.nendo.argosy.domain.model.Changelog
import com.nendo.argosy.domain.model.ChangelogEntry
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.data.sync.PlatformSyncQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val AUTO_SYNC_DAYS = 7L

data class SyncState(
    val isRommConfigured: Boolean = false,
    val changelogEntry: ChangelogEntry? = null
)

class HomeSyncDelegate @Inject constructor(
    private val romMRepository: RomMRepository,
    private val platformSyncQueue: PlatformSyncQueue,
    private val preferencesRepository: UserPreferencesRepository,
    private val steamAuthManager: SteamAuthManager
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun initializeRomM(scope: CoroutineScope, onSyncComplete: () -> Unit, onFavoritesRefreshed: suspend () -> Unit) {
        scope.launch {
            romMRepository.initialize()

            val isConfigured = romMRepository.isConnected()
            _state.value = _state.value.copy(isRommConfigured = isConfigured)

            if (isConfigured) {
                val prefs = preferencesRepository.preferences.first()
                val lastSync = prefs.lastRommSync
                val oneWeekAgo = Instant.now().minus(AUTO_SYNC_DAYS, ChronoUnit.DAYS)
                val isStale = lastSync == null || lastSync.isBefore(oneWeekAgo)

                if (isStale && !platformSyncQueue.isLibraryBusyNow()) {
                    syncFromRomm(scope, onSyncComplete)
                } else {
                    romMRepository.refreshFavoritesIfNeeded()
                    onFavoritesRefreshed()
                }
            }
        }
    }

    fun syncFromRomm(@Suppress("UNUSED_PARAMETER") scope: CoroutineScope, onSyncComplete: () -> Unit) {
        platformSyncQueue.enqueueLibrary(initializeFirst = true, onComplete = onSyncComplete)
    }

    fun refreshFavoritesIfConnected(scope: CoroutineScope, onFavoritesRefreshed: suspend () -> Unit) {
        if (romMRepository.isConnected()) {
            scope.launch {
                romMRepository.refreshFavoritesIfNeeded()
                onFavoritesRefreshed()
            }
        }
    }

    suspend fun checkForChangelog() {
        val prefs = preferencesRepository.preferences.first()
        val lastSeenVersion = prefs.lastSeenVersion
        val currentVersion = BuildConfig.VERSION_NAME

        if (lastSeenVersion == null) {
            preferencesRepository.setLastSeenVersion(currentVersion)
            return
        }

        if (lastSeenVersion != currentVersion) {
            val entry = Changelog.getEntry(currentVersion)
            if (entry != null) {
                if (entry.requiresActiveSteamAccount && steamAuthManager.getActiveAccount() == null) {
                    return
                }
                // Mark seen at show-time so the modal is one-shot — re-resumes, re-launches,
                // and the user backgrounding the app mid-modal don't queue it up again.
                preferencesRepository.setLastSeenVersion(currentVersion)
                _state.value = _state.value.copy(changelogEntry = entry)
            } else {
                preferencesRepository.setLastSeenVersion(currentVersion)
            }
        }
    }

    fun dismissChangelog(scope: CoroutineScope) {
        scope.launch {
            preferencesRepository.setLastSeenVersion(BuildConfig.VERSION_NAME)
            _state.value = _state.value.copy(changelogEntry = null)
        }
    }

    fun handleChangelogAction(scope: CoroutineScope, action: RequiredAction): RequiredAction {
        scope.launch {
            preferencesRepository.setLastSeenVersion(BuildConfig.VERSION_NAME)
            _state.value = _state.value.copy(changelogEntry = null)
        }
        return action
    }
}
