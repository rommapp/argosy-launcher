package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import java.io.File

/**
 * Edits one setting in an emulator's own INI, which usually sits under Android/data where direct
 * file access is refused. A file that exists but cannot be read is left alone rather than rewritten
 * from nothing, since that would wipe the player's emulator settings.
 */
object EmulatorIniFile {
    private const val TAG = "EmulatorIniFile"

    /**
     * @param transform receives the current text, or null when the file does not exist, and
     * returns the text to write, or null to leave the disk untouched.
     */
    fun update(
        configFile: File,
        fileAccessLayer: FileAccessLayer?,
        transform: (String?) -> String?
    ): Boolean {
        val configPath = configFile.absolutePath
        val isRestricted = fileAccessLayer?.isRestrictedPath(configPath) == true

        val fileExists = if (fileAccessLayer != null && isRestricted) {
            fileAccessLayer.exists(configPath)
        } else {
            configFile.isFile
        }

        val existingBytes: ByteArray? = if (fileExists) {
            try {
                val bytes = if (fileAccessLayer != null && isRestricted) {
                    fileAccessLayer.readBytes(configPath)
                } else {
                    configFile.readBytes()
                }
                if (bytes == null) {
                    Logger.error(TAG, "$configPath exists but could not be read; leaving it untouched")
                    return false
                }
                bytes
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to read $configPath: ${e.message}; leaving it untouched")
                return false
            }
        } else {
            null
        }

        val existingText = existingBytes?.let { String(it, Charsets.UTF_8) }
        val updatedText = transform(existingText) ?: return false

        if (updatedText == existingText) {
            Logger.debug(TAG, "$configPath already holds the wanted setting")
            return true
        }

        if (existingBytes != null) {
            backUpOnce(configPath, existingBytes, fileAccessLayer, isRestricted)
        }

        return try {
            if (fileAccessLayer != null && isRestricted) {
                configFile.parentFile?.let { fileAccessLayer.mkdirs(it.absolutePath) }
                val success = fileAccessLayer.writeBytes(configPath, updatedText.toByteArray(Charsets.UTF_8))
                if (!success) {
                    Logger.error(TAG, "FileAccessLayer failed to write $configPath")
                    return false
                }
            } else {
                configFile.parentFile?.mkdirs()
                configFile.writeText(updatedText, Charsets.UTF_8)
            }
            Logger.info(TAG, "Updated $configPath")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to write $configPath: ${e.message}")
            false
        }
    }

    fun setValue(rawText: String?, section: String, key: String, value: String): String {
        val assignment = "$key = $value"
        if (rawText.isNullOrBlank()) {
            return "[$section]\n$assignment\n"
        }

        val lines = rawText.lines().toMutableList()
        val sectionIndex = lines.indexOfFirst { it.trim().equals("[$section]", ignoreCase = true) }
        if (sectionIndex == -1) {
            return "${rawText.trimEnd()}\n\n[$section]\n$assignment\n"
        }

        val nextSectionOffset = lines.subList(sectionIndex + 1, lines.size)
            .indexOfFirst { it.trim().startsWith("[") }
        val sectionEnd = if (nextSectionOffset == -1) lines.size else sectionIndex + 1 + nextSectionOffset

        val keyIndex = (sectionIndex + 1 until sectionEnd).firstOrNull { index ->
            val line = lines[index]
            line.contains('=') && line.substringBefore('=').trim().equals(key, ignoreCase = true)
        }
        if (keyIndex != null) {
            lines[keyIndex] = assignment
        } else {
            lines.add(sectionIndex + 1, assignment)
        }
        return lines.joinToString("\n")
    }

    private fun backUpOnce(
        configPath: String,
        bytes: ByteArray,
        fileAccessLayer: FileAccessLayer?,
        isRestricted: Boolean
    ) {
        val bakPath = "$configPath.argosy_bak"
        try {
            if (fileAccessLayer != null && isRestricted) {
                if (!fileAccessLayer.exists(bakPath)) {
                    fileAccessLayer.writeBytes(bakPath, bytes)
                }
            } else {
                val bakFile = File(bakPath)
                if (!bakFile.exists()) {
                    bakFile.writeBytes(bytes)
                }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to back up config at $bakPath: ${e.message}")
        }
    }
}
