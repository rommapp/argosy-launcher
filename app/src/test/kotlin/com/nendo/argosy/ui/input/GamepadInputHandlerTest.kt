package com.nendo.argosy.ui.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.SystemHotkeyDetector
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GamepadInputHandlerTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private val delivered = mutableListOf<Pair<GamepadEvent, Boolean>>()
    private val emitted = mutableListOf<GamepadInput>()
    private val collectorScope = CoroutineScope(testDispatcher)
    private val removedDevices = mutableSetOf<Int>()
    private val tracker = mockk<ConnectedControllerTracker>(relaxed = true) {
        every { wasRemoved(any()) } answers { firstArg<Int>() in removedDevices }
    }
    private lateinit var handler: GamepadInputHandler

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.preferences } returns emptyFlow()
        handler = GamepadInputHandler(preferences, tracker)
    }

    @After
    fun tearDown() {
        handler.resetStickMotion()
        collectorScope.cancel()
        Dispatchers.resetMain()
    }

    private fun sample(
        x: Float = 0f,
        y: Float = 0f,
        hatX: Float = 0f,
        hatY: Float = 0f,
        z: Float = 0f,
        rz: Float = 0f,
        deviceId: Int = PAD_A
    ): MotionEvent {
        val axes = mapOf(
            MotionEvent.AXIS_X to x,
            MotionEvent.AXIS_Y to y,
            MotionEvent.AXIS_HAT_X to hatX,
            MotionEvent.AXIS_HAT_Y to hatY,
            MotionEvent.AXIS_Z to z,
            MotionEvent.AXIS_RZ to rz
        )
        val event = mockk<MotionEvent>()
        every { event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK) } returns true
        every { event.source } returns InputDevice.SOURCE_JOYSTICK
        every { event.device } returns null
        every { event.deviceId } returns deviceId
        every { event.getAxisValue(any()) } answers { axes[firstArg()] ?: 0f }
        return event
    }

    private fun process(event: MotionEvent): Boolean =
        handler.processStickMotion(event, steerRightStick = true) { direction, isRepeat ->
            delivered += direction to isRepeat
        }

    private fun collectEmitted() {
        collectorScope.launch { handler.eventFlow().collect { emitted += it } }
        scheduler.runCurrent()
    }

    @Test
    fun `a right stick deflection reaches the right stick stepper while the left stick tracks`() {
        collectEmitted()

        assertTrue(process(sample(x = 1f, rz = 1f)))
        scheduler.runCurrent()

        assertEquals(listOf(GamepadEvent.Right to false), delivered)
        assertEquals(listOf(GamepadEvent.RightStickDown), emitted.map { it.event })
    }

    @Test
    fun `a horizontal right stick deflection steps without moving left stick focus`() {
        collectEmitted()

        assertFalse(process(sample(z = -1f)))
        scheduler.runCurrent()

        assertTrue(delivered.isEmpty())
        assertEquals(listOf(GamepadEvent.RightStickLeft), emitted.map { it.event })
    }

    @Test
    fun `an idle sample from a second pad does not cancel the first pad's held direction`() {
        process(sample(x = 1f, deviceId = PAD_A))
        process(sample(deviceId = PAD_B))
        scheduler.advanceTimeBy(401)

        assertEquals(listOf(GamepadEvent.Right to false, GamepadEvent.Right to true), delivered)
    }

    @Test
    fun `the owning pad returning to neutral still ends the held direction`() {
        process(sample(x = 1f, deviceId = PAD_A))
        process(sample(deviceId = PAD_B))
        process(sample(deviceId = PAD_A))
        scheduler.advanceTimeBy(1000)

        assertEquals(listOf(GamepadEvent.Right to false), delivered)
    }

    @Test
    fun `a hat press under a held stick delivers no hat edge`() {
        process(sample(x = 1f))
        process(sample(x = 1f, hatY = 1f))

        assertEquals(listOf(GamepadEvent.Right to false), delivered)
    }

    @Test
    fun `a hat press with the stick at rest is left unconsumed for DPAD key synthesis`() {
        assertFalse(process(sample(hatX = 1f)))
        scheduler.advanceTimeBy(1000)

        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `a raw motion listener sees a stick press before the tracker and its decline leaves one edge`() {
        val seen = mutableListOf<MotionEvent>()
        handler.setRawMotionEventListener { seen += it; false }

        val stickPress = sample(x = 1f)
        assertTrue(process(stickPress))

        assertEquals(listOf(stickPress), seen)
        assertEquals(listOf(GamepadEvent.Right to false), delivered)
    }

    @Test
    fun `a sample the raw motion listener consumes leaves no edge and no repeat`() {
        val seen = mutableListOf<MotionEvent>()
        handler.setRawMotionEventListener { seen += it; true }

        val hatPress = sample(hatX = 1f)
        assertTrue(process(hatPress))
        scheduler.advanceTimeBy(1000)

        assertEquals(listOf(hatPress), seen)
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `a consumed sample cancels the repeat of a direction still held`() {
        var consuming = false
        handler.setRawMotionEventListener { consuming }
        process(sample(x = 1f))
        scheduler.advanceTimeBy(401)
        assertEquals(listOf(GamepadEvent.Right to false, GamepadEvent.Right to true), delivered)

        consuming = true
        process(sample(x = 1f))
        scheduler.advanceTimeBy(1000)

        assertEquals(2, delivered.size)
    }

    @Test
    fun `registering a raw motion listener stops a running repeat`() {
        process(sample(x = 1f))
        handler.setRawMotionEventListener { true }
        scheduler.advanceTimeBy(1000)

        assertEquals(listOf(GamepadEvent.Right to false), delivered)
    }

    @Test
    fun `no tick is delivered after resetStickMotion`() {
        process(sample(y = -1f))
        scheduler.advanceTimeBy(401)
        assertEquals(listOf(GamepadEvent.Up to false, GamepadEvent.Up to true), delivered)

        handler.resetStickMotion()
        scheduler.advanceTimeBy(1000)

        assertEquals(2, delivered.size)
    }

    @Test
    fun `the stick outranks the hat while both are held`() {
        process(sample(x = 1f, hatY = 1f))
        scheduler.advanceTimeBy(401)

        assertEquals(listOf(GamepadEvent.Right to false, GamepadEvent.Right to true), delivered)
    }

    @Test
    fun `a stick released over a held hat hands the hat back to DPAD key synthesis`() {
        assertTrue(process(sample(x = 1f, hatY = 1f)))
        assertFalse(process(sample(hatY = 1f)))
        scheduler.advanceTimeBy(1000)

        assertEquals(listOf(GamepadEvent.Right to false), delivered)
    }

    @Test
    fun `releasing stick and hat together delivers nothing`() {
        process(sample(x = 1f, hatY = 1f))
        process(sample())
        scheduler.advanceTimeBy(1000)

        assertEquals(listOf(GamepadEvent.Right to false), delivered)
    }

    @Test
    fun `a single stick press and hold delivers exactly one first press`() {
        process(sample(y = 1f))
        process(sample(y = 0.9f))
        process(sample(y = 0.4f))
        scheduler.advanceTimeBy(401)

        assertEquals(listOf(GamepadEvent.Down to false, GamepadEvent.Down to true), delivered)
    }

    @Test
    fun `a held direction stops repeating once its pad is removed`() {
        process(sample(x = 1f, deviceId = PAD_A))
        scheduler.advanceTimeBy(401)
        assertEquals(listOf(GamepadEvent.Right to false, GamepadEvent.Right to true), delivered)

        removedDevices += PAD_A
        scheduler.advanceTimeBy(1000)

        assertEquals(2, delivered.size)
    }

    @Test
    fun `another pad's removal leaves a held direction repeating`() {
        process(sample(x = 1f, deviceId = PAD_A))
        removedDevices += PAD_B
        scheduler.advanceTimeBy(401)

        assertEquals(listOf(GamepadEvent.Right to false, GamepadEvent.Right to true), delivered)
    }

    @Test
    fun `a late sample from a removed pad is swallowed without a direction`() {
        removedDevices += PAD_A

        assertTrue(process(sample(x = 1f, deviceId = PAD_A)))
        scheduler.advanceTimeBy(1000)

        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `a key repeat from a removed pad is swallowed`() {
        collectEmitted()
        removedDevices += PAD_A
        val repeat = mockk<KeyEvent> {
            every { deviceId } returns PAD_A
        }

        assertTrue(handler.handleKeyEvent(repeat))
        scheduler.runCurrent()

        assertTrue(emitted.isEmpty())
    }

    private fun keyDown(code: Int): KeyEvent = mockk(relaxed = true) {
        every { keyCode } returns code
        every { action } returns KeyEvent.ACTION_DOWN
        every { repeatCount } returns 0
        every { deviceId } returns PAD_A
        every { device } returns null
    }

    @Test
    fun `a d-pad key a raw key filter declines still reaches navigation`() {
        collectEmitted()
        handler.setRawKeyEventFilter { it.keyCode == KeyEvent.KEYCODE_BUTTON_A }

        assertTrue(handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_DPAD_RIGHT)))
        scheduler.runCurrent()

        assertEquals(listOf(GamepadEvent.Right), emitted.map { it.event })
    }

    @Test
    fun `a key a raw key filter consumes is never dispatched`() {
        collectEmitted()
        handler.setRawKeyEventFilter { it.keyCode == KeyEvent.KEYCODE_BUTTON_B }

        assertTrue(handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_BUTTON_B)))
        scheduler.advanceTimeBy(1000)

        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `a raw key listener's decline still goes back to the platform unmapped`() {
        collectEmitted()
        handler.setRawKeyEventListener { false }

        assertFalse(handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_DPAD_RIGHT)))
        scheduler.runCurrent()

        assertTrue(emitted.isEmpty())
    }

    private fun keyUp(code: Int): KeyEvent = mockk(relaxed = true) {
        every { keyCode } returns code
        every { action } returns KeyEvent.ACTION_UP
        every { repeatCount } returns 0
        every { deviceId } returns PAD_A
        every { device } returns null
    }

    private fun collectHomePresses(): MutableList<HomePress> {
        val presses = mutableListOf<HomePress>()
        collectorScope.launch { handler.homeEventFlow().collect { presses += it } }
        scheduler.runCurrent()
        return presses
    }

    @Test
    fun `a Home key released before the hold time is a tap`() {
        val presses = collectHomePresses()

        assertTrue(handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_MOVE_HOME)))
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS - 1)
        assertTrue(handler.handleKeyEvent(keyUp(KeyEvent.KEYCODE_MOVE_HOME)))
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS)

        assertEquals(listOf(HomePress.TAP), presses)
    }

    @Test
    fun `a Home key still down at the hold time is a hold and its release sends nothing`() {
        val presses = collectHomePresses()

        handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_MOVE_HOME))
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS + 1)
        handler.handleKeyEvent(keyUp(KeyEvent.KEYCODE_MOVE_HOME))
        scheduler.runCurrent()

        assertEquals(listOf(HomePress.HOLD), presses)
    }

    @Test
    fun `the guide button and the system Home key share the tap and hold rule`() {
        val presses = collectHomePresses()

        handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_BUTTON_MODE))
        handler.handleKeyEvent(keyUp(KeyEvent.KEYCODE_BUTTON_MODE))
        handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_HOME))
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS + 1)
        handler.handleKeyEvent(keyUp(KeyEvent.KEYCODE_HOME))
        scheduler.runCurrent()

        assertEquals(listOf(HomePress.TAP, HomePress.HOLD), presses)
    }

    @Test
    fun `a Home key repeat does not restart the hold`() {
        val presses = collectHomePresses()
        val repeat = mockk<KeyEvent>(relaxed = true) {
            every { keyCode } returns KeyEvent.KEYCODE_MOVE_HOME
            every { action } returns KeyEvent.ACTION_DOWN
            every { repeatCount } returns 1
            every { deviceId } returns PAD_A
            every { device } returns null
        }

        handler.handleHomeKey(keyDown(KeyEvent.KEYCODE_MOVE_HOME))
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS / 2)
        handler.handleHomeKey(repeat)
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS / 2 + 1)

        assertEquals(listOf(HomePress.HOLD), presses)
    }

    @Test
    fun `a Home release without its press sends nothing`() {
        val presses = collectHomePresses()

        handler.handleHomeKey(keyUp(KeyEvent.KEYCODE_MOVE_HOME))
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS * 2)

        assertTrue(presses.isEmpty())
    }

    @Test
    fun `a Home key never reaches the gamepad event flow`() {
        collectEmitted()

        handler.handleKeyEvent(keyDown(KeyEvent.KEYCODE_HOME))
        handler.handleKeyEvent(keyUp(KeyEvent.KEYCODE_HOME))
        scheduler.advanceTimeBy(SystemHotkeyDetector.GUIDE_HOLD_MS * 2)

        assertTrue(emitted.isEmpty())
    }

    private companion object {
        const val PAD_A = 7
        const val PAD_B = 8
    }
}
