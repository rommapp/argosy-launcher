package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuiltinCoreResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val platformId = 14L
    private val emulatorConfigDao = mockk<EmulatorConfigDao>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val firmwareDao = mockk<FirmwareDao>()

    private fun resolver(
        vararg biosFiles: String,
        gameCore: String? = null,
        library: List<FirmwareEntity> = emptyList()
    ): BuiltinCoreResolver {
        val systemDir = tempFolder.newFolder()
        biosFiles.forEach { java.io.File(systemDir, it).writeBytes(byteArrayOf(0)) }
        coEvery { emulatorConfigDao.getByGameId(any()) } returns gameCore?.let {
            EmulatorConfigEntity(platformId = null, gameId = 1L, packageName = null, displayName = null, coreName = it)
        }
        coEvery { emulatorConfigDao.getDefaultForPlatform(platformId) } returns null
        coEvery { firmwareDao.getDownloaded() } returns library
        every { preferences.getBuiltinCoreSelections() } returns flowOf(emptyMap())
        return BuiltinCoreResolver(emulatorConfigDao, preferences, firmwareDao, systemDir)
    }

    private fun libraryFirmware(
        fileName: String,
        md5: String?,
        downloaded: Boolean = true,
        platformSlug: String = "psx"
    ): FirmwareEntity {
        val localFile = java.io.File(tempFolder.newFolder(), fileName).apply {
            if (downloaded) writeBytes(byteArrayOf(0))
        }
        return FirmwareEntity(
            platformId = platformId,
            platformSlug = platformSlug,
            rommId = 1L,
            fileName = fileName,
            filePath = "bios/psx/$fileName",
            fileSizeBytes = 1L,
            md5Hash = md5,
            sha1Hash = null,
            localPath = localFile.absolutePath
        )
    }

    @Test
    fun `a downloaded library BIOS not yet copied to the system dir picks Beetle PSX HW`() = runTest {
        val library = listOf(libraryFirmware("scph5501.bin", md5 = null))
        assertEquals("mednafen_psx_hw", resolver(library = library).resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `a library BIOS under another name counts when its hash names a required file`() = runTest {
        val library = listOf(libraryFirmware("SCPH-5501 (USA).BIN", md5 = "490f666e1afb15b7362b406ed1cea246"))
        assertEquals("mednafen_psx_hw", resolver(library = library).resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `a library BIOS held under a sibling RomM slug of the platform counts`() = runTest {
        val library = listOf(libraryFirmware("scph5501.bin", md5 = null, platformSlug = "playstation"))
        assertEquals("mednafen_psx_hw", resolver(library = library).resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `a library BIOS held under an unrelated platform does not count`() = runTest {
        val library = listOf(libraryFirmware("ps2-bios.bin", md5 = null, platformSlug = "ps2"))
        assertEquals("pcsx_rearmed", resolver(library = library).resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `a file named as one of the platform's BIOS counts wherever RomM keeps it`() = runTest {
        val library = listOf(libraryFirmware("scph5501.bin", md5 = null, platformSlug = "ps2"))
        assertEquals("mednafen_psx_hw", resolver(library = library).resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `a library BIOS row whose file is missing on disk does not count`() = runTest {
        val library = listOf(libraryFirmware("scph5501.bin", md5 = null, downloaded = false))
        assertEquals("pcsx_rearmed", resolver(library = library).resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `the psx default is Beetle PSX HW when a PlayStation BIOS is present`() = runTest {
        assertEquals("mednafen_psx_hw", resolver("scph5501.bin").resolveCoreId(1L, platformId, "psx"))
        assertEquals("mednafen_psx_hw", resolver("scph5502.bin").resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `the psx default falls back to PCSX ReARMed when no PlayStation BIOS is present`() = runTest {
        assertEquals("pcsx_rearmed", resolver().resolveCoreId(1L, platformId, "psx"))
        assertEquals("pcsx_rearmed", resolver("unrelated.bin").resolveCoreId(1L, platformId, "psx"))
    }

    @Test
    fun `an explicit Beetle choice is kept even without a BIOS`() = runTest {
        assertEquals(
            "mednafen_psx_hw",
            resolver(gameCore = "mednafen_psx_hw").resolveCoreId(1L, platformId, "psx")
        )
    }

    @Test
    fun `platforms without a BIOS fallback keep their default`() = runTest {
        assertEquals("ppsspp", resolver().resolveCoreId(1L, platformId, "psp"))
    }
}
