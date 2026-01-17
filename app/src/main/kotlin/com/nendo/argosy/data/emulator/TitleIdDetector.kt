package com.nendo.argosy.data.emulator

import android.os.Build
import android.os.Environment
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TitleIdDetector @Inject constructor() {

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

    fun validateSavePathAccess(emulatorId: String, emulatorPackage: String? = null): ValidationResult {
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

        val resolvedPaths = SavePathRegistry.resolvePathWithPackage(config, emulatorPackage)

        for (path in resolvedPaths) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) continue

            // Directory exists - verify we can actually read it (catches SELinux/OEM restrictions)
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

    fun detectRecentTitleId(
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

        val resolvedPaths = SavePathRegistry.resolvePathWithPackage(config, emulatorPackage)
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

        // 3DS structure: Nintendo 3DS/<id0>/<id1>/title/00040000/<titleId>/data
        baseDir.listFiles()?.forEach { id0Folder ->
            if (!id0Folder.isDirectory) return@forEach

            id0Folder.listFiles()?.forEach { id1Folder ->
                if (!id1Folder.isDirectory) return@forEach

                val titleBaseDir = File(id1Folder, "title/00040000")
                if (!titleBaseDir.exists()) return@forEach

                titleBaseDir.listFiles()?.forEach { titleFolder ->
                    if (!titleFolder.isDirectory) return@forEach
                    if (!isValid3dsTitleId(titleFolder.name)) return@forEach

                    val dataFolder = File(titleFolder, "data")
                    val folderToCheck = if (dataFolder.exists()) dataFolder else titleFolder

                    val modified = folderToCheck.lastModified()
                    if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                        mostRecent = DetectedTitleId(
                            titleId = titleFolder.name.uppercase(),
                            modifiedAt = modified,
                            savePath = folderToCheck.absolutePath
                        )
                    }
                }
            }
        }

        return mostRecent
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

    private fun isValid3dsTitleId(name: String): Boolean {
        return (name.length == 8 || name.length == 16) &&
            name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun isValidWiiUTitleId(name: String): Boolean {
        return name.length == 8 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun extractPspTitleIdFromFolder(folderName: String): String? {
        val pattern = Regex("^([A-Z]{4}\\d{5})")
        return pattern.find(folderName)?.groupValues?.get(1)
    }
}
