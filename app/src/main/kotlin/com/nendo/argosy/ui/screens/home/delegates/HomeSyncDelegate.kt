package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.domain.model.Changelog
import com.nendo.argosy.domain.model.ChangelogEntry
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
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
    private val syncLibraryUseCase: SyncLibraryUseCase,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager
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

                if (lastSync == null || lastSync.isBefore(oneWeekAgo)) {
                    syncFromRomm(scope, onSyncComplete)
                } else {
                    romMRepository.refreshFavoritesIfNeeded()
                    onFavoritesRefreshed()
                }
            }
        }
    }

    fun syncFromRomm(scope: CoroutineScope, onSyncComplete: () -> Unit) {
        scope.launch {
            when (val result = syncLibraryUseCase(initializeFirst = true)) {
                is SyncLibraryResult.Error -> notificationManager.showError(result.message)
                is SyncLibraryResult.Success -> onSyncComplete()
            }
        }
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
