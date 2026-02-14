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
                val entries = ContainerParser.parsePfs0(raf)

                Log.d(
                    TAG,
                    "PFS0 entries: ${entries.map { it.name }}"
                )

                val outputSizes = scanNczSizes(raf, entries)
                val totalOutputSize = outputSizes.sum()

                val pfs0Header = ContainerParser.computePfs0Header(
                    entries, outputSizes
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
                val (secureBaseOffset, secureEntries) =
                    ContainerParser.parseXciSecurePartition(raf)

                Log.d(
                    TAG,
                    "XCI secure entries: " +
                        secureEntries.map { it.name }
                )

                val outputSizes = scanNczSizes(raf, secureEntries)

                val newSecureHfs0Header =
                    ContainerParser.computeHfs0Header(
                        secureEntries, outputSizes
                    )

                raf.seek(0x130)
                val offsetBuf = ByteArray(8)
                raf.readFully(offsetBuf)
                val rootHfs0Offset = java.nio.ByteBuffer.wrap(offsetBuf)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .long

                val rootEntries = ContainerParser.parseHfs0(
                    raf, rootHfs0Offset
                )

                val secureIdx = rootEntries.indexOfFirst {
                    it.name.lowercase() == "secure"
                }

                val newSecureSize =
                    newSecureHfs0Header.size.toLong() +
                        outputSizes.sum()

                val rootOutputSizes = rootEntries.mapIndexed { idx, e ->
                    if (idx == secureIdx) newSecureSize else e.size
                }

                val newRootHfs0Header =
                    ContainerParser.computeHfs0Header(
                        rootEntries, rootOutputSizes
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

                    val totalOutputSize = XCI_HEADER_SIZE +
                        newRootHfs0Header.size.toLong() +
                        rootOutputSizes.sum()

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
