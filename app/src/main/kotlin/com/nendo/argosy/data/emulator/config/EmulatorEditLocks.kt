package com.nendo.argosy.data.emulator.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One lock per emulator package, held by every write into that emulator's config and awaited by
 * every launch of it, so an edit and a launch of the same emulator never overlap.
 */
@Singleton
class EmulatorEditLocks @Inject constructor() {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(packageName: String): Mutex = locks.computeIfAbsent(packageName) { Mutex() }

    suspend fun <T> withEdit(packageName: String, block: suspend () -> T): T =
        lockFor(packageName).withLock { block() }

    fun isEditing(packageName: String): Boolean = locks[packageName]?.isLocked == true

    /**
     * Waits until no edit of [packageName] is in flight. False when one was still running after
     * [timeoutMs].
     */
    suspend fun awaitEdits(packageName: String, timeoutMs: Long = LAUNCH_WAIT_MS): Boolean =
        withTimeoutOrNull(timeoutMs) { lockFor(packageName).withLock { } } != null

    companion object {
        const val LAUNCH_WAIT_MS = 30_000L
    }
}
