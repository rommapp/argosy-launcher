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
 * Greeting push plus profile pull. A local greeting edit is pushed via
 * set_quaypass_message on the next Connected transition (empty clears the server
 * copy). On every connect the profile is pulled once from GET /api/me and each
 * field is hydrated only when it has no pending local edit, so a fresh device
 * gets its greeting and avatar doodle while a local edit still wins. The inbound
 * quaypass_message_updated push (handled in SocialRepository) covers greeting
 * edits made on another of the owner's devices while this one is already online;
 * avatar push stays in [QuayPassAvatarSyncCoordinator].
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
                if (state is ArgosSocialService.ConnectionState.Connected) pull()
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
        val prefs = preferencesRepository.userPreferences.first()
        val token = prefs.socialSessionToken ?: return
        val me = authManager.fetchMe(token) ?: return
        if (!prefs.quayPassMessageSyncPending) {
            preferencesRepository.setQuayPassGreetingFromServer(me.quayPassMessage.orEmpty())
        }
        if (!prefs.quayPassAvatarSyncPending) {
            preferencesRepository.setSocialAvatarFromServer(me.quayPassAvatar)
        }
    }

    companion object {
        private const val TAG = "QuayPassMessageSync"
    }
}
