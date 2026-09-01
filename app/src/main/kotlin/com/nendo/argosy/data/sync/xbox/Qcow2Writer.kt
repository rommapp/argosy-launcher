package com.nendo.argosy.data.sync.xbox

import java.io.OutputStream

/**
 * Creates a sparse qcow2 image and allocates clusters into it as guest ranges are written.
 * Only the shape [Qcow2Image] accepts is produced: version 3, no backing file, no encryption,
 * no compression, no snapshots.
 *
 * Allocation is safe here in a way it is not inside a user's existing image, because the file
 * is being built from nothing and there is no save to damage if the refcounts come out wrong.
 */
class Qcow2Writer(
    private val virtualSize: Long,
    private val clusterBits: Int = DEFAULT_CLUSTER_BITS
) {
    private val clusterSize = 1 shl clusterBits
    private val l2Entries = clusterSize / 8
    private val l1Size = ((virtualSize + clusterSize.toLong() * l2Entries - 1) /
        (clusterSize.toLong() * l2Entries)).toInt()

    private val refcountTableCluster = 1
    private val refcountBlockCluster = 2
    private val l1TableCluster = 3

    private val clusters = mutableMapOf<Int, ByteArray>()
    private val l2TableByL1Index = mutableMapOf<Int, Int>()
    private val dataClusterByGuestIndex = mutableMapOf<Long, Int>()
    private var nextCluster = 4

    init {
        require(l1Size * 8 <= clusterSize) { "l1 table of $l1Size entries needs more than one cluster" }
        clusterAt(refcountTableCluster)
        clusterAt(refcountBlockCluster)
        clusterAt(l1TableCluster)
    }

    fun write(guestOffset: Long, data: ByteArray) {
        var done = 0
        while (done < data.size) {
            val at = guestOffset + done
            val within = (at % clusterSize).toInt()
            val chunk = minOf(data.size - done, clusterSize - within)
            val cluster = clusterAt(allocateGuest(at - within))
            data.copyInto(cluster, within, done, done + chunk)
            done += chunk
        }
    }

    fun writeTo(out: OutputStream) {
        writeHeader()
        writeRefcounts()
        val blank = ByteArray(clusterSize)
        for (index in 0 until nextCluster) {
            out.write(clusters[index] ?: blank)
        }
    }

    private fun allocateGuest(guestClusterStart: Long): Int {
        val guestIndex = guestClusterStart / clusterSize
        dataClusterByGuestIndex[guestIndex]?.let { return it }

        val l1Index = (guestIndex / l2Entries).toInt()
        val l2Cluster = l2TableByL1Index.getOrPut(l1Index) {
            val allocated = nextCluster++
            clusterAt(allocated)
            putLong(
                clusterAt(l1TableCluster),
                l1Index * 8,
                allocated.toLong() * clusterSize or COPIED
            )
            allocated
        }

        val dataCluster = nextCluster++
        clusterAt(dataCluster)
        putLong(
            clusterAt(l2Cluster),
            (guestIndex % l2Entries).toInt() * 8,
            dataCluster.toLong() * clusterSize or COPIED
        )
        dataClusterByGuestIndex[guestIndex] = dataCluster
        return dataCluster
    }

    private fun clusterAt(index: Int): ByteArray =
        clusters.getOrPut(index) { ByteArray(clusterSize) }

    private fun writeHeader() {
        val header = clusterAt(0)
        putInt(header, 0, MAGIC)
        putInt(header, 4, 3)
        putInt(header, 20, clusterBits)
        putLong(header, 24, virtualSize)
        putInt(header, 36, l1Size)
        putLong(header, 40, l1TableCluster.toLong() * clusterSize)
        putLong(header, 48, refcountTableCluster.toLong() * clusterSize)
        putInt(header, 56, 1)
        putInt(header, 96, REFCOUNT_ORDER)
        putInt(header, 100, HEADER_LENGTH)
    }

    private fun writeRefcounts() {
        putLong(clusterAt(refcountTableCluster), 0, refcountBlockCluster.toLong() * clusterSize)
        val block = clusterAt(refcountBlockCluster)
        require(nextCluster * 2 <= clusterSize) { "$nextCluster clusters overflow one refcount block" }
        for (index in 0 until nextCluster) {
            block[index * 2] = 0
            block[index * 2 + 1] = 1
        }
    }

    private fun putInt(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) bytes[at + i] = (value ushr ((3 - i) * 8)).toByte()
    }

    private fun putLong(bytes: ByteArray, at: Int, value: Long) {
        for (i in 0 until 8) bytes[at + i] = (value ushr ((7 - i) * 8)).toByte()
    }

    companion object {
        const val DEFAULT_CLUSTER_BITS = 16

        private const val MAGIC = 0x514649fb
        private const val REFCOUNT_ORDER = 4
        private const val HEADER_LENGTH = 104
        private const val COPIED = 1L shl 63
    }
}
