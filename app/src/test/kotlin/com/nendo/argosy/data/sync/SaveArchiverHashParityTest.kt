package com.nendo.argosy.data.sync

import io.mockk.mockk
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveArchiverHashParityTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val archiver = SaveArchiver(mockk(relaxed = true), mockk(relaxed = true))

    @Test
    fun `calculateZipHash matches RomM server _compute_zip_hash for known vectors`() {
        val zipFile = tempFolder.newFile("test.zip")
        ZipArchiveOutputStream(zipFile).use { zos ->
            writeEntry(zos, "a.sav", byteArrayOf(0x00, 0x01, 0x02))
            writeEntry(zos, "b.sav", byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        }

        val expected = "fe72f8d850245659647bd6b5f3577a7a"
        val actual = archiver.calculateZipHash(zipFile)

        assertEquals(
            "Client hash must match server _compute_zip_hash output. " +
                "Drift indicates either entry-name encoding mismatch, " +
                "directory-flag mismatch, or content-read mismatch.",
            expected,
            actual
        )
    }

    private fun writeEntry(zos: ZipArchiveOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipArchiveEntry(name)
        entry.size = bytes.size.toLong()
        zos.putArchiveEntry(entry)
        zos.write(bytes)
        zos.closeArchiveEntry()
    }
}
