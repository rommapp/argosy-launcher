package com.nendo.argosy.data.sync.xbox

import java.io.IOException

/**
 * Read and in-place write access to one FATX partition inside an Xbox disk image. Geometry
 * follows mborgerson/fatx, the reference implementation xemu and its forks are built from,
 * so the constants here are upstream-exact and must not be tidied into rounder numbers.
 *
 * Creating entries and growing files are deliberately absent. Both need cluster allocation,
 * which also means updating the qcow2 refcount tables, and a mistake there damages an image
 * the user cannot rebuild. Writing is limited to what already fits its existing chain.
 */
class FatxVolume(
    private val image: Qcow2Image,
    private val partitionOffset: Long,
    partitionSize: Long
) {

    data class Entry(
        val name: String,
        val isDirectory: Boolean,
        val firstCluster: Int,
        val size: Int
    )

    private val bytesPerCluster: Int
    private val clusterCount: Int
    private val fatEntryBytes: Int
    private val dataOffset: Long

    init {
        val superblock = ByteArray(SUPERBLOCK_SIZE)
        image.read(partitionOffset, superblock, 0, SUPERBLOCK_SIZE)
        if (readInt(superblock, 0) != MAGIC) throw IOException("no FATX superblock at $partitionOffset")

        val sectorsPerCluster = readInt(superblock, 8)
        if (sectorsPerCluster <= 0) throw IOException("FATX cluster size is $sectorsPerCluster sectors")

        bytesPerCluster = sectorsPerCluster * SECTOR_SIZE
        clusterCount = (partitionSize / bytesPerCluster).toInt()
        fatEntryBytes = if (clusterCount >= FATX32_MIN_CLUSTERS) 4 else 2

        val fatBytes = align(clusterCount.toLong() * fatEntryBytes, FAT_ALIGNMENT)
        dataOffset = partitionOffset + FAT_OFFSET + fatBytes
    }

    fun listDirectory(cluster: Int): List<Entry> {
        val entries = mutableListOf<Entry>()
        val buffer = ByteArray(bytesPerCluster)

        for (current in chainOf(cluster)) {
            image.read(offsetOfCluster(current), buffer, 0, bytesPerCluster)
            var at = 0
            while (at + DIRENT_SIZE <= bytesPerCluster) {
                when (val nameLength = buffer[at].toInt() and 0xFF) {
                    DIRENT_END, DIRENT_NEVER_USED -> return entries
                    DIRENT_DELETED -> Unit
                    else -> parseEntry(buffer, at, nameLength)?.let { entries.add(it) }
                }
                at += DIRENT_SIZE
            }
        }
        return entries
    }

    fun find(parentCluster: Int, name: String): Entry? =
        listDirectory(parentCluster).firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun resolve(path: List<String>): Entry? {
        var cluster = ROOT_CLUSTER
        var found: Entry? = null
        for (segment in path) {
            found = find(cluster, segment) ?: return null
            cluster = found.firstCluster
        }
        return found
    }

    fun readFile(entry: Entry): ByteArray {
        if (entry.isDirectory) throw IOException("${entry.name} is a directory")
        val data = ByteArray(entry.size)
        var written = 0
        for (cluster in chainOf(entry.firstCluster)) {
            if (written >= entry.size) break
            val chunk = minOf(bytesPerCluster, entry.size - written)
            image.read(offsetOfCluster(cluster), data, written, chunk)
            written += chunk
        }
        return data
    }

    /**
     * Overwrites [entry]'s existing clusters. The size field in the directory entry is left
     * alone, so [data] must be exactly the length already recorded for it.
     */
    fun writeFileInPlace(entry: Entry, data: ByteArray) {
        if (entry.isDirectory) throw IOException("${entry.name} is a directory")
        if (data.size != entry.size) {
            throw IOException("${entry.name} holds ${entry.size} bytes, refusing to write ${data.size}")
        }

        var written = 0
        for (cluster in chainOf(entry.firstCluster)) {
            if (written >= data.size) break
            val chunk = minOf(bytesPerCluster, data.size - written)
            image.write(offsetOfCluster(cluster), data, written, chunk)
            written += chunk
        }
        if (written < data.size) throw IOException("${entry.name} chain ended after $written bytes")
    }

    private fun parseEntry(buffer: ByteArray, at: Int, nameLength: Int): Entry? {
        if (nameLength > MAX_NAME_LENGTH) return null
        val name = String(buffer, at + 2, nameLength, Charsets.ISO_8859_1)
        val attributes = buffer[at + 1].toInt() and 0xFF
        return Entry(
            name = name,
            isDirectory = attributes and ATTRIBUTE_DIRECTORY != 0,
            firstCluster = readInt(buffer, at + 44),
            size = readInt(buffer, at + 48)
        )
    }

    private fun chainOf(firstCluster: Int): Sequence<Int> = sequence {
        var cluster = firstCluster
        var visited = 0
        while (cluster >= ROOT_CLUSTER && cluster < clusterCount) {
            yield(cluster)
            if (++visited > clusterCount) throw IOException("FATX cluster chain loops")
            cluster = nextCluster(cluster)
        }
    }

    private fun nextCluster(cluster: Int): Int {
        val at = partitionOffset + FAT_OFFSET + cluster.toLong() * fatEntryBytes
        val raw = ByteArray(fatEntryBytes)
        image.read(at, raw, 0, fatEntryBytes)
        return if (fatEntryBytes == 2) {
            val value = readShort(raw, 0)
            if (value >= FATX16_END) END_OF_CHAIN else value
        } else {
            val value = readInt(raw, 0)
            if (value.toLong() and 0xFFFFFFFFL >= FATX32_END) END_OF_CHAIN else value
        }
    }

    private fun offsetOfCluster(cluster: Int): Long =
        dataOffset + (cluster - ROOT_CLUSTER).toLong() * bytesPerCluster

    private companion object {
        const val MAGIC = 0x58544146
        const val SECTOR_SIZE = 512
        const val SUPERBLOCK_SIZE = 0x1000
        const val FAT_OFFSET = 0x1000L
        const val FAT_ALIGNMENT = 4096L
        const val FATX32_MIN_CLUSTERS = 65525
        const val FATX16_END = 0xFFF8
        const val FATX32_END = 0xFFFFFFF8L
        const val END_OF_CHAIN = -1
        const val ROOT_CLUSTER = 1
        const val DIRENT_SIZE = 64
        const val DIRENT_END = 0x00
        const val DIRENT_NEVER_USED = 0xFF
        const val DIRENT_DELETED = 0xE5
        const val MAX_NAME_LENGTH = 42
        const val ATTRIBUTE_DIRECTORY = 0x10

        fun align(value: Long, to: Long): Long = (value + to - 1) / to * to

        fun readInt(bytes: ByteArray, at: Int): Int =
            (bytes[at].toInt() and 0xFF) or
                ((bytes[at + 1].toInt() and 0xFF) shl 8) or
                ((bytes[at + 2].toInt() and 0xFF) shl 16) or
                ((bytes[at + 3].toInt() and 0xFF) shl 24)

        fun readShort(bytes: ByteArray, at: Int): Int =
            (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    }
}
