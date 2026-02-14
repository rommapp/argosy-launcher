package com.nendo.argosy.data.download.nsz

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class NczSection(
    val offset: Long,
    val size: Long,
    val cryptoType: Long,
    val padding: Long,
    val cryptoKey: ByteArray,
    val cryptoCounter: ByteArray
) {
    val needsEncryption: Boolean
        get() = cryptoType == 3L || cryptoType == 4L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NczSection) return false
        return offset == other.offset &&
            size == other.size &&
            cryptoType == other.cryptoType
    }

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + cryptoType.hashCode()
        return result
    }
}

data class NczBlockHeader(
    val blockSizeExponent: Int,
    val numberOfBlocks: Int,
    val decompressedSize: Long,
    val compressedBlockSizes: IntArray
) {
    val blockSize: Int
        get() = 1 shl blockSizeExponent

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NczBlockHeader) return false
        return blockSizeExponent == other.blockSizeExponent &&
            numberOfBlocks == other.numberOfBlocks &&
            decompressedSize == other.decompressedSize
    }

    override fun hashCode(): Int {
        var result = blockSizeExponent
        result = 31 * result + numberOfBlocks
        result = 31 * result + decompressedSize.hashCode()
        return result
    }
}

data class NczHeaderData(
    val sections: List<NczSection>,
    val blockHeader: NczBlockHeader?,
    val compressedDataOffset: Long
)

object NczHeaderParser {

    const val NCA_HEADER_SIZE = 0x4000L
    private const val SECTION_ENTRY_SIZE = 64
    private const val MAX_SECTION_COUNT = 100

    private val NCZSECTN_MAGIC = "NCZSECTN".toByteArray(Charsets.US_ASCII)
    private val NCZBLOCK_MAGIC = "NCZBLOCK".toByteArray(Charsets.US_ASCII)

    fun parse(input: InputStream): NczHeaderData {
        var bytesRead = 0L

        val magicBytes = readExact(input, 8)
        bytesRead += 8
        if (!magicBytes.contentEquals(NCZSECTN_MAGIC)) {
            throw IOException(
                "Invalid NCZ: expected NCZSECTN magic, got " +
                    magicBytes.toString(Charsets.US_ASCII)
            )
        }

        val sectionCountBytes = readExact(input, 8)
        bytesRead += 8
        val sectionCount = ByteBuffer.wrap(sectionCountBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .long

        if (sectionCount < 0 || sectionCount > MAX_SECTION_COUNT) {
            throw IOException(
                "Invalid NCZ section count: $sectionCount"
            )
        }

        val sections = (0 until sectionCount.toInt()).map {
            val entryBytes = readExact(input, SECTION_ENTRY_SIZE)
            bytesRead += SECTION_ENTRY_SIZE
            parseSection(entryBytes)
        }

        val possibleMagic = readExact(input, 8)
        bytesRead += 8

        val blockHeader = if (possibleMagic.contentEquals(NCZBLOCK_MAGIC)) {
            val header = parseBlockHeader(input)
            bytesRead += 16 + (header.numberOfBlocks.toLong() * 4)
            header
        } else {
            null
        }

        val compressedDataOffset = NCA_HEADER_SIZE + bytesRead -
            if (blockHeader == null) 8 else 0

        return NczHeaderData(
            sections = sections,
            blockHeader = blockHeader,
            compressedDataOffset = compressedDataOffset
        )
    }

    private fun parseSection(data: ByteArray): NczSection {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return NczSection(
            offset = buf.long,
            size = buf.long,
            cryptoType = buf.long,
            padding = buf.long,
            cryptoKey = ByteArray(16).also { buf.get(it) },
            cryptoCounter = ByteArray(16).also { buf.get(it) }
        )
    }

    private fun parseBlockHeader(input: InputStream): NczBlockHeader {
        val version = readExact(input, 1)[0].toInt() and 0xFF
        require(version == 1) { "Unsupported NCZBLOCK version: $version" }

        val typeBytes = readExact(input, 1)[0].toInt() and 0xFF
        require(typeBytes == 0) { "Unsupported NCZBLOCK type: $typeBytes" }

        val unused = readExact(input, 1)

        val exponent = readExact(input, 1)[0].toInt() and 0xFF

        val numBlocksBytes = readExact(input, 4)
        val numBlocks = ByteBuffer.wrap(numBlocksBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int

        val decompSizeBytes = readExact(input, 8)
        val decompressedSize = ByteBuffer.wrap(decompSizeBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .long

        val compressedBlockSizes = IntArray(numBlocks)
        for (i in 0 until numBlocks) {
            val sizeBytes = readExact(input, 4)
            compressedBlockSizes[i] = ByteBuffer.wrap(sizeBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
        }

        return NczBlockHeader(
            blockSizeExponent = exponent,
            numberOfBlocks = numBlocks,
            decompressedSize = decompressedSize,
            compressedBlockSizes = compressedBlockSizes
        )
    }

    internal fun readExact(input: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(buf, offset, size - offset)
            if (read == -1) {
                throw IOException(
                    "Unexpected EOF: expected $size bytes, got $offset"
                )
            }
            offset += read
        }
        return buf
    }
}
