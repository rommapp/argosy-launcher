package com.nendo.argosy.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerQualityCeilingsTest {

    @Test
    fun `a chosen resolution with an original bitrate streams at that resolution's limit`() {
        assertEquals(40_000, PlayerQualityCeilings(maxHeight = 2160).streamingBitrateKbps())
        assertEquals(10_000, PlayerQualityCeilings(maxHeight = 1080).streamingBitrateKbps())
        assertEquals(6_000, PlayerQualityCeilings(maxHeight = 720).streamingBitrateKbps())
        assertEquals(1_500, PlayerQualityCeilings(maxHeight = 360).streamingBitrateKbps())
    }

    @Test
    fun `a chosen bitrate wins over the resolution's limit`() {
        val ceilings = PlayerQualityCeilings(maxHeight = 720, maxBitrateKbps = 2_000)

        assertEquals(2_000, ceilings.streamingBitrateKbps())
    }

    @Test
    fun `original resolution and bitrate leave the ceiling open`() {
        assertNull(PlayerQualityCeilings().streamingBitrateKbps())
        assertNull(PlayerQualityCeilings(maxFramerate = 30).streamingBitrateKbps())
    }
}
