package com.nendo.argosy.data.emulator

import android.os.Environment
import android.util.Log
import com.nendo.argosy.data.storage.FileAccessLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EdenContentManager"
private const val EDEN_PACKAGE = "dev.eden.eden_emulator"
private const val CONFIG_PATH = "files/config/config.ini"
private const val KEY_PREFIX = "Paths\\external_content_dirs"
private const val SIZE_KEY = "$KEY_PREFIX\\size"

/**
 * Reads and writes Eden's external_content_dirs config to register game
 * directories for automatic update/DLC loading. Eden uses a Qt INI format
 * with 1-indexed array entries.
 */
@Singleton
class EdenContentManager @Inject constructor(
    private val fileAccessLayer: FileAccessLayer,
    private val emulatorDetector: EmulatorDetector
) {

    private val configAbsolutePath: String
        get() {
            val extStorage = Environment.getExternalStorageDirectory().absolutePath
            return "$extStorage/Android/data/$EDEN_PACKAGE/$CONFIG_PATH"
        }

    fun isEdenInstalled(): Boolean = emulatorDetector.isInstalled(EDEN_PACKAGE)

    suspend fun readRegisteredDirs(): List<String> = withContext(Dispatchers.IO) {
        val config = readConfigFile() ?: return@withContext emptyList()
        parseExternalContentDirs(config)
    }

    suspend fun isDirectoryRegistered(path: String): Boolean {
        return readRegisteredDirs().any { it == path }
    }

    suspend fun registerDirectory(path: String): Boolean {
        return registerDirectories(listOf(path))
    }

    suspend fun registerDirectories(paths: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext true

        val config = readConfigFile()
        if (config == null) {
            Log.w(TAG, "Cannot read Eden config; is Eden installed and has been run at least once?")
            return@withContext false
        }

        val existing = parseExternalContentDirs(config)
        val newPaths = paths.filter { it !in existing }
        if (newPaths.isEmpty()) return@withContext true

        val allDirs = existing + newPaths
        val updated = updateExternalContentDirs(config, allDirs)
        writeConfigFile(updated)
    }

    private fun readConfigFile(): String? {
        val path = configAbsolutePath
        if (!fileAccessLayer.exists(path)) {
            Log.w(TAG, "Eden config not found at $path")
            return null
        }
        val stream = fileAccessLayer.getInputStream(path) ?: return null
        return try {
            stream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Eden config: ${e.message}")
            null
        } finally {
            stream.close()
        }
    }

    private fun writeConfigFile(content: String): Boolean {
        val path = configAbsolutePath
        val data = content.toByteArray()
        val writeResult = fileAccessLayer.writeBytes(path, data)
        Log.d(TAG, "writeBytes returned $writeResult for $path")
        val readBack = fileAccessLayer.readBytes(path)
        Log.d(TAG, "readBack: ${readBack?.size} bytes, expected ${data.size} bytes, match=${readBack != null && readBack.contentEquals(data)}")
        val success = readBack != null && readBack.contentEquals(data)
        if (!success) {
            Log.e(TAG, "Failed to write Eden config at $path")
        }
        return success
    }

    internal fun parseExternalContentDirs(configText: String): List<String> {
        val lines = configText.lines()
        val size = lines.firstOrNull { it.startsWith(SIZE_KEY) }
            ?.substringAfter('=')?.trim()?.toIntOrNull() ?: 0
        if (size == 0) return emptyList()

        val dirs = mutableListOf<String>()
        for (i in 1..size) {
            val pathKey = "$KEY_PREFIX\\$i\\path="
            val path = lines.firstOrNull { it.startsWith(pathKey) }
                ?.substringAfter('=')?.trim()
            if (!path.isNullOrBlank()) {
                dirs.add(path)
            }
        }
        return dirs
    }

    internal fun updateExternalContentDirs(configText: String, newDirs: List<String>): String {
        val lines = configText.lines().toMutableList()

        // Remove all existing external_content_dirs entries
        lines.removeAll { it.startsWith(KEY_PREFIX) }

        // Find the [Paths] section to insert after, or append at end
        val insertIndex = findPathsSectionInsertIndex(lines)

        val newEntries = mutableListOf<String>()
        newEntries.add("$SIZE_KEY=${newDirs.size}")
        newDirs.forEachIndexed { index, path ->
            val i = index + 1
            newEntries.add("$KEY_PREFIX\\$i\\path=$path")
            newEntries.add("$KEY_PREFIX\\$i\\deep_scan=true")
        }

        lines.addAll(insertIndex, newEntries)
        return lines.joinToString("\n")
    }

    private fun findPathsSectionInsertIndex(lines: List<String>): Int {
        // Look for existing Paths\ keys to insert near them
        val lastPathsIndex = lines.indexOfLast {
            it.startsWith("Paths\\") && !it.startsWith(KEY_PREFIX)
        }
        if (lastPathsIndex >= 0) return lastPathsIndex + 1

        // Look for [Paths] section header
        val sectionIndex = lines.indexOfFirst {
            it.trim().equals("[Paths]", ignoreCase = true)
        }
        if (sectionIndex >= 0) return sectionIndex + 1

        return lines.size
    }
}
