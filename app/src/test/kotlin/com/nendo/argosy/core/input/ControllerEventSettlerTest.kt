package com.nendo.argosy.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerEventSettlerTest {

    private val pad = ConnectedPad("057e:2017:pad", "Nintendo SNES Controller", builtIn = false)
    private val junk = ConnectedPad("057e:2017:junk", "Nintendo Co., Ltd. SNES Controller", builtIn = false)

    private fun connected(p: ConnectedPad) = ControllerEvent(ControllerChange.CONNECTED, p)
    private fun disconnected(p: ConnectedPad) = ControllerEvent(ControllerChange.DISCONNECTED, p)

    @Test
    fun `a connect is announced only once it has settled`() {
        val settler = ControllerEventSettler(1_500)

        assertEquals(emptyList<ControllerEvent>(), settler.offer(listOf(connected(pad)), now = 0))
        assertEquals(1_000L, settler.nextDueIn(now = 500))
        assertEquals(emptyList<ControllerEvent>(), settler.due(now = 1_499))
        assertEquals(listOf(connected(pad)), settler.due(now = 1_500))
        assertNull(settler.nextDueIn(now = 1_500))
    }

    @Test
    fun `a device gone inside the window is never announced either way`() {
        val settler = ControllerEventSettler(1_500)

        settler.offer(listOf(connected(junk)), now = 0)
        assertEquals(emptyList<ControllerEvent>(), settler.offer(listOf(disconnected(junk)), now = 900))
        assertEquals(emptyList<ControllerEvent>(), settler.due(now = 5_000))
    }

    @Test
    fun `the muted device drops while the clean pad that replaces it is announced`() {
        val settler = ControllerEventSettler(1_500)

        settler.offer(listOf(connected(junk)), now = 0)
        settler.offer(listOf(disconnected(junk), connected(pad)), now = 800)

        assertEquals(emptyList<ControllerEvent>(), settler.due(now = 2_000))
        assertEquals(listOf(connected(pad)), settler.due(now = 2_300))
    }

    @Test
    fun `an announced pad's disconnect passes at once`() {
        val settler = ControllerEventSettler(1_500)

        settler.offer(listOf(connected(pad)), now = 0)
        settler.due(now = 1_500)

        assertEquals(listOf(disconnected(pad)), settler.offer(listOf(disconnected(pad)), now = 9_000))
    }

    @Test
    fun `a pad reconnecting inside the window settles from its latest connect`() {
        val settler = ControllerEventSettler(1_500)

        settler.offer(listOf(connected(pad)), now = 0)
        settler.offer(listOf(disconnected(pad)), now = 500)
        settler.offer(listOf(connected(pad)), now = 1_000)

        assertEquals(emptyList<ControllerEvent>(), settler.due(now = 2_000))
        assertEquals(listOf(connected(pad)), settler.due(now = 2_500))
    }
}
