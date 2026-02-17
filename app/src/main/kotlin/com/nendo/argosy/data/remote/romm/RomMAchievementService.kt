package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMAchievementService"

@Singleton
class RomMAchievementService @Inject constructor(
    private val connectionManager: RomMConnectionManager
) {
    private val api: RomMApi? get() = connectionManager.getApi()

    private var raProgressionRefreshedThisSession = false
    private var cachedRAProgression: Map<Long, List<RomMEarnedAchievement>> = emptyMap()

    fun onAppResumed() {
        raProgressionRefreshedThisSession = false
        cachedRAProgression = emptyMap()
    }

    fun getEarnedBadgeIds(raGameId: Long): Set<String> {
        return cachedRAProgression[raGameId]?.map { it.id }?.toSet() ?: emptySet()
    }

    fun getEarnedAchievements(raGameId: Long): List<RomMEarnedAchievement> {
        return cachedRAProgression[raGameId] ?: emptyList()
    }

    suspend fun refreshRAProgressionOnStartup() {
        val currentApi = api ?: return
        try {
            val userResponse = currentApi.getCurrentUser()
            if (!userResponse.isSuccessful) return

            val user = userResponse.body() ?: return
            if (user.raUsername.isNullOrBlank()) return

            var progression = user.raProgression?.results ?: emptyList()

            val refreshResponse = currentApi.refreshRAProgression(user.id)
            if (refreshResponse.isSuccessful) {
                raProgressionRefreshedThisSession = true
                val refreshedUserResponse = currentApi.getCurrentUser()
                if (refreshedUserResponse.isSuccessful) {
                    progression = refreshedUserResponse.body()?.raProgression?.results ?: emptyList()
                }
            } else {
                raProgressionRefreshedThisSession = true
            }

            cachedRAProgression = progression
                .filter { it.romRaId != null }
                .associate { gameProgress ->
                    gameProgress.romRaId!! to gameProgress.earnedAchievements
                }
        } catch (_: Exception) {
        }
    }

    suspend fun refreshRAProgressionIfNeeded(): RomMResult<Unit> {
        if (raProgressionRefreshedThisSession) {
            return RomMResult.Success(Unit)
        }

        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            val userResponse = currentApi.getCurrentUser()
            if (!userResponse.isSuccessful) {
                return RomMResult.Error("Failed to get user", userResponse.code())
            }
            val user = userResponse.body() ?: return RomMResult.Error("No user data")
            if (user.raUsername.isNullOrBlank()) {
                return RomMResult.Error("No RetroAchievements username configured")
            }

            val response = currentApi.refreshRAProgression(user.id)
            if (response.isSuccessful) {
                raProgressionRefreshedThisSession = true
                RomMResult.Success(Unit)
            } else {
                RomMResult.Error("Failed to refresh RA progression", response.code())
            }
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to refresh RA progression")
        }
    }
}
