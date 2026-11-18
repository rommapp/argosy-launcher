package com.nendo.argosy.core.input

import android.view.InputDevice
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ControllerProfilesTest {

    private val defaults = ControllerProfile(controllerLayout = "auto", swapAB = false)
    private val xboxLettered = ControllerProfile(controllerLayout = "xbox", swapAB = true)
    private val nintendoLettered = ControllerProfile(controllerLayout = "nintendo", swapXY = true)

    private val xboxPadId = "11720:12290:xbox-pad"
    private val nintendoPadId = "11720:12290:nintendo-pad"
    private val otherModelId = "1118:2835:other"

    @Before
    fun setUp() {
        mockkStatic(InputDevice::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(InputDevice::class)
    }

    @Test
    fun `an exact match wins over a same-model match`() {
        val profiles = ControllerProfiles(
            defaults = defaults,
            saved = linkedMapOf(xboxPadId to xboxLettered, nintendoPadId to nintendoLettered)
        )

        assertEquals(nintendoLettered, profiles.forController(nintendoPadId))
        assertEquals(nintendoPadId, profiles.savedKeyFor(nintendoPadId))
        assertEquals(xboxLettered, profiles.forController(xboxPadId))
    }

    @Test
    fun `an exact match wins whichever order the profiles were saved in`() {
        val profiles = ControllerProfiles(
            defaults = defaults,
            saved = linkedMapOf(nintendoPadId to nintendoLettered, xboxPadId to xboxLettered)
        )

        assertEquals(xboxLettered, profiles.forController(xboxPadId))
        assertEquals(nintendoLettered, profiles.forController(nintendoPadId))
    }

    @Test
    fun `a pad without its own profile takes one saved for the same vendor and product`() {
        val profiles = ControllerProfiles(defaults = defaults, saved = mapOf(xboxPadId to xboxLettered))

        assertEquals(xboxLettered, profiles.forController(nintendoPadId))
        assertEquals(xboxPadId, profiles.savedKeyFor(nintendoPadId))
    }

    @Test
    fun `a pad of another model never takes a saved profile`() {
        val profiles = ControllerProfiles(defaults = defaults, saved = mapOf(xboxPadId to xboxLettered))

        assertEquals(defaults, profiles.forController(otherModelId))
        assertNull(profiles.savedFor(otherModelId))
    }

    @Test
    fun `vendor and product both zero skip the model fallback`() {
        val profiles = ControllerProfiles(defaults = defaults, saved = mapOf("0:0:first" to xboxLettered))

        assertEquals(defaults, profiles.forController("0:0:second"))
        assertNull(profiles.savedKeyFor("0:0:second"))
        assertEquals(xboxLettered, profiles.forController("0:0:first"))
    }

    @Test
    fun `a vendor prefix is not mistaken for a longer vendor id`() {
        val profiles = ControllerProfiles(defaults = defaults, saved = mapOf("1:22:pad" to xboxLettered))

        assertEquals(defaults, profiles.forController("1:2:pad"))
        assertEquals(defaults, profiles.forController("11:22:pad"))
    }

    @Test
    fun `no saved profile and no active pad resolve to the defaults`() {
        val empty = ControllerProfiles(defaults = defaults)
        val saved = ControllerProfiles(defaults = defaults, saved = mapOf(xboxPadId to xboxLettered))

        assertEquals(defaults, empty.forController(xboxPadId))
        assertEquals(defaults, saved.forController(null))
        assertNull(saved.savedKeyFor(null))
    }

    @Test
    fun `a device id resolves through that device's controller id`() {
        val pad = mockk<InputDevice> {
            every { vendorId } returns 11720
            every { productId } returns 12290
            every { descriptor } returns "xbox-pad"
        }
        every { InputDevice.getDevice(5) } returns pad
        every { InputDevice.getDevice(6) } returns null
        val profiles = ControllerProfiles(defaults = defaults, saved = mapOf(xboxPadId to xboxLettered))

        assertEquals(xboxLettered, profiles.forDevice(5))
        assertEquals(defaults, profiles.forDevice(6))
    }
}
