package com.nendo.argosy.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import com.nendo.argosy.data.emulator.SwitchKeyManager
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class BiosSwitchInstallTest {

    private lateinit var tempDir: File
    private lateinit var fal: FileAccessLayer
    private lateinit var repository: BiosRepository
    private val written = mutableMapOf<String, ByteArrayOutputStream>()

    @Before
    fun setUp() {
        tempDir = createTempDirectory("bios_switch").toFile()
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns File(tempDir, "files").apply { mkdirs() }
        every { context.packageManager.getPackageInfo(any<String>(), any<Int>()) } returns PackageInfo()

        val prodKeys = File(tempDir, "prod.keys").apply { writeText("header_key = 00") }
        val firmwareZip = File(tempDir, "Firmware.zip").apply {
            ZipOutputStream(outputStream()).use { zip ->
                listOf("a.nca", "b.cnmt.nca").forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(name.toByteArray())
                    zip.closeEntry()
                }
            }
        }
        val firmwareDao = mockk<FirmwareDao>(relaxed = true)
        coEvery { firmwareDao.getByPlatformSlug("switch") } returns listOf(
            firmware(1, "prod.keys", prodKeys),
            firmware(2, "Firmware.zip", firmwareZip)
        )
        val keys = mockk<SwitchKeyManager>(relaxed = true)
        every { keys.validateKeysForFirmware(any(), any()) } returns true

        fal = mockk(relaxed = true)
        every { fal.mkdirs(any()) } returns true
        every { fal.writeBytes(any(), any()) } returns true
        every { fal.getOutputStream(any()) } answers {
            ByteArrayOutputStream().also { written[firstArg()] = it }
        }

        repository = BiosRepository(
            context = context,
            firmwareDao = firmwareDao,
            platformDao = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
            switchKeyManager = keys,
            attributionRepository = mockk(relaxed = true),
            fal = fal
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun firmware(id: Long, name: String, file: File) = FirmwareEntity(
        id = id,
        platformId = 20,
        platformSlug = "switch",
        rommId = id,
        fileName = name,
        filePath = "bios/switch/$name",
        fileSizeBytes = file.length(),
        md5Hash = null,
        sha1Hash = null,
        localPath = file.absolutePath,
        downloadedAt = Instant.now(),
        lastVerifiedAt = null
    )

    @Test
    fun `keys and every firmware file reach Eden through the file access layer`() = runBlocking {
        val result = repository.installSwitchFirmware()

        assertEquals(BiosRepository.SwitchInstallResult.Success, result)
        verify { fal.writeBytes(match { it.endsWith("/keys/prod.keys") }, any()) }
        assertEquals(
            setOf("a.nca", "b.cnmt.nca"),
            written.keys.filter { "/nand/system/Contents/registered/" in it }.map { it.substringAfterLast('/') }.toSet()
        )
    }

    @Test
    fun `a refused key write is an error, not a success`() = runBlocking {
        every { fal.writeBytes(match { it.endsWith("/keys/prod.keys") }, any()) } returns false

        val result = repository.installSwitchFirmware()

        assertTrue(result is BiosRepository.SwitchInstallResult.Error)
    }

    @Test
    fun `a refused firmware write is an error, not a success`() = runBlocking {
        every { fal.getOutputStream(any()) } returns null

        val result = repository.installSwitchFirmware()

        assertTrue(result is BiosRepository.SwitchInstallResult.Error)
    }

    @Test
    fun `a folder that cannot be created is an error`() = runBlocking {
        every { fal.mkdirs(any()) } returns false
        every { fal.isDirectory(any()) } returns false

        val result = repository.installSwitchFirmware()

        assertTrue(result is BiosRepository.SwitchInstallResult.Error)
    }
}
