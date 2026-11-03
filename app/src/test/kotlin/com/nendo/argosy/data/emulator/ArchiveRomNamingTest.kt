package com.nendo.argosy.data.emulator

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveRomNamingTest {

    @Rule
    @JvmField
    val temp = TemporaryFolder()

    private fun zipWith(archiveName: String, vararg entryNames: String): File {
        val archive = temp.newFile(archiveName)
        ZipOutputStream(archive.outputStream()).use { out ->
            entryNames.forEach { name ->
                out.putNextEntry(ZipEntry(name))
                out.write(byteArrayOf(1, 2, 3))
                out.closeEntry()
            }
        }
        return archive
    }

    private fun sevenZWith(archiveName: String, vararg entryNames: String): File {
        val archive = temp.newFile(archiveName)
        SevenZOutputFile(archive).use { out ->
            entryNames.forEach { name ->
                val entry = SevenZArchiveEntry().apply { this.name = name }
                out.putArchiveEntry(entry)
                out.write(byteArrayOf(1, 2, 3))
                out.closeArchiveEntry()
            }
        }
        return archive
    }

    @Test
    fun `sync content name is the extracted entry for the built-in emulator`() {
        val zip = zipWith("Super Mario World (U) [!].smc.zip", "Super Mario World (U) [!].smc")

        assertEquals(
            "Super Mario World (U) [!].smc",
            ArchiveRomNaming.syncContentName(zip, "snes", EmulatorRegistry.BUILTIN_ID)
        )
        assertEquals(
            "Super Mario World (U) [!].smc",
            ArchiveRomNaming.syncContentName(zip, "snes", EmulatorRegistry.LEGACY_BUILTIN_ID)
        )
    }

    @Test
    fun `sync content name agrees with the name the built-in core saves under`() {
        listOf(
            zipWith("Metroid II - Return of Samus.zip", "Metroid II - Return of Samus (USA).gb") to "gb",
            sevenZWith("Game.7z", "Game (Europe).sfc") to "snes",
            zipWith("Plain.zip", "Plain.gba") to "gba"
        ).forEach { (archive, platform) ->
            val synced = ArchiveRomNaming.syncContentName(archive, platform, EmulatorRegistry.BUILTIN_ID)
            assertEquals(ArchiveRomNaming.liveBaseName(archive, platform), File(synced).nameWithoutExtension)
        }
    }

    @Test
    fun `sync content name stays the archive for emulators that load it directly`() {
        val zip = zipWith("Game.zip", "Game (USA).gba")

        assertEquals("Game.zip", ArchiveRomNaming.syncContentName(zip, "gba", "retroarch"))
    }

    @Test
    fun `sync content name stays the archive where the zip is the rom`() {
        val zip = zipWith("sf2.zip", "sf2.01")

        assertEquals("sf2.zip", ArchiveRomNaming.syncContentName(zip, "arcade", EmulatorRegistry.BUILTIN_ID))
    }

    @Test
    fun `sync content name is the file itself when it is not an archive`() {
        val rom = temp.newFile("Game (USA).gba").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertEquals("Game (USA).gba", ArchiveRomNaming.syncContentName(rom, "gba", EmulatorRegistry.BUILTIN_ID))
    }

    @Test
    fun `live base name is the extracted entry on a platform that unpacks archives`() {
        val zip = zipWith("Game.zip", "Game (USA).gba")

        assertEquals("Game (USA)", ArchiveRomNaming.liveBaseName(zip, "gba"))
        assertEquals(listOf("Game (USA)", "Game"), ArchiveRomNaming.candidateBaseNames(zip, "gba"))
    }

    @Test
    fun `live base name stays the archive where the zip is the rom`() {
        val zip = zipWith("sf2.zip", "sf2.01")

        assertEquals("sf2", ArchiveRomNaming.liveBaseName(zip, "arcade"))
        assertEquals(listOf("sf2"), ArchiveRomNaming.candidateBaseNames(zip, "arcade"))
    }

    @Test
    fun `live base name of a bare rom is its own name`() {
        val rom = temp.newFile("Game (USA).gba").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertEquals("Game (USA)", ArchiveRomNaming.liveBaseName(rom, "gba"))
        assertEquals(listOf("Game (USA)"), ArchiveRomNaming.candidateBaseNames(rom, "gba"))
    }

    @Test
    fun `7z launch base name comes from the entry not the archive`() {
        val archive = sevenZWith("Chrono Trigger (USA).7z", "Chrono Trigger (U) [!].sfc")
        assertEquals("Chrono Trigger (U) [!]", ArchiveRomNaming.launchBaseName(archive))
    }

    @Test
    fun `format is detected by magic bytes not extension`() {
        val misnamed = sevenZWith("Actually7z.zip", "Final Fantasy VI (U).sfc")
        assertEquals("Final Fantasy VI (U)", ArchiveRomNaming.launchBaseName(misnamed))
    }

    @Test
    fun `launch base name comes from the entry not the archive`() {
        val archive = zipWith("Pokemon Emerald (USA).zip", "Pokemon - Emerald Version (U).gba")
        assertEquals("Pokemon - Emerald Version (U)", ArchiveRomNaming.launchBaseName(archive))
    }

    @Test
    fun `nested entries resolve to the bare file name`() {
        val archive = zipWith("Sonic.zip", "roms/genesis/Sonic The Hedgehog (W) (REV01).md")
        assertEquals("Sonic The Hedgehog (W) (REV01)", ArchiveRomNaming.launchBaseName(archive))
    }

    @Test
    fun `macOS resource forks are skipped`() {
        val archive = zipWith("Zelda.zip", "._Zelda.gba", "Zelda - The Minish Cap.gba")
        assertEquals("Zelda - The Minish Cap", ArchiveRomNaming.launchBaseName(archive))
    }

    @Test
    fun `non-archives and unreadable files yield no name`() {
        val plain = temp.newFile("Metroid.gba")
        assertNull(ArchiveRomNaming.launchBaseName(plain))
        assertNull(ArchiveRomNaming.launchBaseName(File(temp.root, "missing.zip")))
    }

    @Test
    fun `an empty archive yields no name rather than throwing`() {
        val archive = zipWith("Empty.zip")
        assertNull(ArchiveRomNaming.launchBaseName(archive))
    }

    @Test
    fun `an archive named the same as its entry still resolves`() {
        val archive = zipWith("Tetris.zip", "Tetris.gb")
        assertEquals("Tetris", ArchiveRomNaming.launchBaseName(archive))
    }
}
