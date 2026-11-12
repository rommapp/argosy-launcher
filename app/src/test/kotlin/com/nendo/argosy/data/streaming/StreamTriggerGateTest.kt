package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMStreamingConfig
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTriggerGateTest {

    private val supported = RomMCapabilities.from("5.3.0")
    private val psx = RomMStreamingContainer(platform = "psx", label = "Living room PC", emulator = "duckstation")
    private val enabled = RomMStreamingConfig(enabled = true, containers = listOf(psx))

    @Test
    fun `streaming starts at RomM 5_3_0`() {
        assertFalse(RomMCapabilities.from("5.2.9").supportsStreaming)
        assertTrue(RomMCapabilities.from("5.3.0").supportsStreaming)
        assertTrue(RomMCapabilities.from("5.5.0-alpha.1").supportsStreaming)
        assertFalse(RomMCapabilities.NONE.supportsStreaming)
    }

    @Test
    fun `every trigger met offers the platform's container`() {
        assertEquals(psx, StreamTriggerGate.containerFor(supported, enabled, startRefused = false, platformSlug = "psx"))
    }

    @Test
    fun `an older server offers nothing even with streaming enabled`() {
        assertNull(StreamTriggerGate.containerFor(RomMCapabilities.from("5.2.0"), enabled, false, "psx"))
    }

    @Test
    fun `a disabled or missing config offers nothing`() {
        assertNull(StreamTriggerGate.containerFor(supported, enabled.copy(enabled = false), false, "psx"))
        assertNull(StreamTriggerGate.containerFor(supported, null, false, "psx"))
    }

    @Test
    fun `a platform without a container offers nothing`() {
        assertNull(StreamTriggerGate.containerFor(supported, enabled, false, "n64"))
        assertNull(StreamTriggerGate.containerFor(supported, enabled, false, null))
        assertNull(StreamTriggerGate.containerFor(supported, enabled, false, ""))
    }

    @Test
    fun `platform slugs match regardless of case`() {
        assertEquals(psx, StreamTriggerGate.containerFor(supported, enabled, false, "PSX"))
    }

    @Test
    fun `a refused claim hides the offer until the next refresh`() {
        assertNull(StreamTriggerGate.containerFor(supported, enabled, startRefused = true, platformSlug = "psx"))
    }

    @Test
    fun `the label falls back to the emulator when the admin set none`() {
        assertEquals("Living room PC", StreamTriggerGate.labelOf(psx))
        assertEquals("duckstation", StreamTriggerGate.labelOf(psx.copy(label = "")))
        assertNull(StreamTriggerGate.labelOf(psx.copy(label = "", emulator = "")))
    }
}
