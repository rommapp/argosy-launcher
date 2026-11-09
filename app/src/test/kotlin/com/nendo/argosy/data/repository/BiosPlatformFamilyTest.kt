package com.nendo.argosy.data.repository

import android.content.Context
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class BiosPlatformFamilyTest {

    private lateinit var tempDir: File
    private lateinit var firmwareDao: FirmwareDao
    private lateinit var repository: BiosRepository

    @Before
    fun setUp() {
        tempDir = createTempDirectory("bios_family").toFile()
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
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun firmware(
        id: Long,
        platformId: Long,
        platformSlug: String,
        fileName: String,
        content: ByteArray = NEOGEO_BYTES
    ): FirmwareEntity {
        val source = File(tempDir, "files/bios/$platformSlug/$fileName").apply {
            parentFile?.mkdirs()
            writeBytes(content)
        }
        return FirmwareEntity(
            id = id,
            platformId = platformId,
            platformSlug = platformSlug,
            rommId = id,
            fileName = fileName,
            filePath = "bios/$platformSlug/$fileName",
            fileSizeBytes = content.size.toLong(),
            md5Hash = null,
            sha1Hash = null,
            localPath = source.absolutePath,
            downloadedAt = Instant.now(),
            lastVerifiedAt = null
        )
    }

    private fun systemFile(name: String) = File(repository.getLibretroSystemDir(), name)

    @Test
    fun `an AES launch copies the neogeo zip RomM attached to the MVS platform`() = runBlocking {
        val mvs = firmware(id = 1, platformId = 10, platformSlug = "neogeomvs", fileName = "neogeo.zip")
        coEvery { firmwareDao.getDownloaded() } returns listOf(mvs)

        val copied = repository.distributeBiosToEmulator("neogeoaes", EmulatorRegistry.BUILTIN_PACKAGE)

        assertEquals(1, copied)
        assertArrayEquals(NEOGEO_BYTES, systemFile("neogeo.zip").readBytes())
    }

    @Test
    fun `an AES launch copies a neogeo zip RomM keeps under the arcade platform`() = runBlocking {
        val arcade = firmware(id = 1, platformId = 20, platformSlug = "arcade", fileName = "neogeo.zip")
        val unrelated = firmware(id = 2, platformId = 20, platformSlug = "arcade", fileName = "pgm.zip")
        coEvery { firmwareDao.getDownloaded() } returns listOf(arcade, unrelated)

        val copied = repository.distributeBiosToEmulator("neogeoaes", EmulatorRegistry.BUILTIN_PACKAGE)

        assertEquals(1, copied)
        assertArrayEquals(NEOGEO_BYTES, systemFile("neogeo.zip").readBytes())
        assertFalse(systemFile("pgm.zip").exists())
    }

    @Test
    fun `the launching platform's own copy wins over a sibling's file of the same name`() = runBlocking {
        val ownBytes = ByteArray(NEOGEO_BYTES.size) { 7 }
        val mvs = firmware(id = 1, platformId = 10, platformSlug = "neogeomvs", fileName = "neogeo.zip")
        val aes = firmware(id = 2, platformId = 11, platformSlug = "neogeoaes", fileName = "neogeo.zip", content = ownBytes)
        coEvery { firmwareDao.getDownloaded() } returns listOf(mvs, aes)

        val copied = repository.distributeBiosToEmulator("neogeoaes", EmulatorRegistry.BUILTIN_PACKAGE)

        assertEquals(1, copied)
        assertArrayEquals(ownBytes, systemFile("neogeo.zip").readBytes())
    }

    @Test
    fun `a platform outside the family receives none of its firmware`() = runBlocking {
        val mvs = firmware(id = 1, platformId = 10, platformSlug = "neogeomvs", fileName = "neogeo.zip")
        val neoCd = firmware(id = 2, platformId = 12, platformSlug = "neogeocd", fileName = "neocd.bin")
        coEvery { firmwareDao.getDownloaded() } returns listOf(mvs, neoCd)

        val copied = repository.distributeBiosToEmulator("neogeocd", EmulatorRegistry.BUILTIN_PACKAGE)

        assertEquals(1, copied)
        assertTrue(systemFile("neocd.bin").isFile)
        assertFalse(systemFile("neogeo.zip").exists())
    }

    @Test
    fun `disabling MVS keeps the neogeo zip an enabled AES platform still needs`() = runBlocking {
        val mvs = firmware(id = 1, platformId = 10, platformSlug = "neogeomvs", fileName = "neogeo.zip")
        val aes = firmware(id = 2, platformId = 11, platformSlug = "neogeoaes", fileName = "neogeo.zip")
        val distributed = systemFile("neogeo.zip").apply { writeBytes(NEOGEO_BYTES) }
        coEvery { firmwareDao.getDownloadedForDisabledPlatforms() } returns listOf(mvs)
        coEvery { firmwareDao.getByPlatformSlug("neogeomvs") } returns listOf(mvs)
        coEvery { firmwareDao.getSyncEnabledAll() } returns listOf(aes)

        repository.redownloadAll()

        assertTrue(distributed.isFile)
        assertFalse(File(mvs.localPath!!).exists())
    }

    @Test
    fun `disabling MVS still removes its copy when no enabled platform holds that file`() = runBlocking {
        val mvs = firmware(id = 1, platformId = 10, platformSlug = "neogeomvs", fileName = "neogeo.zip")
        val psx = firmware(id = 3, platformId = 13, platformSlug = "psx", fileName = "scph5501.bin")
        val distributed = systemFile("neogeo.zip").apply { writeBytes(NEOGEO_BYTES) }
        coEvery { firmwareDao.getDownloadedForDisabledPlatforms() } returns listOf(mvs)
        coEvery { firmwareDao.getByPlatformSlug("neogeomvs") } returns listOf(mvs)
        coEvery { firmwareDao.getSyncEnabledAll() } returns listOf(psx)

        repository.redownloadAll()

        assertFalse(distributed.exists())
    }

    private companion object {
        val NEOGEO_BYTES = ByteArray(2048) { it.toByte() }
    }
}
