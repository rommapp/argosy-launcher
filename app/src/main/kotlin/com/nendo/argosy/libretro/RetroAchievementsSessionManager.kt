package com.nendo.argosy.libretro

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.RAAwardResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.libretro.ui.AchievementUnlock
import com.nendo.argosy.libretro.ui.RAConnectionInfo
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RetroAchievementsSessionManager(
    private val gameId: Long,
    private val romPath: String,
    private val hardcoreMode: Boolean,
    private val gameDao: GameDao,
    private val achievementDao: AchievementDao,
    private val raRepository: RetroAchievementsRepository,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val ambientLedManager: AmbientLedManager,
    private val scope: CoroutineScope,
    private val context: Context
) {
    private val achievementInfo = mutableMapOf<Long, AchievementPatchInfo>()
    private val achievementUnlockQueue = mutableListOf<AchievementUnlock>()

    var gameRaId: Long? = null
        private set
    var raSessionActive: Boolean = false
        private set
    var totalAchievements by mutableIntStateOf(0)
        private set
    var earnedAchievements by mutableIntStateOf(0)
        private set
    var currentAchievementUnlock by mutableStateOf<AchievementUnlock?>(null)
        private set
    var raConnectionInfo by mutableStateOf<RAConnectionInfo?>(null)
        private set

    private var heartbeatJob: Job? = null

    private data class AchievementPatchInfo(
        val title: String,
        val description: String?,
        val points: Int,
        val badgeName: String?
    )

    fun initialize(retroView: GLRetroView) {
        scope.launch {
            val isLoggedIn = raRepository.isLoggedIn()
            Log.d(TAG, "RA login check: isLoggedIn=$isLoggedIn")
            if (!isLoggedIn) {
                Log.d(TAG, "Not logged in to RA, skipping session")
                return@launch
            }

            val game = gameDao.getById(gameId) ?: return@launch
            gameRaId = game.raId
            Log.d(TAG, "Game loaded: title=${game.title}, raId=$gameRaId")

            if (gameRaId == null) {
                Log.d(TAG, "Game has no RA ID, skipping RA session")
                return@launch
            }

            Log.d(TAG, "Starting RA session for raId=$gameRaId, hardcore=$hardcoreMode")
            val sessionResult = raRepository.startSession(gameRaId!!, hardcoreMode)
            if (sessionResult.success) {
                raSessionActive = true
                val preUnlocked = sessionResult.unlockedAchievements
                Log.d(TAG, "RA session started for game $gameRaId (hardcore=$hardcoreMode), pre-unlocked=${preUnlocked.size}: $preUnlocked")

                val patchData = raRepository.getGamePatchData(gameRaId!!)
                if (patchData != null) {
                    val raConsoleId = patchData.consoleId ?: 0
                    if (game.romHash == null && raConsoleId > 0) {
                        try {
                            val hash = com.swordfish.libretrodroid.LibretroDroid.computeRomHash(romPath, raConsoleId)
                            if (hash != null) {
                                Log.d(TAG, "Computed ROM hash: $hash")
                                gameDao.updateRomHash(gameId, hash)
                            } else {
                                Log.w(TAG, "Failed to compute ROM hash for $romPath (console $raConsoleId)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error computing ROM hash", e)
                        }
                    } else if (game.romHash != null) {
                        Log.d(TAG, "Using cached ROM hash: ${game.romHash}")
                    }

                    val validAchievements = patchData.achievements?.filter { ach ->
                        !ach.title.contains("Unknown Emulator", ignoreCase = true) &&
                        !ach.title.contains("Emulator Warning", ignoreCase = true) &&
                        ach.memAddr.isNotBlank()
                    } ?: emptyList()

                    validAchievements.forEach { patch ->
                        achievementInfo[patch.id] = AchievementPatchInfo(
                            title = patch.title,
                            description = patch.description,
                            points = patch.points,
                            badgeName = patch.badgeName
                        )
                    }

                    totalAchievements = validAchievements.size
                    earnedAchievements = preUnlocked.count { it in validAchievements.map { a -> a.id }.toSet() }

                    raConnectionInfo = RAConnectionInfo(
                        isHardcore = hardcoreMode,
                        earnedCount = earnedAchievements,
                        totalCount = totalAchievements
                    )

                    val toWatch = validAchievements
                        .filter { it.id !in preUnlocked }

                    if (toWatch.isNotEmpty()) {
                        val achievementDefs = toWatch.map { patch ->
                            com.swordfish.libretrodroid.AchievementDef(patch.id, patch.memAddr)
                        }.toTypedArray()

                        Log.d(TAG, "Sending ${achievementDefs.size} achievements to native for console $raConsoleId")
                        com.swordfish.libretrodroid.LibretroDroid.initAchievements(achievementDefs, raConsoleId)

                        retroView.achievementUnlockListener = { achievementId ->
                            onAchievementUnlocked(achievementId)
                        }
                    } else {
                        Log.d(TAG, "No achievements to watch (all pre-unlocked)")
                    }
                }

                startHeartbeatLoop()
            } else {
                Log.w(TAG, "Failed to start RA session")
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob = scope.launch {
            while (isActive && raSessionActive) {
                delay(240_000L)
                val raId = gameRaId ?: break
                raRepository.sendHeartbeat(raId, null)
                Log.d(TAG, "RA heartbeat sent for game $raId")
            }
        }
    }

    private fun onAchievementUnlocked(achievementId: Long) {
        val info = achievementInfo[achievementId]

        Log.i(TAG, "=== ACHIEVEMENT UNLOCKED ===")
        Log.i(TAG, "  ID: $achievementId")
        Log.i(TAG, "  Title: ${info?.title}")
        Log.i(TAG, "  Points: ${info?.points}")
        Log.i(TAG, "  Hardcore: $hardcoreMode")

        earnedAchievements++

        scope.launch {
            Log.d(TAG, "Submitting achievement $achievementId to RA server...")
            val result = raRepository.awardAchievement(
                gameId = gameId,
                achievementRaId = achievementId,
                forHardcoreMode = hardcoreMode
            )

            when (result) {
                is RAAwardResult.Success -> {
                    Log.i(TAG, "Achievement $achievementId awarded to RA successfully")
                }
                is RAAwardResult.AlreadyAwarded -> {
                    Log.d(TAG, "Achievement $achievementId already awarded on RA")
                }
                is RAAwardResult.Queued -> {
                    Log.d(TAG, "Achievement $achievementId queued for later submission")
                    com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(context)
                }
                is RAAwardResult.Error -> {
                    Log.e(TAG, "Failed to award achievement to RA: ${result.message}")
                }
            }

            val now = System.currentTimeMillis()
            if (hardcoreMode) {
                achievementDao.markUnlockedHardcore(gameId, achievementId, now)
                Log.d(TAG, "Marked achievement $achievementId as hardcore unlocked in local DB")
            } else {
                achievementDao.markUnlocked(gameId, achievementId, now)
                Log.d(TAG, "Marked achievement $achievementId as unlocked in local DB")
            }

            gameDao.incrementEarnedAchievementCount(gameId)
            Log.d(TAG, "Incremented earned achievement count for game $gameId")

            val totalCount = achievementDao.countByGameId(gameId)
            val earnedCount = achievementDao.countUnlockedByGameId(gameId)
            achievementUpdateBus.emit(
                AchievementUpdateBus.AchievementUpdate(
                    gameId = gameId,
                    totalCount = totalCount,
                    earnedCount = earnedCount
                )
            )
            Log.d(TAG, "Emitted achievement update: $earnedCount/$totalCount earned for game $gameId")
        }

        val badgeUrl = info?.badgeName?.let {
            "https://media.retroachievements.org/Badge/$it.png"
        }

        val unlock = AchievementUnlock(
            id = achievementId,
            title = info?.title ?: "Achievement",
            description = info?.description,
            points = info?.points ?: 0,
            badgeUrl = badgeUrl,
            isHardcore = hardcoreMode
        )

        achievementUnlockQueue.add(unlock)
        ambientLedManager.flashAchievement(hardcoreMode)
        if (currentAchievementUnlock == null) {
            showNextUnlock()
        }
    }

    fun showNextUnlock() {
        currentAchievementUnlock = if (achievementUnlockQueue.isNotEmpty()) {
            achievementUnlockQueue.removeAt(0)
        } else {
            null
        }
    }

    fun dismissConnectionInfo() {
        raConnectionInfo = null
    }

    fun destroy() {
        heartbeatJob?.cancel()
        raSessionActive = false
        com.swordfish.libretrodroid.LibretroDroid.clearAchievements()
    }

    companion object {
        private const val TAG = "RASessionManager"
    }
}
