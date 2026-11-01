package com.nendo.argosy.data.remote.jellyfin

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.nendo.argosy.data.preferences.MediaStreamingQuality
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JellyfinDeviceProfileBitrateTest {

    private val builder = JellyfinDeviceProfileBuilder()
    private val adapter = Moshi.Builder().build().adapter(JellyfinDeviceProfile::class.java)

    @Before
    fun stubDecoders() {
        mockkConstructor(MediaCodecList::class)
        every { anyConstructed<MediaCodecList>().codecInfos } returns emptyArray<MediaCodecInfo>()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a tier with no bitrate ceiling still declares an explicit one`() {
        val profile = builder.build(maxStreamingBitrateKbps = MediaStreamingQuality.AUTO.maxBitrateKbps)

        assertEquals(
            JellyfinDeviceProfileBuilder.UNCAPPED_STREAMING_BITRATE_BPS,
            profile.maxStreamingBitrate
        )
        assertTrue(adapter.toJson(profile).contains("\"MaxStreamingBitrate\":120000000"))
    }

    @Test
    fun `the uncapped ceiling sits above every capped tier`() {
        val capped = MediaStreamingQuality.entries.mapNotNull { it.maxBitrateKbps }

        assertTrue(capped.isNotEmpty())
        capped.forEach { kbps ->
            assertTrue(kbps * 1000 < JellyfinDeviceProfileBuilder.UNCAPPED_STREAMING_BITRATE_BPS)
        }
    }

    @Test
    fun `a capped tier declares exactly its own ceiling`() {
        MediaStreamingQuality.entries.filter { it.maxBitrateKbps != null }.forEach { tier ->
            val profile = builder.build(maxStreamingBitrateKbps = tier.maxBitrateKbps)

            assertEquals(tier.name, tier.maxBitrateKbps!! * 1000, profile.maxStreamingBitrate)
        }
    }
}
