package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SavePathValidator
import com.nendo.argosy.data.emulator.SessionEndResult
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.SyncProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEndCoordinatorTest {
    private val emulatorPackage = "com.example.testemulator"
    private val emulatorId = "test-emulator"
    private val session = ActiveSession(gameId = 7L, startTime = Instant.EPOCH, emulatorPackage = emulatorPackage)

    private val playSessionTracker: PlaySessionTracker = mockk(relaxed = true)
    private val emulatorResolver: EmulatorResolver = mockk(relaxed = true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val savePathValidator: SavePathValidator = mockk(relaxed = true)
    private val preferences = MutableStateFlow(UserPreferences(saveSyncEnabled = true))

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(SavePathRegistry)
        every { SavePathRegistry.canSyncWithSettings(emulatorId, true) } returns true
        every { SavePathRegistry.canSyncWithSettings(emulatorId, false) } returns false
        every { playSessionTracker.activeSession } returns MutableStateFlow(session)
        every { playSessionTracker.getSessionDuration() } returns Duration.ofMinutes(10)
        every { playSessionTracker.sawSaveActivity() } returns false
        coEvery { emulatorResolver.resolveEmulatorId(emulatorPackage) } returns emulatorId
        every { preferencesRepository.preferences } returns preferences
        coEvery { savePathValidator.validateAccess(emulatorId, emulatorPackage) } returns SavePathValidator.Result.Valid
        coEvery { playSessionTracker.endSession() } returns SessionEndResult.Success
    }

    @After
    fun tearDown() {
        unmockkObject(SavePathRegistry)
        Dispatchers.resetMain()
    }

    private fun coordinator() = SessionEndCoordinator(
        application = mockk(relaxed = true),
        playSessionTracker = playSessionTracker,
        emulatorResolver = emulatorResolver,
        preferencesRepository = preferencesRepository,
        gameRepository = mockk(relaxed = true) { coEvery { getById(any()) } returns null },
        savePathValidator = savePathValidator,
        gameLauncher = mockk(relaxed = true)
    )

    private fun TestScope.recordRunning(coordinator: SessionEndCoordinator): List<Boolean> {
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.sessionEndRunning.toList(values)
        }
        return values
    }

    private fun TestScope.recordOverlay(coordinator: SessionEndCoordinator): List<SyncOverlayState?> {
        val values = mutableListOf<SyncOverlayState?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.syncOverlayState.toList(values)
        }
        return values
    }

    @Test
    fun `a clean session end publishes no overlay`() = runTest {
        val coordinator = coordinator()
        val overlay = recordOverlay(coordinator)

        coordinator.endClosedSession().join()

        coVerify(exactly = 1) { playSessionTracker.endSession() }
        assertEquals(listOf<SyncOverlayState?>(null), overlay)
    }

    @Test
    fun `a failed session end publishes no overlay`() = runTest {
        coEvery { playSessionTracker.endSession() } returns SessionEndResult.Error("Failed to cache save")
        val coordinator = coordinator()
        val overlay = recordOverlay(coordinator)

        coordinator.endClosedSession().join()

        coVerify(exactly = 1) { playSessionTracker.endSession() }
        assertEquals(listOf<SyncOverlayState?>(null), overlay)
    }

    @Test
    fun `session end running covers exactly the synced session end`() = runTest {
        val coordinator = coordinator()
        val running = recordRunning(coordinator)
        var runningDuringEnd: Boolean? = null
        coEvery { playSessionTracker.endSession() } coAnswers {
            runningDuringEnd = coordinator.sessionEndRunning.value
            SessionEndResult.Success
        }

        coordinator.endClosedSession().join()

        assertEquals(true, runningDuringEnd)
        assertEquals(listOf(false, true, false), running)
    }

    @Test
    fun `a short session never reports a running session end`() = runTest {
        every { playSessionTracker.getSessionDuration() } returns Duration.ofSeconds(5)
        val coordinator = coordinator()
        val running = recordRunning(coordinator)

        coordinator.endClosedSession().join()

        verify(exactly = 1) { playSessionTracker.cancelSession() }
        coVerify(exactly = 0) { playSessionTracker.endSession() }
        assertEquals(listOf(false), running)
    }

    @Test
    fun `an unresolved emulator or sync turned off never reports a running session end`() = runTest {
        val coordinator = coordinator()
        val running = recordRunning(coordinator)

        coEvery { emulatorResolver.resolveEmulatorId(emulatorPackage) } returns null
        coordinator.endClosedSession().join()
        coEvery { emulatorResolver.resolveEmulatorId(emulatorPackage) } returns emulatorId
        preferences.value = UserPreferences(saveSyncEnabled = false)
        coordinator.endClosedSession().join()

        coVerify(exactly = 2) { playSessionTracker.endSession() }
        assertEquals(listOf(false), running)
    }

    @Test
    fun `running clears when the session end throws`() = runTest {
        coEvery { playSessionTracker.endSession() } throws IllegalStateException("capture failed")
        val coordinator = coordinator()
        val running = recordRunning(coordinator)

        coordinator.endClosedSession().join()

        assertEquals(listOf(false, true, false), running)
        assertNull(coordinator.syncOverlayState.value)
        verify(exactly = 1) { playSessionTracker.endSessionInBackground() }
    }

    @Test
    fun `permission required publishes the blocked prompt and ends the session only once answered`() = runTest {
        coEvery { savePathValidator.validateAccess(emulatorId, emulatorPackage) } returns
            SavePathValidator.Result.PermissionRequired
        val coordinator = coordinator()
        val running = recordRunning(coordinator)

        coordinator.endClosedSession().join()

        val prompt = coordinator.syncOverlayState.value
        assertNotNull(prompt)
        assertTrue(prompt!!.syncProgress is SyncProgress.BlockedReason.PermissionRequired)
        coVerify(exactly = 0) { playSessionTracker.endSession() }

        prompt.onSkip!!.invoke()
        advanceUntilIdle()

        assertNull(coordinator.syncOverlayState.value)
        coVerify(exactly = 1) { playSessionTracker.endSession() }
        assertEquals(listOf(false), running)
    }

    @Test
    fun `an unreadable save publishes access denied`() = runTest {
        val coordinator = coordinator()
        val running = recordRunning(coordinator)
        coEvery { playSessionTracker.endSession() } returns SessionEndResult.SaveUnreadable("/saves/test", emulatorId)

        coordinator.endClosedSession().join()

        val prompt = coordinator.syncOverlayState.value
        assertTrue(prompt?.syncProgress is SyncProgress.BlockedReason.AccessDenied)
        assertEquals("/saves/test", (prompt!!.syncProgress as SyncProgress.BlockedReason.AccessDenied).path)
        assertEquals(listOf(false, true, false), running)
        assertFalse(coordinator.sessionEndRunning.value)
    }
}
