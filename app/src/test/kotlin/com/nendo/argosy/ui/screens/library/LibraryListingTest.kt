package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SortPartition
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryListingTest {

    private val games = listOf(
        game(1, "Chrono Trigger", genre = "RPG", players = "1"),
        game(2, "Bomberman", genre = "Action", players = "1-4"),
        game(3, "Earthbound", genre = "RPG", players = "1"),
        game(4, "Secret of Mana", genre = "RPG", players = "1-3"),
        game(5, "Tetris", genre = "Puzzle", players = "1-2")
    )

    @Test
    fun `genre and sort leave the matching games in title order`() = runTest {
        val listing = listingOf(ActiveFilters(genres = setOf("RPG")))

        assertEquals(listOf(1L, 3L, 4L), listing.games.map { it.id })
        assertEquals(listOf("C", "E", "S"), listing.sectionLabels)
    }

    @Test
    fun `search, players and region ids narrow together`() = runTest {
        val listing = listingOf(
            ActiveFilters(searchQuery = "o", players = PlayerCountBucket.TWO),
            LibraryFilterIds(regionIds = setOf(2L, 4L, 5L))
        )

        assertEquals(listOf(2L, 4L), listing.games.map { it.id })
    }

    @Test
    fun `a descending sort reverses the sections`() = runTest {
        val listing = listingOf(ActiveFilters(sort = ActiveSort(SortOption.TITLE, descending = true)))

        assertEquals(listOf(5L, 4L, 3L, 1L, 2L), listing.games.map { it.id })
    }

    @Test
    fun `section lookups follow the flat game order`() = runTest {
        val listing = listingOf(ActiveFilters(genres = setOf("RPG")))

        assertEquals("E", listing.sectionLabelForGameIndex(1))
        assertEquals(2, listing.firstGameIndexOfSection("S"))
        assertEquals(null, listing.firstGameIndexOfSection("Z"))
    }

    @Test
    fun `source admits match the library's per-source queries`() {
        val hidden = game(9, "Hidden", isHidden = true)
        val favorite = game(10, "Fav", isFavorite = true)
        val remote = game(11, "Remote", source = GameSource.ROMM_REMOTE)
        val local = game(12, "Local", source = GameSource.LOCAL_ONLY, localPath = "/roms/local.sfc")

        assertFalse(SourceFilter.ALL.admitsSource(hidden))
        assertTrue(SourceFilter.HIDDEN.admitsSource(hidden))
        assertTrue(SourceFilter.FAVORITES.admitsSource(favorite))
        assertFalse(SourceFilter.FAVORITES.admitsSource(remote))
        assertFalse(SourceFilter.PLAYABLE.admitsSource(remote))
        assertTrue(SourceFilter.PLAYABLE.admitsSource(local))
    }

    @Test
    fun `the filter panel toggles a genre on and off`() {
        val panel = FilterPanelState(filterOptions = FilterOptions(genres = listOf("Action", "RPG")))
            .withCategory(FilterCategory.GENRE)
            .movedOption(1)

        val on = panel.confirmedFilters()
        val off = panel.copy(activeFilters = on!!).confirmedFilters()

        assertEquals(setOf("RPG"), on.genres)
        assertEquals(emptySet<String>(), off!!.genres)
    }

    private suspend fun listingOf(
        filters: ActiveFilters,
        ids: LibraryFilterIds = LibraryFilterIds()
    ): LibraryListing = buildLibraryListing(games, filters, ids, SortPartition.NONE) { it.toTestUi() }

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the library view model lists exactly what the shared pipeline builds`() = runTest(dispatcher) {
        val prefs = UserPreferences()
        val viewModel = libraryViewModel(prefs)
        viewModel.setInitialTileFilters(LibraryLinkFilters(genres = setOf("RPG")))
        advanceUntilIdle()

        val filters = viewModel.uiState.value.activeFilters
        val expected = buildLibraryListing(
            games,
            filters,
            LibraryFilterIds(),
            SortPartition(prefs.sortInstalledFirst, prefs.sortFavoritesFirst)
        ) { it.toTestUi() }

        assertEquals(setOf("RPG"), filters.genres)
        assertEquals(expected.games.map { it.id }, viewModel.uiState.value.games.map { it.id })
        assertEquals(expected.sectionLabels, viewModel.uiState.value.sectionLabels)
        assertEquals(setOf(1L, 3L, 4L), viewModel.uiState.value.games.map { it.id }.toSet())
    }

    private fun libraryViewModel(prefs: UserPreferences): LibraryViewModel {
        val gameRepository = mockk<com.nendo.argosy.data.repository.GameRepository>(relaxed = true)
        val mediaRepository = mockk<com.nendo.argosy.data.repository.MediaRepository>(relaxed = true)
        val platformRepository = mockk<com.nendo.argosy.data.repository.PlatformRepository>(relaxed = true)
        val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository>(relaxed = true)
        val gameLaunchDelegate = mockk<com.nendo.argosy.ui.screens.common.GameLaunchDelegate>(relaxed = true)
        val collectionModalDelegate =
            mockk<com.nendo.argosy.ui.screens.common.CollectionModalDelegate>(relaxed = true)
        val gradientExtractionDelegate =
            mockk<com.nendo.argosy.ui.screens.common.GradientExtractionDelegate>(relaxed = true)
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
        every { gameRepository.observeAllList() } returns flowOf(games)
        every { mediaRepository.isSignedIn } returns flowOf(false)
        every { mediaRepository.observeLibraries() } returns flowOf(emptyList())
        every { platformRepository.observeVisiblePlatforms() } returns MutableStateFlow(emptyList())
        every { preferences.userPreferences } returns flowOf(prefs)
        return LibraryViewModel(
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
            quickNavigation = mockk { every { enabled } returns MutableStateFlow(true) },
            launchProgressTracker = mockk { every { launchLive } returns MutableStateFlow(false) },
            runningGameMenu = mockk(relaxed = true),
            streamLaunch = com.nendo.argosy.ui.screens.stream.noStreamLaunch(),
            filterMemory = LibraryFilterMemory()
        ).also { it.listDispatcher = dispatcher }
    }
}

private fun GameListItem.toTestUi() = LibraryGameUi(
    id = id,
    title = title,
    sortTitle = sortTitle,
    platformId = platformId,
    platformSlug = platformSlug,
    platformDisplayName = platformSlug,
    coverPath = null,
    source = source,
    isFavorite = isFavorite,
    isDownloaded = isDownloaded,
    isRommGame = rommId != null,
    isAndroidApp = false,
    emulatorName = null,
    listDetails = com.nendo.argosy.ui.common.GameListDetails()
)

private fun game(
    id: Long,
    title: String,
    genre: String? = null,
    players: String? = null,
    isHidden: Boolean = false,
    isFavorite: Boolean = false,
    source: GameSource = GameSource.ROMM_SYNCED,
    localPath: String? = null
) = GameListItem(
    id = id,
    platformId = 7,
    platformSlug = "snes",
    title = title,
    sortTitle = title.lowercase(),
    localPath = localPath,
    source = source,
    isFavorite = isFavorite,
    isHidden = isHidden,
    isMultiDisc = false,
    rommId = id,
    steamAppId = null,
    packageName = null,
    steamLauncher = null,
    playCount = 0,
    playTimeMinutes = 0,
    lastPlayed = null,
    genre = genre,
    players = players,
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
