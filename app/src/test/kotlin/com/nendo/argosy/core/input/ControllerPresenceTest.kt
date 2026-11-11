package com.nendo.argosy.core.input

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerPresenceTest {

    private val dualSense = ConnectedPad("1:2:ds", "DualSense Wireless Controller", builtIn = false)
    private val eightBitDo = ConnectedPad("3:4:8bd", "8BitDo Pro 2", builtIn = false)

    @Test
    fun `the first snapshot is the baseline and reports nothing`() {
        val presence = ControllerPresence()

        assertEquals(emptyList<ControllerEvent>(), presence.update(listOf(dualSense, eightBitDo)))
    }

    @Test
    fun `a pad added after the baseline is reported connected`() {
        val presence = ControllerPresence()
        presence.update(listOf(dualSense))

        assertEquals(
            listOf(ControllerEvent(ControllerChange.CONNECTED, eightBitDo)),
            presence.update(listOf(dualSense, eightBitDo))
        )
    }

    @Test
    fun `a removed pad is reported with the name it had while connected`() {
        val presence = ControllerPresence()
        presence.update(listOf(dualSense, eightBitDo))

        val events = presence.update(listOf(dualSense))

        assertEquals(listOf(ControllerEvent(ControllerChange.DISCONNECTED, eightBitDo)), events)
        assertEquals("8BitDo Pro 2", events.single().controller.name)
    }

    @Test
    fun `an unchanged snapshot reports nothing`() {
        val presence = ControllerPresence()
        presence.update(listOf(dualSense))

        assertEquals(emptyList<ControllerEvent>(), presence.update(listOf(dualSense.copy(batteryPercent = 40))))
    }

    @Test
    fun `a pad present at the baseline that leaves and returns is reported both ways`() {
        val presence = ControllerPresence()
        presence.update(listOf(dualSense))

        assertEquals(listOf(ControllerEvent(ControllerChange.DISCONNECTED, dualSense)), presence.update(emptyList()))
        assertEquals(listOf(ControllerEvent(ControllerChange.CONNECTED, dualSense)), presence.update(listOf(dualSense)))
    }
}
