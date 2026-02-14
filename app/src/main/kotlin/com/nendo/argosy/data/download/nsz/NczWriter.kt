package com.nendo.argosy.data.download.nsz

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Decompresses NCZ data (the body after the NCA header) into a valid
 * NCA body. Handles both solid and block compression modes.
 *
 * After zstd decompression, sections with cryptoType 3 or 4 must be
 * re-encrypted with AES-128-CTR using each section's key and counter.
 */
object NczWriter {

    private const val BUFFER_SIZE = 64 * 1024

    fun decompress(
        input: InputStream,
        output: OutputStream,
        header: NczHeaderData,
        totalDecompressedSize: Long,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ) {
        if (header.blockHeader != null) {
            decompressBlock(
                input, output, header, totalDecompressedSize, onProgress
            )
        } else {
            decompressSolid(
                input, output, header, totalDecompressedSize, onProgress
            )
        }
    }

    private fun decompressSolid(
        input: InputStream,
        output: OutputStream,
        header: NczHeaderData,
        totalDecompressedSize: Long,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ) {
        val zstdStream = ZstdInputStream(input)
        val buf = ByteArray(BUFFER_SIZE)
        var cursor = NczHeaderParser.NCA_HEADER_SIZE
        var bytesWritten = 0L

        while (bytesWritten < totalDecompressedSize) {
            val toRead = minOf(
                BUFFER_SIZE.toLong(),
                totalDecompressedSize - bytesWritten
            ).toInt()
            val read = zstdStream.read(buf, 0, toRead)
            if (read == -1) break

            processAndWrite(
                output, buf, read, cursor, header.sections
            )

            cursor += read
            bytesWritten += read
            onProgress?.invoke(bytesWritten, totalDecompressedSize)
        }
    }

    private fun decompressBlock(
        input: InputStream,
        output: OutputStream,
        header: NczHeaderData,
        totalDecompressedSize: Long,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ) {
        val blockHeader = header.blockHeader!!
        val blockSize = blockHeader.blockSize
        var cursor = NczHeaderParser.NCA_HEADER_SIZE
        var bytesWritten = 0L

        for (i in 0 until blockHeader.numberOfBlocks) {
            val compressedSize = blockHeader.compressedBlockSizes[i]
            val compressedData = NczHeaderParser.readExact(
                input, compressedSize
            )

            val remaining = totalDecompressedSize - bytesWritten
            val expectedSize = minOf(blockSize.toLong(), remaining).toInt()

            val decompressed = if (compressedSize == expectedSize) {
                compressedData
            } else {
                val decompBuf = ByteArray(blockSize)
                val decompSize = Zstd.decompress(
                    decompBuf, compressedData
                )
                if (Zstd.isError(decompSize)) {
                    throw IOException(
                        "Zstd block decompression failed: " +
                            Zstd.getErrorName(decompSize)
                    )
                }
                if (decompSize.toInt() != expectedSize) {
                    decompBuf.copyOf(decompSize.toInt())
                } else {
                    decompBuf.copyOf(expectedSize)
                }
            }

            processAndWrite(
                output,
                decompressed,
                decompressed.size,
                cursor,
                header.sections
            )

            cursor += decompressed.size
            bytesWritten += decompressed.size
            onProgress?.invoke(bytesWritten, totalDecompressedSize)
        }
    }

    private fun processAndWrite(
        output: OutputStream,
        data: ByteArray,
        length: Int,
        cursor: Long,
        sections: List<NczSection>
    ) {
        var offset = 0
        var pos = cursor

        while (offset < length) {
            val section = findSection(sections, pos)
            val remaining = length - offset

            val chunkSize = if (section != null) {
                val sectionEnd = section.offset + section.size
                val bytesToSectionEnd = (sectionEnd - pos).toInt()
                minOf(remaining, bytesToSectionEnd)
            } else {
                val nextSection = findNextSection(sections, pos)
                if (nextSection != null) {
                    val bytesToNext = (nextSection.offset - pos).toInt()
                    minOf(remaining, bytesToNext)
                } else {
                    remaining
                }
            }

            if (section != null && section.needsEncryption) {
                val cipher = AesCtrCipher(
                    section.cryptoKey,
                    section.cryptoCounter,
                    pos - section.offset
                )
                val encrypted = cipher.process(
                    data, offset, chunkSize
                )
                output.write(encrypted)
            } else {
                output.write(data, offset, chunkSize)
            }

            offset += chunkSize
            pos += chunkSize
        }
    }

    private fun findSection(
        sections: List<NczSection>,
        position: Long
    ): NczSection? {
        return sections.find { s ->
            position >= s.offset && position < s.offset + s.size
        }
    }

    private fun findNextSection(
        sections: List<NczSection>,
        position: Long
    ): NczSection? {
        return sections
            .filter { it.offset > position }
            .minByOrNull { it.offset }
    }
}
