package com.nendo.argosy.data.download.nsz

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ContainerEntry(
    val name: String,
    val dataOffset: Long,
    val size: Long
) {
    val isNcz: Boolean
        get() = name.lowercase().endsWith(".ncz")

    val outputName: String
        get() = if (isNcz) {
            name.substring(0, name.length - 4) + ".nca"
        } else {
            name
        }
}

object ContainerParser {

    private const val PFS0_MAGIC = 0x30534650 // "PFS0" LE
    private const val HFS0_MAGIC = 0x30534648 // "HFS0" LE
    private const val PFS0_ENTRY_SIZE = 24
    private const val HFS0_ENTRY_SIZE = 64
    private const val PFS0_HEADER_BASE = 16

    fun parsePfs0(
        raf: RandomAccessFile,
        baseOffset: Long = 0
    ): List<ContainerEntry> {
        raf.seek(baseOffset)
        val headerBuf = ByteArray(PFS0_HEADER_BASE)
        raf.readFully(headerBuf)
        val header = ByteBuffer.wrap(headerBuf)
            .order(ByteOrder.LITTLE_ENDIAN)

        val magic = header.int
        if (magic != PFS0_MAGIC) {
            throw IOException(
                "Invalid PFS0 magic: 0x${magic.toString(16)}"
            )
        }

        val fileCount = header.int
        val stringTableSize = header.int

        val entriesSize = fileCount * PFS0_ENTRY_SIZE
        val entryData = ByteArray(entriesSize)
        raf.readFully(entryData)

        val stringTableData = ByteArray(stringTableSize)
        raf.readFully(stringTableData)

        val dataStartOffset = baseOffset + PFS0_HEADER_BASE +
            entriesSize + stringTableSize

        val entries = mutableListOf<ContainerEntry>()
        val entryBuf = ByteBuffer.wrap(entryData)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until fileCount) {
            val entryDataOffset = entryBuf.long
            val entrySize = entryBuf.long
            val stringOffset = entryBuf.int
            entryBuf.int // reserved

            val name = readNullTerminated(
                stringTableData,
                stringOffset
            )

            entries.add(
                ContainerEntry(
                    name = name,
                    dataOffset = dataStartOffset + entryDataOffset,
                    size = entrySize
                )
            )
        }

        return entries
    }

    fun parseHfs0(
        raf: RandomAccessFile,
        baseOffset: Long = 0
    ): List<ContainerEntry> {
        raf.seek(baseOffset)
        val headerBuf = ByteArray(PFS0_HEADER_BASE)
        raf.readFully(headerBuf)
        val header = ByteBuffer.wrap(headerBuf)
            .order(ByteOrder.LITTLE_ENDIAN)

        val magic = header.int
        if (magic != HFS0_MAGIC) {
            throw IOException(
                "Invalid HFS0 magic: 0x${magic.toString(16)}"
            )
        }

        val fileCount = header.int
        val stringTableSize = header.int

        val entriesSize = fileCount * HFS0_ENTRY_SIZE
        val entryData = ByteArray(entriesSize)
        raf.readFully(entryData)

        val stringTableData = ByteArray(stringTableSize)
        raf.readFully(stringTableData)

        val dataStartOffset = baseOffset + PFS0_HEADER_BASE +
            entriesSize + stringTableSize

        val entries = mutableListOf<ContainerEntry>()
        val entryBuf = ByteBuffer.wrap(entryData)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until fileCount) {
            val entryDataOffset = entryBuf.long
            val entrySize = entryBuf.long
            val stringOffset = entryBuf.int
            val hashRegionSize = entryBuf.int
            entryBuf.position(entryBuf.position() + 8) // reserved
            val hash = ByteArray(32)
            entryBuf.get(hash)

            val name = readNullTerminated(
                stringTableData,
                stringOffset
            )

            entries.add(
                ContainerEntry(
                    name = name,
                    dataOffset = dataStartOffset + entryDataOffset,
                    size = entrySize
                )
            )
        }

        return entries
    }

    fun parseXciSecurePartition(
        raf: RandomAccessFile
    ): Pair<Long, List<ContainerEntry>> {
        raf.seek(0x130)
        val offsetBuf = ByteArray(8)
        raf.readFully(offsetBuf)
        val rootHfs0Offset = ByteBuffer.wrap(offsetBuf)
            .order(ByteOrder.LITTLE_ENDIAN)
            .long

        val rootEntries = parseHfs0(raf, rootHfs0Offset)

        val secureEntry = rootEntries.find {
            it.name.lowercase() == "secure"
        } ?: throw IOException(
            "XCI missing 'secure' partition. " +
                "Found: ${rootEntries.map { it.name }}"
        )

        val secureEntries = parseHfs0(
            raf,
            secureEntry.dataOffset
        )

        return Pair(secureEntry.dataOffset, secureEntries)
    }

    fun computePfs0Header(
        entries: List<ContainerEntry>,
        sizes: List<Long>
    ): ByteArray {
        val names = entries.map { it.outputName }
        val stringTable = buildStringTable(names)
        val headerSize = PFS0_HEADER_BASE +
            (entries.size * PFS0_ENTRY_SIZE) + stringTable.size

        val buf = ByteBuffer.allocate(headerSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(PFS0_MAGIC)
        buf.putInt(entries.size)
        buf.putInt(stringTable.size)
        buf.putInt(0) // reserved

        var dataOffset = 0L
        val nameOffsets = computeStringOffsets(names)

        for (i in entries.indices) {
            buf.putLong(dataOffset)
            buf.putLong(sizes[i])
            buf.putInt(nameOffsets[i])
            buf.putInt(0) // reserved
            dataOffset += sizes[i]
        }

        buf.put(stringTable)
        return buf.array()
    }

    fun computeHfs0Header(
        entries: List<ContainerEntry>,
        sizes: List<Long>
    ): ByteArray {
        val names = entries.map { it.outputName }
        val stringTable = buildStringTable(names)
        val headerSize = PFS0_HEADER_BASE +
            (entries.size * HFS0_ENTRY_SIZE) + stringTable.size

        val buf = ByteBuffer.allocate(headerSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(HFS0_MAGIC)
        buf.putInt(entries.size)
        buf.putInt(stringTable.size)
        buf.putInt(0) // reserved

        var dataOffset = 0L
        val nameOffsets = computeStringOffsets(names)

        for (i in entries.indices) {
            buf.putLong(dataOffset)
            buf.putLong(sizes[i])
            buf.putInt(nameOffsets[i])
            buf.putInt(0) // hash region size
            buf.position(buf.position() + 8) // reserved
            buf.put(ByteArray(32)) // empty hash
            dataOffset += sizes[i]
        }

        buf.put(stringTable)
        return buf.array()
    }

    private fun buildStringTable(names: List<String>): ByteArray {
        val sb = mutableListOf<Byte>()
        for (name in names) {
            sb.addAll(name.toByteArray(Charsets.US_ASCII).toList())
            sb.add(0)
        }
        return sb.toByteArray()
    }

    private fun computeStringOffsets(
        names: List<String>
    ): List<Int> {
        val offsets = mutableListOf<Int>()
        var offset = 0
        for (name in names) {
            offsets.add(offset)
            offset += name.toByteArray(Charsets.US_ASCII).size + 1
        }
        return offsets
    }

    private fun readNullTerminated(
        data: ByteArray,
        offset: Int
    ): String {
        var end = offset
        while (end < data.size && data[end] != 0.toByte()) {
            end++
        }
        return String(data, offset, end - offset, Charsets.US_ASCII)
    }
}
