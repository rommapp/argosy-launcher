package com.nendo.argosy.data.download.nsz

import com.github.luben.zstd.Zstd
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.createTempDirectory

/**
 * Builds a synthetic NSP, derives an NSZ from it, and asserts the
 * decompressor reproduces the NSP byte for byte. The container carries a
 * padded PFS0 string table, a gap before the first file, and an NCZ whose
 * sections mix a plaintext crypto type with AES-CTR.
 */
class NszRoundTripTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDirectory("nsz_round_trip").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `solid NSZ decompresses to the original NSP byte for byte`() {
        val fixture = buildFixture(blockSizeExponent = null)

        val result = NszDecompressor.decompress(fixture.nszFile, null)

        assertEquals("game.nsp", result.name)
        assertArrayEquals(fixture.expectedNsp, result.readBytes())
    }

    @Test
    fun `block NSZ decompresses to the original NSP byte for byte`() {
        val fixture = buildFixture(blockSizeExponent = 14)

        val result = NszDecompressor.decompress(fixture.nszFile, null)

        assertArrayEquals(fixture.expectedNsp, result.readBytes())
    }

    @Test
    fun `measured size matches the size actually written`() {
        val fixture = buildFixture(blockSizeExponent = null)

        val measured = NszDecompressor.measureDecompressedSize(fixture.nszFile)
        val result = NszDecompressor.decompress(fixture.nszFile, null)

        assertEquals(fixture.expectedNsp.size.toLong(), measured)
        assertEquals(fixture.expectedNsp.size.toLong(), result.length())
    }

    @Test
    fun `progress never reports more than the total`() {
        val fixture = buildFixture(blockSizeExponent = 14)

        var last = 0L
        NszDecompressor.decompress(fixture.nszFile) { written, total ->
            assertTrue("progress $written exceeded total $total", written <= total)
            last = written
        }

        assertEquals(fixture.expectedNsp.size.toLong(), last)
    }

    @Test
    fun `truncated payload fails instead of writing a short NSP`() {
        val fixture = buildFixture(blockSizeExponent = null)
        val full = fixture.nszFile.readBytes()
        fixture.nszFile.writeBytes(full.copyOf(full.size - 64))

        val error = runCatching {
            NszDecompressor.decompress(fixture.nszFile, null)
        }.exceptionOrNull()

        assertTrue("expected an IOException, got $error", error is IOException)
        assertTrue(
            "temp output should not be left behind",
            tempDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") }
        )
    }

    /**
     * nsz only ever decrypts CTR sections, so a section of any other type is still ciphertext
     * inside the NCZ and has to be written back untouched. Re-encrypting it would corrupt it,
     * and rejecting the container would fail a file the reference implementation decompresses.
     */
    @Test
    fun `a non CTR section is written back verbatim`() {
        val fixture = buildFixture(blockSizeExponent = null, cryptoTypeOverride = 2)

        val result = NszDecompressor.decompress(fixture.nszFile, null)

        assertArrayEquals(fixture.expectedNsp, result.readBytes())
    }

    @Test
    fun `skip layer hash crypto types are re-encrypted like plain CTR`() {
        for (cryptoType in listOf(4L, 5L, 6L)) {
            tempDir.listFiles().orEmpty().forEach { it.delete() }
            val fixture = buildFixture(
                blockSizeExponent = null,
                cryptoTypeOverride = cryptoType
            )

            val result = NszDecompressor.decompress(fixture.nszFile, null)

            assertArrayEquals(
                "crypto type $cryptoType round trip",
                fixture.expectedNsp,
                result.readBytes()
            )
        }
    }

    @Test
    fun `block mode rejects a body shorter than the header claims`() {
        val fixture = buildFixture(blockSizeExponent = 14)
        val bytes = fixture.nszFile.readBytes()
        val blockHeader = indexOfMagic(bytes, "NCZBLOCK")
        val sizeField = blockHeader + 16
        val claimed = ByteBuffer.wrap(bytes, sizeField, 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .long
        ByteBuffer.wrap(bytes, sizeField, 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(claimed + 0x1000)
        fixture.nszFile.writeBytes(bytes)

        val error = runCatching {
            NszDecompressor.decompress(fixture.nszFile, null)
        }.exceptionOrNull()

        assertTrue("expected an IOException, got $error", error is IOException)
    }

    @Test
    fun `payload decoding stops at the end of its own entry`() {
        val sections = listOf(
            SectionSpec(
                offset = NCA_HEADER,
                cryptoType = 1L,
                plaintext = ByteArray(PLAIN_SIZE) { (it * 7).toByte() }
            ),
            SectionSpec(
                offset = NCA_HEADER + PLAIN_SIZE,
                cryptoType = 3L,
                plaintext = ByteArray(CTR_SIZE) { (it * 13 + 5).toByte() }
            )
        )
        var body = ByteArray(0)
        sections.forEach { body += it.plaintext }

        val split = PLAIN_SIZE
        val ncz = ByteArray(NCA_HEADER.toInt()) { (it % 97).toByte() } +
            buildSectionHeader(sections) +
            zstd(body.copyOfRange(0, split))
        val strandedFrame = zstd(body.copyOfRange(split, body.size))

        val nszFile = File(tempDir, "game.nsz")
        nszFile.writeBytes(
            buildPfs0(listOf("game.ncz" to ncz, "tail.bin" to strandedFrame))
        )

        val error = runCatching {
            NszDecompressor.decompress(nszFile, null)
        }.exceptionOrNull()

        assertTrue(
            "reading past the entry would have silently completed the NCA, " +
                "got $error",
            error is IOException
        )
    }

    private fun indexOfMagic(bytes: ByteArray, magic: String): Int {
        val needle = magic.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..bytes.size - needle.size) {
            for (j in needle.indices) {
                if (bytes[i + j] != needle[j]) continue@outer
            }
            return i
        }
        throw AssertionError("$magic not found in fixture")
    }

    private data class Fixture(val nszFile: File, val expectedNsp: ByteArray)

    private fun buildFixture(
        blockSizeExponent: Int?,
        cryptoTypeOverride: Long? = null
    ): Fixture {
        val sections = listOf(
            SectionSpec(
                offset = NCA_HEADER,
                cryptoType = 1L,
                plaintext = ByteArray(PLAIN_SIZE) { (it * 7).toByte() }
            ),
            SectionSpec(
                offset = NCA_HEADER + PLAIN_SIZE,
                cryptoType = cryptoTypeOverride ?: 3L,
                plaintext = ByteArray(CTR_SIZE) { (it * 13 + 5).toByte() }
            )
        )

        val ncaPrefix = ByteArray(NCA_HEADER.toInt()) { (it % 97).toByte() }
        var nca = ncaPrefix
        for (section in sections) {
            nca += if (section.cryptoType in 3L..6L) {
                encryptCtr(section.offset, section.plaintext)
            } else {
                section.plaintext
            }
        }

        val cnmt = ByteArray(0x400) { 0x5A }
        val ncz = buildNcz(ncaPrefix, sections, blockSizeExponent)

        val nszFile = File(tempDir, "game.nsz")
        nszFile.writeBytes(
            buildPfs0(listOf("meta.cnmt.nca" to cnmt, "game.ncz" to ncz))
        )
        return Fixture(
            nszFile,
            buildPfs0(listOf("meta.cnmt.nca" to cnmt, "game.nca" to nca))
        )
    }

    private data class SectionSpec(
        val offset: Long,
        val cryptoType: Long,
        val plaintext: ByteArray
    ) {
        val size: Long get() = plaintext.size.toLong()
    }

    private fun encryptCtr(offset: Long, plaintext: ByteArray): ByteArray {
        val iv = CRYPTO_COUNTER.copyOf()
        ByteBuffer.wrap(iv, 8, 8).order(ByteOrder.BIG_ENDIAN).putLong(offset / 16)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(CRYPTO_KEY, "AES"),
            IvParameterSpec(iv)
        )
        return cipher.doFinal(plaintext)
    }

    private fun buildSectionHeader(sections: List<SectionSpec>): ByteArray {
        val headerBuf = ByteBuffer.allocate(16 + sections.size * 64)
            .order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.put("NCZSECTN".toByteArray(Charsets.US_ASCII))
        headerBuf.putLong(sections.size.toLong())
        for (section in sections) {
            headerBuf.putLong(section.offset)
            headerBuf.putLong(section.size)
            headerBuf.putLong(section.cryptoType)
            headerBuf.putLong(0)
            headerBuf.put(CRYPTO_KEY)
            headerBuf.put(CRYPTO_COUNTER)
        }
        return headerBuf.array()
    }

    private fun buildNcz(
        ncaPrefix: ByteArray,
        sections: List<SectionSpec>,
        blockSizeExponent: Int?
    ): ByteArray {
        val sectionHeader = buildSectionHeader(sections)

        var body = ByteArray(0)
        sections.forEach { body += it.plaintext }

        if (blockSizeExponent == null) {
            return ncaPrefix + sectionHeader + zstd(body)
        }

        val blockSize = 1 shl blockSizeExponent
        val blocks = (body.indices step blockSize).map {
            zstd(body.copyOfRange(it, minOf(it + blockSize, body.size)))
        }
        val blockBuf = ByteBuffer.allocate(NCZBLOCK_HEADER_SIZE + blocks.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        blockBuf.put("NCZBLOCK".toByteArray(Charsets.US_ASCII))
        blockBuf.put(1)
        blockBuf.put(0)
        blockBuf.put(0)
        blockBuf.put(blockSizeExponent.toByte())
        blockBuf.putInt(blocks.size)
        blockBuf.putLong(body.size.toLong())
        blocks.forEach { blockBuf.putInt(it.size) }

        var payload = ByteArray(0)
        blocks.forEach { payload += it }
        return ncaPrefix + sectionHeader + blockBuf.array() + payload
    }

    private fun zstd(data: ByteArray): ByteArray {
        val out = ByteArray(Zstd.compressBound(data.size.toLong()).toInt())
        return out.copyOf(Zstd.compress(out, data, 3).toInt())
    }

    private fun buildPfs0(files: List<Pair<String, ByteArray>>): ByteArray {
        val stringTable = ByteArray(STRING_TABLE_SIZE)
        var cursor = 0
        val nameOffsets = files.map { (name, _) ->
            val at = cursor
            val raw = name.toByteArray(Charsets.US_ASCII)
            raw.copyInto(stringTable, at)
            cursor += raw.size + 1
            at
        }

        val headerSize = PFS0_HEADER_BASE + files.size * PFS0_ENTRY_SIZE +
            stringTable.size
        val buf = ByteBuffer
            .allocate(
                headerSize + FIRST_FILE_GAP.toInt() + files.sumOf { it.second.size }
            )
            .order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(PFS0_MAGIC)
        buf.putInt(files.size)
        buf.putInt(stringTable.size)
        buf.putInt(0)

        var dataOffset = FIRST_FILE_GAP
        for (i in files.indices) {
            buf.putLong(dataOffset)
            buf.putLong(files[i].second.size.toLong())
            buf.putInt(nameOffsets[i])
            buf.putInt(0)
            dataOffset += files[i].second.size
        }

        buf.put(stringTable)
        buf.put(ByteArray(FIRST_FILE_GAP.toInt()))
        files.forEach { buf.put(it.second) }
        return buf.array()
    }

    private companion object {
        const val NCA_HEADER = 0x4000L
        const val PLAIN_SIZE = 0x8000
        const val CTR_SIZE = 0x14000
        const val STRING_TABLE_SIZE = 0x30
        const val FIRST_FILE_GAP = 0x200L
        const val PFS0_MAGIC = 0x30534650
        const val PFS0_HEADER_BASE = 16
        const val PFS0_ENTRY_SIZE = 24
        const val NCZBLOCK_HEADER_SIZE = 24
        val CRYPTO_KEY = ByteArray(16) { (it * 11 + 3).toByte() }
        val CRYPTO_COUNTER = ByteArray(16) { if (it < 8) (it + 2).toByte() else 0 }
    }
}
