package com.nendo.argosy.data.quaypass

import android.util.Log
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the social WS connection state and the offline-queued QuayPass
 * friend requests. On every transition into Connected with a non-empty queue,
 * de-dups the queued account ids against the current friends list (accepted
 * and pending) and sends the remainder; ids that fail to send stay queued for
 * the next connect. Mirrors [QuayPassAvatarSyncCoordinator].
 */
@Singleton
class QuayPassFriendRequestQueueCoordinator @Inject constructor(
    private val socialService: ArgosSocialService,
    private val socialRepository: SocialRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            combine(
                socialService.connectionState,
                syncPreferencesRepository.quayPassPendingFriendRequests()
            ) { state, queued ->
                state to queued
            }
                .distinctUntilChanged()
                .collect { (state, queued) ->
                    if (state is ArgosSocialService.ConnectionState.Connected && queued.isNotEmpty()) {
                        flush(queued)
                    }
                }
        }
    }

    private suspend fun flush(queued: Set<String>) {
        val knownFriendIds = socialRepository.friends.value.mapTo(mutableSetOf()) { it.id }
        val toSend = queued - knownFriendIds
        val unsent = toSend.filterTo(mutableSetOf()) { !socialService.sendFriendRequest(it) }
        syncPreferencesRepository.setQuayPassPendingFriendRequests(unsent)
        Log.i(
            TAG,
            "Flushed ${toSend.size - unsent.size} queued friend requests " +
                "(deduped=${queued.size - toSend.size}, retained=${unsent.size})"
        )
    }

    companion object {
        private const val TAG = "QuayPassFriendReqQueue"
    }
}
