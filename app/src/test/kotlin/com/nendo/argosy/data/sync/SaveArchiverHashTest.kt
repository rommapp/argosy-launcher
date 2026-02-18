package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.mockk
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

class SaveArchiverHashTest {

    private lateinit var tempDir: File
    private lateinit var saveArchiver: SaveArchiver

    @Before
    fun setup() {
        tempDir = createTempDirectory("save_archiver_hash_test").toFile()
        saveArchiver = SaveArchiver(
            androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true),
            fal = mockk<FileAccessLayer>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `calculateFileHash produces correct MD5 for known content`() {
        val file = File(tempDir, "test.bin")
        file.writeText("hello world")

        val expected = md5Hex("hello world".toByteArray())
        val result = saveArchiver.calculateFileHash(file)

        assertEquals(expected, result)
    }

    @Test
    fun `calculateZipHash sorts entries and produces content-addressable hash`() {
        val zipA = createZip("sorted_a.zip", listOf(
            "b.txt" to "content_b",
            "a.txt" to "content_a"
        ))
        val zipB = createZip("sorted_b.zip", listOf(
            "a.txt" to "content_a",
            "b.txt" to "content_b"
        ))

        val hashA = saveArchiver.calculateZipHash(zipA)
        val hashB = saveArchiver.calculateZipHash(zipB)

        assertEquals(
            "ZIP hash should be identical regardless of entry order",
            hashA, hashB
        )
    }

    @Test
    fun `calculateZipHash ignores ZIP metadata and compression differences`() {
        val zip1 = createZip("meta1.zip", listOf("file.txt" to "data"))

        val zip2 = File(tempDir, "meta2.zip")
        ZipArchiveOutputStream(
            BufferedOutputStream(FileOutputStream(zip2))
        ).use { zos ->
            val entry = ZipArchiveEntry("file.txt")
            entry.time = 1000000000L
            entry.comment = "different metadata"
            zos.putArchiveEntry(entry)
            zos.write("data".toByteArray())
            zos.closeArchiveEntry()
        }

        assertNotEquals(
            "Raw file bytes should differ due to metadata",
            zip1.readBytes().toList(),
            zip2.readBytes().toList()
        )

        assertEquals(
            "Content hash should be identical",
            saveArchiver.calculateZipHash(zip1),
            saveArchiver.calculateZipHash(zip2)
        )
    }

    @Test
    fun `calculateFolderAsZipHash matches calculateZipHash on equivalent ZIP`() {
        val folder = File(tempDir, "test_folder").apply { mkdirs() }
        File(folder, "save1.srm").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(folder, "save2.dat").writeBytes(byteArrayOf(5, 6, 7, 8))

        val zipFile = File(tempDir, "test_folder.zip")
        saveArchiver.zipFolder(folder, zipFile)

        val folderHash = saveArchiver.calculateFolderAsZipHash(folder)
        val zipHash = saveArchiver.calculateZipHash(zipFile)

        assertEquals(
            "Folder-as-zip hash should match actual zip hash",
            folderHash, zipHash
        )
    }

    @Test
    fun `calculateContentHash dispatches ZIP vs plain file correctly`() {
        val plainFile = File(tempDir, "plain.sav")
        plainFile.writeBytes(byteArrayOf(10, 20, 30))

        val zipFile = createZip("save.zip", listOf("data.bin" to "content"))

        assertEquals(
            saveArchiver.calculateFileHash(plainFile),
            saveArchiver.calculateContentHash(plainFile)
        )
        assertEquals(
            saveArchiver.calculateZipHash(zipFile),
            saveArchiver.calculateContentHash(zipFile)
        )
    }

    @Test
    fun `calculateZipHash skips directory entries`() {
        val zipFile = File(tempDir, "with_dirs.zip")
        ZipArchiveOutputStream(
            BufferedOutputStream(FileOutputStream(zipFile))
        ).use { zos ->
            zos.putArchiveEntry(ZipArchiveEntry("subdir/"))
            zos.closeArchiveEntry()

            zos.putArchiveEntry(ZipArchiveEntry("subdir/file.txt"))
            zos.write("hello".toByteArray())
            zos.closeArchiveEntry()
        }

        val zipWithoutDir = createZip(
            "without_dirs.zip",
            listOf("subdir/file.txt" to "hello")
        )

        assertEquals(
            saveArchiver.calculateZipHash(zipFile),
            saveArchiver.calculateZipHash(zipWithoutDir)
        )
    }

    @Test
    fun `calculateFolderAsZipHash with nested subdirectories`() {
        val folder = File(tempDir, "nested").apply { mkdirs() }
        File(folder, "sub").mkdirs()
        File(folder, "sub/deep.bin").writeBytes(byteArrayOf(1, 2))
        File(folder, "root.bin").writeBytes(byteArrayOf(3, 4))

        val zipFile = File(tempDir, "nested.zip")
        saveArchiver.zipFolder(folder, zipFile)

        assertEquals(
            saveArchiver.calculateFolderAsZipHash(folder),
            saveArchiver.calculateZipHash(zipFile)
        )
    }

    private fun createZip(name: String, entries: List<Pair<String, String>>): File {
        val zipFile = File(tempDir, name)
        ZipArchiveOutputStream(
            BufferedOutputStream(FileOutputStream(zipFile))
        ).use { zos ->
            for ((entryName, content) in entries) {
                zos.putArchiveEntry(ZipArchiveEntry(entryName))
                zos.write(content.toByteArray())
                zos.closeArchiveEntry()
            }
        }
        return zipFile
    }

    private fun md5Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(data)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
