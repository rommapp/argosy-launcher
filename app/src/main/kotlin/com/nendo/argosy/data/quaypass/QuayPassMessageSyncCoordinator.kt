package com.nendo.argosy.data.quaypass

import android.util.Log
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.SocialAuthManager
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
 * Two-way greeting sync, mirroring [QuayPassAvatarSyncCoordinator]. The greeting
 * is a settable profile field: a local edit is pushed via set_quaypass_message on
 * the next Connected transition (empty clears the server copy); on connect with no
 * pending edit the current greeting is pulled from GET /api/me so a fresh device
 * hydrates. A local edit wins over the server value. The inbound
 * quaypass_message_updated push (handled in SocialRepository) covers edits made on
 * another of the owner's devices while this one is already online.
 */
@Singleton
class QuayPassMessageSyncCoordinator @Inject constructor(
    private val socialService: ArgosSocialService,
    private val authManager: SocialAuthManager,
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
        scope.launch {
            socialService.connectionState.collect { state ->
                if (state !is ArgosSocialService.ConnectionState.Connected) return@collect
                if (!preferencesRepository.userPreferences.first().quayPassMessageSyncPending) pull()
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

    private suspend fun pull() {
        val token = preferencesRepository.userPreferences.first().socialSessionToken ?: return
        val me = authManager.fetchMe(token) ?: return
        preferencesRepository.setQuayPassGreetingFromServer(me.quayPassMessage.orEmpty())
    }

    companion object {
        private const val TAG = "QuayPassMessageSync"
    }
}
