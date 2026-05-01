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

/**
 * Manages the RetroAchievements session lifecycle for a libretro session:
 * construction, hardcore-mode wiring, and the activity-side surface needed by
 * overlays (current unlock, connection notification) and netplay rules.
 */
class LibretroAchievementBridge(
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
    private var session by mutableStateOf<RetroAchievementsSessionManager?>(null)

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
            hardcoreMode = hardcoreMode,
            gameDao = gameDao,
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
        created.initialize(retroView)
    }

    fun showNextUnlock() {
        session?.showNextUnlock()
    }

    fun dismissConnectionInfo() {
        session?.dismissConnectionInfo()
    }

    fun destroy() {
        session?.destroy()
        session = null
    }
}
