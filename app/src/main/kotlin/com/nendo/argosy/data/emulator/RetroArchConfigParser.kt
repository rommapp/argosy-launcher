package com.nendo.argosy.data.emulator

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RetroArchConfigParser"

data class RetroArchSaveConfig(
    val savefileDirectory: String?,
    val savefilesInContentDir: Boolean,
    val sortByContentDirectory: Boolean,
    val sortByCore: Boolean
)

data class RetroArchStateConfig(
    val savestateDirectory: String?,
    val savestatesInContentDir: Boolean,
    val sortByContentDirectory: Boolean,
    val sortByCore: Boolean
)

@Singleton
class RetroArchConfigParser @Inject constructor() {

    private val configPaths = listOf(
        "/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg",
        "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
        "/storage/emulated/0/RetroArch/retroarch.cfg"
    )

    fun findConfigFile(packageName: String): File? {
        val packageSpecificPath = when (packageName) {
            "com.retroarch" -> "/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg"
            "com.retroarch.aarch64" -> "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg"
            else -> null
        }

        if (packageSpecificPath != null) {
            val file = File(packageSpecificPath)
            if (file.exists()) return file
        }

        val portableConfig = File("/storage/emulated/0/RetroArch/retroarch.cfg")
        if (portableConfig.exists()) return portableConfig

        return configPaths
            .map { File(it) }
            .firstOrNull { it.exists() }
    }

    fun parse(packageName: String): RetroArchSaveConfig? {
        val configFile = findConfigFile(packageName)
        if (configFile == null) {
            Log.d(TAG, "No retroarch.cfg found for $packageName")
            return null
        }

        Log.d(TAG, "Parsing config: ${configFile.absolutePath}")
        return parseFile(configFile)
    }

    private fun parseFile(file: File): RetroArchSaveConfig {
        val config = mutableMapOf<String, String>()

        try {
            file.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        val (key, value) = trimmed.split("=", limit = 2)
                        config[key.trim()] = value.trim().removeSurrounding("\"")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config file", e)
        }

        val savefileDirectory = config["savefile_directory"]?.takeIf {
            it != "default" && it.isNotBlank()
        }
        val savefilesInContentDir = config["savefiles_in_content_dir"] == "true"
        // sort_savefiles_enable = "Sort into folders by Core Name"
        val sortByCore = config["sort_savefiles_enable"] == "true"
        // sort_savefiles_by_content_enable = "Sort into folders by Content Directory" (ROM's parent folder)
        val sortByContentDir = config["sort_savefiles_by_content_enable"] == "true"

        return RetroArchSaveConfig(
            savefileDirectory = savefileDirectory,
            savefilesInContentDir = savefilesInContentDir,
            sortByContentDirectory = sortByContentDir,
            sortByCore = sortByCore
        )
    }

    fun resolveSavePaths(
        packageName: String,
        systemName: String?,
        coreName: String?,
        contentDirectory: String? = null,
        basePathOverride: String? = null
    ): List<String> {
        val config = parse(packageName)
        val paths = mutableListOf<String>()

        if (basePathOverride == null && config?.savefilesInContentDir == true && contentDirectory != null) {
            paths.addAll(getBasePathAlternatives(contentDirectory))
            return paths
        }

        val baseDir = basePathOverride
            ?: config?.savefileDirectory
            ?: "/storage/emulated/0/RetroArch/saves"

        val baseDirs = getBasePathAlternatives(baseDir)

        if (config == null && coreName != null) {
            for (base in baseDirs) {
                val baseDir = File(base)
                val coreDir = File(base, coreName)

                if (coreDir.exists() && coreDir.isDirectory) {
                    paths.add(coreDir.absolutePath)
                    Log.d(TAG, "resolveSavePaths: config missing, found core folder $coreName")
                } else if (baseDir.exists() && hasCoreFolders(baseDir)) {
                    paths.add(coreDir.absolutePath)
                    Log.d(TAG, "resolveSavePaths: config missing, base has core folders, using $coreName")
                } else {
                    paths.add(base)
                    Log.d(TAG, "resolveSavePaths: config missing, no core folders found, using flat")
                }
            }
            return paths
        }

        val sortByContentDir = config?.sortByContentDirectory == true
        val sortByCore = config?.sortByCore == true

        for (base in baseDirs) {
            val path = buildString {
                append(base)
                if (sortByContentDir && systemName != null) {
                    append("/").append(systemName)
                }
                if (sortByCore && coreName != null) {
                    append("/").append(coreName)
                }
            }
            paths.add(path)
        }

        Log.d(TAG, "resolveSavePaths: ${paths.first()} (sortByContentDir=$sortByContentDir, sortByCore=$sortByCore)")
        return paths
    }

    private fun getBasePathAlternatives(path: String): List<String> {
        val alternatives = mutableListOf(path)
        when {
            path.contains("/storage/emulated/0/") -> {
                alternatives.add(path.replace("/storage/emulated/0/", "/Internal/"))
            }
            path.contains("/Internal/") -> {
                alternatives.add(path.replace("/Internal/", "/storage/emulated/0/"))
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

    companion object {
        private val KNOWN_CORE_NAMES = setOf(
            "mGBA", "Gambatte", "Snes9x", "bsnes", "Genesis Plus GX", "PPSSPP",
            "Mupen64Plus", "DeSmuME", "melonDS", "Beetle PSX", "PCSX ReARMed",
            "FinalBurn Neo", "MAME", "Stella", "FCEUmm", "Nestopia", "VBA-M",
            "Flycast", "Dolphin", "Citra", "PPSSPP", "Mednafen"
        )
    }

    fun parseStateConfig(packageName: String): RetroArchStateConfig? {
        val configFile = findConfigFile(packageName)
        if (configFile == null) {
            Log.d(TAG, "No retroarch.cfg found for $packageName")
            return null
        }

        return parseStateConfigFromFile(configFile)
    }

    private fun parseStateConfigFromFile(file: File): RetroArchStateConfig {
        val config = mutableMapOf<String, String>()

        try {
            file.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        val (key, value) = trimmed.split("=", limit = 2)
                        config[key.trim()] = value.trim().removeSurrounding("\"")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config file", e)
        }

        val savestateDirectory = config["savestate_directory"]?.takeIf {
            it != "default" && it.isNotBlank()
        }
        val savestatesInContentDir = config["savestates_in_content_dir"] == "true"
        val sortByCore = config["sort_savestates_enable"] == "true"
        val sortByContentDir = config["sort_savestates_by_content_enable"] == "true"

        return RetroArchStateConfig(
            savestateDirectory = savestateDirectory,
            savestatesInContentDir = savestatesInContentDir,
            sortByContentDirectory = sortByContentDir,
            sortByCore = sortByCore
        )
    }

    fun resolveStatePaths(
        packageName: String,
        coreName: String?,
        contentDirectory: String? = null,
        basePathOverride: String? = null
    ): List<String> {
        val config = parseStateConfig(packageName)
        val paths = mutableListOf<String>()

        if (basePathOverride == null && config?.savestatesInContentDir == true && contentDirectory != null) {
            paths.addAll(getBasePathAlternatives(contentDirectory))
            return paths
        }

        val baseDir = basePathOverride
            ?: config?.savestateDirectory
            ?: "/storage/emulated/0/RetroArch/states"

        val baseDirs = getBasePathAlternatives(baseDir)

        if (config == null && coreName != null) {
            for (base in baseDirs) {
                val statesDir = File(base)
                val coreDir = File(base, coreName)

                if (coreDir.exists() && coreDir.isDirectory) {
                    paths.add(coreDir.absolutePath)
                    Log.d(TAG, "resolveStatePaths: config missing, found core folder $coreName")
                } else if (statesDir.exists() && hasCoreFolders(statesDir)) {
                    paths.add(coreDir.absolutePath)
                    Log.d(TAG, "resolveStatePaths: config missing, base has core folders, using $coreName")
                } else {
                    paths.add(base)
                    Log.d(TAG, "resolveStatePaths: config missing, no core folders found, using flat")
                }
            }
            return paths
        }

        val sortByCore = config?.sortByCore == true

        for (base in baseDirs) {
            val path = buildString {
                append(base)
                if (sortByCore && coreName != null) {
                    append("/").append(coreName)
                }
            }
            paths.add(path)
        }

        Log.d(TAG, "resolveStatePaths: ${paths.first()} (sortByCore=$sortByCore)")
        return paths
    }
}
