package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A custom save path is stored under a config id, so the id used when writing it has to be
 * the one used when reading it back. Fork and variant packages resolve to a synthesized
 * `<baseId>_<package>` emulator id, and only the collapsed form addresses a real config.
 */
class SavePathConfigIdTest {

    @Test
    fun `a family variant collapses to the config its base owns`() {
        val family = EmulatorRegistry.getEmulatorFamilies().first { it.baseId == "azahar" }
        val variant = EmulatorRegistry.createDefFromFamily(family, "io.github.azahar_emu.azahar")

        assertEquals(
            "azahar",
            SavePathRegistry.canonicalConfigId(variant.id, variant.packageName)
        )
    }

    @Test
    fun `the collapsed id is the one the settings read path derives`() {
        val family = EmulatorRegistry.getEmulatorFamilies().first { it.baseId == "azahar" }
        val variant = EmulatorRegistry.createDefFromFamily(family, "io.github.azahar_emu.azahar")

        val readId = SavePathRegistry.getConfigByPackage(variant.packageName)?.emulatorId
            ?: SavePathRegistry.getConfig(variant.id)?.emulatorId
        val writeId = SavePathRegistry.canonicalConfigId(variant.id, variant.packageName)

        assertEquals(readId, writeId)
    }

    @Test
    fun `a registered package keeps its own config id`() {
        val def = EmulatorRegistry.getByPackage("com.retroarch.aarch64")
        requireNotNull(def)

        assertEquals(
            "retroarch_64",
            SavePathRegistry.canonicalConfigId(def.id, def.packageName)
        )
    }
}
