package com.nendo.argosy.data.emulator

import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TitleIdDetector @Inject constructor(
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao
) {

    companion object {
        private const val TAG = "TitleIdDetector"
    }

    data class DetectedTitleId(
        val titleId: String,
        val modifiedAt: Long,
        val savePath: String
    )

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data object PermissionRequired : ValidationResult()
        data class SavePathNotFound(val checkedPaths: List<String>) : ValidationResult()
        data class AccessDenied(val path: String) : ValidationResult()
        data object NotFolderBased : ValidationResult()
        data object NoConfig : ValidationResult()
    }

    suspend fun validateSavePathAccess(emulatorId: String, emulatorPackage: String? = null): ValidationResult {
        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        if (config == null) {
            Logger.debug(TAG, "[SaveSync] VALIDATE | No config for emulator | emulator=$emulatorId")
            return ValidationResult.NoConfig
        }

        if (!config.usesFolderBasedSaves) {
            return ValidationResult.NotFolderBased
        }

        if (!hasFileAccessPermission()) {
            Logger.debug(TAG, "[SaveSync] VALIDATE | Permission not granted | emulator=$emulatorId")
            return ValidationResult.PermissionRequired
        }

        val resolvedPaths = resolvePathsWithUserOverride(emulatorId, config, emulatorPackage)

        for (path in resolvedPaths) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) continue

            val canRead = try {
                dir.listFiles() != null
            } catch (e: SecurityException) {
                Logger.debug(TAG, "[SaveSync] VALIDATE | SecurityException reading path | path=$path, error=${e.message}")
                false
            }

            if (canRead) {
                Logger.debug(TAG, "[SaveSync] VALIDATE | Path accessible | path=$path")
                return ValidationResult.Valid
            } else {
                Logger.debug(TAG, "[SaveSync] VALIDATE | Path exists but access denied (SELinux/OEM restriction?) | path=$path")
                return ValidationResult.AccessDenied(path)
            }
        }

        Logger.debug(TAG, "[SaveSync] VALIDATE | No save path found | emulator=$emulatorId, package=$emulatorPackage, paths=$resolvedPaths")
        return ValidationResult.SavePathNotFound(resolvedPaths)
    }

    private fun hasFileAccessPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    suspend fun detectRecentTitleId(
        emulatorId: String,
        platformSlug: String,
        sessionStartTime: Long,
        emulatorPackage: String? = null
    ): DetectedTitleId? {
        Logger.debug(TAG, "[SaveSync] DETECT | Starting title ID detection | emulator=$emulatorId, package=$emulatorPackage, platform=$platformSlug, sessionStart=$sessionStartTime")

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        if (config == null) {
            Logger.debug(TAG, "[SaveSync] DETECT | No config for emulator | emulator=$emulatorId")
            return null
        }
        if (!config.usesFolderBasedSaves) {
            Logger.debug(TAG, "[SaveSync] DETECT | Emulator uses single-file saves, skipping | emulator=$emulatorId")
            return null
        }

        val resolvedPaths = resolvePathsWithUserOverride(emulatorId, config, emulatorPackage, platformSlug)
        Logger.debug(TAG, "[SaveSync] DETECT | Scanning paths | paths=$resolvedPaths")
        for (basePath in resolvedPaths) {
            val detected = scanForRecentTitleId(basePath, platformSlug, sessionStartTime)
            if (detected != null) {
                val deltaMs = detected.modifiedAt - sessionStartTime
                Logger.debug(TAG, "[SaveSync] DETECT | Title ID detected | titleId=${detected.titleId}, path=${detected.savePath}, modTime=${detected.modifiedAt}, deltaSinceSession=${deltaMs}ms")
                return detected
            }
        }

        Logger.debug(TAG, "[SaveSync] DETECT | No recent title ID found | emulator=$emulatorId, platform=$platformSlug")
        return null
    }

    private suspend fun resolvePathsWithUserOverride(
        emulatorId: String,
        config: SavePathConfig,
        emulatorPackage: String?,
        platformSlug: String? = null
    ): List<String> {
        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        return if (userConfig?.isUserOverride == true) {
            val basePath = userConfig.savePathPattern
            val effectivePath = when (platformSlug) {
                "3ds" -> "$basePath/sdmc/Nintendo 3DS"
                else -> basePath
            }
            listOf(effectivePath)
        } else {
            SavePathRegistry.resolvePathWithPackage(config, emulatorPackage)
        }
    }

    private fun scanForRecentTitleId(
        basePath: String,
        platformSlug: String,
        sessionStartTime: Long
    ): DetectedTitleId? {
        val baseDir = File(basePath)
        if (!baseDir.exists()) return null

        return when (platformSlug) {
            "switch" -> scanSwitchSaves(baseDir, sessionStartTime)
            "vita", "psvita" -> scanVitaSaves(baseDir, sessionStartTime)
            "psp" -> scanPspSaves(baseDir, sessionStartTime)
            "3ds" -> scan3dsSaves(baseDir, sessionStartTime)
            "wiiu" -> scanWiiUSaves(baseDir, sessionStartTime)
            else -> null
        }
    }

    private fun scanSwitchSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null
        var scannedFolders = 0

        baseDir.listFiles()?.forEach { userFolder ->
            if (!userFolder.isDirectory) return@forEach

            userFolder.listFiles()?.forEach { profileFolder ->
                if (!profileFolder.isDirectory) return@forEach

                profileFolder.listFiles()?.forEach { titleFolder ->
                    if (!titleFolder.isDirectory) return@forEach
                    scannedFolders++
                    if (!isValidSwitchTitleId(titleFolder.name)) return@forEach

                    val modified = titleFolder.lastModified()
                    if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                        mostRecent = DetectedTitleId(
                            titleId = titleFolder.name.uppercase(),
                            modifiedAt = modified,
                            savePath = titleFolder.absolutePath
                        )
                    }
                }
            }
        }

        val deltaMs = mostRecent?.let { it.modifiedAt - sessionStartTime }
        Logger.debug(TAG, "[SaveSync] DETECT | Switch scan complete | basePath=${baseDir.absolutePath}, scannedFolders=$scannedFolders, selected=${mostRecent?.titleId}, deltaSinceSession=${deltaMs}ms")
        return mostRecent
    }

    private fun scanVitaSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        baseDir.listFiles()?.forEach { titleFolder ->
            if (!titleFolder.isDirectory) return@forEach
            if (!isValidVitaTitleId(titleFolder.name)) return@forEach

            val modified = titleFolder.lastModified()
            if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                mostRecent = DetectedTitleId(
                    titleId = titleFolder.name.uppercase(),
                    modifiedAt = modified,
                    savePath = titleFolder.absolutePath
                )
            }
        }

        return mostRecent
    }

    private fun scanPspSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        baseDir.listFiles()?.forEach { saveFolder ->
            if (!saveFolder.isDirectory) return@forEach

            val titleId = extractPspTitleIdFromFolder(saveFolder.name)
            if (titleId != null) {
                val modified = saveFolder.lastModified()
                if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                    mostRecent = DetectedTitleId(
                        titleId = titleId,
                        modifiedAt = modified,
                        savePath = saveFolder.absolutePath
                    )
                }
            }
        }

        return mostRecent
    }

    private fun scan3dsSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null
        var scannedDirs = 0
        var foundTitleIds = 0

        fun scanCategoryFolder(categoryDir: File) {
            val category = categoryDir.name.uppercase()
            categoryDir.listFiles()?.forEach { gameFolder ->
                if (!gameFolder.isDirectory) return@forEach
                if (!isValid3dsGameId(gameFolder.name)) return@forEach

                foundTitleIds++
                val fullTitleId = category + gameFolder.name.uppercase()
                val dataFolder = File(gameFolder, "data")
                val folderToCheck = if (dataFolder.exists() && dataFolder.isDirectory) dataFolder else gameFolder
                val modified = findNewestFileTime(folderToCheck)
                Logger.debug(TAG, "[SaveSync] DETECT | 3DS found titleId=$fullTitleId | modified=$modified, sessionStart=$sessionStartTime, isNewer=${modified > sessionStartTime}")
                if (modified > sessionStartTime && (mostRecent == null || modified > mostRecent!!.modifiedAt)) {
                    mostRecent = DetectedTitleId(
                        titleId = fullTitleId,
                        modifiedAt = modified,
                        savePath = if (dataFolder.exists()) dataFolder.absolutePath else gameFolder.absolutePath
                    )
                }
            }
        }

        fun scanForCategories(dir: File, depth: Int) {
            if (depth > 10) return
            val files = dir.listFiles() ?: return
            files.forEach { file ->
                if (!file.isDirectory) return@forEach
                scannedDirs++

                if (is3dsCategoryFolder(file.name)) {
                    scanCategoryFolder(file)
                } else {
                    scanForCategories(file, depth + 1)
                }
            }
        }

        scanForCategories(baseDir, 0)
        Logger.debug(TAG, "[SaveSync] DETECT | 3DS scan complete | scannedDirs=$scannedDirs, foundTitleIds=$foundTitleIds, selected=${mostRecent?.titleId}")
        return mostRecent
    }

    private fun is3dsCategoryFolder(name: String): Boolean {
        return name.length == 8 &&
            name.uppercase().startsWith("0004") &&
            name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun isValid3dsGameId(name: String): Boolean {
        return name.length == 8 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun findNewestFileTime(dir: File): Long {
        var newest = dir.lastModified()
        dir.listFiles()?.forEach { file ->
            val time = if (file.isDirectory) findNewestFileTime(file) else file.lastModified()
            if (time > newest) newest = time
        }
        return newest
    }

    private fun scanWiiUSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        // Wii U structure: 00050000/<titleId>/user/<userId>
        baseDir.listFiles()?.forEach { titleFolder ->
            if (!titleFolder.isDirectory) return@forEach
            if (!isValidWiiUTitleId(titleFolder.name)) return@forEach

            val userFolder = File(titleFolder, "user")
            val folderToCheck = if (userFolder.exists()) userFolder else titleFolder

            val modified = folderToCheck.lastModified()
            if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                mostRecent = DetectedTitleId(
                    titleId = titleFolder.name.uppercase(),
                    modifiedAt = modified,
                    savePath = titleFolder.absolutePath
                )
            }
        }

        return mostRecent
    }

    private fun isValidSwitchTitleId(name: String): Boolean {
        return name.length == 16 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun isValidVitaTitleId(name: String): Boolean {
        return name.length == 9 && name.matches(Regex("[A-Z]{4}\\d{5}"))
    }

    private fun isValidWiiUTitleId(name: String): Boolean {
        return name.length == 8 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun extractPspTitleIdFromFolder(folderName: String): String? {
        val pattern = Regex("^([A-Z]{4}\\d{5})")
        return pattern.find(folderName)?.groupValues?.get(1)
    }
}
