package com.nendo.argosy.data.sync

import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

/** Boot-time barrier: orphan-session recovery re-captures the active slot's live save before the
 * sync coordinator is allowed to drain dirty caches, so a stale mid-game snapshot can never be
 * uploaded ahead of the freshest on-disk bytes. Completed once per process by PlaySessionTracker. */
@Singleton
class SaveRecoveryGate @Inject constructor() {
    private val recovered = CompletableDeferred<Unit>()

    fun markComplete() {
        recovered.complete(Unit)
    }

    suspend fun await() {
        recovered.await()
    }
}
