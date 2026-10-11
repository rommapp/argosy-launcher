package com.nendo.argosy

import com.nendo.argosy.data.preferences.DisplayRoleOverride
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
    private lateinit var appContext: android.content.Context
    private lateinit var hostContext: android.content.Context
    private var keyguardLocked = false

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionStateStore = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true) {
            every { userPreferences } returns kotlinx.coroutines.flow.emptyFlow()
        }
        every { sessionStateStore.hasActiveSession() } returns false
        appContext = mockk(relaxed = true) {
            every { getSystemService(android.app.KeyguardManager::class.java) } returns
                mockk {
                    every { isKeyguardLocked } answers {
                        this@DualScreenManagerRoleSwapTest.keyguardLocked
                    }
                }
        }
        hostContext = mockk(relaxed = true) {
            every { applicationContext } returns appContext
        }
        manager = newManager(scope = testScope.backgroundScope)
    }

    @After
    fun tearDown() {
        testScope.backgroundScope.cancel()
        testScope.cancel()
        io.mockk.unmockkAll()
        Dispatchers.resetMain()
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

    @Test
    fun `locking during delayed emulator recovery prevents focus until a new unlocked recovery`() = testScope.runTest {
        io.mockk.mockkObject(com.nendo.argosy.hardware.FocusDirectorActivity.Companion)
        every {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(any(), any())
        } returns Unit
        every { sessionStateStore.hasActiveSession() } returns true
        manager = newManager(scope = testScope.backgroundScope)
        manager.emulatorDisplayId = PRESENTATION_DISPLAY
        manager.restoreEmulatorFocus()
        testScope.testScheduler.runCurrent()
        testScope.testScheduler.advanceTimeBy(100)
        assertEquals(false, manager.isKeyguardShowing)

        keyguardLocked = true
        testScope.testScheduler.advanceTimeBy(200)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(any(), any())
        }
        keyguardLocked = false
        manager.restoreEmulatorFocus()
        testScope.testScheduler.advanceTimeBy(200)
        testScope.testScheduler.runCurrent()

        verify(exactly = 1) {
            com.nendo.argosy.hardware.FocusDirectorActivity.launchOnDisplay(appContext, PRESENTATION_DISPLAY)
        }
    }

    @Test
    fun `locking during delayed companion recovery suppresses launch and unlock retries it`() = testScope.runTest {
        val receiver = companionRecoveryReceiver()
        keyguardLocked = false
        manager.ensureCompanionLaunched()
        testScope.testScheduler.runCurrent()
        testScope.testScheduler.advanceTimeBy(250)
        assertEquals(false, manager.isKeyguardShowing)
        keyguardLocked = true
        testScope.testScheduler.advanceTimeBy(250)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) { hostContext.startActivity(any(), any<android.os.Bundle>()) }

        keyguardLocked = false
        receiver.onReceive(appContext, userPresentIntent())
        testScope.testScheduler.runCurrent()
        testScope.testScheduler.advanceTimeBy(500)
        testScope.testScheduler.runCurrent()

        verify(exactly = 1) { hostContext.startActivity(any(), any<android.os.Bundle>()) }
        manager.unregisterReceivers()
    }

    @Test
    fun `unlock recovery does not displace a foreign app on the second screen`() = testScope.runTest {
        val receiver = companionRecoveryReceiver()
        every { sessionStateStore.isForeignAppOnSecondary() } returns true
        keyguardLocked = false

        receiver.onReceive(appContext, userPresentIntent())
        testScope.testScheduler.advanceTimeBy(500)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) { hostContext.startActivity(any(), any<android.os.Bundle>()) }
        manager.unregisterReceivers()
    }

    @Test
    fun `unlock recovery does not displace an active session outside the launcher`() = testScope.runTest {
        val receiver = companionRecoveryReceiver()
        every { sessionStateStore.hasActiveSession() } returns true
        every { sessionStateStore.isArgosyForeground() } returns false
        keyguardLocked = false

        receiver.onReceive(appContext, userPresentIntent())
        testScope.testScheduler.advanceTimeBy(500)
        testScope.testScheduler.runCurrent()

        verify(exactly = 0) { hostContext.startActivity(any(), any<android.os.Bundle>()) }
        manager.unregisterReceivers()
    }

    private fun companionRecoveryReceiver(): android.content.BroadcastReceiver {
        io.mockk.mockkStatic(androidx.core.content.ContextCompat::class)
        val receiver = slot<android.content.BroadcastReceiver>()
        every {
            androidx.core.content.ContextCompat.registerReceiver(
                appContext, capture(receiver), any(), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } returns null
        io.mockk.mockkObject(com.nendo.argosy.hardware.CompanionGuardService.Companion)
        every { com.nendo.argosy.hardware.CompanionGuardService.start(any()) } returns Unit
        manager = newManager(
            scope = testScope.backgroundScope,
            displayAffinityHelper = mockk(relaxed = true) {
                every { isDockedDark } returns false
                every { hasSecondaryDisplay } returns true
                every { getCompanionLaunchOptions() } returns mockk(relaxed = true)
            }
        )
        keyguardLocked = true
        manager.registerReceivers()
        testScope.testScheduler.runCurrent()
        return receiver.captured
    }

    private fun userPresentIntent(): android.content.Intent = mockk {
        every { action } returns android.content.Intent.ACTION_USER_PRESENT
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
        scope: kotlinx.coroutines.CoroutineScope = testScope,
        displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper =
            mockk(relaxed = true) { every { getRoleDisplayIds(any()) } returns null },
        gameWindowMover: com.nendo.argosy.hardware.GameWindowMover = FakeGameWindowMover(arrives = false)
    ): DualScreenManager = DualScreenManager(
        context = hostContext,
        scope = scope,
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
        achievementUpdateBus = mockk(relaxed = true) {
            every { updates } returns kotlinx.coroutines.flow.MutableSharedFlow()
        },
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
        mediaPlaybackTracker = mockk(relaxed = true) {
            every { activePlayback } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        },
        mediaAvailabilityVerifier = mockk(relaxed = true),
        mediaDownloadDelegate = mockk(relaxed = true),
        mediaSeriesDelegate = mockk(relaxed = true),
        mediaSiblingsDelegate = mockk(relaxed = true),
        gameWindowMover = gameWindowMover
    )
}
