package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.preferences.UserPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LibraryGameStartTest {

    private val dispatcher = StandardTestDispatcher()
    private val gameRepository = mockk<com.nendo.argosy.data.repository.GameRepository>(relaxed = true)
    private val mediaRepository = mockk<com.nendo.argosy.data.repository.MediaRepository>(relaxed = true)
    private val platformRepository = mockk<com.nendo.argosy.data.repository.PlatformRepository>(relaxed = true)
    private val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository>(relaxed = true)
    private val gameLaunchDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GameLaunchDelegate>(relaxed = true)
    private val collectionModalDelegate =
        mockk<com.nendo.argosy.ui.screens.common.CollectionModalDelegate>(relaxed = true)
    private val gradientExtractionDelegate =
        mockk<com.nendo.argosy.ui.screens.common.GradientExtractionDelegate>(relaxed = true)
    private val gameStart = slot<com.nendo.argosy.ui.screens.common.GameStart>()
    private val notificationManager =
        mockk<com.nendo.argosy.core.notification.NotificationManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { gameLaunchDelegate.syncOverlayState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.discPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.variantPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.memcardPickerState } returns MutableStateFlow(null)
        every {
            gameLaunchDelegate.launchGame(
                any(), any(), any(), any(), any(), any(), any(), any(), capture(gameStart), any()
            )
        } returns Unit
        every { collectionModalDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.common.CollectionModalDelegate.State()
        )
        every { gradientExtractionDelegate.gradients } returns MutableStateFlow(emptyMap())
        every { gradientExtractionDelegate.getGradient(any()) } returns null
        every { gameRepository.observeHiddenList() } returns flowOf(emptyList())
        every { gameRepository.observeAllList() } returns flowOf(emptyList())
        every { mediaRepository.isSignedIn } returns flowOf(false)
        every { mediaRepository.observeLibraries() } returns flowOf(emptyList())
        every { platformRepository.observeVisiblePlatforms() } returns MutableStateFlow(emptyList())
        every { preferences.userPreferences } returns flowOf(UserPreferences())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a launch hands the delegate the game to start with no display override`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.launchGame(5L)
            advanceUntilIdle()

            assertEquals(5L, gameStart.captured.gameId)
            assertNull(gameStart.captured.overrideDisplayId)
        }

    @Test
    fun `a game window that fails to open shows the launch failed notice`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.launchGame(5L)
            advanceUntilIdle()
            gameStart.captured.onFailure!!.invoke(SecurityException("denied"))

            verify(exactly = 1) {
                notificationManager.show(
                    title = any(),
                    subtitle = NotificationText.Res(R.string.notif_gamelaunch_launch_failed),
                    type = NotificationType.ERROR,
                    imagePath = any(),
                    platformSlug = any(),
                    duration = any(),
                    key = any(),
                    immediate = any(),
                    accentColor = any()
                )
            }
        }

    private fun viewModel() = LibraryViewModel(
        context = mockk(relaxed = true),
        platformRepository = platformRepository,
        gameRepository = gameRepository,
        mediaRepository = mediaRepository,
        collectionRepository = mockk(relaxed = true),
        gameNavigationContext = mockk(relaxed = true),
        notificationManager = notificationManager,
        preferencesRepository = preferences,
        homeTileRepository = mockk(relaxed = true),
        customGridShapeStore = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameLaunchDelegate = gameLaunchDelegate,
        collectionModalDelegate = collectionModalDelegate,
        romMRepository = mockk(relaxed = true),
        playStoreService = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        apkInstallManager = mockk(relaxed = true),
        platformSyncQueue = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        modalResetSignal = com.nendo.argosy.ui.ModalResetSignal(),
        gradientExtractionDelegate = gradientExtractionDelegate,
        downloadIndicatorSource = mockk(relaxed = true),
        emulatorDetector = mockk(relaxed = true),
        steamLauncherLinkController = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        siblingChoice = mockk(relaxed = true),
        socialRepository = mockk(relaxed = true),
        saveListStatusRepository = mockk(relaxed = true),
        libraryDefaultPlatformMigration = mockk(relaxed = true),
        showcaseSource = mockk(relaxed = true),
        reorderPlatforms = mockk(relaxed = true),
        quickNavigation = mockk { io.mockk.every { enabled } returns MutableStateFlow(true) },
        launchProgressTracker = mockk { io.mockk.every { launchLive } returns MutableStateFlow(false) },
        runningGameMenu = mockk(relaxed = true),
        streamLaunch = com.nendo.argosy.ui.screens.stream.noStreamLaunch(),
        filterMemory = LibraryFilterMemory()
    ).also { it.listDispatcher = dispatcher }
}
