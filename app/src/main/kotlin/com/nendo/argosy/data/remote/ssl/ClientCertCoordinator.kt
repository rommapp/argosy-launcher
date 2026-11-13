package com.nendo.argosy.data.remote.ssl

import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
import com.nendo.argosy.data.remote.jellyfin.JellyfinConnectionManager
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClientCertCoordinator"

/**
 * Keeps [ClientCertStore] told which hosts belong to each target, and reconnects a target whenever
 * its certificate changes.
 */
@Singleton
class ClientCertCoordinator @Inject constructor(
    private val store: ClientCertStore,
    private val rommAccounts: RomMAccountRepository,
    private val rommConnection: RomMConnectionManager,
    private val jellyfinPreferences: JellyfinPreferencesRepository,
    private val jellyfinConnection: JellyfinConnectionManager
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)

    fun start() {
        scope.launch {
            combine(rommAccounts.observeActiveAccount(), rommConnection.baseUrlState) { account, current ->
                account?.addressCandidates().orEmpty() + current
            }
                .distinctUntilChanged()
                .collect { store.updateHosts(ClientCertTarget.ROMM, it) }
        }
        scope.launch {
            jellyfinPreferences.preferences
                .map { listOfNotNull(it.serverUrl) }
                .distinctUntilChanged()
                .collect { store.updateHosts(ClientCertTarget.JELLYFIN, it) }
        }
        scope.launch {
            store.changedFor.collect { target ->
                when (target) {
                    ClientCertTarget.ROMM -> {
                        val revision = store.revision
                        if (!rommConnection.initialize(reprobe = true) && store.revision == revision &&
                            !rommConnection.isConnected()
                        ) {
                            rommConnection.initialize(reprobe = true)
                        }
                    }
                    ClientCertTarget.JELLYFIN -> jellyfinConnection.initialize(force = true)
                }
            }
        }
    }
}
