package com.nendo.argosy.data.sync.xbox

import com.nendo.argosy.data.storage.SeekableFile

class MemorySeekableFile(initialSize: Int) : SeekableFile {
    var bytes = ByteArray(initialSize)
        private set

    var closed = false
        private set

    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length, bytes.size - position.toInt())
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }

    override fun write(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        System.arraycopy(buffer, offset, bytes, position.toInt(), length)
    }

    override fun close() {
        closed = true
    }
}

/**
 * Builds a qcow2 image in memory with clusters allocated on demand. Refcount tables are
 * omitted because the reader never consults them, so the fixture stays small enough to
 * hold an 8GB virtual disk.
 */
class Qcow2Fixture(
    val virtualSize: Long,
    private val clusterBits: Int = 16,
    private val l1Size: Int = 16
) {
    private val clusterSize = 1 shl clusterBits
    private val l2Entries = clusterSize / 8
    private val l1TableOffset = 0x30000L

    private var storage = ByteArray(0)
    private var used = 0
    private val allocated = mutableMapOf<Long, Long>()
    private var nextHostOffset = l1TableOffset + clusterSize

    var incompatibleFeatures: Long = 0

    fun writeGuest(guestOffset: Long, data: ByteArray) {
        var done = 0
        while (done < data.size) {
            val at = guestOffset + done
            val within = (at % clusterSize).toInt()
            val chunk = minOf(data.size - done, clusterSize - within)
            val host = ensureCluster(at - within)
            ensureCapacity(host + clusterSize)
            data.copyInto(storage, (host + within).toInt(), done, done + chunk)
            done += chunk
        }
    }

    fun build(): MemorySeekableFile {
        ensureCapacity(nextHostOffset)
        writeHeader()
        writeL1Table()
        val file = MemorySeekableFile(used)
        file.write(0, storage, 0, used)
        return file
    }

    private fun ensureCluster(guestClusterStart: Long): Long =
        allocated.getOrPut(guestClusterStart) {
            val host = nextHostOffset
            nextHostOffset += clusterSize
            ensureCapacity(nextHostOffset)
            host
        }

    private fun ensureCapacity(size: Long) {
        if (size <= used) return
        if (size > storage.size) storage = storage.copyOf(maxOf(size, storage.size * 2L).toInt())
        used = size.toInt()
    }

    private fun writeHeader() {
        putInt(0, 0x514649fb)
        putInt(4, 3)
        putInt(20, clusterBits)
        putLong(24, virtualSize)
        putInt(36, l1Size)
        putLong(40, l1TableOffset)
        putLong(72, incompatibleFeatures)
        putInt(100, 104)
    }

    private fun writeL1Table() {
        for ((guestClusterStart, host) in allocated) {
            val clusterIndex = guestClusterStart / clusterSize
            val l1Index = (clusterIndex / l2Entries).toInt()
            val l2TableOffset = l2TableFor(l1Index)
            val l2Index = (clusterIndex % l2Entries).toInt()
            putLong(l2TableOffset + l2Index * 8L, host or COPIED)
        }
    }

    private val l2Tables = mutableMapOf<Int, Long>()

    private fun l2TableFor(l1Index: Int): Long = l2Tables.getOrPut(l1Index) {
        val offset = nextHostOffset
        nextHostOffset += clusterSize
        ensureCapacity(nextHostOffset)
        putLong(l1TableOffset + l1Index * 8L, offset or COPIED)
        offset
    }

    private fun putInt(at: Long, value: Int) {
        ensureCapacity(at + 4)
        for (i in 0 until 4) {
            storage[(at + i).toInt()] = (value ushr ((3 - i) * 8)).toByte()
        }
    }

    private fun putLong(at: Long, value: Long) {
        ensureCapacity(at + 8)
        for (i in 0 until 8) {
            storage[(at + i).toInt()] = (value ushr ((7 - i) * 8)).toByte()
        }
    }

    private companion object {
        const val COPIED = 1L shl 63
    }
}

/**
 * Lays a FATX partition into a [Qcow2Fixture] with the directory tree an Xbox save occupies.
 * Geometry mirrors what [FatxVolume] derives, so a mismatch in either direction fails a test
 * rather than silently reading the wrong offsets.
 */
class FatxFixture(
    private val qcow2: Qcow2Fixture,
    private val partitionOffset: Long,
    private val partitionSize: Long,
    private val sectorsPerCluster: Int = 32
) {
    private val bytesPerCluster = sectorsPerCluster * 512
    private val clusterCount = (partitionSize / bytesPerCluster).toInt()
    private val fatEntryBytes = if (clusterCount >= 65525) 4 else 2
    private val dataOffset = partitionOffset + 0x1000 +
        ((clusterCount.toLong() * fatEntryBytes + 4095) / 4096 * 4096)

    private var nextCluster = 1

    fun writeSuperblock() {
        val block = ByteArray(0x1000) { 0xFF.toByte() }
        block[0] = 'F'.code.toByte()
        block[1] = 'A'.code.toByte()
        block[2] = 'T'.code.toByte()
        block[3] = 'X'.code.toByte()
        writeIntLe(block, 4, 0x5B206DDA)
        writeIntLe(block, 8, sectorsPerCluster)
        writeIntLe(block, 12, 1)
        qcow2.writeGuest(partitionOffset, block)
    }

    fun allocateCluster(): Int {
        val cluster = nextCluster++
        val entry = if (fatEntryBytes == 2) {
            byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        } else {
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        }
        qcow2.writeGuest(partitionOffset + 0x1000 + cluster.toLong() * fatEntryBytes, entry)
        return cluster
    }

    data class Dirent(
        val name: String,
        val isDirectory: Boolean,
        val firstCluster: Int,
        val size: Int = 0,
        val packedTimestamp: Int = 0x24CD08B9
    )

    fun writeDirectory(cluster: Int, entries: List<Dirent>) {
        val block = ByteArray(bytesPerCluster) { 0xFF.toByte() }
        entries.forEachIndexed { index, entry ->
            val at = index * 64
            block[at] = entry.name.length.toByte()
            block[at + 1] = if (entry.isDirectory) 0x10 else 0x20
            entry.name.forEachIndexed { i, c -> block[at + 2 + i] = c.code.toByte() }
            writeIntLe(block, at + 44, entry.firstCluster)
            writeIntLe(block, at + 48, entry.size)
            writeIntLe(block, at + 52, entry.packedTimestamp)
            writeIntLe(block, at + 56, entry.packedTimestamp)
            writeIntLe(block, at + 60, entry.packedTimestamp)
        }
        if (entries.size * 64 < bytesPerCluster) {
            block[entries.size * 64] = 0x00
        }
        qcow2.writeGuest(offsetOfCluster(cluster), block)
    }

    fun writeFile(cluster: Int, data: ByteArray) {
        val block = ByteArray(bytesPerCluster)
        data.copyInto(block)
        qcow2.writeGuest(offsetOfCluster(cluster), block)
    }

    private fun offsetOfCluster(cluster: Int): Long =
        dataOffset + (cluster - 1).toLong() * bytesPerCluster

    private fun writeIntLe(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) bytes[at + i] = (value ushr (i * 8)).toByte()
    }
}
