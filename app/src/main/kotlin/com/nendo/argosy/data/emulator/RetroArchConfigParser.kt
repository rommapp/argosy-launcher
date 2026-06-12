package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StoragePathUtils
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RetroArchConfigParser"

data class RetroArchSaveConfig(
    val savefileDirectory: String?,
    val savefilesInContentDir: Boolean,
    val sortByContentDirectory: Boolean,
    val sortByCore: Boolean,
    val lastLoadedCore: String? = null
)

data class RetroArchStateConfig(
    val savestateDirectory: String?,
    val savestatesInContentDir: Boolean,
    val sortByContentDirectory: Boolean,
    val sortByCore: Boolean
)

@Singleton
class RetroArchConfigParser @Inject constructor(
    private val fileAccessLayer: FileAccessLayer
) {

    private val primaryRoot: String get() = StoragePathUtils.primaryExternalRoot

    private val configPaths: List<String>
        get() = listOf(
            "$primaryRoot/Android/data/com.retroarch/files/retroarch.cfg",
            "$primaryRoot/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
            "$primaryRoot/Android/data/com.retroarch.ra32/files/retroarch.cfg",
            "$primaryRoot/RetroArch/retroarch.cfg"
        )

    fun findConfigPath(packageName: String): String? {
        val packageSpecificPath = when (packageName) {
            "com.retroarch" -> "$primaryRoot/Android/data/com.retroarch/files/retroarch.cfg"
            "com.retroarch.aarch64" -> "$primaryRoot/Android/data/com.retroarch.aarch64/files/retroarch.cfg"
            "com.retroarch.ra32" -> "$primaryRoot/Android/data/com.retroarch.ra32/files/retroarch.cfg"
            else -> null
        }

        if (packageSpecificPath != null && fileAccessLayer.exists(packageSpecificPath)) {
            return packageSpecificPath
        }

        val portableConfig = "$primaryRoot/RetroArch/retroarch.cfg"
        if (fileAccessLayer.exists(portableConfig)) return portableConfig

        return configPaths.firstOrNull { fileAccessLayer.exists(it) }
    }

    fun parse(packageName: String): RetroArchSaveConfig? {
        val path = findConfigPath(packageName)
        if (path == null) {
            Logger.debug(TAG, "No retroarch.cfg found for $packageName")
            return null
        }

        Logger.debug(TAG, "Parsing config: $path")
        val raw = readConfig(path) ?: return null
        return raw.toSaveConfig()
    }

    fun parseStateConfig(packageName: String): RetroArchStateConfig? {
        val path = findConfigPath(packageName)
        if (path == null) {
            Logger.debug(TAG, "No retroarch.cfg found for $packageName")
            return null
        }

        val raw = readConfig(path) ?: return null
        return raw.toStateConfig()
    }

    // Reads cfg via the FAL so SAF / alt-access fallbacks cover Android 11+
    // scoped storage on /Android/data/<other-app>/. Direct java.io.File hits
    // EACCES on strict ROMs (issue #187).
    private fun readConfig(path: String): Map<String, String>? {
        val stream = fileAccessLayer.getInputStream(path)
        if (stream == null) {
            Logger.warn(TAG, "FAL could not open config: $path")
            return null
        }
        return try {
            stream.bufferedReader().useLines { parseLines(it) }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse config file: $path", e)
            null
        }
    }

    private fun parseLines(lines: Sequence<String>): Map<String, String> {
        val config = mutableMapOf<String, String>()
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                val (key, value) = trimmed.split("=", limit = 2)
                config[key.trim()] = value.trim().removeSurrounding("\"")
            }
        }
        return config
    }

    private fun Map<String, String>.toSaveConfig(): RetroArchSaveConfig {
        val savefileDirectory = this["savefile_directory"]?.takeIf {
            it != "default" && it.isNotBlank()
        }
        val savefilesInContentDir = this["savefiles_in_content_dir"] == "true"
        val sortByCore = this["sort_savefiles_enable"]?.equals("true", ignoreCase = true) ?: false
        val sortByContentDir = this["sort_savefiles_by_content_enable"]?.equals("true", ignoreCase = true) ?: false
        val lastLoadedCore = this["libretro_path"]
            ?.takeIf { it.isNotBlank() && it != "default" }
            ?.let { File(it).nameWithoutExtension.removeSuffix("_android").removeSuffix("_libretro") }

        return RetroArchSaveConfig(
            savefileDirectory = savefileDirectory,
            savefilesInContentDir = savefilesInContentDir,
            sortByContentDirectory = sortByContentDir,
            sortByCore = sortByCore,
            lastLoadedCore = lastLoadedCore
        )
    }

    private fun Map<String, String>.toStateConfig(): RetroArchStateConfig {
        val savestateDirectory = this["savestate_directory"]?.takeIf {
            it != "default" && it.isNotBlank()
        }
        val savestatesInContentDir = this["savestates_in_content_dir"] == "true"
        val sortByCore = this["sort_savestates_enable"]?.equals("true", ignoreCase = true) ?: true
        val sortByContentDir = this["sort_savestates_by_content_enable"]?.equals("true", ignoreCase = true) ?: false

        return RetroArchStateConfig(
            savestateDirectory = savestateDirectory,
            savestatesInContentDir = savestatesInContentDir,
            sortByContentDirectory = sortByContentDir,
            sortByCore = sortByCore
        )
    }

    // Test-only entry points: bypass the FAL and read a real File directly.
    // Production code reaches here only via [parse]/[parseStateConfig] above.
    internal fun parseFile(file: File): RetroArchSaveConfig =
        try {
            file.useLines { parseLines(it).toSaveConfig() }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse config file", e)
            emptyMap<String, String>().toSaveConfig()
        }

    internal fun parseStateConfigFromFile(file: File): RetroArchStateConfig =
        try {
            file.useLines { parseLines(it).toStateConfig() }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse config file", e)
            emptyMap<String, String>().toStateConfig()
        }

    /**
     * Resolves candidate RetroArch save directories.
     *
     * @param contentDirName ROM's parent directory basename (e.g. "Super Nintendo"), used when
     *   `sort_savefiles_by_content_enable` is set. This is NOT the platform slug -- RetroArch
     *   sorts into the ROM's actual parent folder name, not Argosy's internal platform id.
     *   Pass `null` when no specific ROM is available.
     */
    fun resolveSavePaths(
        packageName: String,
        contentDirName: String?,
        coreName: String?,
        contentDirectory: String? = null,
        basePathOverride: String? = null
    ): List<String> = resolveSavePathsWithConfig(
        config = parse(packageName),
        contentDirName = contentDirName,
        coreName = coreName,
        contentDirectory = contentDirectory,
        basePathOverride = basePathOverride
    )

    /**
     * Core save-path resolution logic, separated from file I/O for testability.
     * Production callers should use [resolveSavePaths] which reads the on-device config.
     */
    internal fun resolveSavePathsWithConfig(
        config: RetroArchSaveConfig?,
        contentDirName: String?,
        coreName: String?,
        contentDirectory: String? = null,
        basePathOverride: String? = null
    ): List<String> {
        val paths = mutableListOf<String>()

        val sortByContentDir = config?.sortByContentDirectory == true
        val sortByCore = config?.sortByCore == true

        if (basePathOverride == null && config?.savefilesInContentDir == true && contentDirectory != null) {
            for (base in getBasePathAlternatives(contentDirectory)) {
                paths.add(buildSortedPath(base, contentDirName, coreName, sortByContentDir, sortByCore))
            }
            Logger.debug(TAG, "resolveSavePaths: content-dir ${paths.first()} (sortByContentDir=$sortByContentDir, sortByCore=$sortByCore)")
            return paths
        }

        val baseDir = basePathOverride
            ?: config?.savefileDirectory
            ?: "$primaryRoot/RetroArch/saves"

        val baseDirs = getBasePathAlternatives(baseDir)

        val saveDir = coreName?.let { EmulatorRegistry.getRetroArchSaveDirName(it) }
            ?: discoverSingleCoreFolder(baseDirs)

        if (config == null && saveDir != null) {
            for (base in baseDirs) {
                val baseDirFile = File(base)
                val coreDir = File(base, saveDir)

                if (coreDir.exists() && coreDir.isDirectory) {
                    paths.add(coreDir.absolutePath)
                    Logger.debug(TAG, "resolveSavePaths: config missing, found core folder $saveDir")
                } else if (baseDirFile.exists() && hasCoreFolders(baseDirFile)) {
                    paths.add(coreDir.absolutePath)
                    Logger.debug(TAG, "resolveSavePaths: config missing, base has core folders, using $saveDir")
                } else {
                    paths.add(base)
                    Logger.debug(TAG, "resolveSavePaths: config missing, no core folders found, using flat")
                }
            }
            return paths
        }

        for (base in baseDirs) {
            paths.add(buildSortedPath(base, contentDirName, saveDir, sortByContentDir, sortByCore))
        }

        Logger.debug(TAG, "resolveSavePaths: ${paths.first()} (sortByContentDir=$sortByContentDir, sortByCore=$sortByCore)")
        return paths
    }

    private fun buildSortedPath(
        base: String,
        contentDirName: String?,
        coreName: String?,
        sortByContentDir: Boolean,
        sortByCore: Boolean
    ): String = buildString {
        append(base)
        if (sortByContentDir && contentDirName != null) {
            append("/").append(contentDirName)
        }
        if (sortByCore && coreName != null) {
            append("/").append(coreName)
        }
    }

    private fun getBasePathAlternatives(path: String): List<String> {
        val alternatives = mutableListOf(path)
        val primaryWithSlash = "$primaryRoot/"
        when {
            path.contains(primaryWithSlash) -> {
                alternatives.add(path.replace(primaryWithSlash, "/Internal/"))
            }
            path.contains("/Internal/") -> {
                alternatives.add(path.replace("/Internal/", primaryWithSlash))
            }
        }
        return alternatives
    }

    private fun hasCoreFolders(savesDir: File): Boolean {
        val subdirs = savesDir.listFiles()?.filter { it.isDirectory } ?: return false
        return subdirs.any { dir ->
            KNOWN_CORE_NAMES.any { core -> dir.name.equals(core, ignoreCase = true) }
        }
    }

    // When the caller can't supply a core id but RA is configured to sort saves into core
    // subdirectories, scan the base dir for a single matching core folder and use it. If
    // there's exactly one, that's the unambiguous answer; if zero or many, return null and
    // let the caller fall back to the flat base.
    private fun discoverSingleCoreFolder(baseDirs: List<String>): String? {
        for (base in baseDirs) {
            val dir = File(base)
            if (!dir.exists() || !dir.isDirectory) continue
            val coreSubdirs = dir.listFiles()?.filter { f ->
                f.isDirectory && KNOWN_CORE_NAMES.any { it.equals(f.name, ignoreCase = true) }
            } ?: continue
            if (coreSubdirs.size == 1) {
                Logger.debug(TAG, "discoverSingleCoreFolder: inferring '${coreSubdirs[0].name}' from $base")
                return coreSubdirs[0].name
            }
        }
        return null
    }

    companion object {
        private val KNOWN_CORE_NAMES = setOf(
            "mGBA", "Gambatte", "Snes9x", "bsnes", "Genesis Plus GX", "PPSSPP",
            "Mupen64Plus", "DeSmuME", "melonDS", "Beetle PSX", "PCSX ReARMed",
            "FinalBurn Neo", "MAME", "Stella", "FCEUmm", "Nestopia", "VBA-M",
            "Flycast", "Dolphin", "Citra", "PPSSPP", "Mednafen"
        )
    }

    /**
     * Resolves candidate RetroArch savestate directories.
     *
     * @param contentDirName ROM's parent directory basename, used when
     *   `sort_savestates_by_content_enable` is set. Pass `null` when no specific ROM is available.
     */
    fun resolveStatePaths(
        packageName: String,
        contentDirName: String?,
        coreName: String?,
        contentDirectory: String? = null,
        basePathOverride: String? = null
    ): List<String> = resolveStatePathsWithConfig(
        config = parseStateConfig(packageName),
        contentDirName = contentDirName,
        coreName = coreName,
        contentDirectory = contentDirectory,
        basePathOverride = basePathOverride
    )

    /**
     * Core state-path resolution logic, separated from file I/O for testability.
     * Production callers should use [resolveStatePaths].
     */
    internal fun resolveStatePathsWithConfig(
        config: RetroArchStateConfig?,
        contentDirName: String?,
        coreName: String?,
        contentDirectory: String? = null,
        basePathOverride: String? = null
    ): List<String> {
        val paths = mutableListOf<String>()

        val sortByContentDir = config?.sortByContentDirectory == true
        val sortByCore = config?.sortByCore == true

        if (basePathOverride == null && config?.savestatesInContentDir == true && contentDirectory != null) {
            for (base in getBasePathAlternatives(contentDirectory)) {
                paths.add(buildSortedPath(base, contentDirName, coreName, sortByContentDir, sortByCore))
            }
            Logger.debug(TAG, "resolveStatePaths: content-dir ${paths.first()} (sortByContentDir=$sortByContentDir, sortByCore=$sortByCore)")
            return paths
        }

        val baseDir = basePathOverride
            ?: config?.savestateDirectory
            ?: "$primaryRoot/RetroArch/states"

        val baseDirs = getBasePathAlternatives(baseDir)

        if (config == null && coreName != null) {
            for (base in baseDirs) {
                val statesDir = File(base)
                val coreDir = File(base, coreName)

                if (coreDir.exists() && coreDir.isDirectory) {
                    paths.add(coreDir.absolutePath)
                    Logger.debug(TAG, "resolveStatePaths: config missing, found core folder $coreName")
                } else if (statesDir.exists() && hasCoreFolders(statesDir)) {
                    paths.add(coreDir.absolutePath)
                    Logger.debug(TAG, "resolveStatePaths: config missing, base has core folders, using $coreName")
                } else {
                    paths.add(base)
                    Logger.debug(TAG, "resolveStatePaths: config missing, no core folders found, using flat")
                }
            }
            return paths
        }

        val effectiveCoreName = coreName ?: discoverSingleCoreFolder(baseDirs)
        for (base in baseDirs) {
            paths.add(buildSortedPath(base, contentDirName, effectiveCoreName, sortByContentDir, sortByCore))
        }

        Logger.debug(TAG, "resolveStatePaths: ${paths.first()} (sortByContentDir=$sortByContentDir, sortByCore=$sortByCore)")
        return paths
    }
}
