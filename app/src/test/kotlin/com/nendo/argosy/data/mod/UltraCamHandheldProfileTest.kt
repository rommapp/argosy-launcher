package com.nendo.argosy.data.mod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraCamHandheldProfileTest {
    private val original = """
        [AspectRatio]
        Width=64
        Height=27
        [Resolution]
        Width=3840
        Height=2160
        ShadowResolution=2048
        QualityImprovements=True
        [Handheld]
        Width=1280
        Height=720
        OverrideHandheld_Resolution=False
        [UltraCamFPS]
        DynamicFPS=True
        MaxFPS=120
        mMovieFPS=30
        [Controls]
        untouched=1
    """.trimIndent()

    @Test
    fun `patches existing profile keys and preserves unrelated content and line endings`() {
        val patched = UltraCamHandheldProfile.apply(original.replace("\n", "\r\n") + "\r\n")
        assertTrue(patched.contains("Width=16\r\nHeight=9\r\n"))
        assertTrue(patched.contains("ShadowResolution=1024\r\nQualityImprovements=False"))
        assertTrue(patched.contains("OverrideHandheld_Resolution=True"))
        assertTrue(patched.contains("MaxFPS=60\r\nmMovieFPS=30"))
        assertTrue(patched.endsWith("[Controls]\r\nuntouched=1\r\n"))
    }

    @Test
    fun `refuses missing or duplicate profile keys`() {
        listOf(original.replace("MaxFPS=120", ""), original.replace("MaxFPS=120", "MaxFPS=120\nMaxFPS=30")).forEach { text ->
            try {
                UltraCamHandheldProfile.apply(text)
                throw AssertionError("Expected profile rejection")
            } catch (failure: ModInstallException) {
                assertEquals(ModInstallFailure.UNSUPPORTED_PROFILE, failure.reason)
            }
        }
    }
}
