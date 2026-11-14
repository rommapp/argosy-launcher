package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.libretro.LaunchMode
import com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class GameDetailPlayOptionsSessionEndTest {

    private val dispatcher = StandardTestDispatcher()
    private val gameLaunchDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GameLaunchDelegate>(relaxed = true)
    private val playOptionsDelegate =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.PlayOptionsDelegate>(relaxed = true)
    private val activeSaveRepository =
        mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true)
    private val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository>(relaxed = true)
    private val saveChannelDelegate =
        mockk<com.nendo.argosy.ui.common.savechannel.SaveChannelDelegate>(relaxed = true)
    private val saveManagement =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.SaveManagementDelegate>(relaxed = true)
    private val playSessionTracker = mockk<com.nendo.argosy.data.emulator.PlaySessionTracker>(relaxed = true)
    private val collectionModalDelegate =
        mockk<com.nendo.argosy.ui.screens.common.CollectionModalDelegate>(relaxed = true)
    private val pickerModalDelegate =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.PickerModalDelegate>(relaxed = true)
    private val downloadDelegate =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.DownloadDelegate>(relaxed = true)
    private val screenshotDelegate =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.ScreenshotDelegate>(relaxed = true)
    private val ratingsStatus =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.RatingsStatusDelegate>(relaxed = true)
    private val moreOptionsDelegate =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.MoreOptionsDelegate>(relaxed = true)
    private val perGameSettingsDelegate =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.PerGameSettingsDelegate>(relaxed = true)
    private val achievementDelegate =
        mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.AchievementDelegate>(relaxed = true)
    private val socialRepository = mockk<com.nendo.argosy.data.social.SocialRepository>(relaxed = true)
    private val gradientExtractionDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GradientExtractionDelegate>(relaxed = true)
    private val appsRepository = mockk<com.nendo.argosy.data.repository.AppsRepository>(relaxed = true)
    private val siblingChoice = mockk<com.nendo.argosy.ui.screens.common.SiblingChoiceDelegate>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { gameLaunchDelegate.syncOverlayState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.sessionEndRunning } returns MutableStateFlow(false)
        every { gameLaunchDelegate.memcardPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.variantPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.isSessionEndBusy } returns false
        every { preferences.userPreferences } returns flowOf(UserPreferences())
        every { saveManagement.saveChannelDelegate } returns saveChannelDelegate
        every { saveChannelDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.common.savechannel.SaveChannelState()
        )
        every { saveChannelDelegate.saveStatusEvents } returns MutableSharedFlow()
        every { playSessionTracker.activeSession } returns MutableStateFlow(null)
        every { collectionModalDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.common.CollectionModalDelegate.State()
        )
        every { pickerModalDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.gamedetail.delegates.PickerModalState()
        )
        every { pickerModalDelegate.selection } returns MutableStateFlow(null)
        every { downloadDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.gamedetail.delegates.DownloadUiState()
        )
        every { downloadDelegate.launchEvents } returns MutableSharedFlow()
        every { screenshotDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.gamedetail.delegates.ScreenshotState()
        )
        every { ratingsStatus.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.gamedetail.delegates.RatingsStatusState()
        )
        every { playOptionsDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.gamedetail.delegates.PlayOptionsState()
        )
        every { moreOptionsDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.gamedetail.delegates.MoreOptionsState()
        )
        every { perGameSettingsDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.gamedetail.delegates.PerGameSettingsState()
        )
        every { achievementDelegate.achievements } returns MutableStateFlow(emptyList())
        every { socialRepository.hiddenGameIds } returns MutableStateFlow(emptySet())
        every { socialRepository.connectionState } returns MutableStateFlow(
            mockk<com.nendo.argosy.data.social.SocialConnectionState>()
        )
        every { socialRepository.friendsActivity } returns MutableStateFlow(emptyMap())
        every { socialRepository.reviewSummaries } returns MutableStateFlow(emptyMap())
        every { socialRepository.gameReviews } returns MutableStateFlow(emptyMap())
        every { socialRepository.reviewWriteEvents } returns MutableSharedFlow()
        every { gradientExtractionDelegate.gradients } returns MutableStateFlow(emptyMap())
        every { appsRepository.packageChanges } returns emptyFlow()
        every { siblingChoice.state } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `play options stay closed while a session end runs`() = runTest(dispatcher) {
        every { gameLaunchDelegate.isSessionEndBusy } returns true
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.showPlayOptions()
        advanceUntilIdle()

        verify(exactly = 0) { playOptionsDelegate.showPlayOptions(any(), any(), any()) }
    }

    @Test
    fun `play options open when no session end runs`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.showPlayOptions()
        advanceUntilIdle()

        verify(exactly = 1) { playOptionsDelegate.showPlayOptions(any(), any(), any()) }
    }

    @Test
    fun `new game from an open play options modal keeps the active save while a session end runs`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            every { gameLaunchDelegate.isSessionEndBusy } returns true

            viewModel.handlePlayOption(PlayOptionAction.NewCasual)
            advanceUntilIdle()

            coVerify(exactly = 0) { activeSaveRepository.clearActive(any()) }
            verify(exactly = 0) {
                gameLaunchDelegate.launchSimple(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
        }

    @Test
    fun `new game clears the active save and launches when no session end runs`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.handlePlayOption(PlayOptionAction.NewCasual)
            advanceUntilIdle()

            coVerify(exactly = 1) { activeSaveRepository.clearActive(any()) }
            verify(exactly = 1) {
                gameLaunchDelegate.launchSimple(
                    any(), any(), any(), any(), any(), any(), any(), LaunchMode.NEW_CASUAL, any(), any()
                )
            }
        }

    private fun viewModel() = GameDetailViewModel(
        context = mockk(relaxed = true),
        gameDiscDao = mockk(relaxed = true),
        gameFileDao = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        emulatorConfigDao = mockk(relaxed = true),
        emulatorDetector = mockk(relaxed = true),
        emulatorResolver = mockk(relaxed = true),
        stateSupportResolver = mockk(relaxed = true),
        prefetchGameSaveDataUseCase = mockk(relaxed = true),
        builtinCoreResolver = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        activeSaveRepository = activeSaveRepository,
        gameNavigationContext = mockk(relaxed = true),
        configureEmulatorUseCase = mockk(relaxed = true),
        romMRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameLaunchDelegate = gameLaunchDelegate,
        playSessionTracker = playSessionTracker,
        collectionModalDelegate = collectionModalDelegate,
        imageCacheManager = mockk(relaxed = true),
        preferencesRepository = preferences,
        gameUpdateBus = com.nendo.argosy.core.event.GameUpdateBus(),
        repairImageCacheUseCase = mockk(relaxed = true),
        modalResetSignal = com.nendo.argosy.ui.ModalResetSignal(),
        titleIdDownloadObserver = mockk(relaxed = true),
        gameDocumentLoader = mockk(relaxed = true),
        documentHighlightStore = mockk(relaxed = true),
        pickerModalDelegate = pickerModalDelegate,
        modInstallDelegate = mockk(relaxed = true),
        achievementDelegate = achievementDelegate,
        downloadDelegate = downloadDelegate,
        saveManagement = saveManagement,
        screenshotDelegate = screenshotDelegate,
        ratingsStatus = ratingsStatus,
        artworkDelegate = mockk(relaxed = true),
        playOptionsDelegate = playOptionsDelegate,
        moreOptionsDelegate = moreOptionsDelegate,
        perGameSettingsDelegate = perGameSettingsDelegate,
        speedrunSplitsDelegate = mockk(relaxed = true),
        socialRepository = socialRepository,
        steamLauncherLinkController = mockk(relaxed = true),
        variantScanner = mockk(relaxed = true),
        variantResolver = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        getRelatedGamesUseCase = mockk(relaxed = true),
        gradientExtractionDelegate = gradientExtractionDelegate,
        gameThemeAudio = mockk(relaxed = true),
        siblingChoice = siblingChoice,
        steamInstallReports = mockk(relaxed = true),
        appsRepository = appsRepository,
        runningGames = mockk {
            every { state } returns MutableStateFlow(com.nendo.argosy.data.emulator.background.RunningGamesSnapshot())
        },
        runningGameMenu = mockk(relaxed = true),
        streamLaunch = com.nendo.argosy.ui.screens.stream.noStreamLaunch()
    )
}
