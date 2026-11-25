package com.nendo.argosy.data.music.navidrome

import com.nendo.argosy.data.local.dao.MusicListenDao
import com.nendo.argosy.data.local.entity.MusicListenEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.remote.navidrome.NavidromeApiClient
import com.nendo.argosy.data.remote.navidrome.NavidromeCredentials
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NavidromeScrobbler"
private const val FLUSH_BATCH = 50
private val FLUSH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)
private val REACHABLE_FLUSH_GAP_MS = TimeUnit.MINUTES.toMillis(1)

/**
 * Reports Navidrome plays. A counted listen is marked pending and submitted with its original
 * start time; pending listens flush oldest first at start, whenever the server answers another
 * call, and every 15 minutes. Navidrome play counts are never read back.
 */
@Singleton
class NavidromeScrobbler @Inject constructor(
    private val api: NavidromeApiClient,
    private val preferences: NavidromePreferencesRepository,
    private val listenDao: MusicListenDao,
    private val accountReader: MusicAccountReader
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Mutex()
    private var lastReachableFlushAt = 0L

    init {
        scope.launch {
            while (true) {
                flush()
                delay(FLUSH_INTERVAL_MS)
            }
        }
        scope.launch {
            api.reachable.collect {
                val now = System.currentTimeMillis()
                if (now - lastReachableFlushAt >= REACHABLE_FLUSH_GAP_MS) {
                    lastReachableFlushAt = now
                    flush()
                }
            }
        }
    }

    fun nowPlaying(sourceTrackId: String) {
        scope.launch {
            val credentials = credentials() ?: return@launch
            api.scrobble(credentials, sourceTrackId, submission = false, timeMs = null)
                .onFailure { Logger.verbose(TAG) { "Now playing not sent: ${it.message}" } }
        }
    }

    suspend fun submitCounted(ownerKey: Long, listenId: Long) {
        listenDao.markScrobblePending(ownerKey, listenId)
        flush()
    }

    suspend fun flush() {
        val credentials = credentials() ?: return
        val accounts = accountReader.current()
        flushPending(credentials, accounts.ownerKey, accounts.navidromeAccountKey)
    }

    internal suspend fun flushPending(credentials: NavidromeCredentials, ownerKey: Long, accountKey: String): Int =
        flushLock.withLock {
            var sent = 0
            var more = true
            while (more) {
                val batch = listenDao.pendingScrobbles(ownerKey, MusicSourceKind.NAVIDROME.token, accountKey, FLUSH_BATCH)
                val accepted = batch.takeWhile { submit(credentials, it) }.size
                sent += accepted
                more = accepted == FLUSH_BATCH
            }
            sent
        }

    private suspend fun submit(credentials: NavidromeCredentials, listen: MusicListenEntity): Boolean {
        val result = api.scrobble(credentials, listen.sourceTrackId, submission = true, timeMs = listen.startedAt)
        if (result.isFailure) {
            Logger.verbose(TAG) { "Scrobble held for retry: ${result.exceptionOrNull()?.message}" }
            return false
        }
        listenDao.markScrobbleSent(listen.ownerKey, listen.id, System.currentTimeMillis())
        return true
    }

    private suspend fun credentials(): NavidromeCredentials? = preferences.preferences.first().credentials()
}
