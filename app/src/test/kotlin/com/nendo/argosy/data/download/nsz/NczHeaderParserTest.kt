package com.nendo.argosy.data.download.nsz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NczHeaderParserTest {

    @Test
    fun `parse reads NCZSECTN header with one section`() {
        val data = buildNczsectnHeader(
            sections = listOf(
                TestSection(
                    offset = 0x4000,
                    size = 0x100000,
                    cryptoType = 3,
                    key = ByteArray(16) { 0xAA.toByte() },
                    counter = ByteArray(16) { 0xBB.toByte() }
                )
            )
        )

        val result = NczHeaderParser.parse(ByteArrayInputStream(data))

        assertEquals(1, result.sections.size)
        val section = result.sections[0]
        assertEquals(0x4000L, section.offset)
        assertEquals(0x100000L, section.size)
        assertEquals(3L, section.cryptoType)
        assertTrue(section.needsEncryption)
        assertNull(result.blockHeader)
    }

    @Test
    fun `parse reads multiple sections`() {
        val sections = listOf(
            TestSection(0x4000, 0x100000, 3),
            TestSection(0x104000, 0x200000, 1),
            TestSection(0x304000, 0x50000, 4)
        )

        val data = buildNczsectnHeader(sections = sections)
        val result = NczHeaderParser.parse(ByteArrayInputStream(data))

        assertEquals(3, result.sections.size)
        assertEquals(0x4000L, result.sections[0].offset)
        assertEquals(0x104000L, result.sections[1].offset)
        assertEquals(0x304000L, result.sections[2].offset)
        assertTrue(result.sections[0].needsEncryption)
        assertTrue(!result.sections[1].needsEncryption)
        assertTrue(result.sections[2].needsEncryption)
    }

    @Test
    fun `parse detects NCZBLOCK header`() {
        val sectionData = buildNczsectnHeader(
            sections = listOf(
                TestSection(0x4000, 0x100000, 3)
            ),
            includeBlockHeader = true,
            blockExponent = 14,
            blockCount = 2,
            decompressedSize = 0x8000,
            compressedBlockSizes = intArrayOf(4000, 3500)
        )

        val result = NczHeaderParser.parse(
            ByteArrayInputStream(sectionData)
        )

        assertNotNull(result.blockHeader)
        val block = result.blockHeader!!
        assertEquals(14, block.blockSizeExponent)
        assertEquals(1 shl 14, block.blockSize)
        assertEquals(2, block.numberOfBlocks)
        assertEquals(0x8000L, block.decompressedSize)
        assertEquals(4000, block.compressedBlockSizes[0])
        assertEquals(3500, block.compressedBlockSizes[1])
    }

    @Test(expected = IOException::class)
    fun `parse throws on invalid magic`() {
        val data = "NOTVALID".toByteArray() + ByteArray(100)
        NczHeaderParser.parse(ByteArrayInputStream(data))
    }

    @Test(expected = IOException::class)
    fun `parse throws on excessive section count`() {
        val buf = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put("NCZSECTN".toByteArray())
        buf.putLong(999)
        NczHeaderParser.parse(ByteArrayInputStream(buf.array()))
    }

    private data class TestSection(
        val offset: Long,
        val size: Long,
        val cryptoType: Long,
        val key: ByteArray = ByteArray(16),
        val counter: ByteArray = ByteArray(16)
    )

    private fun buildNczsectnHeader(
        sections: List<TestSection>,
        includeBlockHeader: Boolean = false,
        blockExponent: Int = 14,
        blockCount: Int = 0,
        decompressedSize: Long = 0,
        compressedBlockSizes: IntArray = IntArray(0)
    ): ByteArray {
        val sectionDataSize = 8 + 8 + (sections.size * 64)
        val blockDataSize = if (includeBlockHeader) {
            8 + 4 + 4 + 8 + (blockCount * 4)
        } else {
            0
        }
        val paddingSize = if (!includeBlockHeader) 8 else 0

        val buf = ByteBuffer.allocate(
            sectionDataSize + blockDataSize + paddingSize
        ).order(ByteOrder.LITTLE_ENDIAN)

        buf.put("NCZSECTN".toByteArray())
        buf.putLong(sections.size.toLong())

        for (s in sections) {
            buf.putLong(s.offset)
            buf.putLong(s.size)
            buf.putLong(s.cryptoType)
            buf.putLong(0) // padding
            buf.put(s.key)
            buf.put(s.counter)
        }

        if (includeBlockHeader) {
            buf.put("NCZBLOCK".toByteArray())
            buf.put(1) // version
            buf.put(0) // type
            buf.put(0) // unused
            buf.put(blockExponent.toByte())
            buf.putInt(blockCount)
            buf.putLong(decompressedSize)
            for (size in compressedBlockSizes) {
                buf.putInt(size)
            }
        } else {
            buf.put(ByteArray(8))
        }

        return buf.array()
    }
}
