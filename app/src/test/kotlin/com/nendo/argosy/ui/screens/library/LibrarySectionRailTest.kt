package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.ui.input.InputHandler
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibrarySectionRailTest {

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
    private val quickNavigation = MutableStateFlow(true)

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

    private fun TestScope.loaded(layout: LibraryLayout): Pair<LibraryViewModel, InputHandler> {
        every { preferences.userPreferences } returns flowOf(UserPreferences(libraryLayout = layout))
        val viewModel = viewModel()
        viewModel.setInitialSourceFilter(SourceFilter.ALL)
        advanceUntilIdle()
        val handler = viewModel.createInputHandler(
            isDefaultView = true,
            onGameSelect = {},
            onMediaLibrarySelect = {},
            onNavigateToDefault = {},
            onDrawerToggle = {}
        )
        return viewModel to handler
    }

    @Test
    fun `right past the last game of a grid row enters the rail at the current section`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.GRID)

        assertTrue(handler.onRight().handled)
        assertFalse(viewModel.uiState.value.isSectionRailFocused)
        assertTrue(handler.onRight().handled)

        val state = viewModel.uiState.value
        assertTrue(state.isSectionRailFocused)
        assertEquals(state.currentSectionLabel, state.sectionRailFocusedLabel)
        assertEquals(1, state.focusedIndex)
    }

    @Test
    fun `right in list layout enters the rail`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)

        assertTrue(handler.onRight().handled)

        assertEquals(0, viewModel.uiState.value.sectionRailFocusIndex)
    }

    @Test
    fun `rail letters wrap in both directions`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)
        handler.onRight()
        val lastIndex = viewModel.uiState.value.sectionLabels.lastIndex

        handler.onUp()
        assertEquals(lastIndex, viewModel.uiState.value.sectionRailFocusIndex)

        handler.onDown()
        assertEquals(0, viewModel.uiState.value.sectionRailFocusIndex)
    }

    @Test
    fun `confirm on the rail jumps to the first game of that letter and leaves the rail`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)
        handler.onRight()
        handler.onDown()
        val target = viewModel.uiState.value.sectionRailFocusedLabel

        assertTrue(handler.onConfirm().handled)

        val state = viewModel.uiState.value
        assertNull(state.sectionRailFocusIndex)
        assertEquals(target, state.currentSectionLabel)
        assertEquals("banana", state.focusedGame?.sortTitle)
    }

    @Test
    fun `left on the rail jumps like confirm`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)
        handler.onRight()
        handler.onUp()

        assertTrue(handler.onLeft().handled)

        assertNull(viewModel.uiState.value.sectionRailFocusIndex)
        assertEquals("cherry", viewModel.uiState.value.focusedGame?.sortTitle)
    }

    @Test
    fun `back on the rail returns to the games without jumping`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)
        handler.onRight()
        handler.onDown()

        assertTrue(handler.onBack().handled)

        assertNull(viewModel.uiState.value.sectionRailFocusIndex)
        assertEquals(0, viewModel.uiState.value.focusedIndex)
    }

    @Test
    fun `bumpers are left to the app on the game list and held on the rail`() = runTest(dispatcher) {
        val (_, handler) = loaded(LibraryLayout.LIST)

        assertFalse(handler.onPrevSection().handled)
        assertFalse(handler.onNextSection().handled)

        handler.onRight()
        assertTrue(handler.onPrevSection().handled)
        assertTrue(handler.onNextSection().handled)
    }

    @Test
    fun `triggers no longer jump between letters`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)

        assertTrue(handler.onNextTrigger().handled)

        assertEquals(0, viewModel.uiState.value.focusedIndex)
        assertFalse(viewModel.uiState.value.isSectionRailFocused)
    }

    @Test
    fun `with quick navigation off the triggers jump to the next letter`() = runTest(dispatcher) {
        quickNavigation.value = false
        val (viewModel, handler) = loaded(LibraryLayout.LIST)

        assertTrue(handler.onNextTrigger().handled)
        advanceUntilIdle()

        assertEquals("banana", viewModel.uiState.value.focusedGame?.sortTitle)
    }

    @Test
    fun `with quick navigation on the right stick jumps letters and wraps both ways`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)

        val up = handler.onRightStickUp()
        advanceUntilIdle()
        assertTrue(up.handled)
        assertEquals(SoundType.SECTION_CHANGE, up.soundOverride)
        assertEquals("cherry", viewModel.uiState.value.focusedGame?.sortTitle)

        assertTrue(handler.onRightStickDown().handled)
        advanceUntilIdle()
        assertEquals("alpha", viewModel.uiState.value.focusedGame?.sortTitle)

        assertTrue(handler.onRightStickDown().handled)
        advanceUntilIdle()
        assertEquals("banana", viewModel.uiState.value.focusedGame?.sortTitle)
    }

    @Test
    fun `with quick navigation off the right stick also jumps letters`() = runTest(dispatcher) {
        quickNavigation.value = false
        val (viewModel, handler) = loaded(LibraryLayout.LIST)

        assertTrue(handler.onRightStickDown().handled)
        advanceUntilIdle()

        assertEquals("banana", viewModel.uiState.value.focusedGame?.sortTitle)
    }

    @Test
    fun `the right stick is held without jumping while the rail has focus`() = runTest(dispatcher) {
        val (viewModel, handler) = loaded(LibraryLayout.LIST)
        handler.onRight()
        val railIndex = viewModel.uiState.value.sectionRailFocusIndex

        val result = handler.onRightStickDown()
        advanceUntilIdle()

        assertTrue(result.handled)
        assertNull(result.soundOverride)
        assertEquals(railIndex, viewModel.uiState.value.sectionRailFocusIndex)
        assertEquals(0, viewModel.uiState.value.focusedIndex)
    }

    @Test
    fun `with quick navigation off the bumpers stay in the library`() = runTest(dispatcher) {
        quickNavigation.value = false
        val (_, handler) = loaded(LibraryLayout.LIST)

        assertTrue(handler.onPrevSection().handled)
        assertTrue(handler.onNextSection().handled)
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
        quickNavigation = mockk { every { enabled } returns quickNavigation },
        launchProgressTracker = mockk { io.mockk.every { launchLive } returns MutableStateFlow(false) },
        runningGameMenu = mockk(relaxed = true),
        streamLaunch = com.nendo.argosy.ui.screens.stream.noStreamLaunch()
    ).also { it.listDispatcher = dispatcher }
}
