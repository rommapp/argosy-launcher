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
private const val RESOLUTION_DELAY_MS = 250L

@Singleton
class SteamIgdbResolver @Inject constructor(
    private val socialService: ArgosSocialService,
    private val gameDao: GameDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var resolutionJob: Job? = null

    fun requestResolutionForUnresolved() {
        if (!socialService.isConnected()) {
            Log.d(TAG, "Social service not connected, skipping IGDB resolution")
            return
        }
        val existing = resolutionJob
        if (existing != null && existing.isActive) {
            Log.d(TAG, "Resolution already in progress, skipping")
            return
        }
        resolutionJob = scope.launch { resolveThrottled() }
    }

    private suspend fun resolveThrottled() {
        val unresolved = gameDao.getUnresolvedSteamGames()
        if (unresolved.isEmpty()) return
        Log.d(TAG, "Requesting IGDB resolution for ${unresolved.size} Steam games (${RESOLUTION_DELAY_MS}ms between each)")
        for (game in unresolved) {
            if (!socialService.isConnected()) {
                Log.w(TAG, "Lost connection during resolution, stopping")
                return
            }
            val steamAppId = game.steamAppId ?: continue
            socialService.sendResolveSteamGame(steamAppId, game.title)
            delay(RESOLUTION_DELAY_MS)
        }
        Log.d(TAG, "IGDB resolution pass complete")
    }

    fun requestResolution(steamAppId: Long) {
        if (!socialService.isConnected()) return
        socialService.sendResolveSteamGame(steamAppId)
    }
}
