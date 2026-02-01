package com.nendo.argosy.data.download

import android.util.Log
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

private const val TAG = "ZipExtractor"

data class ExtractedFolderRom(
    val primaryFile: File?,
    val discFiles: List<File>,
    val m3uFile: File?,
    val gameFolder: File,
    val allFiles: List<File>
) {
    val launchPath: String
        get() {
            // 1. M3U for multi-disc games
            m3uFile?.absolutePath?.let { return it }

            // 2. For disc-based games, prefer cue/gdi/chd over raw iso/bin
            val cueGdiFile = discFiles.find { it.extension.lowercase() in setOf("cue", "gdi") }
            if (cueGdiFile != null) return cueGdiFile.absolutePath

            val chdFile = discFiles.find { it.extension.lowercase() == "chd" }
            if (chdFile != null) return chdFile.absolutePath

            // 3. Fall back to primary file or first disc
            if (primaryFile != null) return primaryFile.absolutePath
            if (discFiles.isNotEmpty()) return discFiles.first().absolutePath

            // 4. Search allFiles for disc files (handles zips with files in subfolders)
            val discExtensions = setOf("bin", "cue", "chd", "iso", "img", "mdf", "gdi", "cdi")
            val allDiscFiles = allFiles.filter {
                it.extension.lowercase() in discExtensions && !it.name.startsWith("._")
            }
            val nestedCueGdi = allDiscFiles.find { it.extension.lowercase() in setOf("cue", "gdi") }
            if (nestedCueGdi != null) return nestedCueGdi.absolutePath
            val nestedChd = allDiscFiles.find { it.extension.lowercase() == "chd" }
            if (nestedChd != null) return nestedChd.absolutePath
            if (allDiscFiles.isNotEmpty()) return allDiscFiles.first().absolutePath

            return gameFolder.absolutePath
        }
}

private val NSW_UPDATE_EXTENSIONS = setOf("nsp")
private val NSW_PLATFORM_SLUGS = setOf("switch", "nsw")
private val UPDATE_FILENAME_PATTERNS = listOf("[UPD]", "[UPDATE]")
private val DLC_FILENAME_PATTERNS = listOf("[DLC]")
private val DISC_EXTENSIONS = setOf("bin", "cue", "chd", "iso", "img", "mdf", "gdi", "cdi")
private val ZIP_MAGIC_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val SEVEN_Z_MAGIC_BYTES = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

private val ZIP_AS_ROM_PLATFORMS = setOf(
    "arcade", "mame", "fbneo", "fba",
    "neogeo", "neogeocd",
    "cps1", "cps2", "cps3",
    "naomi", "naomi2", "atomiswave",
    "model2", "model3",
    "hyperneogeo64",
    "vita", "psvita",
    "dos"
)

data class PlatformExtractConfig(
    val platformSlugs: Set<String>,
    val gameExtensions: Set<String>,
    val updateExtensions: Set<String>,
    val dlcExtensions: Set<String>,
    val updateFolder: String,
    val dlcFolder: String
)

// RomM uses standardized folder names: update, dlc, hack, mod, patch, manual, demo, translation, prototype
private val PLATFORM_CONFIGS = listOf(
    PlatformExtractConfig(
        platformSlugs = setOf("switch", "nsw"),
        gameExtensions = setOf("xci", "nsp", "nca", "nro"),
        updateExtensions = setOf("nsp"),
        dlcExtensions = setOf("nsp"),
        updateFolder = "update",
        dlcFolder = "dlc"
    ),
    PlatformExtractConfig(
        platformSlugs = setOf("vita", "psvita"),
        gameExtensions = setOf("vpk", "zip"),
        updateExtensions = setOf("vpk"),
        dlcExtensions = setOf("vpk"),
        updateFolder = "update",
        dlcFolder = "dlc"
    ),
    PlatformExtractConfig(
        platformSlugs = setOf("wiiu"),
        gameExtensions = setOf("wua", "wud", "wux", "wup", "rpx"),
        updateExtensions = setOf("wup"),
        dlcExtensions = setOf("wup"),
        updateFolder = "update",
        dlcFolder = "dlc"
    ),
    PlatformExtractConfig(
        platformSlugs = setOf("wii"),
        gameExtensions = setOf("wbfs", "iso", "ciso", "wia", "rvz"),
        updateExtensions = setOf("wad"),
        dlcExtensions = setOf("wad"),
        updateFolder = "update",
        dlcFolder = "dlc"
    )
)

object ZipExtractor {

    fun isNswPlatform(platformSlug: String): Boolean {
        return platformSlug.lowercase() in NSW_PLATFORM_SLUGS
    }

    fun usesZipAsRomFormat(platformSlug: String): Boolean {
        return platformSlug.lowercase() in ZIP_AS_ROM_PLATFORMS
    }

    fun getPlatformConfig(platformSlug: String): PlatformExtractConfig? {
        val slug = platformSlug.lowercase()
        return PLATFORM_CONFIGS.find { slug in it.platformSlugs }
    }

    fun hasUpdateSupport(platformSlug: String): Boolean {
        return getPlatformConfig(platformSlug) != null
    }

    fun isZipFile(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return file.inputStream().use { stream ->
            val header = ByteArray(4)
            stream.read(header) == 4 && header.contentEquals(ZIP_MAGIC_BYTES)
        }
    }

    fun isSevenZFile(file: File): Boolean {
        if (!file.isFile || file.length() < 6) return false
        return file.inputStream().use { stream ->
            val header = ByteArray(6)
            stream.read(header) == 6 && header.contentEquals(SEVEN_Z_MAGIC_BYTES)
        }
    }

    fun isArchiveFile(file: File): Boolean = isZipFile(file) || isSevenZFile(file)

    sealed class ArchiveValidationResult {
        data object Valid : ArchiveValidationResult()
        data class Invalid(val reason: String) : ArchiveValidationResult()
    }

    fun validateArchive(file: File, expectedSize: Long = 0): ArchiveValidationResult {
        if (!file.exists()) {
            return ArchiveValidationResult.Invalid("File does not exist")
        }

        if (!isArchiveFile(file)) {
            return ArchiveValidationResult.Invalid("File is not a valid archive")
        }

        if (expectedSize > 0 && file.length() < expectedSize) {
            val percent = (file.length() * 100 / expectedSize).toInt()
            return ArchiveValidationResult.Invalid(
                "Download incomplete ($percent% - ${file.length()} of $expectedSize bytes)"
            )
        }

        return when {
            isZipFile(file) -> validateZipInternal(file)
            isSevenZFile(file) -> validateSevenZInternal(file)
            else -> ArchiveValidationResult.Invalid("Unknown archive format")
        }
    }

    private fun validateZipInternal(file: File): ArchiveValidationResult {
        return try {
            ZipFile.builder().setFile(file).get().use { zip ->
                val entries = zip.entries
                var entryCount = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    zip.getInputStream(entry).use { stream ->
                        val buffer = ByteArray(1)
                        stream.read(buffer)
                    }
                    entryCount++
                }
                if (entryCount == 0) {
                    ArchiveValidationResult.Invalid("ZIP archive is empty")
                } else {
                    ArchiveValidationResult.Valid
                }
            }
        } catch (e: java.util.zip.ZipException) {
            ArchiveValidationResult.Invalid("ZIP file is corrupted: ${e.message}")
        } catch (e: Exception) {
            ArchiveValidationResult.Invalid("Failed to validate ZIP: ${e.message}")
        }
    }

    private fun validateSevenZInternal(file: File): ArchiveValidationResult {
        return try {
            SevenZFile.builder().setFile(file).get().use { sevenZ ->
                var entryCount = 0
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entryCount++
                    }
                    entry = sevenZ.nextEntry
                }
                if (entryCount == 0) {
                    ArchiveValidationResult.Invalid("7z archive is empty")
                } else {
                    ArchiveValidationResult.Valid
                }
            }
        } catch (e: Exception) {
            ArchiveValidationResult.Invalid("Failed to validate 7z: ${e.message}")
        }
    }

    fun shouldExtractArchive(archiveFile: File, platformSlug: String? = null): Boolean {
        if (archiveFile.extension.equals("apk", ignoreCase = true)) return false

        val zipIsRomFormat = platformSlug?.let { usesZipAsRomFormat(it) } ?: false

        return when {
            isSevenZFile(archiveFile) -> {
                // 7z always needs extraction - standalone emulators don't support it
                // Only exception would be arcade, but arcade ROMs use zip not 7z
                if (!zipIsRomFormat) true else shouldExtractSevenZInternal(archiveFile)
            }
            isZipFile(archiveFile) -> !zipIsRomFormat && shouldExtractZipInternal(archiveFile)
            else -> false
        }
    }

    private fun shouldExtractZipInternal(zipFile: File): Boolean {
        return try {
            ZipFile.builder().setFile(zipFile).get().use { zip ->
                val entries = zip.entries.toList().filter { !it.isDirectory }
                val hasMultipleFiles = entries.size > 1
                val hasFolderStructure = entries.any { it.name.contains("/") || it.name.contains("\\") }
                hasMultipleFiles || hasFolderStructure
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect zip: ${e.message}")
            false
        }
    }

    private fun shouldExtractSevenZInternal(sevenZFile: File): Boolean {
        return try {
            SevenZFile.builder().setFile(sevenZFile).get().use { sevenZ ->
                var fileCount = 0
                var hasFolderStructure = false
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        fileCount++
                        if (entry.name.contains("/") || entry.name.contains("\\")) {
                            hasFolderStructure = true
                        }
                    }
                    entry = sevenZ.nextEntry
                }
                fileCount > 1 || hasFolderStructure
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect 7z: ${e.message}")
            false
        }
    }

    private fun generateM3uFile(gameFolder: File, gameTitle: String, discFiles: List<File>): File {
        val m3uFile = File(gameFolder, "$gameTitle.m3u")
        val sortedDiscs = discFiles
            .filter { it.extension.lowercase() !in setOf("cue", "gdi") || discFiles.none { other ->
                other.nameWithoutExtension == it.nameWithoutExtension && other.extension.lowercase() in setOf("bin", "img")
            }}
            .sortedWith(compareBy(
                { extractDiscNumber(it.name) ?: Int.MAX_VALUE },
                { it.name }
            ))

        val content = sortedDiscs.joinToString("\n") { it.name }
        m3uFile.writeText(content, Charsets.US_ASCII)
        return m3uFile
    }

    private fun extractDiscNumber(fileName: String): Int? {
        val patterns = listOf(
            Regex("""[Dd]isc\s*(\d+)"""),
            Regex("""[Dd]isk\s*(\d+)"""),
            Regex("""[Cc][Dd]\s*(\d+)"""),
            Regex("""\((\d+)\s*of\s*\d+\)""")
        )
        for (pattern in patterns) {
            pattern.find(fileName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    fun organizeNswSingleFile(
        romFile: File,
        gameTitle: String,
        platformDir: File
    ): File {
        val sanitizedTitle = sanitizeFileName(gameTitle)
        val gameFolder = File(platformDir, sanitizedTitle).apply { mkdirs() }

        val extension = romFile.name.substringAfterLast('.', "").lowercase()
        val targetFileName = if (romFile.name.startsWith(sanitizedTitle)) {
            romFile.name
        } else {
            "$sanitizedTitle.${extension}"
        }

        val targetFile = File(gameFolder, targetFileName)

        if (romFile.absolutePath != targetFile.absolutePath && !romFile.renameTo(targetFile)) {
            romFile.copyTo(targetFile, overwrite = true)
            romFile.delete()
        }

        return targetFile
    }

    fun getUpdatesFolder(localPath: String, platformSlug: String? = null): File? {
        val romFile = File(localPath)
        if (!romFile.exists()) return null

        val gameFolder = romFile.parentFile ?: return null
        val config = platformSlug?.let { getPlatformConfig(it) }
        val folderName = config?.updateFolder ?: "update"

        // Check both singular and plural folder names for compatibility
        val candidates = listOf(folderName, "${folderName}s").distinct()
        for (name in candidates) {
            val folder = File(gameFolder, name)
            if (folder.exists() && folder.isDirectory) {
                return folder
            }
        }
        return null
    }

    fun getDlcFolder(localPath: String, platformSlug: String): File? {
        val romFile = File(localPath)
        if (!romFile.exists()) return null

        val gameFolder = romFile.parentFile ?: return null
        val config = getPlatformConfig(platformSlug) ?: return null
        val dlcFolder = File(gameFolder, config.dlcFolder)

        return if (dlcFolder.exists() && dlcFolder.isDirectory) {
            dlcFolder
        } else {
            null
        }
    }

    fun listUpdateFiles(localPath: String, platformSlug: String? = null): List<File> {
        val config = platformSlug?.let { getPlatformConfig(it) }
        val updatesFolder = getUpdatesFolder(localPath, platformSlug) ?: return emptyList()
        val updateExtensions = config?.updateExtensions ?: NSW_UPDATE_EXTENSIONS

        return updatesFolder.listFiles()
            ?.filter { it.isFile && !it.name.startsWith("._") && it.extension.lowercase() in updateExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun listDlcFiles(localPath: String, platformSlug: String): List<File> {
        val config = getPlatformConfig(platformSlug) ?: return emptyList()
        val dlcFolder = getDlcFolder(localPath, platformSlug) ?: return emptyList()

        return dlcFolder.listFiles()
            ?.filter { it.isFile && !it.name.startsWith("._") && it.extension.lowercase() in config.dlcExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun listAllUpdateFiles(localPath: String, platformSlug: String? = null): List<File> {
        val config = platformSlug?.let { getPlatformConfig(it) } ?: return emptyList()
        val gameFolder = File(localPath).parentFile ?: return emptyList()
        val results = mutableSetOf<File>()

        results.addAll(listUpdateFiles(localPath, platformSlug))

        gameFolder.listFiles()
            ?.filter { file ->
                file.isFile &&
                    !file.name.startsWith("._") &&
                    file.extension.lowercase() in config.updateExtensions &&
                    UPDATE_FILENAME_PATTERNS.any { file.name.contains(it, ignoreCase = true) }
            }
            ?.let { results.addAll(it) }

        return results.sortedBy { it.name }
    }

    fun listAllDlcFiles(localPath: String, platformSlug: String): List<File> {
        val config = getPlatformConfig(platformSlug) ?: return emptyList()
        val gameFolder = File(localPath).parentFile ?: return emptyList()
        val results = mutableSetOf<File>()

        results.addAll(listDlcFiles(localPath, platformSlug))

        gameFolder.listFiles()
            ?.filter { file ->
                file.isFile &&
                    !file.name.startsWith("._") &&
                    file.extension.lowercase() in config.dlcExtensions &&
                    DLC_FILENAME_PATTERNS.any { file.name.contains(it, ignoreCase = true) }
            }
            ?.let { results.addAll(it) }

        return results.sortedBy { it.name }
    }

    fun extractFolderRom(
        archiveFilePath: File,
        gameTitle: String,
        platformDir: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): ExtractedFolderRom {
        val sanitizedTitle = sanitizeFileName(gameTitle)
        val gameFolder = File(platformDir, sanitizedTitle)

        Log.d(TAG, "=== Folder ROM Extraction Start ===")
        Log.d(TAG, "Archive file: ${archiveFilePath.absolutePath}")
        Log.d(TAG, "Archive exists: ${archiveFilePath.exists()}, size: ${archiveFilePath.length()}")
        Log.d(TAG, "Game folder: ${gameFolder.absolutePath}")

        val extension = if (isSevenZFile(archiveFilePath)) "7z" else "zip"
        val actualArchiveFile = if (archiveFilePath.absolutePath == gameFolder.absolutePath) {
            val renamedArchive = File(platformDir, "${sanitizedTitle}.$extension")
            Log.d(TAG, "Archive path collides with game folder, renaming to: ${renamedArchive.absolutePath}")
            if (!archiveFilePath.renameTo(renamedArchive)) {
                archiveFilePath.copyTo(renamedArchive, overwrite = true)
                archiveFilePath.delete()
            }
            renamedArchive
        } else {
            archiveFilePath
        }

        gameFolder.mkdirs()

        val extractionResult = when {
            isSevenZFile(actualArchiveFile) -> extractSevenZInternal(actualArchiveFile, gameFolder, onProgress)
            else -> extractZipInternal(actualArchiveFile, gameFolder, onProgress)
        }

        if (actualArchiveFile != archiveFilePath && actualArchiveFile.exists()) {
            Log.d(TAG, "Cleaning up renamed archive: ${actualArchiveFile.absolutePath}")
            actualArchiveFile.delete()
        }

        Log.d(TAG, "=== Folder ROM Extraction Complete ===")
        Log.d(TAG, "Primary file: ${extractionResult.primaryFile?.absolutePath}")
        Log.d(TAG, "Root disc files: ${extractionResult.rootDiscFiles.size}")
        Log.d(TAG, "Existing M3U: ${extractionResult.existingM3u?.absolutePath}")
        Log.d(TAG, "Total extracted: ${extractionResult.allFiles.size}")

        val launchableDiscFiles = filterToLaunchableDiscs(extractionResult.rootDiscFiles)
        Log.d(TAG, "Launchable disc files: ${launchableDiscFiles.size}")

        val m3uFile = when {
            launchableDiscFiles.size <= 1 -> {
                Log.d(TAG, "Single disc game - skipping m3u, will use disc file directly")
                null
            }
            extractionResult.existingM3u != null && isValidM3u(extractionResult.existingM3u, launchableDiscFiles) -> {
                Log.d(TAG, "Using validated existing m3u")
                extractionResult.existingM3u
            }
            launchableDiscFiles.size > 1 -> {
                Log.d(TAG, "Generating new m3u for ${launchableDiscFiles.size} discs")
                generateM3uFile(gameFolder, sanitizedTitle, launchableDiscFiles)
            }
            else -> null
        }

        return ExtractedFolderRom(
            primaryFile = extractionResult.primaryFile,
            discFiles = extractionResult.rootDiscFiles.sortedBy { it.name },
            m3uFile = m3uFile,
            gameFolder = gameFolder,
            allFiles = extractionResult.allFiles
        )
    }

    private data class RawExtractionResult(
        val allFiles: List<File>,
        val rootDiscFiles: List<File>,
        val primaryFile: File?,
        val existingM3u: File?
    )

    private fun extractZipInternal(
        zipFile: File,
        gameFolder: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): RawExtractionResult {
        val allFiles = mutableListOf<File>()
        val rootDiscFiles = mutableListOf<File>()
        var primaryFile: File? = null
        var existingM3u: File? = null

        ZipFile.builder().setFile(zipFile).get().use { zip ->
            val entries = zip.entries.toList().filter { !it.isDirectory }
            val totalBytes = entries.sumOf { it.size }
            var bytesWritten = 0L
            var lastReportedBytes = 0L
            val progressThreshold = 1024 * 1024L

            Log.d(TAG, "Total entries found: ${entries.size}, total bytes: $totalBytes")

            entries.forEach { entry ->
                val entryPath = entry.name
                val fileName = File(entryPath).name
                val ext = fileName.substringAfterLast('.', "").lowercase()
                val isRootFile = !entryPath.contains("/") && !entryPath.contains("\\")

                val targetFile = File(gameFolder, entryPath)
                targetFile.parentFile?.mkdirs()

                Log.d(TAG, "Extracting: $entryPath -> ${targetFile.absolutePath}, size: ${entry.size}")

                zip.getInputStream(entry).buffered().use { input ->
                    targetFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            if (bytesWritten - lastReportedBytes >= progressThreshold) {
                                onProgress?.invoke(bytesWritten, totalBytes)
                                lastReportedBytes = bytesWritten
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                Log.d(TAG, "Extracted: $fileName, expected=${entry.size}, actual=${targetFile.length()}")

                if (targetFile.length() == 0L && entry.size > 0) {
                    Log.e(TAG, "ERROR: File $fileName extracted as 0 bytes but expected ${entry.size}")
                }

                allFiles.add(targetFile)
                classifyExtractedFile(fileName, ext, isRootFile, targetFile, rootDiscFiles, { primaryFile = it }, { existingM3u = it }) {
                    primaryFile == null
                }
            }
        }

        return RawExtractionResult(allFiles, rootDiscFiles, primaryFile, existingM3u)
    }

    private fun extractSevenZInternal(
        sevenZFile: File,
        gameFolder: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?
    ): RawExtractionResult {
        val allFiles = mutableListOf<File>()
        val rootDiscFiles = mutableListOf<File>()
        var primaryFile: File? = null
        var existingM3u: File? = null

        SevenZFile.builder().setFile(sevenZFile).get().use { sevenZ ->
            val entries = mutableListOf<org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry>()
            var entry = sevenZ.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries.add(entry)
                }
                entry = sevenZ.nextEntry
            }

            val totalBytes = entries.sumOf { it.size }
            var bytesWritten = 0L
            var lastReportedBytes = 0L
            val progressThreshold = 1024 * 1024L

            Log.d(TAG, "Total entries found: ${entries.size}, total bytes: $totalBytes")

            SevenZFile.builder().setFile(sevenZFile).get().use { sevenZRead ->
                var readEntry = sevenZRead.nextEntry
                while (readEntry != null) {
                    if (!readEntry.isDirectory) {
                        val entryPath = readEntry.name
                        val fileName = File(entryPath).name
                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        val isRootFile = !entryPath.contains("/") && !entryPath.contains("\\")

                        val targetFile = File(gameFolder, entryPath)
                        targetFile.parentFile?.mkdirs()

                        Log.d(TAG, "Extracting: $entryPath -> ${targetFile.absolutePath}, size: ${readEntry.size}")

                        targetFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            val inputStream = sevenZRead.getInputStream(readEntry)
                            var bytes = inputStream.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesWritten += bytes
                                if (bytesWritten - lastReportedBytes >= progressThreshold) {
                                    onProgress?.invoke(bytesWritten, totalBytes)
                                    lastReportedBytes = bytesWritten
                                }
                                bytes = inputStream.read(buffer)
                            }
                        }

                        Log.d(TAG, "Extracted: $fileName, expected=${readEntry.size}, actual=${targetFile.length()}")

                        if (targetFile.length() == 0L && readEntry.size > 0) {
                            Log.e(TAG, "ERROR: File $fileName extracted as 0 bytes but expected ${readEntry.size}")
                        }

                        allFiles.add(targetFile)
                        classifyExtractedFile(fileName, ext, isRootFile, targetFile, rootDiscFiles, { primaryFile = it }, { existingM3u = it }) {
                            primaryFile == null
                        }
                    }
                    readEntry = sevenZRead.nextEntry
                }
            }
        }

        return RawExtractionResult(allFiles, rootDiscFiles, primaryFile, existingM3u)
    }

    private inline fun classifyExtractedFile(
        fileName: String,
        extension: String,
        isRootFile: Boolean,
        targetFile: File,
        rootDiscFiles: MutableList<File>,
        setPrimaryFile: (File) -> Unit,
        setExistingM3u: (File) -> Unit,
        isPrimaryFileNull: () -> Boolean
    ) {
        val isMacOsResourceFork = fileName.startsWith("._")
        when {
            isMacOsResourceFork -> { }
            extension == "m3u" && isRootFile -> setExistingM3u(targetFile)
            extension in DISC_EXTENSIONS && isRootFile -> rootDiscFiles.add(targetFile)
            isPrimaryFileNull() && isGameFile(extension) && isRootFile -> setPrimaryFile(targetFile)
        }
    }

    private fun filterToLaunchableDiscs(discFiles: List<File>): List<File> {
        // For disc-based games, we need to identify which files are actually launchable
        // vs which are just data files referenced by cue/gdi sheets
        val cueGdiFiles = discFiles.filter { it.extension.lowercase() in setOf("cue", "gdi") }
        val chdFiles = discFiles.filter { it.extension.lowercase() == "chd" }

        return when {
            // CHD files are self-contained, each is a launchable disc
            chdFiles.isNotEmpty() -> chdFiles
            // CUE/GDI files reference bin/iso/img, so they are the launchable files
            cueGdiFiles.isNotEmpty() -> cueGdiFiles
            // Otherwise, use iso/bin/img files directly
            else -> discFiles.filter { it.extension.lowercase() in setOf("iso", "bin", "img", "cdi") }
        }
    }

    private fun isValidM3u(m3uFile: File, launchableDiscs: List<File>): Boolean {
        if (!m3uFile.exists()) return false

        val lines = try {
            m3uFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read m3u for validation: ${e.message}")
            return false
        }

        // M3U should list exactly the launchable disc files
        if (lines.size != launchableDiscs.size) {
            Log.d(TAG, "M3U validation failed: ${lines.size} entries vs ${launchableDiscs.size} launchable discs")
            return false
        }

        // Each line should reference an existing launchable file
        val launchableNames = launchableDiscs.map { it.name.lowercase() }.toSet()
        val m3uDir = m3uFile.parentFile ?: return false

        for (line in lines) {
            val referencedFile = File(m3uDir, line)
            if (!referencedFile.exists()) {
                Log.d(TAG, "M3U validation failed: referenced file doesn't exist: $line")
                return false
            }
            // Check if it references a launchable file (not a data file like .iso when .cue exists)
            if (referencedFile.name.lowercase() !in launchableNames) {
                Log.d(TAG, "M3U validation failed: $line is not a launchable disc file")
                return false
            }
        }

        return true
    }

    private fun isGameFile(extension: String): Boolean {
        val gameExtensions = setOf(
            // Nintendo Switch
            "xci", "nsp", "nca", "nro",
            // Nintendo 3DS
            "3ds", "cci", "cxi", "cia",
            // Nintendo DS
            "nds", "dsi",
            // Nintendo Wii/WiiU
            "wbfs", "wua", "wud", "wux", "wup", "rpx", "iso", "ciso", "wia", "rvz",
            // PlayStation
            "chd", "cue", "bin", "img", "mdf", "pbp", "vpk",
            // Sega
            "gdi", "cdi",
            // General
            "zip", "7z"
        )
        return extension in gameExtensions
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }
}
