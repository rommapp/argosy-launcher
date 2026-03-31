package com.nendo.argosy.data.repository

import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamIgdbResolver"
private const val RESOLUTION_DELAY_MS = 1000L
private const val RECONNECT_POLL_MS = 5000L
private const val RECONNECT_TIMEOUT_MS = 120_000L

@Singleton
class SteamIgdbResolver @Inject constructor(
    private val socialService: ArgosSocialService,
    private val gameDao: GameDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolutionJob: Job? = null

    fun requestResolutionForUnresolved(force: Boolean = false) {
        if (!socialService.isConnected()) {
            Log.d(TAG, "Social service not connected, skipping IGDB resolution")
            return
        }
        val existing = resolutionJob
        if (existing != null && existing.isActive) {
            Log.d(TAG, "Resolution already in progress, skipping")
            return
        }
        resolutionJob = scope.launch { resolveThrottled(force) }
    }

    private suspend fun resolveThrottled(force: Boolean = false) {
        val games = if (force) gameDao.getAllSteamGamesForResolve() else gameDao.getUnresolvedSteamGames()
        if (games.isEmpty()) return
        Log.d(TAG, "Requesting IGDB resolution for ${games.size} Steam games (${RESOLUTION_DELAY_MS}ms between each)")
        for (game in games) {
            if (!awaitConnection()) {
                Log.w(TAG, "Connection lost and did not recover, aborting resolution")
                return
            }
            val steamAppId = game.steamAppId ?: continue
            val sent = socialService.sendResolveSteamGame(steamAppId, game.title, game.releaseYear)
            if (!sent) {
                Log.w(TAG, "Failed to send resolve for ${game.title}, waiting for reconnect")
                if (!awaitConnection()) {
                    Log.w(TAG, "Connection lost and did not recover, aborting resolution")
                    return
                }
                socialService.sendResolveSteamGame(steamAppId, game.title, game.releaseYear)
            }
            delay(RESOLUTION_DELAY_MS)
        }
        Log.d(TAG, "IGDB resolution pass complete")
    }

    private suspend fun awaitConnection(): Boolean {
        if (socialService.isConnected()) return true
        Log.d(TAG, "Waiting for social connection to resume...")
        val deadline = System.currentTimeMillis() + RECONNECT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(RECONNECT_POLL_MS)
            if (socialService.isConnected()) {
                Log.d(TAG, "Social connection restored, resuming resolution")
                return true
            }
        }
        return false
    }

    fun requestResolution(steamAppId: Long) {
        if (!socialService.isConnected()) return
        socialService.sendResolveSteamGame(steamAppId)
    }
}
