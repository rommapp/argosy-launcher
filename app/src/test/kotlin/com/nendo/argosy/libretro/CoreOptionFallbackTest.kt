package com.nendo.argosy.libretro

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreOptionFallbackTest {

    private val textures = CoreOptionDef(
        key = "dolphin_load_custom_textures",
        displayName = "Load Custom Textures",
        values = listOf("disabled", "enabled"),
        defaultValue = "disabled"
    )

    private val noLaunchDefaults = emptyMap<String, String>()
    private val textureLaunchDefault = mapOf(textures.key to "enabled")

    @Test
    fun `resetting a global setting returns to the manifest default, not the setting being removed`() {
        val global = mapOf(textures.key to "enabled")
        assertEquals("disabled", LibretroActivity.inheritedCoreOption(textures, perGame = false, global, noLaunchDefaults))
    }

    @Test
    fun `resetting a per-game setting returns to the global setting`() {
        val global = mapOf(textures.key to "enabled")
        assertEquals("enabled", LibretroActivity.inheritedCoreOption(textures, perGame = true, global, noLaunchDefaults))
    }

    @Test
    fun `resetting returns to the provisioned texture default when no global setting exists`() {
        assertEquals("enabled", LibretroActivity.inheritedCoreOption(textures, perGame = false, emptyMap(), textureLaunchDefault))
        assertEquals("enabled", LibretroActivity.inheritedCoreOption(textures, perGame = true, emptyMap(), textureLaunchDefault))
    }

    @Test
    fun `a player setting wins over the provisioned texture default`() {
        val global = mapOf(textures.key to "disabled")
        assertEquals("disabled", LibretroActivity.effectiveCoreOption(textures, perGame = false, emptyMap(), global, textureLaunchDefault))
        assertEquals("disabled", LibretroActivity.inheritedCoreOption(textures, perGame = true, global, textureLaunchDefault))
    }

    @Test
    fun `turning per-game settings off drops the per-game value for the global one`() {
        val game = mapOf(textures.key to "enabled")
        val global = mapOf(textures.key to "disabled")
        assertEquals("enabled", LibretroActivity.effectiveCoreOption(textures, perGame = true, game, global, noLaunchDefaults))
        assertEquals("disabled", LibretroActivity.effectiveCoreOption(textures, perGame = false, game, global, noLaunchDefaults))
    }

    @Test
    fun `turning per-game settings off with no global setting falls to the provisioned default, then the manifest default`() {
        val game = mapOf(textures.key to "disabled")
        assertEquals("enabled", LibretroActivity.effectiveCoreOption(textures, perGame = false, game, emptyMap(), textureLaunchDefault))
        assertEquals("disabled", LibretroActivity.effectiveCoreOption(textures, perGame = false, mapOf(textures.key to "enabled"), emptyMap(), noLaunchDefaults))
    }
}
