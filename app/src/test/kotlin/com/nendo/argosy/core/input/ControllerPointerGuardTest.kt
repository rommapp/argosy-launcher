package com.nendo.argosy.core.input

import android.view.MotionEvent
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.streaming.StreamSessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ControllerPointerGuardTest {

    private val live = MutableStateFlow(true)
    private val touchableCalls = mutableListOf<Boolean>()
    private val controllerPointer = mockk<MotionEvent>()
    private val otherEvent = mockk<MotionEvent>()

    private fun TestScope.guard() = ControllerPointerGuard(
        windowName = "launcher",
        scope = backgroundScope,
        setWindowTouchable = { touchableCalls += it },
        isControllerPointer = { it === controllerPointer }
    ).also {
        it.follow(live)
        runCurrent()
    }

    private fun TestScope.setLive(value: Boolean) {
        live.value = value
        runCurrent()
    }

    @Test
    fun `a controller pointer during a live session over a window not in front lets pointers pass through`() = runTest {
        val guard = guard()

        assertTrue(guard.consumes(controllerPointer))

        assertEquals(listOf(false), touchableCalls)
    }

    @Test
    fun `the window takes pointer input again once the pass-through time is over`() = runTest {
        val guard = guard()
        guard.consumes(controllerPointer)

        advanceTimeBy(ControllerPointerGuard.PASS_THROUGH_MS - 1)
        runCurrent()
        assertEquals(listOf(false), touchableCalls)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(false, true), touchableCalls)
    }

    @Test
    fun `a controller pointer after the pass-through time starts it again`() = runTest {
        val guard = guard()
        guard.consumes(controllerPointer)
        advanceTimeBy(ControllerPointerGuard.PASS_THROUGH_MS)
        runCurrent()

        guard.consumes(controllerPointer)

        assertEquals(listOf(false, true, false), touchableCalls)
    }

    @Test
    fun `a controller pointer over the window in front is consumed but never passes through`() = runTest {
        val guard = guard()
        guard.onInFrontChanged(true)

        assertTrue(guard.consumes(controllerPointer))

        assertTrue(touchableCalls.isEmpty())
    }

    @Test
    fun `other events and events outside a live session pass through untouched`() = runTest {
        val guard = guard()

        assertFalse(guard.consumes(otherEvent))
        setLive(false)
        assertFalse(guard.consumes(controllerPointer))

        assertTrue(touchableCalls.isEmpty())
    }

    @Test
    fun `coming to the front makes the window touchable at once and cancels the timer`() = runTest {
        val guard = guard()
        guard.consumes(controllerPointer)

        guard.onInFrontChanged(true)
        advanceTimeBy(ControllerPointerGuard.PASS_THROUGH_MS * 2)
        runCurrent()

        assertEquals(listOf(false, true), touchableCalls)
    }

    @Test
    fun `release restores touch once and repeated pointers do not re-apply the flag`() = runTest {
        val guard = guard()
        guard.consumes(controllerPointer)
        guard.consumes(controllerPointer)

        guard.release()
        guard.release()

        assertEquals(listOf(false, true), touchableCalls)
    }

    @Test
    fun `turning guarding off ends a pass-through and stops starting new ones`() = runTest {
        val guard = guard()
        guard.consumes(controllerPointer)

        setLive(false)

        assertEquals(listOf(false, true), touchableCalls)
        assertFalse(guard.consumes(controllerPointer))
        assertEquals(listOf(false, true), touchableCalls)
    }

    @Test
    fun `guarding follows the session and the touchpad setting together`() = runTest {
        val session = MutableStateFlow<ActiveSession?>(null)
        val prefs = MutableStateFlow(UserPreferences(ignoreControllerTouchpad = true))
        val tracker = mockk<PlaySessionTracker> { every { activeSession } returns session }
        val repository = mockk<UserPreferencesRepository> { every { userPreferences } returns prefs }
        val guarding = controllerTouchpadGuarding(tracker, repository)

        assertFalse(guarding.first())
        session.value = ActiveSession(gameId = 1L, startTime = Instant.EPOCH, emulatorPackage = "org.ppsspp.ppsspp")
        assertTrue(guarding.first())
        prefs.value = UserPreferences(ignoreControllerTouchpad = false)
        assertFalse(guarding.first())
    }

    @Test
    fun `a stream session does not guard the touchpad`() = runTest {
        val session = MutableStateFlow<ActiveSession?>(
            ActiveSession(gameId = 1L, startTime = Instant.EPOCH, emulatorPackage = StreamSessionManager.STREAM_PACKAGE)
        )
        val prefs = MutableStateFlow(UserPreferences(ignoreControllerTouchpad = true))
        val tracker = mockk<PlaySessionTracker> { every { activeSession } returns session }
        val repository = mockk<UserPreferencesRepository> { every { userPreferences } returns prefs }

        assertFalse(controllerTouchpadGuarding(tracker, repository).first())
    }

    @Test
    fun `leaving the front does not pass pointers through until a controller pointer arrives`() = runTest {
        val guard = guard()
        guard.onInFrontChanged(true)
        guard.onInFrontChanged(false)

        assertTrue(touchableCalls.isEmpty())
        assertTrue(guard.consumes(controllerPointer))
        assertEquals(listOf(false), touchableCalls)
    }
}
