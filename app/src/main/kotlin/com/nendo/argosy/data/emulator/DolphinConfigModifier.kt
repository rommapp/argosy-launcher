package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import java.io.File

object DolphinConfigModifier {
    private const val TAG = "DolphinConfigModifier"

    fun findConfigDir(targetTextureDir: File): File? {
        var curr: File? = targetTextureDir
        while (curr != null) {
            val configDirect = File(curr, "Config")
            if (configDirect.isDirectory) return configDirect

            val name = curr.name
            if (name == "files" || name.contains("dolphin", ignoreCase = true) || name.contains("mmjr", ignoreCase = true)) {
                return File(curr, "Config")
            }
            curr = curr.parentFile
        }
        return null
    }

    fun enableCustomTextures(
        targetTextureDir: File,
        fileAccessLayer: FileAccessLayer? = null
    ): Boolean {
        val configDir = findConfigDir(targetTextureDir) ?: return false
        val gfxFile = File(configDir, "GFX.ini")
        val gfxPath = gfxFile.absolutePath
        val isRestricted = fileAccessLayer?.isRestrictedPath(gfxPath) == true

        val fileExists = if (fileAccessLayer != null && isRestricted) {
            fileAccessLayer.exists(gfxPath)
        } else {
            gfxFile.isFile
        }

        val existingBytes: ByteArray? = if (fileExists) {
            try {
                val bytes = if (fileAccessLayer != null && isRestricted) {
                    fileAccessLayer.readBytes(gfxPath)
                } else {
                    gfxFile.readBytes()
                }
                if (bytes == null) {
                    Logger.error(TAG, "GFX.ini exists at $gfxPath but readBytes returned null; aborting to prevent wiping user config")
                    return false
                }
                bytes
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to read existing Dolphin GFX.ini at $gfxPath: ${e.message}; aborting to prevent wiping user config")
                return false
            }
        } else {
            null
        }

        // Create backup of existing config before modifying
        if (existingBytes != null) {
            val bakPath = "$gfxPath.argosy_bak"
            try {
                if (fileAccessLayer != null && isRestricted) {
                    if (!fileAccessLayer.exists(bakPath)) {
                        fileAccessLayer.writeBytes(bakPath, existingBytes)
                    }
                } else {
                    val bakFile = File(bakPath)
                    if (!bakFile.exists()) {
                        bakFile.writeBytes(existingBytes)
                    }
                }
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to create Dolphin GFX.ini backup at $bakPath: ${e.message}")
            }
        }

        val existingText = existingBytes?.let { String(it, Charsets.UTF_8) }
        val updatedText = injectHiresTexturesSetting(existingText)

        if (existingText != null && updatedText == existingText) {
            Logger.debug(TAG, "Dolphin GFX.ini already has HiresTextures enabled")
            return true
        }

        return try {
            if (fileAccessLayer != null && isRestricted) {
                fileAccessLayer.mkdirs(configDir.absolutePath)
                val success = fileAccessLayer.writeBytes(gfxPath, updatedText.toByteArray(Charsets.UTF_8))
                if (!success) {
                    Logger.error(TAG, "FileAccessLayer failed to write GFX.ini at $gfxPath")
                    return false
                }
            } else {
                configDir.mkdirs()
                gfxFile.writeText(updatedText, Charsets.UTF_8)
            }
            Logger.info(TAG, "Enabled custom textures in Dolphin GFX.ini at $gfxPath")
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to write Dolphin GFX.ini: ${e.message}")
            false
        }
    }

    internal fun injectHiresTexturesSetting(rawText: String?): String {
        if (rawText.isNullOrBlank()) {
            return "[Settings]\nHiresTextures = True\nCacheHiresTextures = True\n"
        }

        val lines = rawText.lines().toMutableList()
        var settingsIndex = -1
        var nextSectionIndex = -1

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.equals("[Settings]", ignoreCase = true)) {
                settingsIndex = i
            } else if (settingsIndex != -1 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                nextSectionIndex = i
                break
            }
        }

        if (settingsIndex == -1) {
            val builder = StringBuilder(rawText.trimEnd())
            builder.append("\n\n[Settings]\nHiresTextures = True\nCacheHiresTextures = True\n")
            return builder.toString()
        }

        val endOfSection = if (nextSectionIndex != -1) nextSectionIndex else lines.size
        var hiresIndex = -1
        var cacheIndex = -1
        for (i in (settingsIndex + 1) until endOfSection) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("HiresTextures", ignoreCase = true) && trimmed.contains("=")) {
                hiresIndex = i
            } else if (trimmed.startsWith("CacheHiresTextures", ignoreCase = true) && trimmed.contains("=")) {
                cacheIndex = i
            }
        }

        if (hiresIndex != -1) {
            lines[hiresIndex] = "HiresTextures = True"
        }
        if (cacheIndex != -1) {
            lines[cacheIndex] = "CacheHiresTextures = True"
        }

        if (hiresIndex == -1 && cacheIndex == -1) {
            lines.add(settingsIndex + 1, "HiresTextures = True")
            lines.add(settingsIndex + 2, "CacheHiresTextures = True")
        } else if (hiresIndex == -1) {
            lines.add(settingsIndex + 1, "HiresTextures = True")
        } else if (cacheIndex == -1) {
            lines.add(hiresIndex + 1, "CacheHiresTextures = True")
        }

        return lines.joinToString("\n")
    }
}
