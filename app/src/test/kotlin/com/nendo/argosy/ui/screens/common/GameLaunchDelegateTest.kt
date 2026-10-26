package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameLaunchDelegateTest {

    private val holdMs = ComponentDefaults.LaunchOverlay.veilMs.toLong()
    private val gameId = 7L
    private val intent: Intent = mockk(relaxed = true)
    private val options: Bundle = mockk()
    private val game: GameEntity = mockk(relaxed = true)
    private val gameRepository: GameRepository = mockk(relaxed = true)
    private val launchGameUseCase: LaunchGameUseCase = mockk()
    private val playSessionTracker: PlaySessionTracker = mockk(relaxed = true)
    private val emulatorResolver: com.nendo.argosy.data.emulator.EmulatorResolver = mockk(relaxed = true)
    private val preLaunchRecapStep: PreLaunchRecapStep = mockk()
    private val resolver: EmulatorLaunchTargetResolver = mockk()
    private val manager: DualScreenManager = mockk()
    private val syncOverlayState = MutableStateFlow<SyncOverlayState?>(null)
    private val sessionEndRunning = MutableStateFlow(false)
    private val sessionEndCoordinator: SessionEndCoordinator = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { intent.component } returns null
        every { game.id } returns gameId
        every { game.title } returns "Game"
        coEvery { gameRepository.getById(gameId) } returns game
        every { playSessionTracker.activeSession } returns MutableStateFlow(null)
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns null
        coEvery { preLaunchRecapStep.run(any(), any(), any()) } returns true
        coEvery { resolver.launchOptionsFor(gameId, any(), any()) } returns options
        every { manager.startGame(any()) } returns Result.success(Unit)
        DualScreenManagerHolder.instance = manager
        every { sessionEndCoordinator.syncOverlayState } returns syncOverlayState
        every { sessionEndCoordinator.sessionEndRunning } returns sessionEndRunning
    }

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

    private fun TestScope.tracker() = spyk(LaunchProgressTracker(backgroundScope))

    private fun TestScope.router() = GameStartRouter(mockk<Context>(relaxed = true), resolver, playSessionTracker, backgroundScope)

    private fun useCaseReturns(result: LaunchResult) {
        coEvery {
            launchGameUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns result
    }

    private fun delegate(tracker: LaunchProgressTracker, router: GameStartRouter) = GameLaunchDelegate(
        application = mockk(relaxed = true),
        gameRepository = gameRepository,
        emulatorResolver = emulatorResolver,
        preferencesRepository = mockk(relaxed = true),
        launchGameUseCase = launchGameUseCase,
        launchWithSyncUseCase = mockk(relaxed = true),
        playSessionTracker = playSessionTracker,
        gameLauncher = mockk(relaxed = true),
        hapticManager = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        sessionEndCoordinator = sessionEndCoordinator,
        saveSyncRepository = mockk(relaxed = true),
        saveCacheManager = mockk(relaxed = true),
        activeSaveRepository = mockk(relaxed = true),
        variantResolver = mockk(relaxed = true),
        emulatorSaveConfigRepository = mockk(relaxed = true),
        retroAchievementsRepository = mockk(relaxed = true),
        getUnifiedSavesUseCase = mockk(relaxed = true),
        launchProgressTracker = tracker,
        launchHandoff = LaunchHandoff(tracker, mockk(relaxed = true), router, playSessionTracker, holdMs),
        resolveSnapshotConflict = mockk(relaxed = true),
        connectedControllerTracker = mockk(relaxed = true),
        preLaunchRecapStep = preLaunchRecapStep,
        backgroundGameLaunch = mockk {
            coEvery { findLive(any(), any(), any()) } returns null
            coEvery { keepsAlive(any()) } returns false
            coEvery { clearForLaunch(any(), any()) } returns BackgroundGameLaunch.Clear.NOT_RUNNING
        }
    )

    private fun callbacks(onFailure: (Throwable) -> Unit = {}, onLaunchFailed: () -> Unit = {}) =
        LaunchResultCallbacks(
            start = GameStart(gameId, onFailure = onFailure),
            onLaunchFailed = onLaunchFailed
        )

    @Test
    fun `a successful launch publishes launching, requests the window once after the veil, and frees the tracker`() = runTest {
        useCaseReturns(LaunchResult.Success(intent))
        val tracker = tracker()
        val router = router()

        delegate(tracker, router).launchSimple(this, gameId, callbacks = callbacks())
        runCurrent()

        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(holdMs)
        runCurrent()

        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
        verify(exactly = 1) { tracker.finish(any(), true) }
        verify(exactly = 0) { tracker.finish(any(), false) }
        assertNotNull(tracker.begin(gameId, "Game"))
    }

    @Test
    fun `an external launch defers its session to the handoff, which starts it once the window is requested`() = runTest {
        val session = ExternalSessionStart(gameId, "org.citra.emu", null, null, LaunchOrigin.INTERNAL)
        useCaseReturns(LaunchResult.Success(intent, session = session))
        val tracker = tracker()
        val router = router()

        delegate(tracker, router).launchSimple(this, gameId, callbacks = callbacks())
        runCurrent()

        coVerify(exactly = 1) {
            launchGameUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), true)
        }
        verify(exactly = 0) { playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any()) }

        advanceTimeBy(holdMs)
        runCurrent()

        verifyOrder {
            manager.startGame(GameLaunchRequest(intent, options))
            playSessionTracker.startSession(gameId, "org.citra.emu", null, false, true, false, null, LaunchOrigin.INTERNAL)
        }
        verify(exactly = 1) { playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a launch whose window fails to open clears at once and finishes its ticket once`() = runTest {
        val refusal = SecurityException("denied")
        every { manager.startGame(any()) } returns Result.failure(refusal)
        useCaseReturns(LaunchResult.Success(intent))
        val tracker = tracker()
        val router = router()
        var failure: Throwable? = null

        delegate(tracker, router).launchSimple(this, gameId, callbacks = callbacks(onFailure = { failure = it }))
        advanceTimeBy(holdMs)
        runCurrent()

        assertSame(refusal, failure)
        assertNull(tracker.progress.value)
        assertNull(tracker.current)
        verify(exactly = 1) { tracker.finish(any(), any()) }
        assertNotNull(tracker.begin(gameId, "Game"))
    }

    @Test
    fun `a picked memory card continues under its own ticket, placed before launching is published`() = runTest {
        val tracker = tracker()
        val router = router()
        var stepWhenPlaced: LaunchStep? = LaunchStep.Launching
        coEvery { resolver.launchOptionsFor(gameId, any(), any()) } answers {
            stepWhenPlaced = tracker.progress.value?.step
            options
        }
        coEvery {
            launchGameUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            LaunchResult.SelectMemcard(gameId, "duckstation", "PlayStation", emptyList()),
            LaunchResult.Success(intent)
        )
        val delegate = delegate(tracker, router)

        delegate.launchSimple(this, gameId, callbacks = callbacks())
        runCurrent()
        assertNotNull(delegate.memcardPickerState.value)

        delegate.selectMemcard(this, "/cards/card1.mcd")
        runCurrent()

        assertNull(stepWhenPlaced)
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        coVerify(exactly = 2) { tracker.begin(gameId, "Game") }
        verify(exactly = 0) { manager.startGame(any()) }

        advanceTimeBy(holdMs)
        runCurrent()

        verify(exactly = 1) { manager.startGame(GameLaunchRequest(intent, options)) }
        verify(exactly = 1) { tracker.finish(any(), true) }
        assertNotNull(tracker.begin(gameId, "Game"))
    }

    @Test
    fun `a launch the use case refuses finishes its ticket once as not launched`() = runTest {
        useCaseReturns(LaunchResult.NoRomFile(null))
        val tracker = tracker()
        val router = router()
        var failedCalls = 0

        delegate(tracker, router).launchSimple(
            this,
            gameId,
            callbacks = callbacks(onLaunchFailed = { failedCalls++ })
        )
        advanceTimeBy(holdMs)
        runCurrent()

        assertEquals(1, failedCalls)
        assertNull(tracker.progress.value)
        assertNull(tracker.current)
        verify(exactly = 1) { tracker.finish(any(), false) }
        verify(exactly = 0) { tracker.finish(any(), true) }
        verify(exactly = 0) { manager.startGame(any()) }
    }

    private fun TestScope.launchGame(delegate: GameLaunchDelegate, onLaunchFailed: () -> Unit = {}) =
        delegate.launchGame(this, gameId, start = GameStart(gameId), onLaunchFailed = onLaunchFailed)

    private fun TestScope.recordSteps(tracker: LaunchProgressTracker): List<LaunchProgress?> {
        val values = mutableListOf<LaunchProgress?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { tracker.progress.toList(values) }
        return values
    }

    private val sessionEndWaitStep = LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(null))

    @Test
    fun `a launch pressed while a session end runs shows the save step and continues when it ends`() = runTest {
        coEvery { preLaunchRecapStep.run(any(), any(), any()) } returns false
        sessionEndRunning.value = true
        val tracker = tracker()
        var failedCalls = 0

        launchGame(delegate(tracker, router()), onLaunchFailed = { failedCalls++ })
        runCurrent()

        assertEquals(sessionEndWaitStep, tracker.progress.value?.step)
        coVerify(exactly = 0) { preLaunchRecapStep.run(any(), any(), any()) }

        sessionEndRunning.value = false
        runCurrent()

        coVerify(exactly = 1) { preLaunchRecapStep.run(any(), gameId, "Game") }
        assertEquals(1, failedCalls)
    }

    @Test
    fun `a launch with no session end running publishes no wait step`() = runTest {
        coEvery { preLaunchRecapStep.run(any(), any(), any()) } returns false
        val tracker = tracker()
        val steps = recordSteps(tracker)

        launchGame(delegate(tracker, router()))
        runCurrent()

        assertTrue(steps.none { it?.step == sessionEndWaitStep })
        coVerify(exactly = 1) { preLaunchRecapStep.run(any(), gameId, "Game") }
    }

    @Test
    fun `cancel during the session-end wait stops the launch before the recap`() = runTest {
        sessionEndRunning.value = true
        val tracker = tracker()
        var failedCalls = 0

        launchGame(delegate(tracker, router()), onLaunchFailed = { failedCalls++ })
        runCurrent()
        assertTrue(tracker.cancel())
        runCurrent()

        assertEquals(1, failedCalls)
        coVerify(exactly = 0) { preLaunchRecapStep.run(any(), any(), any()) }
        assertNull(tracker.progress.value)
        assertTrue(sessionEndRunning.value)
        assertNotNull(tracker.begin(gameId, "Game"))
    }

    @Test
    fun `a running session end makes the delegate busy, which GameDetail Play refuses on`() = runTest {
        val delegate = delegate(tracker(), router())
        assertFalse(delegate.isSessionEndBusy)
        assertFalse(delegate.sessionEndRunning.value)

        sessionEndRunning.value = true

        assertTrue(delegate.isSessionEndBusy)
        assertTrue(delegate.sessionEndRunning.value)
        assertFalse(delegate.isSyncing)
    }

    @Test
    fun `a blocked prompt makes the delegate busy with no session end running`() = runTest {
        val delegate = delegate(tracker(), router())

        syncOverlayState.value = SyncOverlayState("Game", SyncProgress.BlockedReason.PermissionRequired())

        assertTrue(delegate.isSessionEndBusy)
        assertFalse(delegate.sessionEndRunning.value)
    }

    @Test
    fun `a blocked prompt still refuses the launch`() = runTest {
        syncOverlayState.value = SyncOverlayState("Game", SyncProgress.BlockedReason.PermissionRequired())
        val tracker = tracker()
        var failedCalls = 0

        launchGame(delegate(tracker, router()), onLaunchFailed = { failedCalls++ })
        runCurrent()

        assertEquals(1, failedCalls)
        coVerify(exactly = 0) { tracker.begin(any(), any()) }
        coVerify(exactly = 0) { preLaunchRecapStep.run(any(), any(), any()) }
    }

    @Test
    fun `a session end that raises a blocked prompt refuses the launch waiting on it`() = runTest {
        sessionEndRunning.value = true
        val tracker = tracker()
        var failedCalls = 0

        launchGame(delegate(tracker, router()), onLaunchFailed = { failedCalls++ })
        runCurrent()
        syncOverlayState.value = SyncOverlayState("Game", SyncProgress.BlockedReason.AccessDenied())
        sessionEndRunning.value = false
        runCurrent()

        assertEquals(1, failedCalls)
        coVerify(exactly = 0) { preLaunchRecapStep.run(any(), any(), any()) }
        assertNull(tracker.progress.value)
        verify(exactly = 1) { tracker.finish(any(), false) }
    }
}
