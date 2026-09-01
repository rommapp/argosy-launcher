package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.entity.FirmwareEntity
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.createTempDirectory

class BiosDistributedCopyTest {

    private lateinit var tempDir: File
    private lateinit var repository: BiosRepository

    @Before
    fun setUp() {
        tempDir = createTempDirectory("bios_distributed").toFile()
        repository = BiosRepository(
            context = mockk<Context>(relaxed = true),
            firmwareDao = mockk(relaxed = true),
            platformDao = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
            switchKeyManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            xboxDiskImageProvisioner = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun md5Of(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun firmware(sizeBytes: Long, md5: String?) = FirmwareEntity(
        id = 1,
        platformId = 1,
        platformSlug = "ps1",
        rommId = 1,
        fileName = "scph5501.bin",
        filePath = "bios/ps1/scph5501.bin",
        fileSizeBytes = sizeBytes,
        md5Hash = md5,
        sha1Hash = null,
        localPath = null,
        downloadedAt = Instant.now(),
        lastVerifiedAt = null
    )

    private fun write(name: String, content: ByteArray): File =
        File(tempDir, name).apply { writeBytes(content) }

    @Test
    fun `removes the copy argosy distributed`() {
        val bytes = ByteArray(512) { it.toByte() }
        val target = write("scph5501.bin", bytes)

        repository.deleteDistributedCopy(target, firmware(bytes.size.toLong(), md5Of(bytes)))

        assertFalse(target.exists())
    }

    @Test
    fun `keeps a different file that happens to share the name`() {
        val theirs = ByteArray(1024) { 0x7F }
        val target = write("scph5501.bin", theirs)
        val ours = ByteArray(512) { it.toByte() }

        repository.deleteDistributedCopy(target, firmware(ours.size.toLong(), md5Of(ours)))

        assertTrue("a file Argosy never wrote must survive", target.exists())
    }

    @Test
    fun `keeps a same sized file whose contents differ`() {
        val theirs = ByteArray(512) { 0x11 }
        val target = write("scph5501.bin", theirs)
        val ours = ByteArray(512) { it.toByte() }

        repository.deleteDistributedCopy(target, firmware(512, md5Of(ours)))

        assertTrue("size agreeing is not identity", target.exists())
    }

    @Test
    fun `falls back to size when the row carries no hash`() {
        val bytes = ByteArray(512) { it.toByte() }
        val target = write("scph5501.bin", bytes)

        repository.deleteDistributedCopy(target, firmware(bytes.size.toLong(), null))

        assertFalse(target.exists())
    }

    @Test
    fun `a missing target is not an error`() {
        val absent = File(tempDir, "never-written.bin")

        repository.deleteDistributedCopy(absent, firmware(512, null))

        assertFalse(absent.exists())
    }
}
