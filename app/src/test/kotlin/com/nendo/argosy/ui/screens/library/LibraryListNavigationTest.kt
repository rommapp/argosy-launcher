package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.UserPreferences
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryListNavigationTest {

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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { gameLaunchDelegate.syncOverlayState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.discPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.variantPickerState } returns MutableStateFlow(null)
        every { gameLaunchDelegate.memcardPickerState } returns MutableStateFlow(null)
        every { collectionModalDelegate.state } returns MutableStateFlow(
            com.nendo.argosy.ui.screens.common.CollectionModalDelegate.State()
        )
        every { gradientExtractionDelegate.gradients } returns MutableStateFlow(emptyMap())
        every { gradientExtractionDelegate.getGradient(any()) } returns null
        every { gameRepository.observeHiddenList() } returns flowOf(emptyList())
        every { gameRepository.observeAllList() } returns flowOf(
            listOf(game(1, "alpha"), game(2, "apple"), game(3, "banana"), game(4, "cherry"))
        )
        every { mediaRepository.isSignedIn } returns flowOf(false)
        every { mediaRepository.observeLibraries() } returns flowOf(emptyList())
        every { platformRepository.observeVisiblePlatforms() } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loaded(layout: LibraryLayout): LibraryViewModel {
        every { preferences.userPreferences } returns flowOf(UserPreferences(libraryLayout = layout))
        val viewModel = viewModel()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `list mode moves one row per press down and up`() = runTest(dispatcher) {
        val viewModel = loaded(LibraryLayout.LIST)

        val visited = (1..3).map {
            viewModel.moveFocus(FocusMove.DOWN)
            viewModel.uiState.value.focusedIndex
        }

        assertEquals(listOf(1, 2, 3), visited)
        assertTrue(viewModel.moveFocus(FocusMove.UP))
        assertEquals(2, viewModel.uiState.value.focusedIndex)
    }

    @Test
    fun `list mode crosses a section header in one press`() = runTest(dispatcher) {
        val viewModel = loaded(LibraryLayout.LIST)
        viewModel.moveFocus(FocusMove.DOWN)

        viewModel.moveFocus(FocusMove.DOWN)

        assertEquals("banana", viewModel.uiState.value.focusedGame?.sortTitle)
    }

    @Test
    fun `list mode leaves left and right unhandled`() = runTest(dispatcher) {
        val viewModel = loaded(LibraryLayout.LIST)

        assertFalse(viewModel.moveFocus(FocusMove.LEFT))
        assertFalse(viewModel.moveFocus(FocusMove.RIGHT))
        assertEquals(0, viewModel.uiState.value.focusedIndex)
    }

    @Test
    fun `grid mode moves by row rather than by game`() = runTest(dispatcher) {
        val viewModel = loaded(LibraryLayout.GRID)

        viewModel.moveFocus(FocusMove.DOWN)

        assertEquals(2, viewModel.uiState.value.focusedIndex)
    }

    @Test
    fun `list mode reports one column`() = runTest(dispatcher) {
        val viewModel = loaded(LibraryLayout.LIST)

        assertEquals(1, viewModel.uiState.value.columnsCount)
        assertTrue(viewModel.uiState.value.isListLayout)
    }

    private fun game(id: Long, title: String) = GameListItem(
        id = id,
        platformId = 7,
        platformSlug = "snes",
        title = title,
        sortTitle = title,
        localPath = null,
        source = GameSource.ROMM_REMOTE,
        isFavorite = false,
        isHidden = false,
        isMultiDisc = false,
        rommId = id,
        steamAppId = null,
        packageName = null,
        steamLauncher = null,
        playCount = 0,
        playTimeMinutes = 0,
        lastPlayed = null,
        genre = null,
        players = null,
        rating = null,
        userRating = 0,
        userDifficulty = 0,
        releaseYear = null,
        addedAt = Instant.EPOCH,
        achievementCount = 0,
        earnedAchievementCount = 0,
        completion = 0,
        status = null,
        developer = null,
        igdbId = null,
        timeToBeatMainSec = null
    )

    private fun viewModel() = LibraryViewModel(
        context = mockk(relaxed = true),
        platformRepository = platformRepository,
        gameRepository = gameRepository,
        mediaRepository = mediaRepository,
        collectionRepository = mockk(relaxed = true),
        gameNavigationContext = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
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
        streamLaunch = com.nendo.argosy.ui.screens.stream.noStreamLaunch()
    ).also { it.listDispatcher = dispatcher }
}
