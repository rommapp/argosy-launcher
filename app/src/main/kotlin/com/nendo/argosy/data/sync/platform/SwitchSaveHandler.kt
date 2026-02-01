package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SwitchProfileParser
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwitchSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver,
    private val switchProfileParser: SwitchProfileParser
) : PlatformSaveHandler {
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

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val saveFolder = fal.getTransformedFile(localPath)
            if (!saveFolder.exists() || !saveFolder.isDirectory) {
                Logger.debug(TAG, "prepareForUpload: Save folder does not exist or is not a directory | path=$localPath")
                return@withContext null
            }

            val outputFile = File(this@SwitchSaveHandler.context.cacheDir, "${saveFolder.name}.zip")
            if (!saveArchiver.zipFolder(saveFolder, outputFile)) {
                Logger.error(TAG, "prepareForUpload: Failed to zip folder | source=$localPath")
                return@withContext null
            }

            Logger.debug(TAG, "prepareForUpload: Created ZIP | path=${outputFile.absolutePath}, size=${outputFile.length()}")
            PreparedSave(outputFile, isTemporary = true, listOf(localPath))
        }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        withContext(Dispatchers.IO) {
            // Use cached path if available, otherwise resolve from ZIP or construct
            val targetPath = context.localSavePath
                ?: resolveSaveTargetPath(tempFile, context.config, context.emulatorPackage)
                ?: run {
                    val basePath = resolveBasePath(context.config, context.emulatorPackage)
                    if (basePath == null) {
                        return@withContext ExtractResult(false, null, "No base path for Switch saves")
                    }
                    val titleId = context.titleId
                    if (titleId == null) {
                        return@withContext ExtractResult(false, null, "No title ID for Switch save")
                    }
                    constructSavePath(basePath, titleId, context.emulatorPackage)
                }

            val targetFolder = File(targetPath)
            targetFolder.mkdirs()

            // Detect JKSV format and extract appropriately
            val success = if (saveArchiver.isJksvFormat(tempFile)) {
                Logger.debug(TAG, "extractDownload: JKSV format detected, preserving structure")
                saveArchiver.unzipPreservingStructure(tempFile, targetFolder, JKSV_EXCLUDE_FILES)
            } else {
                saveArchiver.unzipSingleFolder(tempFile, targetFolder)
            }

            if (!success) {
                Logger.error(TAG, "extractDownload: Unzip failed | target=$targetPath")
                return@withContext ExtractResult(false, null, "Failed to extract Switch save")
            }

            Logger.debug(TAG, "extractDownload: Complete | target=$targetPath")
            ExtractResult(true, targetPath)
        }

    fun resolveBasePath(config: SavePathConfig, emulatorPackage: String?): String? {
        val resolvedPaths = SavePathRegistry.resolvePathWithPackage(config, emulatorPackage)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
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

    fun isValidCachedSavePath(path: String): Boolean {
        // Expected structure: <base>/save/<userFolder>/<profileFolder>/<titleId>
        // Where userFolder=16 hex, profileFolder=16|32 hex, titleId=16 hex starting with 01
        val parts = path.split("/")
        if (parts.size < 4) return false

        val titleId = parts.last().takeIf { it.isNotEmpty() } ?: parts[parts.size - 2]
        val profileFolder = if (parts.last().isEmpty()) parts[parts.size - 3] else parts[parts.size - 2]
        val userFolder = if (parts.last().isEmpty()) parts[parts.size - 4] else parts[parts.size - 3]

        val isValid = isValidUserFolderId(userFolder) &&
            isValidProfileFolderId(profileFolder) &&
            isValidTitleId(titleId)

        if (!isValid) {
            Logger.debug(TAG, "isValidCachedSavePath: invalid | path=$path, user=$userFolder, profile=$profileFolder, titleId=$titleId")
        }
        return isValid
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

        val topLevelFolders = fal.listFiles(basePath)
        Logger.debug(TAG, "findSaveFolderByTitleId: top-level folders=${topLevelFolders?.map { it.name }}")

        topLevelFolders?.forEach { userFolder ->
            if (!userFolder.isDirectory) return@forEach

            // Skip device save folders (titleId directly under /save/) - we only support nested structure
            if (userFolder.name.equals(normalizedTitleId, ignoreCase = true)) {
                Logger.debug(TAG, "findSaveFolderByTitleId: skipping device save format | path=${userFolder.path}")
                return@forEach
            }

            // Only process valid user folders (16 hex chars, typically 0000000000000000)
            if (!isValidUserFolderId(userFolder.name)) {
                Logger.debug(TAG, "findSaveFolderByTitleId: skipping non-user folder | name=${userFolder.name}")
                return@forEach
            }

            val userChildren = fal.listFiles(userFolder.path)
            Logger.debug(TAG, "findSaveFolderByTitleId: userFolder=${userFolder.name}, children=${userChildren?.map { it.name }}")

            userChildren?.forEach { profileFolder ->
                if (!profileFolder.isDirectory) return@forEach
                // Only process valid profile folders (16 or 32 hex chars)
                if (!isValidProfileFolderId(profileFolder.name)) {
                    return@forEach
                }
                val profileChildren = fal.listFiles(profileFolder.path)
                Logger.debug(TAG, "findSaveFolderByTitleId: profileFolder=${profileFolder.name}, children=${profileChildren?.map { it.name }}")

                val nestedSaveFolder = profileChildren?.firstOrNull {
                    it.isDirectory && it.name.equals(normalizedTitleId, ignoreCase = true)
                }
                if (nestedSaveFolder != null) {
                    val modTime = findNewestFileTime(nestedSaveFolder.path)
                    Logger.debug(TAG, "findSaveFolderByTitleId: found nested match | path=${nestedSaveFolder.path}, modTime=$modTime")
                    if (bestMatchPath == null || modTime > bestModTime) {
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
