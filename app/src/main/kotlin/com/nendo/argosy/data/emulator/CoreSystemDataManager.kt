package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import com.nendo.argosy.util.FileNames
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreSystemDataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: SystemDataFetcher
) {
    @Volatile
    private var armsx2FetchFailedThisSession = false

    suspend fun ensureCoreSystemData(coreId: String, systemDir: File): Boolean {
        return when (coreId) {
            "dolphin" -> ensureDolphinSysData(systemDir)
            "ppsspp" -> ensurePpssppSysData(systemDir)
            "armsx2" -> ensureArmsx2Resources(systemDir)
            "scummvm" -> ensureScummvmSysData(systemDir)
            else -> true
        }
    }

    fun needsSystemData(coreId: String, systemDir: File): Boolean {
        return when (coreId) {
            "dolphin" -> isVersionStale(
                File(systemDir, "$DOLPHIN_SYS_PATH/$VERSION_MARKER"),
                BuildConfig.DOLPHIN_SYS_VERSION
            )
            "ppsspp" -> isVersionStale(
                File(systemDir, "$PPSSPP_SYS_PATH/$VERSION_MARKER"),
                BuildConfig.PPSSPP_SYS_VERSION
            )
            "armsx2" -> isVersionStale(
                File(systemDir, "$ARMSX2_RESOURCES_PATH/$VERSION_MARKER"),
                BuildConfig.ARMSX2_SYS_VERSION
            )
            "scummvm" -> isVersionStale(
                File(systemDir, "$SCUMMVM_SYS_PATH/$VERSION_MARKER"),
                BuildConfig.SCUMMVM_SYS_VERSION
            )
            else -> false
        }
    }

    private fun isVersionStale(versionFile: File, expected: Int): Boolean {
        if (!versionFile.exists()) return true
        val installed = versionFile.readText().trim().toIntOrNull()
        return installed != expected
    }

    private suspend fun ensureDolphinSysData(systemDir: File): Boolean = withContext(Dispatchers.IO) {
        val targetDir = File(systemDir, DOLPHIN_SYS_PATH)
        val versionFile = File(targetDir, VERSION_MARKER)
        val currentVersion = BuildConfig.DOLPHIN_SYS_VERSION

        if (versionFile.exists()) {
            val installed = versionFile.readText().trim().toIntOrNull()
            if (installed == currentVersion) return@withContext true
        }

        Logger.info(TAG, "Downloading Dolphin Sys data (version $currentVersion)")

        try {
            downloadAndExtract(DOLPHIN_SYS_URL, systemDir)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to download Dolphin Sys data: ${e.message}")
            return@withContext false
        }

        versionFile.parentFile?.mkdirs()
        versionFile.writeText(currentVersion.toString())
        Logger.info(TAG, "Dolphin Sys data installed successfully")
        true
    }

    private suspend fun ensurePpssppSysData(systemDir: File): Boolean = withContext(Dispatchers.IO) {
        val targetDir = File(systemDir, PPSSPP_SYS_PATH)
        val versionFile = File(targetDir, VERSION_MARKER)
        val currentVersion = BuildConfig.PPSSPP_SYS_VERSION

        if (versionFile.exists()) {
            val installed = versionFile.readText().trim().toIntOrNull()
            if (installed == currentVersion) return@withContext true
        }

        Logger.info(TAG, "Downloading PPSSPP assets (version $currentVersion)")

        try {
            downloadAndExtract(PPSSPP_SYS_URL, systemDir)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to download PPSSPP assets: ${e.message}")
            return@withContext false
        }

        versionFile.parentFile?.mkdirs()
        versionFile.writeText(currentVersion.toString())
        Logger.info(TAG, "PPSSPP assets installed successfully")
        true
    }

    private suspend fun ensureScummvmSysData(systemDir: File): Boolean = withContext(Dispatchers.IO) {
        val versionFile = File(systemDir, "$SCUMMVM_SYS_PATH/$VERSION_MARKER")
        val currentVersion = BuildConfig.SCUMMVM_SYS_VERSION
        if (!isVersionStale(versionFile, currentVersion)) return@withContext true

        Logger.info(TAG, "Downloading ScummVM data files (version $currentVersion)")
        try {
            downloadAndExtract(SCUMMVM_SYS_URL, systemDir)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to download ScummVM data files: ${e.message}")
            return@withContext false
        }

        versionFile.parentFile?.mkdirs()
        versionFile.writeText(currentVersion.toString())
        Logger.info(TAG, "ScummVM data files installed successfully")
        true
    }

    private suspend fun ensureArmsx2Resources(systemDir: File): Boolean = withContext(Dispatchers.IO) {
        File(systemDir, ARMSX2_TEXTURES_PATH).mkdirs()
        configureArmsx2Ini(systemDir)

        val targetDir = File(systemDir, ARMSX2_RESOURCES_PATH)
        val versionFile = File(targetDir, VERSION_MARKER)
        val currentVersion = BuildConfig.ARMSX2_SYS_VERSION
        if (!isVersionStale(versionFile, currentVersion)) return@withContext true
        if (armsx2FetchFailedThisSession) {
            Logger.debug(TAG, "ARMSX2 resources failed to download earlier this session; launching without them")
            return@withContext true
        }

        Logger.info(TAG, "Downloading ARMSX2 resources (version $currentVersion)")
        val complete = ARMSX2_RESOURCE_FILES.all { (source, target) ->
            try {
                downloadFile(source, File(targetDir, target))
                true
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to download ARMSX2 resource $source: ${e.message}")
                false
            }
        }
        if (complete) {
            versionFile.writeText(currentVersion.toString())
            Logger.info(TAG, "ARMSX2 resources installed successfully")
        } else {
            armsx2FetchFailedThisSession = true
        }
        true
    }

    internal fun configureArmsx2Ini(systemDir: File) {
        val iniFile = File(systemDir, ARMSX2_INI_PATH)
        iniFile.parentFile?.mkdirs()
        if (!iniFile.exists()) {
            iniFile.writeText("[$ARMSX2_GS_SECTION]\n$ARMSX2_LOAD_TEXTURES_KEY = true\n")
            return
        }
        val text = iniFile.readText()
        val updated = updateIniSetting(
            content = text,
            section = ARMSX2_GS_SECTION,
            key = ARMSX2_LOAD_TEXTURES_KEY,
            value = "true"
        )
        if (updated != text) {
            iniFile.writeText(updated)
        }
    }

    internal fun updateIniSetting(
        content: String,
        section: String,
        key: String,
        value: String
    ): String {
        val lines = content.lines().toMutableList()
        val lineSeparator = if (content.contains("\r\n")) "\r\n" else "\n"
        var targetSectionStartIndex = -1
        val matchingKeyIndices = mutableListOf<Int>()
        var inTargetSection = false

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val currentSection = trimmed.substring(1, trimmed.length - 1).trim()
                if (inTargetSection) {
                    inTargetSection = false
                }
                if (currentSection.equals(section, ignoreCase = true)) {
                    inTargetSection = true
                    targetSectionStartIndex = i
                }
                continue
            }
            if (inTargetSection) {
                if (trimmed.startsWith(";") || trimmed.startsWith("#")) {
                    continue
                }
                val eqIndex = trimmed.indexOf('=')
                if (eqIndex != -1) {
                    val currentKey = trimmed.substring(0, eqIndex).trim()
                    if (currentKey.equals(key, ignoreCase = true)) {
                        matchingKeyIndices.add(i)
                    }
                }
            }
        }

        when {
            matchingKeyIndices.isNotEmpty() -> {
                lines[matchingKeyIndices.first()] = "$key = $value"
                for (idx in matchingKeyIndices.drop(1).reversed()) {
                    lines.removeAt(idx)
                }
            }
            targetSectionStartIndex != -1 -> {
                lines.add(targetSectionStartIndex + 1, "$key = $value")
            }
            else -> {
                while (lines.isNotEmpty() && lines.last().isBlank()) {
                    lines.removeAt(lines.lastIndex)
                }
                if (lines.isNotEmpty()) {
                    lines.add("")
                }
                lines.add("[$section]")
                lines.add("$key = $value")
                lines.add("")
            }
        }

        val result = lines.joinToString(lineSeparator)
        return if (result.endsWith("\n") || result.isEmpty()) result else result + lineSeparator
    }

    private fun downloadFile(url: String, target: File) {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            fetcher.fetch(url) { input -> partial.outputStream().use { out -> input.copyTo(out) } }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
            }
        } finally {
            partial.delete()
        }
    }

    private fun downloadAndExtract(url: String, destDir: File) {
        fetcher.fetch(url) { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    if (!FileNames.isWithin(outFile, destDir)) {
                        throw SecurityException("Zip entry outside target: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    companion object {
        private const val TAG = "CoreSystemData"
        private const val VERSION_MARKER = ".argosy_version"
        private const val DOLPHIN_SYS_URL =
            "https://buildbot.libretro.com/assets/system/Dolphin.zip"
        private const val DOLPHIN_SYS_PATH = "dolphin-emu/Sys"
        private const val PPSSPP_SYS_URL =
            "https://buildbot.libretro.com/assets/system/PPSSPP.zip"
        private const val PPSSPP_SYS_PATH = "PPSSPP"
        private const val SCUMMVM_SYS_URL =
            "https://buildbot.libretro.com/assets/system/ScummVM.zip"
        private const val SCUMMVM_SYS_PATH = "scummvm"

        const val ARMSX2_UPSTREAM_COMMIT = "0076b5c389ea7296331ad2d96193c6e8d5d314fa"
        const val ARMSX2_RAW_BASE = "https://raw.githubusercontent.com/ARMSX2/ARMSX2/$ARMSX2_UPSTREAM_COMMIT"
        const val ARMSX2_RESOURCES_PATH = "pcsx2/resources"
        const val ARMSX2_TEXTURES_PATH = "pcsx2/textures"
        const val ARMSX2_INI_PATH = "pcsx2/inis/armsx2-libretro.ini"
        private const val ARMSX2_GS_SECTION = "EmuCore/GS"
        private const val ARMSX2_LOAD_TEXTURES_KEY = "LoadTextureReplacements"

        const val PCSX2_PATCHES_URL = "https://github.com/PCSX2/pcsx2_patches/releases/download/latest/patches.zip"

        val ARMSX2_RESOURCE_FILES: List<Pair<String, String>> = listOf(
            "$ARMSX2_RAW_BASE/bin/resources-overlay/armsx2_overrides.yaml" to "armsx2_overrides.yaml",
            "$ARMSX2_RAW_BASE/bin/resources/fonts/Roboto-Regular.ttf" to "fonts/Roboto-Regular.ttf",
            PCSX2_PATCHES_URL to "patches.zip"
        )
    }
}
