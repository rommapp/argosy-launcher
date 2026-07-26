package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every registered RetroArch variant has to round-trip id to package and carry its own
 * save, state and BIOS configs. A variant that is registered in one place and missing
 * from another resolves paths against a sibling package, which is how a fork install
 * ends up reading another RetroArch's saves.
 */
class RetroArchVariantParityTest {

    private val variantIds = listOf("retroarch", "retroarch_64", "retroarch_32")

    @Test
    fun `each variant id maps back to its own package`() {
        variantIds.forEach { id ->
            val def = EmulatorRegistry.getById(id)
            assertNotNull("no EmulatorDef registered for $id", def)
            assertEquals(
                "$id does not round-trip to its own package",
                def!!.packageName,
                RetroArchPathResolver.packageForEmulatorId(id)
            )
        }
    }

    @Test
    fun `each variant package resolves to its own id`() {
        variantIds.forEach { id ->
            val def = EmulatorRegistry.getById(id)!!
            assertEquals(
                "package ${def.packageName} does not resolve back to $id",
                id,
                EmulatorRegistry.getByPackage(def.packageName)?.id
            )
        }
    }

    @Test
    fun `each variant is recognised as retroarch by the path resolver`() {
        variantIds.forEach { id ->
            assertTrue("$id is not recognised as RetroArch", RetroArchPathResolver.isRetroArch(id))
        }
    }

    @Test
    fun `each variant carries its own save state and bios configs`() {
        variantIds.forEach { id ->
            assertNotNull("no SavePathConfig for $id", SavePathRegistry.getConfig(id))
            assertNotNull("no StatePathConfig for $id", StatePathRegistry.getConfig(id))
            assertNotNull("no BiosPathConfig for $id", BiosPathRegistry.getEmulatorBiosPaths(id))
        }
    }

    @Test
    fun `each variant is a libretro host so saves keep the core slug label`() {
        variantIds.forEach { id ->
            assertEquals(
                "$id should report its core slug to the server",
                "pcsx_rearmed",
                EmulatorRegistry.toServerEmulator(id, "pcsx_rearmed")
            )
        }
    }
}
