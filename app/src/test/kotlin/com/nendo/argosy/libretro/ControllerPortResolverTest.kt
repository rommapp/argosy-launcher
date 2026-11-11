package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ControllerPortResolverTest {

    private lateinit var builtIn: InputDevice
    private lateinit var external: InputDevice
    private lateinit var secondExternal: InputDevice

    private val resolver = ControllerPortResolver()
    private val claims = mutableListOf<Pair<String, Int>>()

    @Before
    fun setUp() {
        mockkStatic(InputDevice::class)
        mockkObject(ControllerDetector)
        every { ControllerDetector.isBuiltInPad(any()) } answers { !firstArg<InputDevice>().isExternal }
        builtIn = pad(deviceId = 1, external = false, label = "builtin")
        external = pad(deviceId = 2, external = true, label = "bluetooth")
        secondExternal = pad(deviceId = 3, external = true, label = "usb")
        resolver.addPortClaimedListener { controllerId, port -> claims += controllerId to port }
    }

    @After
    fun tearDown() {
        unmockkObject(ControllerDetector)
        unmockkStatic(InputDevice::class)
    }

    @Test
    fun `built-in pad leaves player one for an unseated external pad`() {
        connected(builtIn, external)

        assertEquals(1, resolver.getPort(builtIn))
        assertEquals(0, resolver.getPort(external))
        assertEquals(1, resolver.getPort(builtIn))
        assertEquals(id(external), resolver.playerOneControllerId())
        assertEquals(listOf(id(builtIn) to 1, id(external) to 0), claims)
    }

    @Test
    fun `built-in pad takes player one when no external pad is connected`() {
        connected(builtIn)

        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(id(builtIn), resolver.playerOneControllerId())
    }

    @Test
    fun `a lone built-in pad takes player one when the saved player one is not connected`() {
        connected(builtIn)
        resolver.setControllerOrder(listOf(order(external, 0), order(builtIn, 1)))

        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(id(builtIn), resolver.playerOneControllerId())
    }

    @Test
    fun `the saved player one gets its seat back when it connects`() {
        connected(builtIn)
        resolver.setControllerOrder(listOf(order(external, 0), order(builtIn, 1)))
        assertEquals(0, resolver.getPort(builtIn))

        connected(builtIn, external)

        assertEquals(0, resolver.getPort(external))
        assertEquals(1, resolver.getPort(builtIn))
    }

    @Test
    fun `external pads seat on first input from zero`() {
        connected(builtIn, external, secondExternal)

        assertEquals(0, resolver.getPort(external))
        assertEquals(1, resolver.getPort(secondExternal))
        assertEquals(2, resolver.getPort(builtIn))
    }

    @Test
    fun `built-in pad reclaims player one after the external pad disconnects`() {
        connected(builtIn, external)
        assertEquals(1, resolver.getPort(builtIn))
        assertEquals(0, resolver.getPort(external))

        connected(builtIn)
        resolver.releaseDisconnected(setOf(id(builtIn)))

        assertNull(resolver.playerOneControllerId())
        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(id(builtIn), resolver.playerOneControllerId())
        assertEquals(id(builtIn) to 0, claims.last())
    }

    @Test
    fun `built-in pad keeps its seat while another external pad remains`() {
        connected(builtIn, external, secondExternal)
        assertEquals(1, resolver.getPort(builtIn))
        assertEquals(0, resolver.getPort(external))
        assertEquals(2, resolver.getPort(secondExternal))

        connected(builtIn, secondExternal)
        resolver.releaseDisconnected(setOf(id(builtIn), id(secondExternal)))

        assertNull(resolver.playerOneControllerId())
        assertEquals(1, resolver.getPort(builtIn))
        assertEquals(2, resolver.getPort(secondExternal))
    }

    @Test
    fun `settings order seats the built-in pad on player one over a connected external pad`() {
        connected(builtIn, external)
        resolver.setControllerOrder(listOf(order(builtIn, 0)))

        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(1, resolver.getPort(external))
    }

    @Test
    fun `settings order on the external pad counts it as seated`() {
        connected(builtIn, external)
        resolver.setControllerOrder(listOf(order(external, 1)))

        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(1, resolver.getPort(external))
    }

    @Test
    fun `settings order survives a release with no external pad connected`() {
        connected(builtIn)
        resolver.setControllerOrder(listOf(order(builtIn, 1)))

        resolver.releaseDisconnected(setOf(id(builtIn)))

        assertEquals(1, resolver.getPort(builtIn))
    }

    @Test
    fun `docked launch pad takes player one from a saved built-in pad`() {
        connected(builtIn, external)
        resolver.setLaunchPad(id(external), isDocked = true)
        resolver.setControllerOrder(listOf(order(builtIn, 0)))

        assertEquals(0, resolver.getPort(external))
        assertEquals(1, resolver.getPort(builtIn))
        assertEquals(id(external), resolver.playerOneControllerId())
    }

    @Test
    fun `docked launch pad takes player one from a built-in pad that claimed it`() {
        connected(builtIn, external)
        resolver.setControllerOrder(listOf(order(external, 1)))
        resolver.setLaunchPad(id(external), isDocked = true)

        assertEquals(1, resolver.getPort(builtIn))
        assertEquals(0, resolver.getPort(external))
        assertEquals(id(external), resolver.playerOneControllerId())
        assertEquals(listOf(id(builtIn) to 1), claims)
    }

    @Test
    fun `docked launch pad moves the built-in pad past ports already taken`() {
        connected(builtIn, external, secondExternal)
        resolver.setControllerOrder(listOf(order(builtIn, 0), order(secondExternal, 1)))
        resolver.setLaunchPad(id(external), isDocked = true)

        assertEquals(0, resolver.getPort(external))
        assertEquals(1, resolver.getPort(secondExternal))
        assertEquals(2, resolver.getPort(builtIn))
    }

    @Test
    fun `undocked launch pad leaves the saved built-in pad on player one`() {
        connected(builtIn, external)
        resolver.setLaunchPad(id(external), isDocked = false)
        resolver.setControllerOrder(listOf(order(builtIn, 0)))

        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(1, resolver.getPort(external))
        assertEquals(id(builtIn), resolver.playerOneControllerId())
    }

    @Test
    fun `docked launch from the built-in pad changes nothing`() {
        connected(builtIn, external)
        resolver.setLaunchPad(id(builtIn), isDocked = true)
        resolver.setControllerOrder(listOf(order(builtIn, 0)))

        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(1, resolver.getPort(external))
    }

    @Test
    fun `docked launch pad already on player one keeps every seat`() {
        connected(builtIn, external)
        resolver.setLaunchPad(id(external), isDocked = true)
        resolver.setControllerOrder(listOf(order(external, 0), order(builtIn, 1)))

        assertEquals(0, resolver.getPort(external))
        assertEquals(1, resolver.getPort(builtIn))
    }

    @Test
    fun `docked launch pad seat leaves the saved order intact`() {
        connected(builtIn, external)
        resolver.setControllerOrder(listOf(order(builtIn, 0)))
        resolver.setLaunchPad(id(external), isDocked = true)
        assertEquals(0, resolver.getPort(external))

        resolver.setLaunchPad(id(external), isDocked = false)

        assertEquals(0, resolver.getPort(builtIn))
        assertEquals(1, resolver.getPort(external))
        assertTrue(claims.none { it.first == id(builtIn) })
    }

    @Test
    fun `every listener hears a claim and a removed one does not`() {
        connected(external)
        val second = mutableListOf<Pair<String, Int>>()
        val listener: (String, Int) -> Unit = { controllerId, port -> second += controllerId to port }
        resolver.addPortClaimedListener(listener)

        resolver.getPort(external)
        resolver.removePortClaimedListener(listener)
        connected(external, secondExternal)
        resolver.getPort(secondExternal)

        assertEquals(listOf(id(external) to 0, id(secondExternal) to 1), claims)
        assertEquals(listOf(id(external) to 0), second)
    }

    @Test
    fun `release reports the seats the departed pads held`() {
        connected(external, secondExternal)
        resolver.getPort(external)
        resolver.getPort(secondExternal)

        connected(external)
        val released = resolver.releaseDisconnected(setOf(id(external)))

        assertEquals(mapOf(id(secondExternal) to 1), released)
        assertNull(resolver.seatOf(id(secondExternal)))
    }

    @Test
    fun `a taken seat outranks the saved pad for it until the taker leaves`() {
        connected(secondExternal)
        resolver.setControllerOrder(listOf(order(external, 0)))

        resolver.takeSeat(id(secondExternal), 0)

        assertEquals(0, resolver.seatOf(id(secondExternal)))
        assertNull(resolver.seatOf(id(external)))
        assertEquals(id(secondExternal), resolver.playerOneControllerId())
        assertEquals(id(secondExternal) to 0, claims.last())

        connected(external)
        resolver.releaseDisconnected(setOf(id(external)))

        assertEquals(0, resolver.seatOf(id(external)))
    }

    private fun connected(vararg devices: InputDevice) {
        val byDeviceId = devices.associateBy { it.id }
        every { InputDevice.getDeviceIds() } returns byDeviceId.keys.toIntArray()
        every { InputDevice.getDevice(any()) } answers { byDeviceId[firstArg<Int>()] }
    }

    private fun id(device: InputDevice): String = controllerIdOf(device)

    private fun order(device: InputDevice, port: Int) = ControllerOrderEntity(
        port = port,
        controllerId = id(device),
        controllerName = device.name
    )

    private fun pad(deviceId: Int, external: Boolean, label: String): InputDevice = mockk {
        every { id } returns deviceId
        every { isExternal } returns external
        every { sources } returns InputDevice.SOURCE_GAMEPAD
        every { isVirtual } returns false
        every { name } returns label
        every { vendorId } returns 1
        every { productId } returns 2
        every { descriptor } returns label
    }
}
