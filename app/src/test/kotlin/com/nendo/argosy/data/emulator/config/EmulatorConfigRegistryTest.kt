package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.EmulatorRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorConfigRegistryTest {

    @Test
    fun `every registered emulator variant of the six apps resolves to its spec`() {
        val expected = mapOf(
            "eden" to EdenConfigSpec.SPEC,
            "eden_duo" to EdenConfigSpec.SPEC,
            "eden_nx2" to EdenConfigSpec.SPEC,
            "dolphin" to DolphinConfigSpec.SPEC,
            "dolphin_handheld" to DolphinConfigSpec.SPEC,
            "dolphin_cs" to DolphinConfigSpec.SPEC,
            "duckstation" to DuckStationConfigSpec.SPEC,
            "armsx2_refresh" to Armsx2ConfigSpec.SPEC,
            "vita3k" to Vita3kConfigSpec.SPEC,
            "vita3k-zx" to Vita3kConfigSpec.SPEC,
            "vita3k-plus" to Vita3kConfigSpec.SPEC,
            "cemu" to CemuConfigSpec.SPEC,
            "cemu_dualscreen" to CemuConfigSpec.SPEC
        )

        expected.forEach { (id, spec) ->
            assertNotNull("$id is a registered emulator", EmulatorRegistry.getById(id))
            assertSame(id, spec, EmulatorConfigRegistry.specFor(id))
        }
    }

    @Test
    fun `family variant ids resolve through their base id`() {
        assertSame(DolphinConfigSpec.SPEC, EmulatorConfigRegistry.specFor("dolphin_org.dolphinemu.mmjr"))
        assertSame(Vita3kConfigSpec.SPEC, EmulatorConfigRegistry.specFor("vita3k_org.vita3k.emulator.custom"))
        assertSame(DuckStationConfigSpec.SPEC, EmulatorConfigRegistry.specFor("duckstation_com.github.stenzek.duckstation.dev"))
        assertSame(Armsx2ConfigSpec.SPEC, EmulatorConfigRegistry.specFor("armsx2_refresh_com.armsx2.nightly"))
        assertSame(EdenConfigSpec.SPEC, EmulatorConfigRegistry.specFor("eden_dev.legacy.eden"))
    }

    @Test
    fun `emulators outside the six apps have no spec`() {
        assertNull(EmulatorConfigRegistry.specFor("armsx2"))
        assertNull(EmulatorConfigRegistry.specFor("retroarch"))
        assertNull(EmulatorConfigRegistry.specFor("ppsspp"))
    }

    @Test
    fun `every spec has one primary file and options only on its own files`() {
        EmulatorConfigRegistry.specs.forEach { (id, spec) ->
            assertEquals(id, 1, spec.files.count { it.isPrimary })
            assertTrue(id, spec.options.all { it.file in spec.files })
            assertTrue(id, spec.secrets.all { it.file in spec.files })
        }
    }

    @Test
    fun `option keys are unique within their file and section`() {
        EmulatorConfigRegistry.specs.forEach { (id, spec) ->
            val keys = spec.options.map { ConfigOptionDecoder.lookupKey(spec, it.section, it.key).let { key -> it.file to key } }
            assertEquals(id, keys.size, keys.toSet().size)
        }
    }

    @Test
    fun `a named default token is one of the option's values`() {
        EmulatorConfigRegistry.specs.forEach { (id, spec) ->
            spec.options.filter { it.values.isNotEmpty() && it.defaultToken != null }.forEach { option ->
                val known = option.values.any { ConfigOptionDecoder.same(option.type, it.token, option.defaultToken!!) }
                assertTrue("$id ${option.section}/${option.key}", known)
            }
        }
    }

    @Test
    fun `every editable option can be stepped or picked`() {
        EmulatorConfigRegistry.specs.forEach { (id, spec) ->
            spec.options.filter { it.editable }.forEach { option ->
                assertTrue("$id ${option.section}/${option.key}", ConfigTokens.isAdjustable(option))
            }
        }
    }

    @Test
    fun `no registered option is a secret`() {
        EmulatorConfigRegistry.specs.forEach { (id, spec) ->
            assertTrue(id, spec.options.none { spec.isSecret(it) })
        }
    }

    @Test
    fun `eden reads the compiled default flag and dolphin ignores key case`() {
        assertEquals("\\default", EdenConfigSpec.SPEC.compiledDefaultFlag)
        assertTrue(DolphinConfigSpec.SPEC.keysIgnoreCase)
    }
}
