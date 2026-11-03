package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.download.ZipExtractor
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/**
 * Names the built-in emulator ends up launching when a single-file archive is extracted.
 *
 * Extraction launches the archive's first usable entry, so saves are named after that entry
 * rather than the archive. Save detection has to agree with extraction on which entry wins,
 * so both sides resolve it here. Covers zip and 7z, the two formats extractSingleFileArchive
 * handles, and sniffs magic bytes rather than trusting the extension.
 */
object ArchiveRomNaming {
    fun isUsableEntry(name: String, isDirectory: Boolean): Boolean =
        !isDirectory && !name.startsWith("._")

    fun isUsableEntry(entry: ZipEntry): Boolean = isUsableEntry(entry.name, entry.isDirectory)

    fun primaryZipEntry(zip: ZipFile): ZipEntry? =
        zip.entries().toList().firstOrNull { isUsableEntry(it) }

    fun launchFileName(entryName: String): String = File(entryName).name

    fun launchBaseName(archive: File): String? =
        launchEntryFileName(archive)?.let { File(it).nameWithoutExtension }

    private fun launchEntryFileName(archive: File): String? {
        if (!archive.isFile) return null
        val entryName = when {
            ZipExtractor.isZipFile(archive) -> firstZipEntryName(archive)
            ZipExtractor.isSevenZFile(archive) -> firstSevenZEntryName(archive)
            else -> null
        } ?: return null
        return launchFileName(entryName)
    }

    /**
     * The base name the built-in core writes saves and states under: the entry it was launched
     * with when the archive is extracted, the archive itself on platforms that play zips as they
     * are. Mirrors the launcher's extraction decision so the cache side agrees with the engine.
     */
    fun liveBaseName(rom: File, platformSlug: String): String {
        val archiveBase = rom.nameWithoutExtension
        if (!ZipExtractor.isArchiveFile(rom) || ZipExtractor.usesZipAsRomFormat(platformSlug)) {
            return archiveBase
        }
        return launchBaseName(rom) ?: archiveBase
    }

    /**
     * The content file name save sync resolves a save unit against: the extracted entry for the
     * built-in emulator, which names its save after the file it launched, and the rom itself for
     * every emulator that loads the archive directly.
     */
    fun syncContentName(rom: File, platformSlug: String, emulatorId: String): String {
        val builtin = emulatorId == EmulatorRegistry.BUILTIN_ID || emulatorId == EmulatorRegistry.LEGACY_BUILTIN_ID
        if (!builtin || !ZipExtractor.isArchiveFile(rom) || ZipExtractor.usesZipAsRomFormat(platformSlug)) {
            return rom.name
        }
        return launchEntryFileName(rom) ?: rom.name
    }

    /**
     * Every base name a state for [rom] may already sit under: the live name first, then the
     * archive's own name for files written before the two were told apart.
     */
    fun candidateBaseNames(rom: File, platformSlug: String): List<String> =
        listOf(liveBaseName(rom, platformSlug), rom.nameWithoutExtension).distinct()

    private fun firstZipEntryName(archive: File): String? =
        try {
            ZipFile(archive).use { primaryZipEntry(it)?.name }
        } catch (_: Exception) {
            null
        }

    private fun firstSevenZEntryName(archive: File): String? =
        try {
            SevenZFile.builder().setFile(archive).get().use { sevenZ ->
                var entry = sevenZ.nextEntry
                var found: String? = null
                while (entry != null && found == null) {
                    if (isUsableEntry(entry.name, entry.isDirectory)) found = entry.name
                    entry = sevenZ.nextEntry
                }
                found
            }
        } catch (_: Exception) {
            null
        }
}
