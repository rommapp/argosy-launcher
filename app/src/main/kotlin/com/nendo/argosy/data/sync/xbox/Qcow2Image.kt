package com.nendo.argosy.data.sync.xbox

import com.nendo.argosy.data.storage.SeekableFile
import java.io.Closeable
import java.io.IOException

/**
 * Guest-offset access to a qcow2 image, which is what hakuX boots even though it names the
 * file `hdd.img`. Only the shape that emulator produces is accepted: no backing file, no
 * encryption, no compression, no external data file. Anything richer is refused at [open]
 * rather than half-handled, because a wrong cluster mapping writes over unrelated data.
 */
class Qcow2Image private constructor(
    private val file: SeekableFile,
    private val header: Header
) : Closeable {

    data class Header(
        val version: Int,
        val clusterBits: Int,
        val virtualSize: Long,
        val l1Size: Int,
        val l1TableOffset: Long,
        val incompatibleFeatures: Long
    ) {
        val clusterSize: Int get() = 1 shl clusterBits
        val l2Entries: Int get() = clusterSize / 8
        val isDirty: Boolean get() = incompatibleFeatures and FEATURE_DIRTY != 0L
    }

    val virtualSize: Long get() = header.virtualSize
    val clusterSize: Int get() = header.clusterSize

    /**
     * True when the emulator left the image mid-write. Refcounts may be stale, so the mapping
     * is still readable but must not be patched until the emulator has repaired it.
     */
    val isDirty: Boolean get() = header.isDirty

    fun read(guestOffset: Long, buffer: ByteArray, offset: Int, length: Int) {
        forEachSpan(guestOffset, length) { span, bufferAt ->
            val host = hostOffsetFor(span.guestOffset)
            if (host == null) {
                buffer.fill(0, offset + bufferAt, offset + bufferAt + span.length)
            } else {
                readExact(host, buffer, offset + bufferAt, span.length)
            }
        }
    }

    fun write(guestOffset: Long, buffer: ByteArray, offset: Int, length: Int) {
        if (isDirty) throw IOException("refusing to write a qcow2 image marked dirty")
        forEachSpan(guestOffset, length) { span, bufferAt ->
            val host = hostOffsetFor(span.guestOffset)
                ?: throw UnallocatedClusterException(span.guestOffset)
            file.write(host, buffer, offset + bufferAt, span.length)
        }
    }

    fun isAllocated(guestOffset: Long): Boolean = hostOffsetFor(guestOffset) != null

    override fun close() = file.close()

    private data class Span(val guestOffset: Long, val length: Int)

    private inline fun forEachSpan(guestOffset: Long, length: Int, body: (Span, Int) -> Unit) {
        if (guestOffset < 0 || length < 0 || guestOffset + length > header.virtualSize) {
            throw IOException("range $guestOffset+$length outside a ${header.virtualSize} byte image")
        }
        var done = 0
        while (done < length) {
            val at = guestOffset + done
            val withinCluster = (at % header.clusterSize).toInt()
            val chunk = minOf(length - done, header.clusterSize - withinCluster)
            body(Span(at, chunk), done)
            done += chunk
        }
    }

    private fun hostOffsetFor(guestOffset: Long): Long? {
        val clusterIndex = guestOffset / header.clusterSize
        val l1Index = (clusterIndex / header.l2Entries).toInt()
        if (l1Index >= header.l1Size) return null

        val l2TableOffset = readLong(header.l1TableOffset + l1Index * 8L) and OFFSET_MASK
        if (l2TableOffset == 0L) return null

        val l2Index = (clusterIndex % header.l2Entries).toInt()
        val entry = readLong(l2TableOffset + l2Index * 8L)
        if (entry and DESCRIPTOR_ZERO != 0L) return null

        val clusterOffset = entry and OFFSET_MASK
        if (clusterOffset == 0L) return null

        return clusterOffset + (guestOffset % header.clusterSize)
    }

    private fun readLong(at: Long): Long {
        val bytes = ByteArray(8)
        readExact(at, bytes, 0, 8)
        return readBigEndianLong(bytes, 0)
    }

    private fun readExact(at: Long, buffer: ByteArray, offset: Int, length: Int) {
        var done = 0
        while (done < length) {
            val count = file.read(at + done, buffer, offset + done, length - done)
            if (count <= 0) throw IOException("short read at ${at + done}")
            done += count
        }
    }

    class UnallocatedClusterException(guestOffset: Long) :
        IOException("guest offset $guestOffset has no allocated cluster")

    companion object {
        private const val MAGIC = 0x514649fb
        private const val OFFSET_MASK = 0x00fffffffffffe00L
        private const val DESCRIPTOR_ZERO = 1L
        private const val FEATURE_DIRTY = 1L
        private const val FEATURE_CORRUPT = 2L
        private const val FEATURE_EXTERNAL_DATA = 4L
        private const val HEADER_SIZE = 104
        private const val MIN_CLUSTER_BITS = 9
        private const val MAX_CLUSTER_BITS = 21

        fun open(file: SeekableFile): Qcow2Image? {
            val raw = ByteArray(HEADER_SIZE)
            var read = 0
            while (read < HEADER_SIZE) {
                val count = file.read(read.toLong(), raw, read, HEADER_SIZE - read)
                if (count <= 0) return null
                read += count
            }

            if (readBigEndianInt(raw, 0) != MAGIC) return null
            val version = readBigEndianInt(raw, 4)
            if (version < 2) return null
            if (readBigEndianLong(raw, 8) != 0L) return null
            if (readBigEndianInt(raw, 32) != 0) return null

            val clusterBits = readBigEndianInt(raw, 20)
            if (clusterBits < MIN_CLUSTER_BITS || clusterBits > MAX_CLUSTER_BITS) return null

            val incompatible = if (version >= 3) readBigEndianLong(raw, 72) else 0L
            if (incompatible and (FEATURE_CORRUPT or FEATURE_EXTERNAL_DATA) != 0L) return null

            return Qcow2Image(
                file,
                Header(
                    version = version,
                    clusterBits = clusterBits,
                    virtualSize = readBigEndianLong(raw, 24),
                    l1Size = readBigEndianInt(raw, 36),
                    l1TableOffset = readBigEndianLong(raw, 40),
                    incompatibleFeatures = incompatible
                )
            )
        }

        private fun readBigEndianInt(bytes: ByteArray, at: Int): Int =
            ((bytes[at].toInt() and 0xFF) shl 24) or
                ((bytes[at + 1].toInt() and 0xFF) shl 16) or
                ((bytes[at + 2].toInt() and 0xFF) shl 8) or
                (bytes[at + 3].toInt() and 0xFF)

        private fun readBigEndianLong(bytes: ByteArray, at: Int): Long =
            (readBigEndianInt(bytes, at).toLong() and 0xFFFFFFFFL shl 32) or
                (readBigEndianInt(bytes, at + 4).toLong() and 0xFFFFFFFFL)
    }
}
