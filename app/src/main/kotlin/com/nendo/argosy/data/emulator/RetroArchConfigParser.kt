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
            paths.add(contentDirectory)
            return paths
        }

        val baseDir = basePathOverride
            ?: config?.savefileDirectory
            ?: "/storage/emulated/0/RetroArch/saves"

        val sortByContentDir = config?.sortByContentDirectory == true
        val sortByCore = config?.sortByCore == true

        val path = buildString {
            append(baseDir)
            if (sortByContentDir && systemName != null) {
                append("/").append(systemName)
            }
            if (sortByCore && coreName != null) {
                append("/").append(coreName)
            }
        }

        paths.add(path)

        Log.d(TAG, "resolveSavePaths: $path (sortByContentDir=$sortByContentDir, sortByCore=$sortByCore)")
        return paths
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
            paths.add(contentDirectory)
            return paths
        }

        val baseDir = basePathOverride
            ?: config?.savestateDirectory
            ?: "/storage/emulated/0/RetroArch/states"

        val sortByCore = config?.sortByCore == true

        val path = buildString {
            append(baseDir)
            if (sortByCore && coreName != null) {
                append("/").append(coreName)
            }
        }

        paths.add(path)

        Log.d(TAG, "resolveStatePaths: $path (sortByCore=$sortByCore)")
        return paths
    }
}
