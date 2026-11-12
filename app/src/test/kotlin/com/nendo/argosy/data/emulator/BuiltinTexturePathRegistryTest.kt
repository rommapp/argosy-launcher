package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BuiltinTexturePathRegistryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `resolves target path for Dolphin ngc and wii under libretro save dir`() {
        val saveDir = tempFolder.newFolder("saves")
        val systemDir = tempFolder.newFolder("system")

        val ngcRule = BuiltinTexturePathRegistry.resolveRule("dolphin", "ngc")
        val wiiRule = BuiltinTexturePathRegistry.resolveRule("dolphin", "wii")
        assertNotNull(ngcRule)
        assertNotNull(wiiRule)
        assertEquals(TextureBaseRoot.LIBRETRO_SAVE_DIR, ngcRule!!.baseRoot)
        assertEquals(TextureFolderKeyStrategy.GAME_ID, ngcRule.folderKeyStrategy)
        assertEquals(mapOf("dolphin_load_custom_textures" to "enabled"), ngcRule.coreOptions)

        val targetDir = BuiltinTexturePathRegistry.resolveTargetDir(ngcRule, saveDir, systemDir, "GZLE01")
        assertEquals(File(saveDir, "User/Load/Textures/GZLE01"), targetDir)

        val wiiTargetDir = BuiltinTexturePathRegistry.resolveTargetDir(wiiRule!!, saveDir, systemDir, "RMGE01")
        assertEquals(File(saveDir, "User/Load/Textures/RMGE01"), wiiTargetDir)
    }

    @Test
    fun `resolves target path for PPSSPP psp under libretro save dir`() {
        val saveDir = tempFolder.newFolder("saves")
        val systemDir = tempFolder.newFolder("system")

        val pspRule = BuiltinTexturePathRegistry.resolveRule("ppsspp", "psp")
        assertNotNull(pspRule)
        assertEquals(TextureBaseRoot.LIBRETRO_SAVE_DIR, pspRule!!.baseRoot)
        assertEquals(TextureFolderKeyStrategy.GAME_ID, pspRule.folderKeyStrategy)
        assertEquals(mapOf("ppsspp_texture_replacement" to "enabled"), pspRule.coreOptions)

        val targetDir = BuiltinTexturePathRegistry.resolveTargetDir(pspRule, saveDir, systemDir, "ULUS10041")
        assertEquals(File(saveDir, "PSP/TEXTURES/ULUS10041"), targetDir)
    }

    @Test
    fun `resolves target path and GLideN64 rdp plugin plus full alpha and htc hts options for N64 cores`() {
        val saveDir = tempFolder.newFolder("saves")
        val systemDir = tempFolder.newFolder("system")

        val n64Rule = BuiltinTexturePathRegistry.resolveRule("mupen64plus_next_gles3", "n64")
        assertNotNull(n64Rule)
        assertEquals(TextureBaseRoot.LIBRETRO_SYSTEM_DIR, n64Rule!!.baseRoot)
        assertEquals(TextureFolderKeyStrategy.N64_HEADER_NAME, n64Rule.folderKeyStrategy)
        assertTrue(n64Rule.supportsHtc)
        assertTrue(n64Rule.supportsHts)

        val targetDir = BuiltinTexturePathRegistry.resolveTargetDir(n64Rule, saveDir, systemDir, "THE LEGEND OF ZELDA")
        assertEquals(File(systemDir, "Mupen64plus/hires_texture/THE LEGEND OF ZELDA"), targetDir)

        val cacheDir = BuiltinTexturePathRegistry.resolveHtcHtsCacheDir(n64Rule, saveDir, systemDir)
        assertEquals(File(systemDir, "Mupen64plus/cache"), cacheDir)

        assertEquals(
            mapOf(
                "mupen64plus-rdp-plugin" to "gliden64",
                "mupen64plus-txHiresEnable" to "True",
                "mupen64plus-txHiresFullAlphaChannel" to "True"
            ),
            n64Rule.optionsForPack(hasHtc = false, hasHts = false)
        )
        assertEquals(
            mapOf(
                "mupen64plus-rdp-plugin" to "gliden64",
                "mupen64plus-txHiresEnable" to "True",
                "mupen64plus-txHiresFullAlphaChannel" to "True",
                "mupen64plus-EnableEnhancedHighResStorage" to "False"
            ),
            n64Rule.optionsForPack(hasHtc = true, hasHts = false)
        )
        assertEquals(
            mapOf(
                "mupen64plus-rdp-plugin" to "gliden64",
                "mupen64plus-txHiresEnable" to "True",
                "mupen64plus-txHiresFullAlphaChannel" to "True",
                "mupen64plus-EnableEnhancedHighResStorage" to "True"
            ),
            n64Rule.optionsForPack(hasHtc = false, hasHts = true)
        )
    }

    @Test
    fun `resolves target path and GLideN64 for Parallel N64 without HTS support`() {
        val saveDir = tempFolder.newFolder("saves")
        val systemDir = tempFolder.newFolder("system")

        val parallelRule = BuiltinTexturePathRegistry.resolveRule("parallel_n64", "n64")
        assertNotNull(parallelRule)
        assertTrue(parallelRule!!.supportsHtc)
        assertFalse(parallelRule.supportsHts)
        assertEquals(
            mapOf(
                "parallel-n64-gfxplugin" to "gliden64",
                "parallel-n64-gliden64-txHiresEnable" to "True",
                "parallel-n64-gliden64-txHiresFullAlphaChannel" to "True"
            ),
            parallelRule.optionsForPack(hasHtc = true, hasHts = false)
        )
    }

    @Test
    fun `resolves target path for Flycast dc and Azahar 3ds`() {
        val saveDir = tempFolder.newFolder("saves")
        val systemDir = tempFolder.newFolder("system")

        val dcRule = BuiltinTexturePathRegistry.resolveRule("flycast", "dc")
        assertNotNull(dcRule)
        val dcTarget = BuiltinTexturePathRegistry.resolveTargetDir(dcRule!!, saveDir, systemDir, "MK-51000")
        assertEquals(File(systemDir, "dc/textures/MK-51000"), dcTarget)

        val azaharRule = BuiltinTexturePathRegistry.resolveRule("azahar", "3ds")
        assertNotNull(azaharRule)
        assertEquals(TextureBaseRoot.LIBRETRO_SAVE_DIR, azaharRule!!.baseRoot)
        val azaharTarget = BuiltinTexturePathRegistry.resolveTargetDir(azaharRule, saveDir, systemDir, "0004000000033500")
        assertEquals(File(saveDir, "Azahar/load/textures/0004000000033500"), azaharTarget)
    }

    @Test
    fun `returns null when core does not support custom texture packs`() {
        assertNull(BuiltinTexturePathRegistry.resolveRule("fceumm", "nes"))
        assertNull(BuiltinTexturePathRegistry.resolveRule("snes9x", "snes"))
        assertNull(BuiltinTexturePathRegistry.resolveRule("mgba", "gb"))
        assertNull(BuiltinTexturePathRegistry.resolveRule("mednafen_psx_hw", "psx"))
        assertNull(BuiltinTexturePathRegistry.resolveRuleForPlatform("nes"))
        assertNull(BuiltinTexturePathRegistry.resolveRuleForPlatform("snes"))
        assertNull(BuiltinTexturePathRegistry.resolveRuleForPlatform("gb"))
        assertNull(BuiltinTexturePathRegistry.resolveRuleForPlatform("gbc"))
        assertNull(BuiltinTexturePathRegistry.resolveRuleForPlatform("gba"))
    }

    @Test
    fun `identifies supplemental texture pack mods by zip contents and save exclusion paths`() {
        val textureZip = createZip("hd_pack.zip", mapOf("GZLE01/tex1.png" to "png_bytes"))
        val romHackZip = createZip("romhack.zip", mapOf("zelda_hack.z64" to "rom_bytes"))

        assertTrue(
            TexturePackInstaller.isSupplementalTexturePack(
                "hd_pack.zip",
                "mod",
                "ngc",
                textureZip.absolutePath
            )
        )
        assertTrue(
            "Un-downloaded zip on NGC is supplemental because zip is not a launchable NGC extension",
            TexturePackInstaller.isSupplementalTexturePack(
                "Hypatia_HD_Pack.zip",
                "mod",
                "ngc",
                null
            )
        )
        assertTrue(
            "Un-downloaded zip on N64 in category mod is supplemental",
            TexturePackInstaller.isSupplementalTexturePack(
                "oot_hires_pack.zip",
                "mod",
                "n64",
                null
            )
        )
        assertTrue(
            "Un-downloaded community zip on N64 without keyword is supplemental",
            TexturePackInstaller.isSupplementalTexturePack("zelda_oot_community_pack.zip", "mod", "n64", null)
        )
        assertTrue(
            TexturePackInstaller.isSupplementalTexturePack(
                "hd_pack.zip",
                "mod",
                "ngc",
                "/data/saves/User/Load/Textures/GZLE01/.argosy_mod_1.manifest"
            )
        )
        assertFalse(
            "Zipped ROM hack in mods/ must not be classified as a texture pack",
            TexturePackInstaller.isSupplementalTexturePack(
                "romhack.zip",
                "mod",
                "n64",
                romHackZip.absolutePath
            )
        )
        assertTrue(TexturePackInstaller.isSupplementalTexturePack("zelda.htc", "mod", "n64"))
        assertTrue(TexturePackInstaller.isSupplementalTexturePack("zelda.hts", "mod", "n64"))
        assertFalse(TexturePackInstaller.isSupplementalTexturePack("romhack.iso", "mod", "ngc"))
        assertFalse(
            TexturePackInstaller.isSupplementalTexturePack(
                "hd_pack.zip",
                "hack",
                "ngc",
                textureZip.absolutePath
            )
        )
        assertFalse(
            TexturePackInstaller.isSupplementalTexturePack(
                "romhack.zip",
                "mod",
                "nes",
                textureZip.absolutePath
            )
        )

        assertTrue(BuiltinTexturePathRegistry.isSystemTexturePath("/data/files/libretro/system/Mupen64plus/hires_texture/OOT/tex.png"))
        assertTrue(BuiltinTexturePathRegistry.isSystemTexturePath("/data/files/libretro/system/Mupen64plus/cache/OOT.hts"))
        assertTrue(BuiltinTexturePathRegistry.isSystemTexturePath("/data/files/libretro/system/dc/textures/MK-51000/tex.png"))
        assertFalse(BuiltinTexturePathRegistry.isSystemTexturePath("/data/files/libretro/system/scph5501.bin"))
    }

    private fun createZip(name: String, entries: Map<String, String>): File {
        val file = tempFolder.newFile(name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((entryName, content) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
    }
}
