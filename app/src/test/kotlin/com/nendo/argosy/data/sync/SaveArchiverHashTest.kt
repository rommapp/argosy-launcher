package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import io.mockk.every
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
        val fal = mockk<FileAccessLayer>(relaxed = true)
        every { fal.listFilesUnion(any()) } answers {
            val path = firstArg<String>()
            File(path).listFiles()?.map {
                FileInfo(
                    path = it.absolutePath,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    isFile = it.isFile,
                    size = it.length(),
                    lastModified = it.lastModified()
                )
            } ?: emptyList()
        }
        every { fal.getInputStream(any()) } answers {
            val path = firstArg<String>()
            val file = File(path)
            if (file.exists() && file.canRead()) file.inputStream() else null
        }
        every { fal.exists(any()) } answers { File(firstArg<String>()).exists() }
        every { fal.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        saveArchiver = SaveArchiver(
            androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true),
            fal = fal
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

    @Test
    fun `zip and folder hash stay in sync for union-only files`() {
        val folder = File(tempDir, "union").apply { mkdirs() }
        File(folder, "visible.bin").writeBytes(byteArrayOf(1, 2, 3))

        val ghostPath = "${folder.absolutePath}/ghost.bin"
        val ghostBytes = byteArrayOf(9, 9, 9, 9)
        val ghostBacking = File(tempDir, "ghost_backing.bin").apply { writeBytes(ghostBytes) }

        val unionFal = mockk<FileAccessLayer>(relaxed = true)
        every { unionFal.listFilesUnion(folder.absolutePath) } returns listOf(
            FileInfo(
                path = File(folder, "visible.bin").absolutePath,
                name = "visible.bin",
                isDirectory = false,
                isFile = true,
                size = 3,
                lastModified = 0
            ),
            FileInfo(
                path = ghostPath,
                name = "ghost.bin",
                isDirectory = false,
                isFile = true,
                size = ghostBytes.size.toLong(),
                lastModified = 0
            )
        )
        every { unionFal.getInputStream(any()) } answers {
            val p = firstArg<String>()
            if (p == ghostPath) ghostBacking.inputStream() else File(p).inputStream()
        }
        every { unionFal.exists(any()) } answers { firstArg<String>() == folder.absolutePath || File(firstArg<String>()).exists() }
        every { unionFal.isDirectory(any()) } answers { firstArg<String>() == folder.absolutePath || File(firstArg<String>()).isDirectory }

        val unionArchiver = SaveArchiver(
            androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true),
            fal = unionFal
        )

        val zipFile = File(tempDir, "union.zip")
        unionArchiver.zipFolder(folder, zipFile)

        val zipEntryNames = mutableListOf<String>()
        org.apache.commons.compress.archivers.zip.ZipFile.builder().setFile(zipFile).get().use { zf ->
            zf.entries.iterator().forEach { zipEntryNames.add(it.name) }
        }

        assertEquals(
            "Zip must include the union-only ghost entry",
            setOf("union/visible.bin", "union/ghost.bin"),
            zipEntryNames.toSet()
        )
        assertEquals(
            "Folder hash must match zip hash when both see the ghost file",
            unionArchiver.calculateFolderAsZipHash(folder),
            unionArchiver.calculateZipHash(zipFile)
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
