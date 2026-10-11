package com.nendo.argosy

import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.data.media.MediaPlaybackTracker
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.domain.model.PresentationStat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DualScreenManagerRoleSwapTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sessionStateStore: com.nendo.argosy.data.preferences.SessionStateStore
    private lateinit var preferencesRepository:
        com.nendo.argosy.data.preferences.UserPreferencesRepository
    private lateinit var manager: DualScreenManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionStateStore = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        every { sessionStateStore.hasActiveSession() } returns false
        manager = newManager()
    }

    @After
    fun tearDown() {
        testScope.cancel()
        testScope.testScheduler.runCurrent()
        io.mockk.unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `rebinding after activity destruction resumes presentation preference updates`() {
        val preferences = MutableStateFlow(UserPreferences())
        every { preferencesRepository.userPreferences } returns preferences
        testScope.testScheduler.runCurrent()
        val initial = preferences.value
        assertEquals(initial.presentationStyle, manager.presentationStyle.value)
        assertEquals(initial.backgroundBlur, manager.presentationBackgroundBlur.value)

        testScope.cancel()
        val hidden = initial.copy(
            presentationStyle = initial.presentationStyle.copy(hiddenStats = PresentationStat.entries.toSet()),
            backgroundBlur = 80
        )
        preferences.value = hidden
        testScope.testScheduler.runCurrent()
        assertEquals(initial.presentationStyle, manager.presentationStyle.value)
        assertEquals(initial.backgroundBlur, manager.presentationBackgroundBlur.value)

        val replacementScope = TestScope(testDispatcher)
        try {
            manager.rebind(mockk(relaxed = true), replacementScope)
            testScope.testScheduler.runCurrent()
            assertTrue(replacementScope.isActive)
            assertEquals(hidden.presentationStyle, manager.presentationStyle.value)
            assertEquals(hidden.backgroundBlur, manager.presentationBackgroundBlur.value)

            val shown = initial.copy(backgroundBlur = 0)
            preferences.value = shown
            testScope.testScheduler.runCurrent()
            assertEquals(shown.presentationStyle, manager.presentationStyle.value)
            assertEquals(shown.backgroundBlur, manager.presentationBackgroundBlur.value)
        } finally {
            replacementScope.cancel()
            testScope.testScheduler.runCurrent()
        }
    }

    @Test
    fun `a swap flips the live arrangement even when the stored override disagrees`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "SWAPPED"

        manager.swapRoles()

        assertTrue(
            "swapRoles must derive the next arrangement from the live value, not the override",
            manager.isRolesSwapped.value
        )
    }

    @Test
    fun `a swap records the override that matches the arrangement it produced`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.swapRoles()

        val stored = slot<String>()
        verify { sessionStateStore.setDisplayRoleOverride(capture(stored)) }
        assertEquals(DisplayRoleOverride.SWAPPED.name, stored.captured)
        assertTrue(manager.isRolesSwapped.value)
    }

    @Test
    fun `a swap is refused while a session is running`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.hasActiveSession() } returns true

        manager.swapRoles()

        assertEquals(false, manager.isRolesSwapped.value)
    }

    @Test
    fun `clearing the override restores auto without touching the arrangement`() {
        manager.setRolesSwapped(true)
        every { sessionStateStore.getDisplayRoleOverride() } returns "SWAPPED"

        manager.clearDisplayRoleOverride()

        verify { sessionStateStore.setDisplayRoleOverride(DisplayRoleOverride.AUTO.name) }
        assertTrue(manager.isRolesSwapped.value)
    }

    @Test
    fun `a stored layout moving the primary role leaves the override at auto`() {
        manager.setRolesSwapped(false)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.setPrimaryDisplayId(android.view.Display.DEFAULT_DISPLAY)
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
        io.mockk.coVerify(exactly = 0) { preferencesRepository.setDisplayRoleOverride(any()) }
    }

    @Test
    fun `a stored layout returning the primary role to the second screen leaves the override at auto`() {
        manager.setRolesSwapped(true)
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"

        manager.setPrimaryDisplayId(2)
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
        verify(exactly = 0) { sessionStateStore.setDisplayRoleOverride(any()) }
        io.mockk.coVerify(exactly = 0) { preferencesRepository.setDisplayRoleOverride(any()) }
    }

    @Test
    fun `a live swap commits the roles once the game reaches the other display`() {
        val host = liveSwapReady(FakeGameWindowMover(arrives = true))

        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(manager.isRolesSwapped.value)
        assertEquals(PRESENTATION_DISPLAY, manager.emulatorDisplayId)
        verify(exactly = 0) { host.displayMoveAbandoned() }
    }

    @Test
    fun `a live swap the game never completes keeps the roles and the game display`() {
        val host = liveSwapReady(FakeGameWindowMover(arrives = false))

        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(false, manager.isRolesSwapped.value)
        assertEquals(PRIMARY_DISPLAY, manager.emulatorDisplayId)
        verify(exactly = 1) { host.displayMoveAbandoned() }
    }

    @Test
    fun `a game arriving after the move was abandoned still commits the roles`() {
        liveSwapReady(FakeGameWindowMover(arrives = false))
        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()

        manager.onGameMovedToDisplay(PRESENTATION_DISPLAY)

        assertTrue(manager.isRolesSwapped.value)
        assertEquals(PRESENTATION_DISPLAY, manager.emulatorDisplayId)
    }

    @Test
    fun `a late arrival after the session ended changes nothing`() {
        liveSwapReady(FakeGameWindowMover(arrives = false))
        manager.swapRoles()
        testScope.testScheduler.advanceUntilIdle()
        manager.emulatorDisplayId = null

        manager.onGameMovedToDisplay(PRESENTATION_DISPLAY)

        assertEquals(false, manager.isRolesSwapped.value)
        assertEquals(null, manager.emulatorDisplayId)
    }

    private fun liveSwapReady(mover: FakeGameWindowMover): DualScreenManager.LiveMoveHost {
        io.mockk.mockkObject(com.nendo.argosy.hardware.FocusDirectorActivity.Companion)
        every {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(any(), any())
        } returns Unit
        every { sessionStateStore.hasActiveSession() } returns true
        every { sessionStateStore.getDisplayRoleOverride() } returns "AUTO"
        manager = newManager(
            displayAffinityHelper = mockk(relaxed = true) {
                every { isDockedDark } returns false
                every { getRoleDisplayIds(false) } returns (PRIMARY_DISPLAY to PRESENTATION_DISPLAY)
                every { getRoleDisplayIds(true) } returns (PRESENTATION_DISPLAY to PRIMARY_DISPLAY)
                every { appScreenDisplayId(any()) } returns null
            },
            gameWindowMover = mover
        )
        mover.onMoved = manager::onGameMovedToDisplay
        manager.setRolesSwapped(false)
        manager.emulatorDisplayId = PRIMARY_DISPLAY
        val host = mockk<DualScreenManager.LiveMoveHost>(relaxed = true)
        manager.registerLiveMoveHost(host)
        manager.registerReceivers()
        testScope.testScheduler.advanceUntilIdle()
        return host
    }

    private class FakeGameWindowMover(
        private val arrives: Boolean
    ) : com.nendo.argosy.hardware.GameWindowMover {
        var onMoved: (Int) -> Unit = {}

        override suspend fun isAvailable(): Boolean = true

        override suspend fun moveGame(displayId: Int): Boolean {
            if (arrives) onMoved(displayId)
            return true
        }
    }

    private companion object {
        const val PRIMARY_DISPLAY = 0
        const val PRESENTATION_DISPLAY = 1
    }

    private fun newManager(
        displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper =
            mockk(relaxed = true) { every { getRoleDisplayIds(any()) } returns null },
        gameWindowMover: com.nendo.argosy.hardware.GameWindowMover = FakeGameWindowMover(arrives = false)
    ): DualScreenManager = DualScreenManager(
        context = mockk(relaxed = true),
        scope = testScope,
        gameDao = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        activeSaveRepository = mockk(relaxed = true),
        prefetchGameSaveDataUseCase = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        collectionRepository = mockk(relaxed = true),
        socialRepository = mockk(relaxed = true),
        downloadQueueDao = mockk(relaxed = true),
        downloadQueueRepository = mockk(relaxed = true),
        gameFileDao = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        gameActionsDelegate = mockk(relaxed = true),
        platformSyncQueue = mockk(relaxed = true),
        sessionEndCoordinator = mockk(relaxed = true),
        saveCacheManager = mockk(relaxed = true),
        raRepository = mockk(relaxed = true),
        raTileContentRepository = mockk(relaxed = true),
        achievementUpdateBus = AchievementUpdateBus(),
        displayAffinityHelper = displayAffinityHelper,
        sessionStateStore = sessionStateStore,
        preferencesRepository = preferencesRepository,
        imageCacheManager = mockk(relaxed = true),
        romMRepository = mockk(relaxed = true),
        gameDocumentLoader = mockk(relaxed = true),
        documentHighlightStore = mockk(relaxed = true),
        resolveGameEmulatorContext = mockk(relaxed = true),
        hapticManager = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        homeTileRepository = mockk(relaxed = true),
        homeTilePromptQueue = mockk(relaxed = true),
        appsRepository = mockk(relaxed = true),
        appShortcutActions = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        titleIdDownloadObserver = mockk(relaxed = true),
        homeGridPageRepository = mockk(relaxed = true),
        pageChooserEntrySource = mockk(relaxed = true),
        ambientAudioManager = mockk(relaxed = true),
        emulatorConfigDao = mockk(relaxed = true),
        configureEmulatorUseCase = mockk(relaxed = true),
        builtinCoreResolver = mockk(relaxed = true),
        saveHandlerRegistry = mockk(relaxed = true),
        steamDownloadQueueDao = mockk(relaxed = true),
        steamRepository = mockk(relaxed = true),
        playSessionTracker = mockk(relaxed = true),
        permissionHelper = mockk(relaxed = true),
        steamContentManager = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        gradientExtractionDelegate = mockk(relaxed = true),
        filePickerFlow = mockk(relaxed = true),
        gameThemeAudioCoordinator = mockk(relaxed = true),
        getPinnedCollectionsUseCase = mockk(relaxed = true),
        getGamesForPinnedCollectionUseCase = mockk(relaxed = true),
        advanceCollectionFocusUseCase = mockk(relaxed = true),
        prepareCollectionQueueUseCase = mockk(relaxed = true),
        mediaRepository = mockk(relaxed = true),
        getRelatedMediaUseCase = mockk(relaxed = true),
        resolveMediaPlayTargetUseCase = mockk(relaxed = true),
        mediaPlaybackTracker = MediaPlaybackTracker(),
        mediaAvailabilityVerifier = mockk(relaxed = true),
        mediaDownloadDelegate = mockk(relaxed = true),
        mediaSeriesDelegate = mockk(relaxed = true),
        mediaSiblingsDelegate = mockk(relaxed = true),
        gameWindowMover = gameWindowMover
    )
}
