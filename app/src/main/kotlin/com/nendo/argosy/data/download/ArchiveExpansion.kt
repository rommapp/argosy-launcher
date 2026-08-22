package com.nendo.argosy.data.download

import com.nendo.argosy.data.download.nsz.NszDecompressor
import com.nendo.argosy.data.platform.PlatformDefinitions
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

/**
 * How much room an archive needs once it is unpacked.
 *
 * Zip and 7z both record every entry's uncompressed length in a directory the reader can walk
 * without decompressing anything, and every NCZ section in an NSZ or XCZ records the length of the
 * NCA it expands to, so [measure] returns the real number for all four. A caller that has no
 * archive on disk yet is always estimating.
 */
object ArchiveExpansion {

    const val ESTIMATE_MULTIPLIER = 3L

    /**
     * What an archive of already-compressed content expands to. A Switch dump, a PS3 or Vita
     * package and a compressed Nintendo container hold data the archiver cannot shrink again, so
     * the archive and its contents are close to the same size; the headroom is for per-file
     * overhead, not for growth.
     */
    const val PACKED_PAYLOAD_MULTIPLIER_PERCENT = 110L

    /**
     * What an NSZ or XCZ expands to before the container has been opened. These are the one case
     * where the archive is genuinely smaller than what it becomes: zstd over the NCA sections
     * commonly halves a Switch dump, and the decompressed NSP is written beside the input before
     * the input is removed. Treating them as already-packed reserves less than the unpack needs.
     */
    const val COMPRESSED_NSW_MULTIPLIER_PERCENT = 200L

    private val EXPANDING_EXTENSIONS = setOf("zip", "7z", "nsz", "xcz")

    private val COMPRESSED_NSW_EXTENSIONS = setOf("nsz", "xcz")

    fun estimate(archiveBytes: Long): Long = archiveBytes * ESTIMATE_MULTIPLIER

    /**
     * Room to reserve for unpacking [archiveBytes], given what the payload is. Callers with the
     * archive already on disk should prefer [measure], which reads the real figure.
     */
    fun estimate(archiveBytes: Long, fileName: String, platformSlug: String): Long {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            extension in COMPRESSED_NSW_EXTENSIONS ->
                archiveBytes * COMPRESSED_NSW_MULTIPLIER_PERCENT / 100L
            isAlreadyPacked(fileName, platformSlug) ->
                archiveBytes * PACKED_PAYLOAD_MULTIPLIER_PERCENT / 100L
            else -> estimate(archiveBytes)
        }
    }

    /**
     * Whether this download's payload is content the archiver could not shrink, so its archive and
     * its contents are close to the same size. Estimating these at the general multiplier reserves
     * several times what the unpack needs and refuses downloads a volume can hold, and these are
     * also the largest games in a library, which is where an over-reservation does most damage.
     */
    fun isAlreadyPacked(fileName: String, platformSlug: String): Boolean {
        if (fileName.substringAfterLast('.', "").lowercase() in COMPRESSED_NSW_EXTENSIONS) {
            return false
        }
        val lower = platformSlug.lowercase()
        return lower in PlatformDefinitions.PACKED_PAYLOAD_PLATFORMS ||
            PlatformDefinitions.getCanonicalSlug(lower) in PlatformDefinitions.PACKED_PAYLOAD_PLATFORMS
    }

    fun measure(archive: File): Long? = when {
        !archive.isFile -> null
        NszDecompressor.isCompressedNsw(archive) -> NszDecompressor.measureDecompressedSize(archive)
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
