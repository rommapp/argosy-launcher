package com.nendo.argosy.hardware

import android.content.Context
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.DownloadQueueRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.DisplayRoleResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecondaryHomeStateManager(
    private val context: Context,
    private val gameRepository: GameRepository,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val steamRepository: SteamRepository,
    private val configureEmulatorUseCase: com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase,
    private val builtinCoreResolver: com.nendo.argosy.data.emulator.BuiltinCoreResolver,
    private val saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry,
    private val steamContentManager: com.nendo.argosy.data.steam.SteamContentManager? = null,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository
) {

    lateinit var sessionStateStore: SessionStateStore
        private set

    data class InitialState(
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
        val restoreScheduled: Boolean,
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

        displayAffinityHelper.dualScreenEnabled = sessionStateStore.isDualScreenEnabled()
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

        val navContext = sessionStateStore.getCarouselNavContext()
        val savedSection = navContext.legacySectionIndex
        val savedSelected = navContext.legacySelectedIndex
        val restoreScheduled = navContext.hasContext || savedSection > 0 || savedSelected > 0
        if (restoreScheduled) {
            dualHomeViewModel.restoreNavContext(navContext)
        }

        val savedScreen = sessionStateStore.getCompanionScreen()
        val savedDetailGameId = sessionStateStore.getDetailGameId()
        var restoredScreen: CompanionScreen? = null
        var restoredDetailViewModel: DualGameDetailViewModel? = null
        var restoredDetailGameId = -1L

        val savedModal = sessionStateStore.getActiveModal()
        if (savedModal != "NONE") {
            sessionStateStore.clearActiveModal()
        }

        val wasScreenshotOpen = sessionStateStore.isScreenshotViewerOpen()
        if (wasScreenshotOpen) {
            sessionStateStore.setScreenshotViewerState(false)
        }

        if (savedScreen == "GAME_DETAIL" && savedDetailGameId > 0 && !isGameActive) {
            val affinityHelper = DisplayAffinityHelper(context)
            val vm = DualGameDetailViewModel(
                gameRepository = gameRepository,
                activeSaveRepository = activeSaveRepository,
                platformRepository = platformRepository,
                collectionRepository = collectionRepository,
                emulatorConfigDao = emulatorConfigDao,
                downloadQueueRepository = downloadQueueRepository,
                steamRepository = steamRepository,
                configureEmulatorUseCase = configureEmulatorUseCase,
                builtinCoreResolver = builtinCoreResolver,
                saveHandlerRegistry = saveHandlerRegistry,
                steamContentManager = steamContentManager,
                displayAffinityHelper = affinityHelper,
                downloadFileStatusRepository = downloadFileStatusRepository,
                sessionStateStore = sessionStateStore,
                preferencesRepository = preferencesRepository,
                context = context
            )
            vm.loadGame(savedDetailGameId)
            restoredDetailViewModel = vm
            restoredScreen = CompanionScreen.GAME_DETAIL
            restoredDetailGameId = savedDetailGameId
        }

        return InitialState(
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
            restoreScheduled = restoreScheduled,
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

    /**
     * Swap state from live preferences rather than the boot-time session mirror, and the
     * only place the companion honours the Controller Layout override.
     *
     * `swap*` drive key mapping and are the raw preference; `*IconsSwapped` drive glyphs
     * and are xor'd with the pad's lettering. Start/Select has no xor by design. Feeding
     * an icon value into key mapping inverts the buttons.
     */
    fun inputSwapStateFrom(prefs: UserPreferences): InputSwapState {
        val isNintendoLayout = when (prefs.controllerLayout) {
            "nintendo" -> true
            "xbox" -> false
            else -> ControllerDetector.detectFromActiveGamepad().layout == DetectedLayout.NINTENDO
        }

        return InputSwapState(
            swapAB = prefs.swapAB,
            swapXY = prefs.swapXY,
            swapStartSelect = prefs.swapStartSelect,
            dualScreenInputFocus = sessionStateStore.getDualScreenInputFocus(),
            abIconsSwapped = isNintendoLayout xor prefs.swapAB,
            xyIconsSwapped = isNintendoLayout xor prefs.swapXY,
            startSelectSwapped = prefs.swapStartSelect
        )
    }

    suspend fun loadCompanionGameData(gameId: Long): CompanionInGameState {
        return withContext(Dispatchers.IO) {
            val game = gameRepository.getById(gameId) ?: return@withContext CompanionInGameState()
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
            gameRepository = gameRepository,
            activeSaveRepository = activeSaveRepository,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            emulatorConfigDao = emulatorConfigDao,
            downloadQueueRepository = downloadQueueRepository,
            steamRepository = steamRepository,
            configureEmulatorUseCase = configureEmulatorUseCase,
            builtinCoreResolver = builtinCoreResolver,
            steamContentManager = steamContentManager,
            displayAffinityHelper = affinityHelper,
            downloadFileStatusRepository = downloadFileStatusRepository,
            sessionStateStore = sessionStateStore,
            preferencesRepository = preferencesRepository,
            saveHandlerRegistry = saveHandlerRegistry,
            context = context
        )
    }

}
