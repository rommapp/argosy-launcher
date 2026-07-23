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
 * Watches the social WS connection state and the local pending-avatar-sync flag.
 * On every transition into Connected, flushes the stored sparse doodle base64
 * to argosy-server via [ArgosSocialService.sendQuayPassAvatar] if a sync is
 * outstanding (empty string clears the server copy). The BLE wire format keeps
 * the raster encoding; only this server payload is sparse. Server is the single
 * source of truth for cross-device sync; launcher pushes on save and on next
 * online state change. (Pull on first sign-in to a new device is a follow-up;
 * v1 only pushes.)
 */
@Singleton
class QuayPassAvatarSyncCoordinator @Inject constructor(
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
                state to prefs.quayPassAvatarSyncPending
            }
                .distinctUntilChanged()
                .collect { (state, pending) ->
                    if (state is ArgosSocialService.ConnectionState.Connected && pending) flush()
                }
        }
    }

    private suspend fun flush() {
        val prefs = preferencesRepository.userPreferences.first()
        val sparse = if (prefs.socialAvatarUseDoodle) prefs.socialAvatarDoodle.orEmpty() else ""
        val sent = socialService.sendQuayPassAvatar(sparse)
        if (sent) {
            preferencesRepository.setQuayPassAvatarSyncPending(false)
            Log.i(TAG, "QuayPass avatar synced to server")
        } else {
            Log.w(TAG, "Failed to send QuayPass avatar; will retry on next connect")
        }
    }

    companion object {
        private const val TAG = "QuayPassAvatarSync"
    }
}
