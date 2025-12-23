package com.nendo.argosy.data.emulator

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CoreVersionExtractor"

sealed class VersionValidationResult {
    data object Compatible : VersionValidationResult()
    data class Mismatch(val savedVersion: String, val currentVersion: String) : VersionValidationResult()
    data object Unknown : VersionValidationResult()
}

@Singleton
class CoreVersionExtractor @Inject constructor() {

    private val retroArchInfoPaths = listOf(
        "/storage/emulated/0/RetroArch/info",
        "/storage/emulated/0/Android/data/com.retroarch/files/info",
        "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/info"
    )

    fun getRetroArchCoreVersion(coreName: String, packageName: String? = null): String? {
        val infoFileName = "${coreName}_libretro_android.info"

        val searchPaths = if (packageName != null) {
            val packageSpecificPath = when (packageName) {
                "com.retroarch" -> "/storage/emulated/0/Android/data/com.retroarch/files/info"
                "com.retroarch.aarch64" -> "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/info"
                else -> null
            }
            if (packageSpecificPath != null) {
                listOf(packageSpecificPath) + retroArchInfoPaths
            } else {
                retroArchInfoPaths
            }
        } else {
            retroArchInfoPaths
        }

        for (basePath in searchPaths) {
            val infoFile = File(basePath, infoFileName)
            if (infoFile.exists()) {
                val version = parseInfoFile(infoFile)
                if (version != null) {
                    Log.d(TAG, "Found core version for $coreName: $version")
                    return version
                }
            }

            val altInfoFileName = "${coreName}_libretro.info"
            val altInfoFile = File(basePath, altInfoFileName)
            if (altInfoFile.exists()) {
                val version = parseInfoFile(altInfoFile)
                if (version != null) {
                    Log.d(TAG, "Found core version for $coreName: $version (alt)")
                    return version
                }
            }
        }

        Log.d(TAG, "Core info file not found for $coreName")
        return null
    }

    private fun parseInfoFile(file: File): String? {
        return try {
            file.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("display_version")) {
                        val value = trimmed.substringAfter("=").trim()
                            .removeSurrounding("\"")
                            .removeSurrounding("'")
                        if (value.isNotBlank()) {
                            return@useLines value
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse info file: ${file.absolutePath}", e)
            null
        }
    }

    fun getCoreIdForEmulator(emulatorId: String, platformId: String): String? {
        return when {
            emulatorId.startsWith("retroarch") -> {
                EmulatorRegistry.getRetroArchCorePatterns()[platformId]?.firstOrNull()
            }
            else -> emulatorId
        }
    }

    fun getEmulatorVersion(emulatorId: String, packageName: String?): String? {
        return when {
            emulatorId.startsWith("retroarch") && packageName != null -> {
                null
            }
            else -> null
        }
    }

    fun validateVersion(
        savedVersion: String?,
        currentVersion: String?
    ): VersionValidationResult {
        if (savedVersion == null || currentVersion == null) {
            return VersionValidationResult.Unknown
        }
        return if (savedVersion == currentVersion) {
            VersionValidationResult.Compatible
        } else {
            VersionValidationResult.Mismatch(savedVersion, currentVersion)
        }
    }

    fun buildCoreIdentifier(emulatorId: String, coreId: String?, version: String?): String {
        val parts = mutableListOf(emulatorId)
        if (coreId != null) parts.add(coreId)
        if (version != null) parts.add(version)
        return parts.joinToString(":")
    }

    fun parseCoreIdentifier(identifier: String): Triple<String, String?, String?> {
        val parts = identifier.split(":")
        return Triple(
            parts.getOrNull(0) ?: identifier,
            parts.getOrNull(1),
            parts.getOrNull(2)
        )
    }
}
