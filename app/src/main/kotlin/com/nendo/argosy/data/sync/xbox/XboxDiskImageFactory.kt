package com.nendo.argosy.data.sync.xbox

import java.io.OutputStream

/**
 * Builds a blank Xbox hard disk image for users who have the flash ROM and MCPX but no drive.
 * The result matches the empty drive xemu ships: five FATX partitions at the standard sectors,
 * formatted and holding nothing. Games write their own `UDATA` on first save, so no dashboard
 * is involved and none is created.
 *
 * Sector offsets are the retail Xbox layout and are upstream-exact. The data partition takes
 * whatever remains of the disk, which is why a smaller image is still valid.
 */
object XboxDiskImageFactory {

    private const val SECTOR_SIZE = 512L
    private const val DEFAULT_SIZE_SECTORS = 0x1000000L
    private const val SECTORS_PER_CLUSTER = 32
    private const val CACHE_SIZE_SECTORS = 0x00177000L
    private const val SYSTEM_SIZE_SECTORS = 0x000FA000L

    private val FIXED_PARTITIONS = listOf(
        0x00000400L to CACHE_SIZE_SECTORS,
        0x00177400L to CACHE_SIZE_SECTORS,
        0x002EE400L to CACHE_SIZE_SECTORS,
        0x00465400L to SYSTEM_SIZE_SECTORS
    )

    private const val DATA_PARTITION_LBA = 0x0055F400L

    fun write(
        out: OutputStream,
        totalSectors: Long = DEFAULT_SIZE_SECTORS,
        volumeId: Int = DEFAULT_VOLUME_ID
    ) {
        require(totalSectors > DATA_PARTITION_LBA) {
            "an Xbox disk needs more than $DATA_PARTITION_LBA sectors"
        }

        val writer = Qcow2Writer(totalSectors * SECTOR_SIZE)
        FIXED_PARTITIONS.forEachIndexed { index, (startLba, sizeSectors) ->
            format(writer, startLba * SECTOR_SIZE, sizeSectors * SECTOR_SIZE, volumeId + index)
        }
        format(
            writer,
            DATA_PARTITION_LBA * SECTOR_SIZE,
            (totalSectors - DATA_PARTITION_LBA) * SECTOR_SIZE,
            volumeId + FIXED_PARTITIONS.size
        )
        writer.writeTo(out)
    }

    /**
     * Geometry is derived exactly as [FatxVolume] derives it when reading. The two must agree or
     * a generated image formats at offsets the reader never looks at.
     */
    private fun format(
        writer: Qcow2Writer,
        partitionOffset: Long,
        partitionSize: Long,
        volumeId: Int
    ) {
        val bytesPerCluster = SECTORS_PER_CLUSTER * SECTOR_SIZE.toInt()
        val clusterCount = (partitionSize / bytesPerCluster).toInt()
        val fatEntryBytes = if (clusterCount >= FATX32_MIN_CLUSTERS) 4 else 2
        val fatBytes = (clusterCount.toLong() * fatEntryBytes + FAT_ALIGNMENT - 1) /
            FAT_ALIGNMENT * FAT_ALIGNMENT

        val superblock = ByteArray(SUPERBLOCK_SIZE) { 0xFF.toByte() }
        superblock[0] = 'F'.code.toByte()
        superblock[1] = 'A'.code.toByte()
        superblock[2] = 'T'.code.toByte()
        superblock[3] = 'X'.code.toByte()
        putIntLe(superblock, 4, volumeId)
        putIntLe(superblock, 8, SECTORS_PER_CLUSTER)
        putIntLe(superblock, 12, FAT_COPIES)
        writer.write(partitionOffset, superblock)

        val head = ByteArray(fatEntryBytes * 2)
        if (fatEntryBytes == 2) {
            putShortLe(head, 0, MEDIA_DESCRIPTOR_16)
            putShortLe(head, 2, END_OF_CHAIN_16)
        } else {
            putIntLe(head, 0, MEDIA_DESCRIPTOR_32)
            putIntLe(head, 4, END_OF_CHAIN_32)
        }
        writer.write(partitionOffset + FAT_OFFSET, head)

        val rootDirectory = ByteArray(bytesPerCluster) { 0xFF.toByte() }
        writer.write(partitionOffset + FAT_OFFSET + fatBytes, rootDirectory)
    }

    private fun putIntLe(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 4) bytes[at + i] = (value ushr (i * 8)).toByte()
    }

    private fun putShortLe(bytes: ByteArray, at: Int, value: Int) {
        for (i in 0 until 2) bytes[at + i] = (value ushr (i * 8)).toByte()
    }

    const val DEFAULT_VOLUME_ID = 0x41524753

    private const val SUPERBLOCK_SIZE = 0x1000
    private const val FAT_OFFSET = 0x1000L
    private const val FAT_ALIGNMENT = 4096L
    private const val FAT_COPIES = 1
    private const val FATX32_MIN_CLUSTERS = 65525
    private const val MEDIA_DESCRIPTOR_16 = 0xFFF8
    private const val END_OF_CHAIN_16 = 0xFFFF
    private const val MEDIA_DESCRIPTOR_32 = -8
    private const val END_OF_CHAIN_32 = -1
}
