package com.nendo.argosy.data.sync.xbox

import com.nendo.argosy.data.storage.FileAccessLayer
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * The Xbox hard disk image an xemu-derived emulator boots, opened for save access. Saves live
 * in `E:\UDATA\<title id hex>\`, and the title id here is the raw hex form the console names
 * the directory after, not the `MS-100` serial printed on the disc.
 *
 * The E: partition sits at a fixed sector on every Xbox-shaped image and runs to the end of
 * the disk, so no partition table is read. That layout is upstream-exact.
 */
class XboxHddImage private constructor(
    private val qcow2: Qcow2Image,
    private val volume: FatxVolume
) : Closeable {

    val isDirty: Boolean get() = qcow2.isDirty

    fun listSaveIds(): List<String> {
        val udata = volume.resolve(listOf(UDATA)) ?: return emptyList()
        return volume.listDirectory(udata.firstCluster)
            .filter { it.isDirectory }
            .map { it.name }
    }

    fun hasSave(titleIdHex: String): Boolean = volume.resolve(listOf(UDATA, titleIdHex)) != null

    /**
     * Copies `E:\UDATA\<titleIdHex>\` into [destination], which is emptied first. Returns false
     * when the game has no save directory, which is the ordinary state before a first save.
     */
    fun extractSave(titleIdHex: String, destination: File): Boolean {
        val root = volume.resolve(listOf(UDATA, titleIdHex)) ?: return false
        destination.deleteRecursively()
        destination.mkdirs()
        extractDirectory(root, destination)
        return true
    }

    /**
     * Writes the tree under [source] back into `E:\UDATA\<titleIdHex>\`. Every file must already
     * exist in the image at the same size, so a save can be restored over itself but a save the
     * emulator has never written cannot be introduced.
     */
    fun writeSaveInPlace(titleIdHex: String, source: File) {
        val root = volume.resolve(listOf(UDATA, titleIdHex))
            ?: throw IOException("no save directory for $titleIdHex in the disk image")
        writeDirectory(root, source)
    }

    override fun close() = qcow2.close()

    private fun extractDirectory(entry: FatxVolume.Entry, destination: File) {
        for (child in volume.listDirectory(entry.firstCluster)) {
            val target = File(destination, child.name)
            if (child.isDirectory) {
                target.mkdirs()
                extractDirectory(child, target)
            } else {
                target.writeBytes(volume.readFile(child))
            }
        }
    }

    private fun writeDirectory(entry: FatxVolume.Entry, source: File) {
        val children = volume.listDirectory(entry.firstCluster).associateBy { it.name.lowercase() }
        for (file in source.listFiles().orEmpty()) {
            val child = children[file.name.lowercase()]
                ?: throw IOException("${file.name} does not exist in the disk image")
            if (file.isDirectory) {
                if (!child.isDirectory) throw IOException("${file.name} is a file in the disk image")
                writeDirectory(child, file)
            } else {
                if (child.isDirectory) throw IOException("${file.name} is a directory in the disk image")
                volume.writeFileInPlace(child, file.readBytes())
            }
        }
    }

    companion object {
        const val UDATA = "UDATA"

        private const val SECTOR_SIZE = 512L
        private const val DATA_PARTITION_LBA = 0x55F400L

        fun open(fal: FileAccessLayer, imagePath: String, writable: Boolean): XboxHddImage? {
            val handle = fal.openSeekable(imagePath, writable) ?: return null
            val qcow2 = Qcow2Image.open(handle)
            if (qcow2 == null) {
                handle.close()
                return null
            }

            val partitionOffset = DATA_PARTITION_LBA * SECTOR_SIZE
            val partitionSize = qcow2.virtualSize - partitionOffset
            if (partitionSize <= 0) {
                qcow2.close()
                return null
            }

            return try {
                XboxHddImage(qcow2, FatxVolume(qcow2, partitionOffset, partitionSize))
            } catch (e: IOException) {
                qcow2.close()
                null
            }
        }
    }
}
