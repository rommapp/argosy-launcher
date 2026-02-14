package com.nendo.argosy.data.download.nsz.integration

import com.github.luben.zstd.Zstd
import com.nendo.argosy.data.download.nsz.ContainerParser
import com.nendo.argosy.data.download.nsz.NczHeaderParser
import com.nendo.argosy.data.download.nsz.NszDecompressor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

/**
 * Builds a synthetic NSZ file in-memory and verifies decompression
 * produces a structurally valid NSP.
 *
 * The synthetic NCZ contains a fixed NCA header + NCZSECTN header
 * (with cryptoType=0, no encryption) + zstd-compressed body.
 *
 * Run with: ./gradlew test -PrunIntegrationTests
 */
class NszDecompressorIntegrationTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDirectory("nsz_integration_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `decompress synthetic NSZ with solid compression`() {
        val ncaBody = ByteArray(0x10000) { (it % 256).toByte() }
        val nszFile = buildSyntheticNsz(
            ncaBody = ncaBody,
            useBlockCompression = false
        )

        assertTrue(NszDecompressor.isCompressedNsw(nszFile))

        var progressCalled = false
        val result = NszDecompressor.decompress(nszFile) { written, total ->
            progressCalled = true
            assertTrue(
                "Progress should not exceed total",
                written <= total + 1024
            )
        }

        assertTrue("Output NSP should exist", result.exists())
        assertTrue("Output should be .nsp", result.name.endsWith(".nsp"))
        assertFalse("Original NSZ should be deleted", nszFile.exists())
        assertTrue("Progress callback should fire", progressCalled)

        val raf = RandomAccessFile(result, "r")
        val entries = ContainerParser.parsePfs0(raf)
        assertEquals(1, entries.size)
        assertEquals("test.nca", entries[0].name)

        val expectedSize = NczHeaderParser.NCA_HEADER_SIZE + ncaBody.size
        assertEquals(expectedSize, entries[0].size)

        val ncaHeader = ByteArray(NczHeaderParser.NCA_HEADER_SIZE.toInt())
        raf.seek(entries[0].dataOffset)
        raf.readFully(ncaHeader)
        assertTrue(
            "NCA header should be preserved",
            ncaHeader.all { it == 0xCA.toByte() }
        )

        val body = ByteArray(ncaBody.size)
        raf.readFully(body)
        assertTrue(
            "NCA body should match original",
            body.contentEquals(ncaBody)
        )

        raf.close()
    }

    @Test
    fun `decompress synthetic NSZ with block compression`() {
        val ncaBody = ByteArray(0x20000) { (it % 256).toByte() }
        val nszFile = buildSyntheticNsz(
            ncaBody = ncaBody,
            useBlockCompression = true
        )

        val result = NszDecompressor.decompress(nszFile, null)

        assertTrue(result.exists())

        val raf = RandomAccessFile(result, "r")
        val entries = ContainerParser.parsePfs0(raf)
        assertEquals(1, entries.size)

        val body = ByteArray(ncaBody.size)
        raf.seek(
            entries[0].dataOffset + NczHeaderParser.NCA_HEADER_SIZE
        )
        raf.readFully(body)
        assertTrue(
            "Block-decompressed body should match original",
            body.contentEquals(ncaBody)
        )
        raf.close()
    }

    @Test
    fun `decompress NSZ with passthrough (non-NCZ) entry`() {
        val plainData = ByteArray(512) { 0x42 }
        val ncaBody = ByteArray(0x8000) { (it % 256).toByte() }
        val nszFile = buildSyntheticNszMultiEntry(
            plainEntryName = "meta.cnmt",
            plainEntryData = plainData,
            nczEntryName = "game.ncz",
            ncaBody = ncaBody
        )

        val result = NszDecompressor.decompress(nszFile, null)
        assertTrue(result.exists())

        val raf = RandomAccessFile(result, "r")
        val entries = ContainerParser.parsePfs0(raf)
        assertEquals(2, entries.size)

        assertEquals("meta.cnmt", entries[0].name)
        val readPlain = ByteArray(plainData.size)
        raf.seek(entries[0].dataOffset)
        raf.readFully(readPlain)
        assertTrue(readPlain.contentEquals(plainData))

        assertEquals("game.nca", entries[1].name)
        raf.close()
    }

    private fun buildSyntheticNsz(
        ncaBody: ByteArray,
        useBlockCompression: Boolean
    ): File {
        val ncaHeader = ByteArray(
            NczHeaderParser.NCA_HEADER_SIZE.toInt()
        ) { 0xCA.toByte() }

        val nczData = buildNczData(
            ncaHeader, ncaBody, useBlockCompression
        )

        return buildNszFromEntries(
            listOf(Pair("test.ncz", nczData))
        )
    }

    private fun buildSyntheticNszMultiEntry(
        plainEntryName: String,
        plainEntryData: ByteArray,
        nczEntryName: String,
        ncaBody: ByteArray
    ): File {
        val ncaHeader = ByteArray(
            NczHeaderParser.NCA_HEADER_SIZE.toInt()
        ) { 0xCA.toByte() }

        val nczData = buildNczData(ncaHeader, ncaBody, false)

        return buildNszFromEntries(
            listOf(
                Pair(plainEntryName, plainEntryData),
                Pair(nczEntryName, nczData)
            )
        )
    }

    private fun buildNczData(
        ncaHeader: ByteArray,
        ncaBody: ByteArray,
        useBlockCompression: Boolean
    ): ByteArray {
        val sectionOffset = NczHeaderParser.NCA_HEADER_SIZE
        val sectionSize = ncaBody.size.toLong()

        val sectionsBuf = ByteBuffer.allocate(16 + 64)
            .order(ByteOrder.LITTLE_ENDIAN)
        sectionsBuf.put("NCZSECTN".toByteArray())
        sectionsBuf.putLong(1) // section count

        sectionsBuf.putLong(sectionOffset) // offset
        sectionsBuf.putLong(sectionSize)   // size
        sectionsBuf.putLong(0)             // cryptoType = none
        sectionsBuf.putLong(0)             // padding
        sectionsBuf.put(ByteArray(16))     // key
        sectionsBuf.put(ByteArray(16))     // counter

        val sections = sectionsBuf.array()

        return if (useBlockCompression) {
            val blockSize = 0x4000
            val blocks = ncaBody.toList()
                .chunked(blockSize)
                .map { it.toByteArray() }

            val compressedBlocks = blocks.map { block ->
                val maxSize = Zstd.compressBound(block.size.toLong()).toInt()
                val compressed = ByteArray(maxSize)
                val compSize = Zstd.compress(
                    compressed, block, 3
                )
                compressed.copyOf(compSize.toInt())
            }

            val blockHeaderBuf = ByteBuffer.allocate(
                8 + 4 + 4 + 8 + (compressedBlocks.size * 4)
            ).order(ByteOrder.LITTLE_ENDIAN)

            blockHeaderBuf.put("NCZBLOCK".toByteArray())
            blockHeaderBuf.put(1)  // version
            blockHeaderBuf.put(0)  // type
            blockHeaderBuf.put(0)  // unused
            blockHeaderBuf.put(14) // exponent (1 << 14 = 0x4000)
            blockHeaderBuf.putInt(compressedBlocks.size)
            blockHeaderBuf.putLong(ncaBody.size.toLong())
            for (cb in compressedBlocks) {
                blockHeaderBuf.putInt(cb.size)
            }

            val blockHeader = blockHeaderBuf.array()
            val allCompressed = compressedBlocks.fold(ByteArray(0)) { acc, b ->
                acc + b
            }

            ncaHeader + sections + blockHeader + allCompressed
        } else {
            val maxSize = Zstd.compressBound(
                ncaBody.size.toLong()
            ).toInt()
            val compressed = ByteArray(maxSize)
            val compSize = Zstd.compress(compressed, ncaBody, 3)
            val zstdData = compressed.copyOf(compSize.toInt())

            ncaHeader + sections + zstdData
        }
    }

    private fun buildNszFromEntries(
        files: List<Pair<String, ByteArray>>
    ): File {
        val stringTable = buildStringTable(files.map { it.first })
        val headerSize = 16 + (files.size * 24) + stringTable.size

        val totalSize = headerSize + files.sumOf { it.second.size }
        val buf = ByteBuffer.allocate(totalSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(0x30534650) // PFS0
        buf.putInt(files.size)
        buf.putInt(stringTable.size)
        buf.putInt(0)

        var dataOffset = 0L
        var stringOffset = 0
        for ((name, data) in files) {
            buf.putLong(dataOffset)
            buf.putLong(data.size.toLong())
            buf.putInt(stringOffset)
            buf.putInt(0)
            dataOffset += data.size
            stringOffset += name.toByteArray(Charsets.US_ASCII).size + 1
        }

        buf.put(stringTable)
        for ((_, data) in files) {
            buf.put(data)
        }

        val nszFile = File(tempDir, "test_game.nsz")
        nszFile.writeBytes(buf.array())
        return nszFile
    }

    private fun buildStringTable(names: List<String>): ByteArray {
        val sb = mutableListOf<Byte>()
        for (name in names) {
            sb.addAll(name.toByteArray(Charsets.US_ASCII).toList())
            sb.add(0)
        }
        return sb.toByteArray()
    }
}
