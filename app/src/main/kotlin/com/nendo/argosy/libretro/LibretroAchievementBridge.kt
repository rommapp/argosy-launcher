package com.nendo.argosy.libretro

import android.content.Context
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.domain.usecase.achievement.VerifyRAGameIdUseCase
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.libretro.ui.AchievementUnlock
import com.nendo.argosy.libretro.ui.RAConnectionInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.core.event.AchievementUpdateBus
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages the RetroAchievements session lifecycle for a libretro session:
 * construction, hardcore-mode wiring, and the activity-side surface needed by
 * overlays (current unlock, connection notification) and netplay rules.
 * [sessionMode] is the only place a hardcore claim comes from; it stays PENDING
 * until the server has answered the start-session request.
 */
class LibretroAchievementBridge(
    private val gameDao: GameDao,
    private val overlayWriter: com.nendo.argosy.data.repository.GameUserOverlayWriter,
    private val achievementDao: AchievementDao,
    private val raRepository: RetroAchievementsRepository,
    private val verifyRAGameIdUseCase: VerifyRAGameIdUseCase,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val ambientLedManager: AmbientLedManager,
    private val socialRepository: SocialRepository,
    private val scope: CoroutineScope,
    private val context: Context
) {
    private var session by mutableStateOf<RetroAchievementsSessionManager?>(null)
    private var modeJob: Job? = null
    private val _sessionMode = MutableStateFlow(RASessionMode.PENDING)

    val sessionMode: StateFlow<RASessionMode> = _sessionMode.asStateFlow()

    val sessionManager: RetroAchievementsSessionManager?
        get() = session

    val currentUnlock: AchievementUnlock?
        get() = session?.currentAchievementUnlock

    val connectionInfo: RAConnectionInfo?
        get() = session?.raConnectionInfo

    fun start(
        gameId: Long,
        romPath: String,
        hardcoreMode: Boolean,
        retroView: GLRetroView
    ) {
        val created = RetroAchievementsSessionManager(
            gameId = gameId,
            romPath = romPath,
            requestedHardcore = hardcoreMode,
            gameDao = gameDao,
            overlayWriter = overlayWriter,
            achievementDao = achievementDao,
            raRepository = raRepository,
            verifyRAGameIdUseCase = verifyRAGameIdUseCase,
            achievementUpdateBus = achievementUpdateBus,
            ambientLedManager = ambientLedManager,
            socialRepository = socialRepository,
            scope = scope,
            context = context
        )
        session = created
        modeJob?.cancel()
        modeJob = scope.launch {
            created.sessionMode.collect { _sessionMode.value = it }
        }
        created.initialize(retroView)
    }

    fun showNextUnlock() {
        session?.showNextUnlock()
    }

    fun dismissConnectionInfo() {
        session?.dismissConnectionInfo()
    }

    fun destroy() {
        modeJob?.cancel()
        modeJob = null
        session?.destroy()
        session = null
    }
}
