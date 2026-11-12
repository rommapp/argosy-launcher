package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.ui.screens.common.SiblingChoiceDelegate
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import java.time.Instant
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
import org.junit.Before
import org.junit.Test

private const val PICKED_FROM = 1L
private const val NEIGHBOUR = 3L
private const val SHOWN = 2L

/**
 * The presentation screen describes the library's focused game, so a pick that swaps the focused
 * entry for another copy has to leave the cursor on that copy.
 */
class LibrarySiblingPickFocusTest {

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
    private val siblingChoice = mockk<SiblingChoiceDelegate>(relaxed = true)
    private val shownCallback = slot<(Long) -> Unit>()

    private val games = MutableStateFlow(listOf(game(PICKED_FROM, "alpha"), game(NEIGHBOUR, "charlie")))

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
        every { gameRepository.observeAllList() } returns games
        every { mediaRepository.isSignedIn } returns flowOf(false)
        every { mediaRepository.observeLibraries() } returns flowOf(emptyList())
        every { platformRepository.observeVisiblePlatforms() } returns MutableStateFlow(emptyList())
        every { preferences.userPreferences } returns flowOf(UserPreferences())
        every { siblingChoice.openActiveVariant(any(), PICKED_FROM, capture(shownCallback)) } just runs
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a pick reflected after it lands moves the cursor to the shown copy`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(PICKED_FROM, viewModel.uiState.value.focusedGame?.id)

        viewModel.openActiveVariant(PICKED_FROM)
        shownCallback.captured(SHOWN)
        advanceUntilIdle()
        games.value = listOf(game(NEIGHBOUR, "charlie"), game(SHOWN, "zulu"))
        advanceUntilIdle()

        assertEquals(SHOWN, viewModel.uiState.value.focusedGame?.id)
    }

    @Test
    fun `a pick reflected before it lands moves the cursor to the shown copy`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openActiveVariant(PICKED_FROM)
        games.value = listOf(game(NEIGHBOUR, "charlie"), game(SHOWN, "zulu"))
        advanceUntilIdle()
        assertEquals(NEIGHBOUR, viewModel.uiState.value.focusedGame?.id)
        shownCallback.captured(SHOWN)
        advanceUntilIdle()

        assertEquals(SHOWN, viewModel.uiState.value.focusedGame?.id)
    }

    @Test
    fun `picking the copy already shown leaves the cursor where it is`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openActiveVariant(PICKED_FROM)
        shownCallback.captured(PICKED_FROM)
        games.value = listOf(game(PICKED_FROM, "alpha"), game(NEIGHBOUR, "charlie"), game(SHOWN, "zulu"))
        advanceUntilIdle()

        assertEquals(PICKED_FROM, viewModel.uiState.value.focusedGame?.id)
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
        siblingChoice = siblingChoice,
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
