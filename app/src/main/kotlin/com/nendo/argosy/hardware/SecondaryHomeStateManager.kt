package com.nendo.argosy.hardware

import android.content.Context
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.DisplayRoleResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecondaryHomeStateManager(
    private val context: Context,
    private val gameDao: GameDao,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val gameFileDao: GameFileDao,
    private val displayAffinityHelper: DisplayAffinityHelper
) {

    lateinit var sessionStateStore: SessionStateStore
        private set

    data class InitialState(
        val useDualScreenMode: Boolean,
        val isShowcaseRole: Boolean,
        val isArgosyForeground: Boolean,
        val isGameActive: Boolean,
        val currentChannelName: String?,
        val isSaveDirty: Boolean,
        val homeApps: List<String>,
        val primaryColor: Int?,
        val isHardcore: Boolean,
        val activeGameId: Long,
        val savedSection: Int,
        val savedSelected: Int,
        val restoredScreen: CompanionScreen?,
        val restoredDetailViewModel: DualGameDetailViewModel?,
        val restoredDetailGameId: Long
    )

    data class InputSwapState(
        val swapAB: Boolean,
        val swapXY: Boolean,
        val swapStartSelect: Boolean,
        val dualScreenInputFocus: String,
        val abIconsSwapped: Boolean,
        val xyIconsSwapped: Boolean,
        val startSelectSwapped: Boolean
    )

    fun loadInitialState(
        viewModel: SecondaryHomeViewModel,
        dualHomeViewModel: DualHomeViewModel
    ): InitialState {
        sessionStateStore = SessionStateStore(context)

        val useDualScreenMode = displayAffinityHelper.hasSecondaryDisplay
        val resolver = DisplayRoleResolver(displayAffinityHelper, sessionStateStore)
        val isShowcaseRole = resolver.isSwapped

        val isArgosyForeground = sessionStateStore.isArgosyForeground()
        val isGameActive = sessionStateStore.hasActiveSession()
        val currentChannelName = sessionStateStore.getChannelName()
        val isSaveDirty = sessionStateStore.isSaveDirty()
        val homeApps = sessionStateStore.getHomeApps().toList()
        val primaryColor = sessionStateStore.getPrimaryColor()
        val isHardcore = sessionStateStore.isHardcore()

        if (homeApps.isNotEmpty()) {
            viewModel.setHomeApps(homeApps)
        }

        val activeGameId = sessionStateStore.getGameId()

        val savedSection = sessionStateStore.getCarouselSectionIndex()
        val savedSelected = sessionStateStore.getCarouselSelectedIndex()
        if (savedSection > 0 || savedSelected > 0) {
            dualHomeViewModel.restorePosition(savedSection, savedSelected)
        }

        val savedScreen = sessionStateStore.getCompanionScreen()
        val savedDetailGameId = sessionStateStore.getDetailGameId()
        var restoredScreen: CompanionScreen? = null
        var restoredDetailViewModel: DualGameDetailViewModel? = null
        var restoredDetailGameId = -1L

        if (savedScreen == "GAME_DETAIL" && savedDetailGameId > 0 && !isGameActive) {
            val affinityHelper = DisplayAffinityHelper(context)
            val vm = DualGameDetailViewModel(
                gameDao = gameDao,
                platformRepository = platformRepository,
                collectionRepository = collectionRepository,
                emulatorConfigDao = emulatorConfigDao,
                gameFileDao = gameFileDao,
                displayAffinityHelper = affinityHelper,
                context = context
            )
            vm.loadGame(savedDetailGameId)
            restoredDetailViewModel = vm
            restoredScreen = CompanionScreen.GAME_DETAIL
            restoredDetailGameId = savedDetailGameId
        }

        return InitialState(
            useDualScreenMode = useDualScreenMode,
            isShowcaseRole = isShowcaseRole,
            isGameActive = isGameActive,
            isArgosyForeground = isArgosyForeground,
            currentChannelName = currentChannelName,
            isSaveDirty = isSaveDirty,
            homeApps = homeApps,
            primaryColor = primaryColor,
            isHardcore = isHardcore,
            activeGameId = activeGameId,
            savedSection = savedSection,
            savedSelected = savedSelected,
            restoredScreen = restoredScreen,
            restoredDetailViewModel = restoredDetailViewModel,
            restoredDetailGameId = restoredDetailGameId
        )
    }

    fun loadInputSwapPreferences(): InputSwapState {
        val swapAB = sessionStateStore.getSwapAB()
        val swapXY = sessionStateStore.getSwapXY()
        val swapStartSelect = sessionStateStore.getSwapStartSelect()
        val dualScreenInputFocus = sessionStateStore.getDualScreenInputFocus()

        val isNintendoLayout = ControllerDetector.detectFromActiveGamepad().layout == DetectedLayout.NINTENDO
        val abIconsSwapped = isNintendoLayout xor swapAB
        val xyIconsSwapped = isNintendoLayout xor swapXY

        return InputSwapState(
            swapAB = swapAB,
            swapXY = swapXY,
            swapStartSelect = swapStartSelect,
            dualScreenInputFocus = dualScreenInputFocus,
            abIconsSwapped = abIconsSwapped,
            xyIconsSwapped = xyIconsSwapped,
            startSelectSwapped = swapStartSelect
        )
    }

    suspend fun loadCompanionGameData(gameId: Long): CompanionInGameState {
        return withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@withContext CompanionInGameState()
            val platform = platformRepository.getById(game.platformId)
            val startTime = sessionStateStore.getSessionStartTimeMillis()
            CompanionInGameState(
                gameId = gameId,
                title = game.title,
                coverPath = game.coverPath,
                platformName = platform?.getDisplayName() ?: game.platformSlug,
                developer = game.developer,
                releaseYear = game.releaseYear,
                playTimeMinutes = game.playTimeMinutes,
                playCount = game.playCount,
                achievementCount = game.achievementCount,
                earnedAchievementCount = game.earnedAchievementCount,
                sessionStartTimeMillis = startTime,
                channelName = sessionStateStore.getChannelName(),
                isHardcore = sessionStateStore.isHardcore(),
                isDirty = sessionStateStore.isSaveDirty(),
                isLoaded = true
            )
        }
    }

    fun createGameDetailViewModel(): DualGameDetailViewModel {
        val affinityHelper = DisplayAffinityHelper(context)
        return DualGameDetailViewModel(
            gameDao = gameDao,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            emulatorConfigDao = emulatorConfigDao,
            gameFileDao = gameFileDao,
            displayAffinityHelper = affinityHelper,
            context = context
        )
    }

    fun persistCarouselPosition(dualHomeViewModel: DualHomeViewModel) {
        val state = dualHomeViewModel.uiState.value
        sessionStateStore.setCarouselPosition(
            state.currentSectionIndex,
            state.selectedIndex
        )
    }
}
