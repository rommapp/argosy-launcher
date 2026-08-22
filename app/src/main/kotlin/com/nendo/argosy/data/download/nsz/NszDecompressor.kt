package com.nendo.argosy.data.download.nsz

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

private const val TAG = "NszDecompressor"
private const val COPY_BUFFER_SIZE = 1024 * 1024
private const val XCI_HEADER_SIZE = 0x200L

/**
 * Decompresses NSZ (compressed NSP) and XCZ (compressed XCI) files
 * into their uncompressed counterparts.
 *
 * NSZ = PFS0 container with .ncz entries instead of .nca
 * XCZ = XCI (gamecard header + HFS0) with .ncz entries
 *
 * NCZ entries contain the original NCA header (0x4000 bytes) followed
 * by an NCZSECTN header, optional NCZBLOCK header, and zstd-compressed
 * NCA body that may require AES-CTR re-encryption.
 */
object NszDecompressor {

    fun isCompressedNsw(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "nsz" || ext == "xcz"
    }

    /**
     * The exact size [decompress] will write, read from the container without decompressing it.
     *
     * Every NCZ section header records the length of the NCA it expands to, so the total is known
     * before a byte is written. Null when the file is not a compressed NSW container or its headers
     * cannot be read, which leaves the caller to fall back to an estimate.
     */
    fun measureDecompressedSize(file: File): Long? = runCatching {
        when (file.extension.lowercase()) {
            "nsz" -> RandomAccessFile(file, "r").use { nszLayout(it).outputFileSize }
            "xcz" -> RandomAccessFile(file, "r").use { xczLayout(it).outputFileSize }
            else -> null
        }
    }.getOrNull()?.takeIf { it > 0 }

    private class NszLayout(
        val entries: List<ContainerEntry>,
        val outputSizes: List<Long>,
        val pfs0Header: ByteArray
    ) {
        val outputFileSize: Long get() = pfs0Header.size.toLong() + outputSizes.sum()
    }

    private class XczLayout(
        val secureEntries: List<ContainerEntry>,
        val secureHeader: ByteArray,
        val rootEntries: List<ContainerEntry>,
        val secureIndex: Int,
        val rootOutputSizes: List<Long>,
        val rootHeader: ByteArray
    ) {
        val outputFileSize: Long get() =
            XCI_HEADER_SIZE + rootHeader.size.toLong() + rootOutputSizes.sum()
    }

    private fun nszLayout(raf: RandomAccessFile): NszLayout {
        val entries = ContainerParser.parsePfs0(raf)
        val outputSizes = scanNczSizes(raf, entries)
        return NszLayout(
            entries = entries,
            outputSizes = outputSizes,
            pfs0Header = ContainerParser.computePfs0Header(entries, outputSizes)
        )
    }

    private fun xczLayout(raf: RandomAccessFile): XczLayout {
        val secureEntries = ContainerParser.parseXciSecurePartition(raf).second
        val secureOutputSizes = scanNczSizes(raf, secureEntries)
        val secureHeader = ContainerParser.computeHfs0Header(secureEntries, secureOutputSizes)

        raf.seek(0x130)
        val offsetBuf = ByteArray(8)
        raf.readFully(offsetBuf)
        val rootHfs0Offset = java.nio.ByteBuffer.wrap(offsetBuf)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .long

        val rootEntries = ContainerParser.parseHfs0(raf, rootHfs0Offset)
        val secureIndex = rootEntries.indexOfFirst { it.name.lowercase() == "secure" }
        val secureSize = secureHeader.size.toLong() + secureOutputSizes.sum()
        val rootOutputSizes = rootEntries.mapIndexed { idx, entry ->
            if (idx == secureIndex) secureSize else entry.size
        }

        return XczLayout(
            secureEntries = secureEntries,
            secureHeader = secureHeader,
            rootEntries = rootEntries,
            secureIndex = secureIndex,
            rootOutputSizes = rootOutputSizes,
            rootHeader = ContainerParser.computeHfs0Header(rootEntries, rootOutputSizes)
        )
    }

    fun decompress(
        inputFile: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): File {
        val ext = inputFile.extension.lowercase()
        return when (ext) {
            "nsz" -> decompressNsz(inputFile, onProgress)
            "xcz" -> decompressXcz(inputFile, onProgress)
            else -> throw IOException(
                "Not a compressed NSW file: ${inputFile.name}"
            )
        }
    }

    private fun decompressNsz(
        inputFile: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): File {
        val outputFile = File(
            inputFile.parent,
            inputFile.nameWithoutExtension + ".nsp"
        )
        val tmpFile = File(outputFile.absolutePath + ".tmp")

        Log.d(TAG, "Decompressing NSZ: ${inputFile.name}")

        try {
            RandomAccessFile(inputFile, "r").use { raf ->
                val layout = nszLayout(raf)
                val entries = layout.entries
                val pfs0Header = layout.pfs0Header
                val totalOutputSize = layout.outputFileSize

                Log.d(
                    TAG,
                    "PFS0 entries: ${entries.map { it.name }}"
                )

                BufferedOutputStream(
                    FileOutputStream(tmpFile),
                    COPY_BUFFER_SIZE
                ).use { output ->
                    output.write(pfs0Header)

                    var bytesWritten = pfs0Header.size.toLong()

                    for (i in entries.indices) {
                        val entry = entries[i]

                        if (entry.isNcz) {
                            bytesWritten = decompressNczEntry(
                                raf, entry, output,
                                bytesWritten, totalOutputSize,
                                onProgress
                            )
                        } else {
                            bytesWritten = copyEntry(
                                raf, entry, output,
                                bytesWritten, totalOutputSize,
                                onProgress
                            )
                        }
                    }
                }
            }

            tmpFile.renameTo(outputFile)
            inputFile.delete()
            Log.d(TAG, "NSZ decompressed: ${outputFile.name}")
            return outputFile
        } catch (e: Exception) {
            tmpFile.delete()
            throw IOException(
                "NSZ decompression failed: ${e.message}", e
            )
        }
    }

    private fun decompressXcz(
        inputFile: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): File {
        val outputFile = File(
            inputFile.parent,
            inputFile.nameWithoutExtension + ".xci"
        )
        val tmpFile = File(outputFile.absolutePath + ".tmp")

        Log.d(TAG, "Decompressing XCZ: ${inputFile.name}")

        try {
            RandomAccessFile(inputFile, "r").use { raf ->
                val layout = xczLayout(raf)
                val secureEntries = layout.secureEntries
                val newSecureHfs0Header = layout.secureHeader
                val rootEntries = layout.rootEntries
                val secureIdx = layout.secureIndex
                val newRootHfs0Header = layout.rootHeader

                Log.d(
                    TAG,
                    "XCI secure entries: " +
                        secureEntries.map { it.name }
                )

                BufferedOutputStream(
                    FileOutputStream(tmpFile),
                    COPY_BUFFER_SIZE
                ).use { output ->
                    raf.seek(0)
                    val gamecardHeader = ByteArray(XCI_HEADER_SIZE.toInt())
                    raf.readFully(gamecardHeader)

                    val rootHfs0OffsetInHeader = 0x130
                    val newRootOffset = XCI_HEADER_SIZE
                    val offsetBytes = java.nio.ByteBuffer.allocate(8)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .putLong(newRootOffset)
                        .array()
                    System.arraycopy(
                        offsetBytes, 0,
                        gamecardHeader, rootHfs0OffsetInHeader, 8
                    )
                    output.write(gamecardHeader)

                    output.write(newRootHfs0Header)

                    var bytesWritten = XCI_HEADER_SIZE +
                        newRootHfs0Header.size.toLong()

                    val totalOutputSize = layout.outputFileSize

                    for (i in rootEntries.indices) {
                        if (i == secureIdx) {
                            output.write(newSecureHfs0Header)
                            bytesWritten += newSecureHfs0Header.size

                            for (j in secureEntries.indices) {
                                val entry = secureEntries[j]
                                if (entry.isNcz) {
                                    bytesWritten = decompressNczEntry(
                                        raf, entry, output,
                                        bytesWritten, totalOutputSize,
                                        onProgress
                                    )
                                } else {
                                    bytesWritten = copyEntry(
                                        raf, entry, output,
                                        bytesWritten, totalOutputSize,
                                        onProgress
                                    )
                                }
                            }
                        } else {
                            bytesWritten = copyEntry(
                                raf, rootEntries[i], output,
                                bytesWritten, totalOutputSize,
                                onProgress
                            )
                        }
                    }
                }
            }

            tmpFile.renameTo(outputFile)
            inputFile.delete()
            Log.d(TAG, "XCZ decompressed: ${outputFile.name}")
            return outputFile
        } catch (e: Exception) {
            tmpFile.delete()
            throw IOException(
                "XCZ decompression failed: ${e.message}", e
            )
        }
    }

    private fun scanNczSizes(
        raf: RandomAccessFile,
        entries: List<ContainerEntry>
    ): List<Long> {
        return entries.map { entry ->
            if (!entry.isNcz) {
                entry.size
            } else {
                raf.seek(entry.dataOffset + NczHeaderParser.NCA_HEADER_SIZE)
                val headerStream = RandomAccessFileInputStream(
                    raf, entry.dataOffset + NczHeaderParser.NCA_HEADER_SIZE
                )
                val nczHeader = NczHeaderParser.parse(headerStream)

                val bodySize = if (nczHeader.blockHeader != null) {
                    nczHeader.blockHeader.decompressedSize
                } else {
                    nczHeader.sections.maxOf { it.offset + it.size } -
                        NczHeaderParser.NCA_HEADER_SIZE
                }

                NczHeaderParser.NCA_HEADER_SIZE + bodySize
            }
        }
    }

    private fun decompressNczEntry(
        raf: RandomAccessFile,
        entry: ContainerEntry,
        output: java.io.OutputStream,
        currentBytesWritten: Long,
        totalOutputSize: Long,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): Long {
        var bytesWritten = currentBytesWritten

        Log.d(TAG, "Decompressing NCZ: ${entry.name}")

        raf.seek(entry.dataOffset)
        val ncaHeader = ByteArray(NczHeaderParser.NCA_HEADER_SIZE.toInt())
        raf.readFully(ncaHeader)
        output.write(ncaHeader)
        bytesWritten += ncaHeader.size

        val nczStart = entry.dataOffset + NczHeaderParser.NCA_HEADER_SIZE
        raf.seek(nczStart)
        val headerStream = RandomAccessFileInputStream(raf, nczStart)
        val nczHeader = NczHeaderParser.parse(headerStream)

        val bodySize = if (nczHeader.blockHeader != null) {
            nczHeader.blockHeader.decompressedSize
        } else {
            nczHeader.sections.maxOf { it.offset + it.size } -
                NczHeaderParser.NCA_HEADER_SIZE
        }

        raf.seek(nczHeader.compressedDataOffset)
        val compressedStream = BufferedInputStream(
            RandomAccessFileInputStream(
                raf, nczHeader.compressedDataOffset
            ),
            COPY_BUFFER_SIZE
        )

        val wrappedProgress = onProgress?.let { callback ->
            { written: Long, _: Long ->
                callback(
                    bytesWritten + written,
                    totalOutputSize
                )
            }
        }

        NczWriter.decompress(
            input = compressedStream,
            output = output,
            header = nczHeader,
            totalDecompressedSize = bodySize,
            onProgress = wrappedProgress
        )

        bytesWritten += bodySize
        onProgress?.invoke(bytesWritten, totalOutputSize)
        return bytesWritten
    }

    private fun copyEntry(
        raf: RandomAccessFile,
        entry: ContainerEntry,
        output: java.io.OutputStream,
        currentBytesWritten: Long,
        totalOutputSize: Long,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): Long {
        var bytesWritten = currentBytesWritten
        raf.seek(entry.dataOffset)

        val buf = ByteArray(COPY_BUFFER_SIZE)
        var remaining = entry.size

        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            raf.readFully(buf, 0, toRead)
            output.write(buf, 0, toRead)
            remaining -= toRead
            bytesWritten += toRead
            onProgress?.invoke(bytesWritten, totalOutputSize)
        }

        return bytesWritten
    }

    /**
     * InputStream adapter over RandomAccessFile for sequential reads
     * from a fixed starting position.
     */
    private class RandomAccessFileInputStream(
        private val raf: RandomAccessFile,
        startOffset: Long
    ) : java.io.InputStream() {

        init {
            raf.seek(startOffset)
        }

        override fun read(): Int = raf.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            raf.read(b, off, len)

        override fun available(): Int = 0
    }
}
