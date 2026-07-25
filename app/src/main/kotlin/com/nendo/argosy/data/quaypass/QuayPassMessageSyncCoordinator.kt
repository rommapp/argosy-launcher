package com.nendo.argosy.data.quaypass

import android.util.Log
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes the user's greeting to argosy-server, mirroring
 * [QuayPassAvatarSyncCoordinator]. The greeting is a settable profile field like
 * the avatar doodle: on every transition into Connected with a sync outstanding,
 * flushes the stored greeting via [ArgosSocialService.sendQuayPassMessage] (empty
 * string clears the server copy). Server is the single source of truth for
 * cross-surface parity; the pass record keeps its own frozen copy via peer_card.
 */
@Singleton
class QuayPassMessageSyncCoordinator @Inject constructor(
    private val socialService: ArgosSocialService,
    private val preferencesRepository: UserPreferencesRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            combine(
                socialService.connectionState,
                preferencesRepository.userPreferences
            ) { state, prefs ->
                state to prefs.quayPassMessageSyncPending
            }
                .distinctUntilChanged()
                .collect { (state, pending) ->
                    if (state is ArgosSocialService.ConnectionState.Connected && pending) flush()
                }
        }
    }

    private suspend fun flush() {
        val message = preferencesRepository.userPreferences.first().quayPassGreeting.orEmpty()
        val sent = socialService.sendQuayPassMessage(message)
        if (sent) {
            preferencesRepository.setQuayPassMessageSyncPending(false)
            Log.i(TAG, "QuayPass message synced to server")
        } else {
            Log.w(TAG, "Failed to send QuayPass message; will retry on next connect")
        }
    }

    companion object {
        private const val TAG = "QuayPassMessageSync"
    }
}
