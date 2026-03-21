package com.nendo.argosy.data.repository

import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.social.ArgosSocialService
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamIgdbResolver"

@Singleton
class SteamIgdbResolver @Inject constructor(
    private val socialService: ArgosSocialService,
    private val gameDao: GameDao
) {
    suspend fun requestResolutionForUnresolved() {
        if (!socialService.isConnected()) return
        val unresolved = gameDao.getUnresolvedSteamGames()
        if (unresolved.isEmpty()) return
        Log.d(TAG, "Requesting IGDB resolution for ${unresolved.size} Steam games")
        for (game in unresolved) {
            val steamAppId = game.steamAppId ?: continue
            socialService.sendResolveSteamGame(steamAppId)
        }
    }

    fun requestResolution(steamAppId: Long) {
        if (!socialService.isConnected()) return
        socialService.sendResolveSteamGame(steamAppId)
    }
}
