package com.nendo.argosy.data.emulator

import com.github.luben.zstd.Zstd
import com.nendo.argosy.util.Logger
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "ZArchiveReader"

private const val ZARCHIVE_MAGIC = 0x169f52d6L
private const val ZARCHIVE_VERSION_1 = 0x61bf3a01L
private const val COMPRESSED_BLOCK_SIZE = 64 * 1024
private const val ENTRIES_PER_OFFSET_RECORD = 16

private const val FOOTER_SIZE = 144
private const val FILE_ENTRY_SIZE = 16
private const val OFFSET_RECORD_SIZE = 40

object ZArchiveReader {

    fun readFile(archive: File, path: String): ByteArray? {
        return try {
            RandomAccessFile(archive, "r").use { raf ->
                val footer = readFooter(raf) ?: return null
                val nameTable = readSection(raf, footer.namesOffset, footer.namesSize)
                val fileTree = readSection(raf, footer.fileTreeOffset, footer.fileTreeSize)
                val offsetRecords = readSection(raf, footer.offsetRecordsOffset, footer.offsetRecordsSize)

                val node = lookupPath(path, fileTree, nameTable) ?: return null
                if (node.isDirectory) return null

                readFileData(raf, node, footer, offsetRecords)
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to read file from ZArchive: ${archive.name}/$path", e)
            null
        }
    }

    fun findWiiUTitleIdFolder(archive: File): String? {
        return try {
            RandomAccessFile(archive, "r").use { raf ->
                val footer = readFooter(raf) ?: return null
                val nameTable = readSection(raf, footer.namesOffset, footer.namesSize)
                val fileTree = readSection(raf, footer.fileTreeOffset, footer.fileTreeSize)

                val rootEntry = readFileEntry(fileTree, 0)
                if (!rootEntry.isDirectory) return null

                val titleIdPattern = Regex("""^(00050000[0-9A-Fa-f]{8})""")
                for (i in 0 until rootEntry.count) {
                    val childIdx = rootEntry.nodeStartIndex + i
                    val child = readFileEntry(fileTree, childIdx)
                    val name = readName(nameTable, child.nameOffset)
                    if (child.isDirectory) {
                        val match = titleIdPattern.find(name)
                        if (match != null) {
                            return match.groupValues[1].uppercase()
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to find title ID folder in ZArchive: ${archive.name}", e)
            null
        }
    }

    private fun readFooter(raf: RandomAccessFile): Footer? {
        if (raf.length() < FOOTER_SIZE) return null

        raf.seek(raf.length() - FOOTER_SIZE)
        val footerBytes = ByteArray(FOOTER_SIZE)
        raf.readFully(footerBytes)

        val buf = ByteBuffer.wrap(footerBytes).order(ByteOrder.BIG_ENDIAN)

        val compressedOffset = buf.long
        val compressedSize = buf.long
        val offsetRecordsOffset = buf.long
        val offsetRecordsSize = buf.long
        val namesOffset = buf.long
        val namesSize = buf.long
        val fileTreeOffset = buf.long
        val fileTreeSize = buf.long
        buf.long // metaDirOffset (unused)
        buf.long // metaDirSize (unused)
        buf.long // metaDataOffset (unused)
        buf.long // metaDataSize (unused)

        buf.position(buf.position() + 32) // Skip SHA256 hash

        buf.long // totalSize (unused)
        val version = buf.int.toLong() and 0xFFFFFFFFL
        val magic = buf.int.toLong() and 0xFFFFFFFFL

        if (magic != ZARCHIVE_MAGIC || version != ZARCHIVE_VERSION_1) return null

        return Footer(
            compressedOffset = compressedOffset,
            compressedSize = compressedSize,
            offsetRecordsOffset = offsetRecordsOffset,
            offsetRecordsSize = offsetRecordsSize,
            namesOffset = namesOffset,
            namesSize = namesSize,
            fileTreeOffset = fileTreeOffset,
            fileTreeSize = fileTreeSize
        )
    }

    private fun readSection(raf: RandomAccessFile, offset: Long, size: Long): ByteArray {
        raf.seek(offset)
        val data = ByteArray(size.toInt())
        raf.readFully(data)
        return data
    }

    private fun lookupPath(path: String, fileTree: ByteArray, nameTable: ByteArray): FileNode? {
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        var currentNode = 0
        val nodeCount = fileTree.size / FILE_ENTRY_SIZE

        for ((segmentIndex, segment) in segments.withIndex()) {
            val entry = readFileEntry(fileTree, currentNode)
            if (!entry.isDirectory) {
                return if (segmentIndex == segments.lastIndex) entry else null
            }

            var found = false
            for (i in 0 until entry.count) {
                val childIndex = entry.nodeStartIndex + i
                if (childIndex >= nodeCount) break

                val childEntry = readFileEntry(fileTree, childIndex)
                val childName = readName(nameTable, childEntry.nameOffset)

                if (childName.equals(segment, ignoreCase = true)) {
                    currentNode = childIndex
                    found = true
                    break
                }
            }

            if (!found) return null
        }

        return readFileEntry(fileTree, currentNode)
    }

    private fun readFileEntry(fileTree: ByteArray, index: Int): FileNode {
        val offset = index * FILE_ENTRY_SIZE
        val buf = ByteBuffer.wrap(fileTree, offset, FILE_ENTRY_SIZE).order(ByteOrder.BIG_ENDIAN)

        val nameOffsetAndFlag = buf.int
        val isDirectory = (nameOffsetAndFlag and 0x80000000.toInt()) == 0
        val nameOffset = nameOffsetAndFlag and 0x7FFFFFFF

        return if (isDirectory) {
            val nodeStartIndex = buf.int
            val count = buf.int
            FileNode(
                nameOffset = nameOffset,
                isDirectory = true,
                nodeStartIndex = nodeStartIndex,
                count = count,
                fileOffset = 0,
                fileSize = 0
            )
        } else {
            val offsetLow = buf.int.toLong() and 0xFFFFFFFFL
            val sizeLow = buf.int.toLong() and 0xFFFFFFFFL
            val sizeHighAndOffsetHigh = buf.int.toLong() and 0xFFFFFFFFL

            val offsetHigh = (sizeHighAndOffsetHigh shr 16) and 0xFFFF
            val sizeHigh = sizeHighAndOffsetHigh and 0xFFFF

            val fileOffset = (offsetHigh shl 32) or offsetLow
            val fileSize = (sizeHigh shl 32) or sizeLow

            FileNode(
                nameOffset = nameOffset,
                isDirectory = false,
                nodeStartIndex = 0,
                count = 0,
                fileOffset = fileOffset,
                fileSize = fileSize
            )
        }
    }

    private fun readName(nameTable: ByteArray, offset: Int): String {
        if (offset >= nameTable.size) return ""
        val length = nameTable[offset].toInt() and 0xFF
        if (offset + 1 + length > nameTable.size) return ""
        return String(nameTable, offset + 1, length, Charsets.ISO_8859_1)
    }

    private fun readFileData(
        raf: RandomAccessFile,
        node: FileNode,
        footer: Footer,
        offsetRecords: ByteArray
    ): ByteArray {
        if (node.fileSize == 0L) return ByteArray(0)

        val output = ByteArray(node.fileSize.toInt())
        var outputPos = 0
        var remainingSize = node.fileSize

        val startBlock = (node.fileOffset / COMPRESSED_BLOCK_SIZE).toInt()
        val startOffsetInBlock = (node.fileOffset % COMPRESSED_BLOCK_SIZE).toInt()
        val endBlock = ((node.fileOffset + node.fileSize - 1) / COMPRESSED_BLOCK_SIZE).toInt()

        for (blockIndex in startBlock..endBlock) {
            val (blockOffset, compressedSize) = getBlockInfo(offsetRecords, blockIndex)

            raf.seek(footer.compressedOffset + blockOffset)
            val compressedData = ByteArray(compressedSize.toInt())
            raf.readFully(compressedData)

            val decompressed = if (compressedSize.toInt() == COMPRESSED_BLOCK_SIZE) {
                compressedData
            } else {
                val decompressedBuf = ByteArray(COMPRESSED_BLOCK_SIZE)
                val decompressedSize = Zstd.decompress(decompressedBuf, compressedData)
                if (Zstd.isError(decompressedSize)) {
                    throw RuntimeException("Zstd decompression failed: ${Zstd.getErrorName(decompressedSize)}")
                }
                decompressedBuf.copyOf(decompressedSize.toInt())
            }

            val copyStart = if (blockIndex == startBlock) startOffsetInBlock else 0
            val copyEnd = if (blockIndex == endBlock) {
                val endOffsetInBlock = ((node.fileOffset + node.fileSize - 1) % COMPRESSED_BLOCK_SIZE).toInt() + 1
                minOf(endOffsetInBlock, decompressed.size)
            } else {
                decompressed.size
            }

            val copyLen = minOf(copyEnd - copyStart, remainingSize.toInt())
            System.arraycopy(decompressed, copyStart, output, outputPos, copyLen)
            outputPos += copyLen
            remainingSize -= copyLen
        }

        return output
    }

    private fun getBlockInfo(offsetRecords: ByteArray, blockIndex: Int): Pair<Long, Long> {
        val recordIndex = blockIndex / ENTRIES_PER_OFFSET_RECORD
        val entryInRecord = blockIndex % ENTRIES_PER_OFFSET_RECORD

        val recordOffset = recordIndex * OFFSET_RECORD_SIZE
        val buf = ByteBuffer.wrap(offsetRecords, recordOffset, OFFSET_RECORD_SIZE).order(ByteOrder.BIG_ENDIAN)

        val baseOffset = buf.long

        var currentOffset = baseOffset
        for (i in 0 until entryInRecord) {
            val sizeMinusOne = buf.short.toInt() and 0xFFFF
            currentOffset += sizeMinusOne + 1
        }

        val compressedSizeMinusOne = buf.short.toInt() and 0xFFFF
        val compressedSize = compressedSizeMinusOne + 1L

        return Pair(currentOffset, compressedSize)
    }

    private data class Footer(
        val compressedOffset: Long,
        val compressedSize: Long,
        val offsetRecordsOffset: Long,
        val offsetRecordsSize: Long,
        val namesOffset: Long,
        val namesSize: Long,
        val fileTreeOffset: Long,
        val fileTreeSize: Long
    )

    private data class FileNode(
        val nameOffset: Int,
        val isDirectory: Boolean,
        val nodeStartIndex: Int,
        val count: Int,
        val fileOffset: Long,
        val fileSize: Long
    )
}
