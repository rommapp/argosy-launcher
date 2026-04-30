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
import com.nendo.argosy.domain.usecase.achievement.VerifyRAGameIdUseCase
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.libretro.ui.AchievementUnlock
import com.nendo.argosy.libretro.ui.RAConnectionInfo
import com.nendo.argosy.core.event.AchievementUpdateBus
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
    private val verifyRAGameIdUseCase: VerifyRAGameIdUseCase,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val ambientLedManager: AmbientLedManager,
    private val socialRepository: SocialRepository,
    private val scope: CoroutineScope,
    private val context: Context
) {
    private val achievementInfo = mutableMapOf<Long, AchievementPatchInfo>()
    private val achievementUnlockQueue = mutableListOf<AchievementUnlock>()

    var gameRaId: Long? = null
        private set
    private var gameIgdbId: Long? = null
    private var gameTitle: String = ""
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
    private var cachedAchievementDefs: Array<com.swordfish.libretrodroid.AchievementDef>? = null
    private var cachedConsoleId: Int? = null
    private var cachedRetroView: GLRetroView? = null
    private var pausedForNetplay: Boolean = false

    private data class AchievementPatchInfo(
        val title: String,
        val description: String?,
        val points: Int,
        val badgeName: String?
    )

    fun initialize(retroView: GLRetroView) {
        cachedRetroView = retroView
        scope.launch {
            val isLoggedIn = raRepository.isLoggedIn()
            Log.d(TAG, "RA login check: isLoggedIn=$isLoggedIn")
            if (!isLoggedIn) {
                Log.d(TAG, "Not logged in to RA, skipping session")
                return@launch
            }

            val game = gameDao.getById(gameId) ?: return@launch
            gameIgdbId = game.igdbId
            gameTitle = game.title

            val verifiedId = verifyRAGameIdUseCase(gameId, forceRehash = true)
            gameRaId = verifiedId ?: game.effectiveRaId
            Log.d(TAG, "Game loaded: title=${game.title}, resolvedRaId=$gameRaId, raId=${game.raId}, verifiedRaId=${game.verifiedRaId}")

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
                    val validIds = validAchievements.map { a -> a.id }.toSet()
                    earnedAchievements = preUnlocked.count { it in validIds }

                    // Persist pre-unlocked achievements to local DB
                    val now = System.currentTimeMillis()
                    for (achId in preUnlocked) {
                        if (achId in validIds) {
                            if (hardcoreMode) {
                                achievementDao.markUnlockedHardcore(gameId, achId, now)
                            } else {
                                achievementDao.markUnlocked(gameId, achId, now)
                            }
                        }
                    }
                    gameDao.updateAchievementCount(gameId, totalAchievements, earnedAchievements)

                    raConnectionInfo = RAConnectionInfo(
                        isHardcore = hardcoreMode,
                        earnedCount = earnedAchievements,
                        totalCount = totalAchievements
                    )

                    val toWatch = validAchievements
                        .filter { it.id !in preUnlocked }

                    val consoleId = patchData.consoleId
                    if (toWatch.isNotEmpty() && consoleId != null) {
                        val achievementDefs = toWatch.map { patch ->
                            com.swordfish.libretrodroid.AchievementDef(patch.id, patch.memAddr)
                        }.toTypedArray()

                        Log.d(TAG, "Sending ${achievementDefs.size} achievements to native for console $consoleId")
                        com.swordfish.libretrodroid.LibretroDroid.initAchievements(achievementDefs, consoleId)
                        cachedAchievementDefs = achievementDefs
                        cachedConsoleId = consoleId

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

            val awardConfirmed = when (result) {
                is RAAwardResult.Success -> {
                    Log.i(TAG, "Achievement $achievementId awarded to RA successfully")
                    true
                }
                is RAAwardResult.AlreadyAwarded -> {
                    Log.d(TAG, "Achievement $achievementId already awarded on RA")
                    false
                }
                is RAAwardResult.Queued -> {
                    Log.d(TAG, "Achievement $achievementId queued for later submission")
                    com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(context)
                    false
                }
                is RAAwardResult.Error -> {
                    Log.e(TAG, "Failed to award achievement to RA: ${result.message}")
                    false
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

            if (awardConfirmed) {
                val sent = socialRepository.emitAchievementUnlocked(
                    igdbId = gameIgdbId,
                    raGameId = gameRaId,
                    gameTitle = gameTitle,
                    achievementRaId = achievementId,
                    achievementName = info?.title ?: "Achievement",
                    achievementDescription = info?.description,
                    points = info?.points ?: 0,
                    badgeName = info?.badgeName,
                    isHardcore = hardcoreMode,
                    earnedCount = earnedCount,
                    totalCount = totalCount,
                    unlockedAt = now
                )
                if (sent) {
                    achievementDao.markSocialShared(gameId, achievementId, System.currentTimeMillis())
                }
                if (earnedCount == totalCount && totalCount > 0) {
                    socialRepository.emitPerfectGame(
                        igdbId = gameIgdbId,
                        gameTitle = gameTitle,
                        isHardcore = hardcoreMode,
                        earnedCount = earnedCount,
                        totalCount = totalCount
                    )
                }
            }
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

    fun pause() {
        if (pausedForNetplay) return
        pausedForNetplay = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        com.swordfish.libretrodroid.LibretroDroid.clearAchievements()
        cachedRetroView?.achievementUnlockListener = null
        Log.d(TAG, "RA paused for netplay")
    }

    fun resume() {
        if (!pausedForNetplay) return
        pausedForNetplay = false
        val defs = cachedAchievementDefs
        val consoleId = cachedConsoleId
        val view = cachedRetroView
        if (defs != null && consoleId != null && view != null && gameRaId != null) {
            com.swordfish.libretrodroid.LibretroDroid.initAchievements(defs, consoleId)
            view.achievementUnlockListener = { achievementId ->
                onAchievementUnlocked(achievementId)
            }
            if (raSessionActive) {
                startHeartbeatLoop()
            }
            Log.d(TAG, "RA resumed after netplay")
        } else {
            Log.d(TAG, "RA resume is a no-op (nothing cached)")
        }
    }

    fun destroy() {
        heartbeatJob?.cancel()
        raSessionActive = false
        pausedForNetplay = false
        cachedAchievementDefs = null
        cachedConsoleId = null
        cachedRetroView = null
        com.swordfish.libretrodroid.LibretroDroid.clearAchievements()
    }

    companion object {
        private const val TAG = "RASessionManager"
    }
}
