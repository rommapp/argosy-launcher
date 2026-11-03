package com.nendo.argosy.data.remote.jellyfin

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinDeviceProfileEnhancementLayerTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun decoder(vararg mimes: String) = mockk<MediaCodecInfo> {
        every { isEncoder } returns false
        every { supportedTypes } returns arrayOf(*mimes)
        every { getCapabilitiesForType(any()) } throws IllegalArgumentException()
    }

    private fun allowedRangeTypes(vararg decoders: MediaCodecInfo): List<List<String>> {
        mockkConstructor(MediaCodecList::class)
        every { anyConstructed<MediaCodecList>().codecInfos } returns arrayOf(*decoders)
        return JellyfinDeviceProfileBuilder().build().codecProfiles
            .filter { it.codec == "hevc" }
            .flatMap { it.conditions }
            .filter { it.property == "VideoRangeType" }
            .onEach { assertEquals(CONDITION_EQUALS_ANY, it.condition) }
            .map { it.value.split("|") }
    }

    @Test
    fun `an hevc-only device refuses the range types that need a dolby vision decoder`() {
        val allowed = allowedRangeTypes(decoder("video/hevc", "audio/mp4a-latm")).single()

        assertFalse("DOVIWithEL" in allowed)
        assertFalse("DOVIWithELHDR10Plus" in allowed)
        assertFalse("DOVI" in allowed)
        assertTrue(allowed.containsAll(listOf("SDR", "HDR10", "HDR10Plus", "DOVIWithHDR10")))
    }

    @Test
    fun `a device with a dolby vision decoder keeps every range type`() {
        val allowed = allowedRangeTypes(
            decoder("video/hevc", "audio/mp4a-latm"),
            decoder("video/dolby-vision")
        )

        assertTrue(allowed.isEmpty())
    }
}
