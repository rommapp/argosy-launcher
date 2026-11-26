package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.libretro.frame.FrameRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveLibretroSettingsResolverTest {

    private val preferences: UserPreferencesRepository = mockk()
    private val dao: PlatformLibretroSettingsDao = mockk()
    private val resolver = EffectiveLibretroSettingsResolver(preferences, dao, mockk<FrameRegistry>(relaxed = true))

    @Test
    fun `a supplied global is used without reading the settings store`() = runTest {
        coEvery { dao.getByPlatformId(7L) } returns null
        val global = BuiltinEmulatorSettings(shader = "Sharp", framesEnabled = false)

        val settings = resolver.getEffectiveSettings(7L, "snes", global)

        assertEquals("Sharp", settings.shader)
        verify(exactly = 0) { preferences.getBuiltinEmulatorSettings() }
    }

    @Test
    fun `without a global the store is read once and gives the same result`() = runTest {
        coEvery { dao.getByPlatformId(7L) } returns null
        val global = BuiltinEmulatorSettings(shader = "Sharp", framesEnabled = false)
        every { preferences.getBuiltinEmulatorSettings() } returns flowOf(global)

        assertEquals(
            resolver.getEffectiveSettings(7L, "snes", global),
            resolver.getEffectiveSettings(7L, "snes")
        )
        verify(exactly = 1) { preferences.getBuiltinEmulatorSettings() }
    }
}
