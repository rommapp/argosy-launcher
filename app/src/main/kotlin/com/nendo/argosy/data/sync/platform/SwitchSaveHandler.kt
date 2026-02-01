package com.nendo.argosy.data.sync.platform

import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SwitchProfileParser
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwitchSaveHandler @Inject constructor(
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver,
    private val switchProfileParser: SwitchProfileParser
) {
    companion object {
        private const val TAG = "SwitchSaveHandler"

        val DEVICE_SAVE_TITLE_IDS = setOf(
            "01006F8002326000", // Animal Crossing: New Horizons
            "0100D2F00D5C0000", // Nintendo Switch Sports
            "01000320000CC000", // 1-2-Switch
            "01002FF008C24000", // Ring Fit Adventure
            "0100C4B0034B2000", // Nintendo Labo Toy-Con 01: Variety Kit
            "01009AB0034E0000", // Nintendo Labo Toy-Con 02: Robot Kit
            "01001E9003502000", // Nintendo Labo Toy-Con 03: Vehicle Kit
            "0100165003504000", // Nintendo Labo Toy-Con 04: VR Kit
            "0100C1800A9B6000"  // Go Vacation
        )

        val JKSV_EXCLUDE_FILES = setOf(".nx_save_meta.bin")
    }

    fun isValidHexId(name: String): Boolean {
        return name.length == 16 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    fun isValidTitleId(titleId: String): Boolean {
        return isValidHexId(titleId) && titleId.uppercase().startsWith("01")
    }

    fun isValidUserFolderId(name: String): Boolean {
        return name.length == 16 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    fun isValidProfileFolderId(name: String): Boolean {
        return (name.length == 16 || name.length == 32) &&
            name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    fun isDeviceSave(titleId: String): Boolean {
        return titleId.uppercase() in DEVICE_SAVE_TITLE_IDS
    }

    fun resolveSaveTargetPath(
        zipFile: File,
        config: SavePathConfig,
        emulatorPackage: String?
    ): String? {
        val titleId = saveArchiver.peekRootFolderName(zipFile)
            ?.takeIf { isValidHexId(it) }
            ?: saveArchiver.parseTitleIdFromJksvMeta(zipFile)

        if (titleId == null) {
            Logger.debug(TAG, "resolveSaveTargetPath: no valid titleId from ZIP (tried Argosy and JKSV formats)")
            return null
        }

        val resolvedPaths = SavePathRegistry.resolvePathWithPackage(config, emulatorPackage)
        val basePath = resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
            ?: return null

        val normalizedTitleId = titleId.uppercase()
        val isDeviceSave = normalizedTitleId in DEVICE_SAVE_TITLE_IDS

        val profileFolder = if (isDeviceSave) {
            findOrCreateZeroProfileFolder(basePath)
        } else {
            findActiveProfileFolder(basePath, emulatorPackage)
        }
        val targetPath = "$profileFolder/$normalizedTitleId"

        Logger.debug(TAG, "resolveSaveTargetPath: resolved to $targetPath (titleId=$normalizedTitleId, isDeviceSave=$isDeviceSave)")
        return targetPath
    }

    fun findActiveProfileFolder(basePath: String, emulatorPackage: String? = null): String {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "findActiveProfileFolder: baseDir doesn't exist, cannot determine profile")
            return basePath
        }

        if (emulatorPackage != null) {
            val dataPath = basePath.substringBefore("/nand/user/save")
            switchProfileParser.parseActiveProfile(emulatorPackage, dataPath)?.let { profileUuid ->
                val profilePath = "$basePath/0000000000000000/$profileUuid"
                if (fal.exists(profilePath) && fal.isDirectory(profilePath)) {
                    Logger.debug(TAG, "findActiveProfileFolder: using profile from emulator config | pkg=$emulatorPackage, uuid=$profileUuid")
                    return profilePath
                } else {
                    Logger.debug(TAG, "findActiveProfileFolder: parsed profile path doesn't exist | path=$profilePath")
                }
            }
        }

        var mostRecentPath: String? = null
        var mostRecentTime = 0L
        var firstNonZeroProfile: String? = null

        fal.listFiles(basePath)?.forEach { userFolder ->
            if (!userFolder.isDirectory) return@forEach
            if (!isValidUserFolderId(userFolder.name)) return@forEach

            fal.listFiles(userFolder.path)?.forEach { profileFolder ->
                if (!profileFolder.isDirectory) return@forEach
                if (!isValidProfileFolderId(profileFolder.name)) return@forEach

                val isZeroProfile = profileFolder.name.all { it == '0' }
                if (!isZeroProfile && firstNonZeroProfile == null) {
                    firstNonZeroProfile = profileFolder.path
                }

                val newestFileTime = findNewestFileTime(profileFolder.path)
                if (!isZeroProfile && newestFileTime > mostRecentTime) {
                    mostRecentTime = newestFileTime
                    mostRecentPath = profileFolder.path
                }
            }
        }

        val result = mostRecentPath ?: firstNonZeroProfile ?: basePath
        Logger.debug(TAG, "findActiveProfileFolder: selected=$result (byFileTime=${mostRecentPath != null}, firstNonZero=$firstNonZeroProfile)")

        return result
    }

    fun findOrCreateZeroProfileFolder(basePath: String): String {
        val zeroUserPath = "$basePath/0000000000000000"
        val zeroProfilePath = "$zeroUserPath/00000000000000000000000000000000"

        if (!fal.exists(zeroProfilePath)) {
            fal.mkdirs(zeroProfilePath)
            Logger.debug(TAG, "findOrCreateZeroProfileFolder: created $zeroProfilePath")
        }

        return zeroProfilePath
    }

    fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "findSaveFolderByTitleId: base path does not exist | path=$basePath")
            return null
        }

        val normalizedTitleId = titleId.uppercase()
        Logger.debug(TAG, "findSaveFolderByTitleId: scanning | path=$basePath, titleId=$normalizedTitleId")

        var bestMatchPath: String? = null
        var bestModTime = 0L

        fal.listFiles(basePath)?.forEach { userFolder ->
            if (!userFolder.isDirectory) return@forEach

            val saveFolder = fal.listFiles(userFolder.path)?.firstOrNull {
                it.isDirectory && it.name.equals(normalizedTitleId, ignoreCase = true)
            }
            if (saveFolder != null) {
                val modTime = findNewestFileTime(saveFolder.path)
                if (modTime > bestModTime) {
                    bestModTime = modTime
                    bestMatchPath = saveFolder.path
                }
            }

            fal.listFiles(userFolder.path)?.forEach { profileFolder ->
                if (!profileFolder.isDirectory) return@forEach
                val nestedSaveFolder = fal.listFiles(profileFolder.path)?.firstOrNull {
                    it.isDirectory && it.name.equals(normalizedTitleId, ignoreCase = true)
                }
                if (nestedSaveFolder != null) {
                    val modTime = findNewestFileTime(nestedSaveFolder.path)
                    if (modTime > bestModTime) {
                        bestModTime = modTime
                        bestMatchPath = nestedSaveFolder.path
                    }
                }
            }
        }

        if (bestMatchPath != null) {
            Logger.debug(TAG, "findSaveFolderByTitleId: found | path=$bestMatchPath")
        }
        return bestMatchPath
    }

    fun constructSavePath(
        baseDir: String,
        titleId: String,
        emulatorPackage: String? = null
    ): String {
        val normalizedTitleId = titleId.uppercase()
        val profileFolder = if (isDeviceSave(normalizedTitleId)) {
            findOrCreateZeroProfileFolder(baseDir)
        } else {
            findActiveProfileFolder(baseDir, emulatorPackage)
        }
        return "$profileFolder/$normalizedTitleId"
    }

    private fun findNewestFileTime(folderPath: String): Long {
        var newest = 0L
        fal.listFiles(folderPath)?.forEach { child ->
            if (child.isFile) {
                if (child.lastModified > newest) {
                    newest = child.lastModified
                }
            } else if (child.isDirectory) {
                val childNewest = findNewestFileTime(child.path)
                if (childNewest > newest) {
                    newest = childNewest
                }
            }
        }
        return newest
    }
}
