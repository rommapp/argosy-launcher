package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.fixtures.realFsFal
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.createTempDirectory

/** Pins SaveArchiver.calculateZipHash to digests independently produced from the documented zip-hash protocol. */
class SaveArchiverTest {

    private lateinit var tempDir: File
    private lateinit var saveArchiver: SaveArchiver

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_archiver_pinned").toFile()
        saveArchiver = SaveArchiver(
            androidDataAccessor = io.mockk.mockk<AndroidDataAccessor>(relaxed = true),
            fal = realFsFal()
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `fixture A single save_bin entry hashes to pinned digest`() {
        val zip = File(tempDir, "fixtureA.zip")
        writeZipEntries(zip, listOf(
            "save.bin" to ByteArray(256) { 0x42 }
        ))

        val hash = saveArchiver.calculateZipHash(zip)

        assertEquals("b3636b49ca5c3d807adee33e75d410ca", hash)
    }

    @Test
    fun `fixture B mixed nested and top entries hash to pinned digest`() {
        val zip = File(tempDir, "fixtureB.zip")
        writeZipEntries(zip, listOf(
            "inner/a.txt" to "alpha".toByteArray(Charsets.US_ASCII),
            "inner/b.txt" to "beta".toByteArray(Charsets.US_ASCII),
            "top.bin" to byteArrayOf(0x00, 0x01, 0x02)
        ))

        val hash = saveArchiver.calculateZipHash(zip)

        assertEquals("8cf6bb36a82a5ee4d7d15fc98599908d", hash)
    }

    @Test
    fun `fixture C nested Switch-shape folder hashes to pinned digest via zipFolder`() {
        val titleId = "0100F2C0115B6000"
        val folder = File(tempDir, titleId).apply { mkdirs() }

        File(folder, "NX6400000-SYSTEM").mkdirs()
        File(folder, "NX6400000-SYSTEM/SYDAT.BIN").writeBytes("system data v1".toByteArray(Charsets.US_ASCII))

        File(folder, "album").mkdirs()
        File(folder, "album/000_Photo.jpg").writeBytes(repeatBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + "jpegdata".toByteArray(Charsets.US_ASCII), 8))
        File(folder, "album/000_Thumb.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + "thumbdata".toByteArray(Charsets.US_ASCII))

        File(folder, "slot_01").mkdirs()
        File(folder, "slot_01/caption.sav").writeBytes("slot1 caption".toByteArray(Charsets.US_ASCII))
        File(folder, "slot_01/progress.sav").writeBytes(ByteArray(64) { 0x01 })

        File(folder, "slot_02").mkdirs()
        File(folder, "slot_02/caption.sav").writeBytes("slot2 caption".toByteArray(Charsets.US_ASCII))
        File(folder, "slot_02/progress.sav").writeBytes(ByteArray(64) { 0x02 })

        File(folder, "storage").mkdirs()
        File(folder, "storage/CacheStorageKey.dat").writeBytes("key=abcd1234".toByteArray(Charsets.US_ASCII))
        File(folder, "storage/empty.dat").writeBytes(ByteArray(0))

        File(folder, "Pokémon.dat").writeBytes("unicode-name".toByteArray(Charsets.US_ASCII))

        val zip = File(tempDir, "fixtureC.zip")
        val zipped = saveArchiver.zipFolder(folder, zip)
        assertTrue("zipFolder must succeed", zipped)

        val hash = saveArchiver.calculateZipHash(zip)

        assertEquals("c0c992d1f1f883f56065bb13b68dfdee", hash)
    }

    private fun writeZipEntries(target: File, entries: List<Pair<String, ByteArray>>) {
        target.parentFile?.mkdirs()
        ZipArchiveOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zos ->
            for ((name, bytes) in entries) {
                zos.putArchiveEntry(ZipArchiveEntry(name))
                zos.write(bytes)
                zos.closeArchiveEntry()
            }
        }
    }

    private fun repeatBytes(source: ByteArray, times: Int): ByteArray {
        val out = ByteArray(source.size * times)
        for (i in 0 until times) {
            System.arraycopy(source, 0, out, i * source.size, source.size)
        }
        return out
    }
}
