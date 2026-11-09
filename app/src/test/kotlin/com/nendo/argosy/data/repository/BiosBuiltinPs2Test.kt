package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.BiosPathRegistry
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class BiosBuiltinPs2Test {

    private lateinit var tempDir: File
    private lateinit var firmwareDao: FirmwareDao
    private lateinit var repository: BiosRepository
    private lateinit var source: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("bios_ps2").toFile()
        firmwareDao = mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns File(tempDir, "files").apply { mkdirs() }
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        val values = mockk<UserPreferences>(relaxed = true)
        every { values.customBiosPath } returns null
        every { prefs.preferences } returns flowOf(values)
        repository = BiosRepository(
            context = context,
            firmwareDao = firmwareDao,
            platformDao = mockk(relaxed = true),
            userPreferencesRepository = prefs,
            switchKeyManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            fal = mockk(relaxed = true)
        )
        source = File(tempDir, "files/bios/ps2/SCPH-70012.bin").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(BIOS_SIZE) { it.toByte() })
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun ps2Bios(localPath: String? = source.absolutePath) = FirmwareEntity(
        id = 7,
        platformId = 3,
        platformSlug = "ps2",
        rommId = 7,
        fileName = "SCPH-70012.bin",
        filePath = "bios/ps2/SCPH-70012.bin",
        fileSizeBytes = BIOS_SIZE.toLong(),
        md5Hash = null,
        sha1Hash = null,
        localPath = localPath,
        downloadedAt = Instant.now(),
        lastVerifiedAt = null
    )

    @Test
    fun `ps2 BIOS for the built-in core lands in pcsx2 bios under its own name`() = runBlocking {
        coEvery { firmwareDao.getByPlatformSlug("ps2") } returns listOf(ps2Bios())

        val copied = repository.distributeBiosToEmulator("ps2", EmulatorRegistry.BUILTIN_PACKAGE)

        val systemDir = repository.getLibretroSystemDir()
        assertEquals(1, copied)
        assertTrue(File(systemDir, "pcsx2/bios/SCPH-70012.bin").isFile)
        assertFalse(File(systemDir, "SCPH-70012.bin").exists())
    }

    @Test
    fun `other platforms keep distributing to the system root`() {
        assertNull(BiosPathRegistry.getBuiltinPlatformBiosDir("psx"))
        assertNull(BiosPathRegistry.getBuiltinPlatformBiosPath("saturn", "sega_101.bin"))
        assertEquals("pcsx2/bios", BiosPathRegistry.getBuiltinPlatformBiosDir("playstation2"))
    }

    @Test
    fun `cleanup removes the copy distribution placed in pcsx2 bios`() = runBlocking {
        coEvery { firmwareDao.getByPlatformSlug("ps2") } returns listOf(ps2Bios())
        repository.distributeBiosToEmulator("ps2", EmulatorRegistry.BUILTIN_PACKAGE)
        val nested = File(repository.getLibretroSystemDir(), "pcsx2/bios/SCPH-70012.bin")
        assertTrue(nested.isFile)
        coEvery { firmwareDao.getDownloadedForDisabledPlatforms() } returns listOf(ps2Bios())
        coEvery { firmwareDao.getSyncEnabledAll() } returns emptyList()

        repository.redownloadAll()

        assertFalse(nested.exists())
    }

    @Test
    fun `cleanup leaves a user's own BIOS in pcsx2 bios alone`() = runBlocking {
        val theirs = File(repository.getLibretroSystemDir(), "pcsx2/bios/SCPH-70012.bin").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(BIOS_SIZE * 2))
        }
        coEvery { firmwareDao.getByPlatformSlug("ps2") } returns listOf(ps2Bios())
        coEvery { firmwareDao.getDownloadedForDisabledPlatforms() } returns listOf(ps2Bios())
        coEvery { firmwareDao.getSyncEnabledAll() } returns emptyList()

        repository.redownloadAll()

        assertTrue(theirs.exists())
    }

    private companion object {
        const val BIOS_SIZE = 4096
    }
}
