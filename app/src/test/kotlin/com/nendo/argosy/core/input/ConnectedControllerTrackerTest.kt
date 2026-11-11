package com.nendo.argosy.core.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConnectedControllerTrackerTest {

    private val xboxPad = pad(deviceId = 11, descriptor = "xbox")
    private val nintendoPad = pad(deviceId = 12, descriptor = "nintendo")
    private val context: Context = mockk {
        every { getSystemService(Context.INPUT_SERVICE) } returns mockk<InputManager>(relaxed = true)
    }

    @Before
    fun setUp() {
        mockkStatic(InputDevice::class)
        mockkObject(ControllerDetector)
        every { ControllerDetector.detectFromDevice(xboxPad) } returns detected(xboxPad, DetectedLayout.XBOX)
        every { ControllerDetector.detectFromDevice(nintendoPad) } returns detected(nintendoPad, DetectedLayout.NINTENDO)
        every { ControllerDetector.isBuiltInPad(any()) } returns false
        connected(xboxPad, nintendoPad)
    }

    @After
    fun tearDown() {
        unmockkObject(ControllerDetector)
        unmockkStatic(InputDevice::class)
    }

    @Test
    fun `glyph layout follows the pad that last sent input`() = runBlocking {
        val tracker = ConnectedControllerTracker(context)
        assertEquals(DetectedLayout.XBOX, tracker.activeLayout.value)

        tracker.noteInput(keyDown(nintendoPad))
        awaitLayout(tracker, DetectedLayout.NINTENDO)
        assertEquals("Pad nintendo", tracker.detectActive().deviceName)

        tracker.noteInput(keyDown(xboxPad))
        awaitLayout(tracker, DetectedLayout.XBOX)
    }

    @Test
    fun `disconnecting the active pad falls back to the remaining pad and its layout`() = runBlocking {
        val tracker = ConnectedControllerTracker(context)
        tracker.noteInput(keyDown(nintendoPad))
        awaitLayout(tracker, DetectedLayout.NINTENDO)

        connected(xboxPad)
        tracker.refresh()

        assertEquals(xboxPad.id, tracker.activeController.value?.deviceId)
        awaitLayout(tracker, DetectedLayout.XBOX)
    }

    @Test
    fun `a pad removed after the first read is reported with its name and present pads are not`() = runBlocking {
        val tracker = ConnectedControllerTracker(context)
        val events = mutableListOf<ControllerEvent>()
        val collector = launch(Dispatchers.Unconfined) { tracker.controllerEvents.toList(events) }

        connected(xboxPad)
        tracker.refresh()
        collector.cancel()

        assertEquals(
            listOf(
                ControllerEvent(
                    ControllerChange.DISCONNECTED,
                    ConnectedPad(controllerIdOf(nintendoPad), "Pad nintendo", builtIn = false)
                )
            ),
            events
        )
        assertEquals(setOf(controllerIdOf(xboxPad)), tracker.connectedControllerIds.value)
    }

    private suspend fun awaitLayout(tracker: ConnectedControllerTracker, layout: DetectedLayout) {
        withTimeout(5_000) { tracker.activeLayout.first { it == layout } }
    }

    private fun connected(vararg devices: InputDevice) {
        val byDeviceId = devices.associateBy { it.id }
        every { InputDevice.getDeviceIds() } returns byDeviceId.keys.toIntArray()
        every { InputDevice.getDevice(any()) } answers { byDeviceId[firstArg<Int>()] }
    }

    private fun detected(device: InputDevice, layout: DetectedLayout) =
        DetectionResult(layout, device.name, device.vendorId, DetectionSource.VENDOR_ID)

    private fun pad(deviceId: Int, descriptor: String): InputDevice = mockk {
        every { id } returns deviceId
        every { sources } returns InputDevice.SOURCE_GAMEPAD
        every { isVirtual } returns false
        every { name } returns "Pad $descriptor"
        every { vendorId } returns deviceId
        every { productId } returns 2
        every { this@mockk.descriptor } returns descriptor
    }

    private fun keyDown(device: InputDevice): KeyEvent = mockk {
        every { action } returns KeyEvent.ACTION_DOWN
        every { deviceId } returns device.id
        every { this@mockk.device } returns device
    }
}
