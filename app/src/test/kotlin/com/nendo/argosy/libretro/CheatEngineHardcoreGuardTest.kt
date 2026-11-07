package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatEngineHardcoreGuardTest {

    private val allowEverything: (String, String) -> Boolean = { _, _ -> true }

    @Test
    fun `cheat engines are refused even when rcheevos has no rule for the core`() {
        assertFalse(LibretroActivity.isCoreSettingAllowed("armsx2_cheats", "enabled", allowEverything))
        assertFalse(LibretroActivity.isCoreSettingAllowed("dolphin_cheats_enabled", "enabled", allowEverything))
        assertTrue(LibretroActivity.isCoreSettingAllowed("dolphin_cheats_enabled", "disabled", allowEverything))
    }

    @Test
    fun `hardcore starts the core with its cheat engine off rather than abandoning the session`() {
        val variables = mapOf("dolphin_cheats_enabled" to "enabled", "dolphin_cpu_core" to "4")
        val result = LibretroActivity.coreVariablesForMode(variables, hardcore = true, manifestKeys = emptyList())
        assertEquals("disabled", result["dolphin_cheats_enabled"])
        assertEquals("4", result["dolphin_cpu_core"])
        assertTrue(result.all { (key, value) -> LibretroActivity.isCoreSettingAllowed(key, value, allowEverything) })
    }

    @Test
    fun `hardcore writes the off value for a core that persists its last setting`() {
        val result = LibretroActivity.coreVariablesForMode(
            emptyMap(),
            hardcore = true,
            manifestKeys = listOf("armsx2_cheats", "armsx2_renderer")
        )
        assertEquals(mapOf("armsx2_cheats" to "disabled"), result)
    }

    @Test
    fun `casual launches keep the variables they were given`() {
        val variables = mapOf("armsx2_cheats" to "enabled")
        assertEquals(
            variables,
            LibretroActivity.coreVariablesForMode(variables, hardcore = false, manifestKeys = listOf("armsx2_cheats"))
        )
    }
}
