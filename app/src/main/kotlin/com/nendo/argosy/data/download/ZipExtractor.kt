package com.nendo.argosy.data.download

import android.util.Log
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "ZipExtractor"

data class ExtractedFolderRom(
    val primaryFile: File?,
    val discFiles: List<File>,
    val m3uFile: File?,
    val gameFolder: File,
    val allFiles: List<File>
) {
    val launchPath: String
        get() = m3uFile?.absolutePath
            ?: primaryFile?.absolutePath
            ?: discFiles.firstOrNull()?.absolutePath
            ?: gameFolder.absolutePath
}

private val NSW_UPDATE_EXTENSIONS = setOf("nsp")
private val NSW_PLATFORM_SLUGS = setOf("switch", "nsw")
private val DISC_EXTENSIONS = setOf("bin", "cue", "chd", "iso", "img", "mdf", "gdi", "cdi")
private val ZIP_MAGIC_BYTES = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

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

    fun getPlatformConfig(platformSlug: String): PlatformExtractConfig? {
        val slug = platformSlug.lowercase()
        return PLATFORM_CONFIGS.find { slug in it.platformSlugs }
    }

    fun hasUpdateSupport(platformSlug: String): Boolean {
        return getPlatformConfig(platformSlug) != null
    }

    fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return file.inputStream().use { stream ->
            val header = ByteArray(4)
            stream.read(header) == 4 && header.contentEquals(ZIP_MAGIC_BYTES)
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
        val updatesFolder = File(gameFolder, folderName)

        return if (updatesFolder.exists() && updatesFolder.isDirectory) {
            updatesFolder
        } else {
            null
        }
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
            ?.filter { it.isFile && it.extension.lowercase() in updateExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun listDlcFiles(localPath: String, platformSlug: String): List<File> {
        val config = getPlatformConfig(platformSlug) ?: return emptyList()
        val dlcFolder = getDlcFolder(localPath, platformSlug) ?: return emptyList()

        return dlcFolder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in config.dlcExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun extractFolderRom(
        zipFilePath: File,
        gameTitle: String,
        platformDir: File,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): ExtractedFolderRom {
        val sanitizedTitle = sanitizeFileName(gameTitle)
        val gameFolder = File(platformDir, sanitizedTitle).apply { mkdirs() }

        Log.d(TAG, "=== Folder ROM Extraction Start ===")
        Log.d(TAG, "ZIP file: ${zipFilePath.absolutePath}")
        Log.d(TAG, "ZIP exists: ${zipFilePath.exists()}, size: ${zipFilePath.length()}")
        Log.d(TAG, "Game folder: ${gameFolder.absolutePath}")

        val allFiles = mutableListOf<File>()
        val discFiles = mutableListOf<File>()
        var primaryFile: File? = null
        var existingM3u: File? = null

        ZipFile(zipFilePath).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory }
            val totalBytes = entries.sumOf { it.size }
            var bytesWritten = 0L
            var lastReportedBytes = 0L
            val progressThreshold = 1024 * 1024L

            Log.d(TAG, "Total entries found: ${entries.size}, total bytes: $totalBytes")

            entries.forEach { entry ->
                val entryPath = entry.name
                val fileName = File(entryPath).name
                val extension = fileName.substringAfterLast('.', "").lowercase()

                // Preserve subfolder structure from ZIP
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

                when {
                    extension == "m3u" -> existingM3u = targetFile
                    extension in DISC_EXTENSIONS -> discFiles.add(targetFile)
                    primaryFile == null && isGameFile(extension) -> primaryFile = targetFile
                }
            }
        }

        Log.d(TAG, "=== Folder ROM Extraction Complete ===")
        Log.d(TAG, "Primary file: ${primaryFile?.absolutePath}")
        Log.d(TAG, "Disc files: ${discFiles.size}")
        Log.d(TAG, "Existing M3U: ${existingM3u?.absolutePath}")
        Log.d(TAG, "Total extracted: ${allFiles.size}")

        // Generate M3U if multiple disc files and no existing M3U
        val m3uFile = existingM3u ?: if (discFiles.size > 1) {
            generateM3uFile(gameFolder, sanitizedTitle, discFiles)
        } else null

        return ExtractedFolderRom(
            primaryFile = primaryFile,
            discFiles = discFiles.sortedBy { it.name },
            m3uFile = m3uFile,
            gameFolder = gameFolder,
            allFiles = allFiles
        )
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
