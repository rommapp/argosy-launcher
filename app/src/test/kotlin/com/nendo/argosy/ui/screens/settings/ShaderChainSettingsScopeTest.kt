package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShaderChainSettingsScopeTest {
    private val platform = PlatformContext(
        platformId = 42L,
        platformName = "Super Nintendo Entertainment System",
        platformSlug = "snes"
    )

    @Test
    fun `global context uses the global chain`() {
        val state = SettingsUiState(
            builtinVideo = BuiltinVideoState(
                shaderChainJson = "global-chain",
                availablePlatforms = listOf(platform)
            ),
            platformLibretro = PlatformLibretroState(
                platformSettings = mapOf(
                    42L to PlatformLibretroSettingsEntity(
                        platformId = 42L,
                        shaderChain = "platform-chain"
                    )
                )
            )
        )

        val scope = routeResolveShaderChainSettingsScope(state)

        assertNull(scope.platformId)
        assertEquals("global-chain", scope.chainJson)
    }

    @Test
    fun `platform context uses its own chain`() {
        val state = platformState(shaderChain = "platform-chain")

        val scope = routeResolveShaderChainSettingsScope(state)

        assertEquals(42L, scope.platformId)
        assertEquals("platform-chain", scope.chainJson)
    }

    @Test
    fun `platform context inherits the global chain when it has no override`() {
        val state = platformState(shaderChain = null)

        val scope = routeResolveShaderChainSettingsScope(state)

        assertEquals(42L, scope.platformId)
        assertEquals("global-chain", scope.chainJson)
    }

    private fun platformState(shaderChain: String?): SettingsUiState = SettingsUiState(
        builtinVideo = BuiltinVideoState(
            shaderChainJson = "global-chain",
            platformContextIndex = 1,
            availablePlatforms = listOf(platform)
        ),
        platformLibretro = PlatformLibretroState(
            platformSettings = mapOf(
                42L to PlatformLibretroSettingsEntity(
                    platformId = 42L,
                    shaderChain = shaderChain
                )
            )
        )
    )
}
