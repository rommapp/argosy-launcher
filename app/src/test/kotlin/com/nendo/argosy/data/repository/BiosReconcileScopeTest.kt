package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory

class BiosReconcileScopeTest {

    private lateinit var tempDir: File
    private lateinit var firmwareDao: FirmwareDao

    @Before
    fun setUp() {
        tempDir = createTempDirectory("bios_reconcile").toFile()
        firmwareDao = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun repositoryWith(customBiosPath: String?): BiosRepository {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns File(tempDir, "files").apply { mkdirs() }

        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        val values = mockk<UserPreferences>(relaxed = true)
        every { values.customBiosPath } returns customBiosPath
        every { prefs.preferences } returns flowOf(values)

        return BiosRepository(
            context = context,
            firmwareDao = firmwareDao,
            platformDao = mockk(relaxed = true),
            userPreferencesRepository = prefs,
            switchKeyManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            xboxDiskImageProvisioner = mockk(relaxed = true)
        )
    }

    private fun row(id: Long, localPath: String) = FirmwareEntity(
        id = id,
        platformId = 1,
        platformSlug = "ps1",
        rommId = id,
        fileName = "scph5501.bin",
        filePath = "bios/ps1/scph5501.bin",
        fileSizeBytes = 512,
        md5Hash = null,
        sha1Hash = null,
        localPath = localPath,
        downloadedAt = Instant.now(),
        lastVerifiedAt = null
    )

    @Test
    fun `a file gone from a readable configured directory is cleared`() = runBlocking {
        val custom = File(tempDir, "sd/bios").apply { mkdirs() }
        val absent = File(custom, "ps1/scph5501.bin")
        coEvery { firmwareDao.getAllDownloaded() } returns listOf(row(1, absent.absolutePath))

        val cleared = repositoryWith(custom.absolutePath).reconcileDownloadedFirmware()

        assertEquals(1, cleared)
        coVerify { firmwareDao.updateLocalPath(1, null, null) }
    }

    @Test
    fun `a present file in the configured directory is left alone`() = runBlocking {
        val custom = File(tempDir, "sd/bios").apply { mkdirs() }
        val present = File(custom, "ps1/scph5501.bin").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(512))
        }
        coEvery { firmwareDao.getAllDownloaded() } returns listOf(row(1, present.absolutePath))

        assertEquals(0, repositoryWith(custom.absolutePath).reconcileDownloadedFirmware())
    }

    @Test
    fun `an unreadable configured directory clears nothing`() = runBlocking {
        val unmounted = File(tempDir, "never-mounted/bios")
        val absent = File(unmounted, "ps1/scph5501.bin")
        coEvery { firmwareDao.getAllDownloaded() } returns listOf(row(1, absent.absolutePath))

        val cleared = repositoryWith(unmounted.absolutePath).reconcileDownloadedFirmware()

        assertEquals(0, cleared)
        coVerify(exactly = 0) { firmwareDao.updateLocalPath(any(), null, null) }
    }

    @Test
    fun `a copy distributed into an emulator folder is never cleared`() = runBlocking {
        val elsewhere = File(tempDir, "RetroArch/system/scph5501.bin")
        coEvery { firmwareDao.getAllDownloaded() } returns listOf(row(1, elsewhere.absolutePath))

        assertEquals(0, repositoryWith(null).reconcileDownloadedFirmware())
    }
}
