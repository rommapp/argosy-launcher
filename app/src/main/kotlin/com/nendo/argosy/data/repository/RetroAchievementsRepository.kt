package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.local.dao.PendingAchievementDao
import com.nendo.argosy.data.local.entity.PendingAchievementEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAAchievementPatch
import com.nendo.argosy.data.remote.ra.RAApi
import com.nendo.argosy.data.remote.ra.RACredentials
import com.nendo.argosy.data.remote.ra.RAPatchData
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RetroAchievementsRepository"

sealed class RALoginResult {
    data class Success(val username: String) : RALoginResult()
    data class Error(val message: String) : RALoginResult()
}

sealed class RAAwardResult {
    data object Success : RAAwardResult()
    data object AlreadyAwarded : RAAwardResult()
    data class Error(val message: String) : RAAwardResult()
    data object Queued : RAAwardResult()
}

@Singleton
class RetroAchievementsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingAchievementDao: PendingAchievementDao,
    private val prefsRepository: UserPreferencesRepository
) {
    private val api: RAApi by lazy { createApi() }

    private fun createApi(): RAApi {
        val moshi = Moshi.Builder().build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Argosy/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.RELEASE}) rcheevos/12.2.1")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(RAApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RAApi::class.java)
    }

    suspend fun isLoggedIn(): Boolean {
        val prefs = prefsRepository.userPreferences.first()
        return !prefs.raUsername.isNullOrBlank() && !prefs.raToken.isNullOrBlank()
    }

    suspend fun getCredentials(): RACredentials? {
        val prefs = prefsRepository.userPreferences.first()
        val username = prefs.raUsername ?: return null
        val token = prefs.raToken ?: return null
        return RACredentials(username, token)
    }

    suspend fun login(username: String, password: String): RALoginResult {
        Logger.debug(TAG, "Logging in to RetroAchievements")
        return try {
            val response = api.login(username = username, password = password)

            if (!response.isSuccessful) {
                Logger.error(TAG, "Login failed: HTTP ${response.code()}")
                return RALoginResult.Error("HTTP ${response.code()}")
            }

            val body = response.body()
            if (body == null || !body.success) {
                val error = body?.error ?: "Unknown error"
                Logger.error(TAG, "Login failed: $error")
                return RALoginResult.Error(error)
            }

            val token = body.token
            if (token.isNullOrBlank()) {
                Logger.error(TAG, "Login succeeded but no token received")
                return RALoginResult.Error("No token received")
            }

            prefsRepository.setRACredentials(username, token)
            Logger.info(TAG, "Login successful")
            RALoginResult.Success(username)
        } catch (e: Exception) {
            Logger.error(TAG, "Login exception: ${e.message}")
            RALoginResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun logout() {
        Logger.info(TAG, "Logging out")
        prefsRepository.clearRACredentials()
    }

    suspend fun awardAchievement(
        gameId: Long,
        achievementRaId: Long,
        forHardcoreMode: Boolean
    ): RAAwardResult {
        val credentials = getCredentials()
        if (credentials == null) {
            Logger.error(TAG, "Cannot award achievement - not logged in")
            return RAAwardResult.Error("Not logged in")
        }

        val validation = generateValidation(achievementRaId, credentials.username, forHardcoreMode)
        val hardcoreInt = if (forHardcoreMode) 1 else 0

        return try {
            val response = api.awardAchievement(
                username = credentials.username,
                token = credentials.token,
                achievementId = achievementRaId,
                hardcore = hardcoreInt,
                validation = validation
            )

            if (!response.isSuccessful) {
                if (forHardcoreMode) {
                    Logger.error(TAG, "Hardcore achievement award failed: HTTP ${response.code()}")
                    return RAAwardResult.Error("HTTP ${response.code()}")
                } else {
                    queueAchievement(gameId, achievementRaId, forHardcoreMode)
                    return RAAwardResult.Queued
                }
            }

            val body = response.body()
            if (body == null) {
                if (forHardcoreMode) {
                    return RAAwardResult.Error("Empty response")
                } else {
                    queueAchievement(gameId, achievementRaId, forHardcoreMode)
                    return RAAwardResult.Queued
                }
            }

            if (!body.warning.isNullOrBlank()) {
                Logger.warn(TAG, "Award response warning: ${body.warning}")
            }

            if (!body.success) {
                val error = body.error ?: "Unknown error"
                if (error.contains("already has", ignoreCase = true)) {
                    Logger.debug(TAG, "Achievement $achievementRaId already awarded")
                    return RAAwardResult.AlreadyAwarded
                }
                if (forHardcoreMode) {
                    Logger.error(TAG, "Hardcore achievement award failed: $error")
                    return RAAwardResult.Error(error)
                } else {
                    queueAchievement(gameId, achievementRaId, forHardcoreMode)
                    return RAAwardResult.Queued
                }
            }

            Logger.info(TAG, "Achievement $achievementRaId awarded (hardcore=$forHardcoreMode)")
            RAAwardResult.Success
        } catch (e: Exception) {
            Logger.error(TAG, "Award exception: ${e.message}")
            if (forHardcoreMode) {
                RAAwardResult.Error(e.message ?: "Network error")
            } else {
                queueAchievement(gameId, achievementRaId, forHardcoreMode)
                RAAwardResult.Queued
            }
        }
    }

    private suspend fun queueAchievement(gameId: Long, achievementRaId: Long, forHardcoreMode: Boolean) {
        val entity = PendingAchievementEntity(
            gameId = gameId,
            achievementRaId = achievementRaId,
            forHardcoreMode = forHardcoreMode,
            earnedAt = Instant.now()
        )
        pendingAchievementDao.insert(entity)
        Logger.info(TAG, "Achievement $achievementRaId queued for later submission")
    }

    suspend fun submitPendingAchievements(): Int {
        val credentials = getCredentials() ?: return 0
        val pending = pendingAchievementDao.getRetryable()
        if (pending.isEmpty()) return 0

        Logger.info(TAG, "Submitting ${pending.size} pending achievements")
        var successCount = 0

        for (entity in pending) {
            val validation = generateValidation(entity.achievementRaId, credentials.username, entity.forHardcoreMode)
            val hardcoreInt = if (entity.forHardcoreMode) 1 else 0

            try {
                val response = api.awardAchievement(
                    username = credentials.username,
                    token = credentials.token,
                    achievementId = entity.achievementRaId,
                    hardcore = hardcoreInt,
                    validation = validation
                )

                val body = response.body()
                val success = response.isSuccessful && body?.success == true
                val alreadyHas = body?.error?.contains("already has", ignoreCase = true) == true

                if (success || alreadyHas) {
                    pendingAchievementDao.delete(entity.id)
                    successCount++
                    Logger.debug(TAG, "Pending achievement ${entity.achievementRaId} submitted")
                } else {
                    val error = body?.error ?: "HTTP ${response.code()}"
                    pendingAchievementDao.incrementRetry(entity.id, error)
                    Logger.warn(TAG, "Pending achievement ${entity.achievementRaId} retry: $error")
                }
            } catch (e: Exception) {
                pendingAchievementDao.incrementRetry(entity.id, e.message ?: "Network error")
                Logger.error(TAG, "Pending achievement ${entity.achievementRaId} exception: ${e.message}")
            }
        }

        Logger.info(TAG, "Submitted $successCount/${pending.size} pending achievements")
        return successCount
    }

    data class RASessionResult(
        val success: Boolean,
        val unlockedAchievements: Set<Long> = emptySet()
    )

    suspend fun startSession(gameRaId: Long, hardcore: Boolean = false): RASessionResult {
        val credentials = getCredentials()
        if (credentials == null) {
            Logger.warn(TAG, "Cannot start RA session - not logged in to RetroAchievements")
            return RASessionResult(false)
        }
        Logger.debug(TAG, "Starting RA session for game $gameRaId")

        return try {
            val response = api.startSession(
                username = credentials.username,
                token = credentials.token,
                gameId = gameRaId,
                hardcore = if (hardcore) 1 else 0
            )

            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Logger.info(TAG, "Session started for game $gameRaId (hardcore=$hardcore)")
                if (!body.warning.isNullOrBlank()) {
                    Logger.warn(TAG, "RA session warning: ${body.warning}")
                }
                val unlocked = mutableSetOf<Long>()
                if (hardcore) {
                    // Hardcore mode: only count hardcore unlocks
                    body.hardcoreUnlocks?.mapTo(unlocked) { it.id }
                } else {
                    // Casual mode: count all unlocks (hardcore unlocks count too)
                    body.hardcoreUnlocks?.mapTo(unlocked) { it.id }
                    body.unlocks?.mapTo(unlocked) { it.id }
                }
                Logger.debug(TAG, "Pre-unlocked achievements: ${unlocked.size}")
                RASessionResult(true, unlocked)
            } else {
                Logger.error(TAG, "Failed to start session: ${body?.error}")
                RASessionResult(false)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Session start exception: ${e.message}")
            RASessionResult(false)
        }
    }

    suspend fun sendHeartbeat(gameRaId: Long, richPresence: String? = null): Boolean {
        val credentials = getCredentials() ?: return false

        return try {
            val response = api.ping(
                username = credentials.username,
                token = credentials.token,
                gameId = gameRaId,
                richPresence = richPresence
            )

            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Logger.error(TAG, "Heartbeat exception: ${e.message}")
            false
        }
    }

    suspend fun getGamePatchData(gameRaId: Long): RAPatchData? {
        val credentials = getCredentials() ?: return null

        return try {
            val response = api.getGameInfo(
                username = credentials.username,
                token = credentials.token,
                gameId = gameRaId
            )

            if (response.isSuccessful) {
                response.body()?.patchData
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Get game info exception: ${e.message}")
            null
        }
    }

    fun observePendingCount(): Flow<Int> = pendingAchievementDao.observeCount()

    suspend fun getPendingCount(): Int = pendingAchievementDao.getCount()

    data class RAGameAchievements(
        val achievements: List<RAAchievementPatch>,
        val unlockedIds: Set<Long>,
        val totalCount: Int,
        val earnedCount: Int
    )

    suspend fun getGameAchievementsWithProgress(gameRaId: Long): RAGameAchievements? {
        val credentials = getCredentials() ?: return null

        // Try Web API first (uses build-time API key)
        val apiKey = BuildConfig.RA_API_KEY
        if (apiKey.isBlank()) {
            Logger.debug(TAG, "No RA API key configured, using Connect API")
            return fallbackToConnectApi(gameRaId, credentials)
        }

        return try {
            val response = api.getGameInfoAndUserProgress(
                gameId = gameRaId,
                username = credentials.username,
                apiKey = apiKey
            )

            if (response.isSuccessful) {
                val body = response.body() ?: return fallbackToConnectApi(gameRaId, credentials)

                val achievements = body.achievements?.values?.toList() ?: emptyList()
                val unlockedIds = achievements
                    .filter { it.dateEarned != null || it.dateEarnedHardcore != null }
                    .map { it.id }
                    .toSet()

                // Convert Web API achievements to patch format for compatibility
                val patchAchievements = achievements.map { ach ->
                    RAAchievementPatch(
                        id = ach.id,
                        memAddr = "",  // Not needed for display
                        title = ach.title,
                        description = ach.description,
                        points = ach.points,
                        badgeName = ach.badgeName,
                        type = ach.type
                    )
                }

                RAGameAchievements(
                    achievements = patchAchievements,
                    unlockedIds = unlockedIds,
                    totalCount = body.numAchievements ?: achievements.size,
                    earnedCount = body.numAwardedToUser ?: unlockedIds.size
                )
            } else {
                Logger.warn(TAG, "Web API failed (${response.code()}), falling back to Connect API")
                fallbackToConnectApi(gameRaId, credentials)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Web API exception: ${e.message}, falling back to Connect API")
            fallbackToConnectApi(gameRaId, credentials)
        }
    }

    private suspend fun fallbackToConnectApi(gameRaId: Long, credentials: RACredentials): RAGameAchievements? {
        val patchData = getGamePatchData(gameRaId) ?: return null
        val rawAchievements = patchData.achievements ?: return null

        // Filter out warning messages that RA returns as pseudo-achievements
        val achievements = rawAchievements.filter { ach ->
            !ach.title.contains("Unknown Emulator", ignoreCase = true) &&
            !ach.title.contains("Emulator Warning", ignoreCase = true) &&
            ach.memAddr.isNotBlank()
        }

        if (achievements.size != rawAchievements.size) {
            Logger.debug(TAG, "Filtered out ${rawAchievements.size - achievements.size} warning pseudo-achievements")
        }

        // Connect API for unlocks (may show emulator warning)
        val unlocked = try {
            val response = api.startSession(
                username = credentials.username,
                token = credentials.token,
                gameId = gameRaId
            )
            if (response.isSuccessful) {
                val body = response.body()
                val ids = mutableSetOf<Long>()
                body?.hardcoreUnlocks?.mapTo(ids) { it.id }
                body?.unlocks?.mapTo(ids) { it.id }
                ids
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            emptySet()
        }

        // Only count unlocks for valid (non-filtered) achievements
        val validAchievementIds = achievements.map { it.id }.toSet()
        val validUnlocks = unlocked.filter { it in validAchievementIds }

        return RAGameAchievements(
            achievements = achievements,
            unlockedIds = validUnlocks.toSet(),
            totalCount = achievements.size,
            earnedCount = validUnlocks.size
        )
    }

    private fun generateValidation(achievementId: Long, username: String, hardcore: Boolean): String {
        val hardcoreFlag = if (hardcore) "1" else "0"
        val input = "$achievementId$username$hardcoreFlag"
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
