package com.nendo.argosy.data.download

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

/**
 * How much room an archive needs once it is unpacked.
 *
 * Zip and 7z both record every entry's uncompressed length in a directory the reader can walk
 * without decompressing anything, so [measure] returns the real number for them. NSZ and XCZ
 * carry their expanded length per compressed NCA section rather than per container, so they fall
 * back to [estimate]. A caller that has no archive on disk yet is always estimating.
 */
object ArchiveExpansion {

    const val ESTIMATE_MULTIPLIER = 3L

    private val EXPANDING_EXTENSIONS = setOf("zip", "7z", "nsz", "xcz")

    fun estimate(archiveBytes: Long): Long = archiveBytes * ESTIMATE_MULTIPLIER

    fun measure(archive: File): Long? = when {
        !archive.isFile -> null
        ZipExtractor.isSevenZFile(archive) -> measureSevenZ(archive)
        ZipExtractor.isZipFile(archive) -> measureZip(archive)
        else -> null
    }

    /**
     * Whether this download is expected to unpack into something larger than itself. Staging only
     * pays for itself when there is an unpack step, so a bare rom or an arcade zip - which stays a
     * zip - is left to write straight to its destination instead of being copied twice.
     *
     * A server-built multi-file archive always unpacks, whatever its name ends in: the folder name
     * it is built from often carries a version number, which leaves the filename with a plausible
     * extension that is nothing of the kind.
     */
    fun expandsOnDisk(fileName: String, platformSlug: String, isMultiFileRom: Boolean): Boolean {
        if (isMultiFileRom) return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in EXPANDING_EXTENSIONS) return false
        if (extension == "nsz" || extension == "xcz") return true
        return !ZipExtractor.usesZipAsRomFormat(platformSlug)
    }

    private fun measureZip(archive: File): Long? = runCatching {
        ZipFile.builder().setFile(archive).get().use { zip ->
            zip.entries.toList().filter { !it.isDirectory }.sumOf { it.size }
        }
    }.getOrNull()?.takeIf { it > 0 }

    private fun measureSevenZ(archive: File): Long? = runCatching {
        SevenZFile.builder().setFile(archive).get().use { sevenZ ->
            sevenZ.entries.filter { !it.isDirectory }.sumOf { it.size }
        }
    }.getOrNull()?.takeIf { it > 0 }
}
