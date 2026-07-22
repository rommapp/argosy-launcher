package com.nendo.argosy.data.platform

import com.nendo.argosy.core.emulator.LibretroSettingDef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformWeightRegistryTest {

    private val rewindSettings = listOf(
        LibretroSettingDef.RewindEnabled,
        LibretroSettingDef.RewindSpeed,
        LibretroSettingDef.RewindBufferDuration
    )

    @Test
    fun `every rewind setting is hidden on heavy platforms`() {
        listOf("psx", "n64", "gc", "saturn", "dreamcast", "psp", "nds", "ps2").forEach { slug ->
            rewindSettings.forEach { setting ->
                assertFalse(
                    "$setting should be hidden for $slug",
                    PlatformWeightRegistry.isSettingVisible(setting, slug)
                )
            }
        }
    }

    @Test
    fun `every rewind setting is visible on light platforms`() {
        listOf("snes", "nes", "gba", "genesis", "gb").forEach { slug ->
            rewindSettings.forEach { setting ->
                assertTrue(
                    "$setting should be visible for $slug",
                    PlatformWeightRegistry.isSettingVisible(setting, slug)
                )
            }
        }
    }

    @Test
    fun `rewind settings stay visible in the global scope`() {
        rewindSettings.forEach { setting ->
            assertTrue(PlatformWeightRegistry.isSettingVisible(setting, platformSlug = null))
        }
    }

    @Test
    fun `non-rewind settings are unaffected by platform weight`() {
        assertTrue(PlatformWeightRegistry.isSettingVisible(LibretroSettingDef.FastForwardEnabled, "psx"))
        assertTrue(PlatformWeightRegistry.isSettingVisible(LibretroSettingDef.FastForwardSpeed, "psx"))
    }
}
