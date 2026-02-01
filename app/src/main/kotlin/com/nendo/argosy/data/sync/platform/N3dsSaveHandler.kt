package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class N3dsSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver
) : PlatformSaveHandler {
    companion object {
        private const val TAG = "N3dsSaveHandler"
        private const val DEFAULT_CATEGORY = "00040000"
    }

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val saveFolder = fal.getTransformedFile(localPath)
            if (!saveFolder.exists() || !saveFolder.isDirectory) {
                Logger.debug(TAG, "prepareForUpload: Save folder does not exist | path=$localPath")
                return@withContext null
            }

            val outputFile = File(this@N3dsSaveHandler.context.cacheDir, "${saveFolder.name}.zip")
            if (!saveArchiver.zipFolder(saveFolder, outputFile)) {
                Logger.error(TAG, "prepareForUpload: Failed to zip folder | source=$localPath")
                return@withContext null
            }

            PreparedSave(outputFile, isTemporary = true, listOf(localPath))
        }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        withContext(Dispatchers.IO) {
            val basePath = resolveBasePath(context.config, null)
            if (basePath == null) {
                return@withContext ExtractResult(false, null, "No base path for 3DS saves")
            }

            val titleId = context.titleId
            if (titleId == null) {
                return@withContext ExtractResult(false, null, "No title ID for 3DS save")
            }

            val targetPath = constructSavePath(basePath, titleId)
            if (targetPath == null) {
                return@withContext ExtractResult(false, null, "Cannot construct 3DS save path (missing id0/id1 structure)")
            }

            val targetFolder = File(targetPath)
            targetFolder.mkdirs()

            val success = saveArchiver.unzipSingleFolder(tempFile, targetFolder)
            if (!success) {
                Logger.error(TAG, "extractDownload: Unzip failed | target=$targetPath")
                return@withContext ExtractResult(false, null, "Failed to extract 3DS save")
            }

            Logger.debug(TAG, "extractDownload: Complete | target=$targetPath")
            ExtractResult(true, targetPath)
        }

    fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(TAG, "Base path does not exist | path=$basePath")
            return null
        }

        val normalizedTitleId = titleId.uppercase()
        val shortTitleId = if (normalizedTitleId.length > 8) {
            normalizedTitleId.takeLast(8)
        } else {
            normalizedTitleId
        }

        Logger.debug(TAG, "Searching for save | baseDir=$basePath, fullId=$normalizedTitleId, shortId=$shortTitleId")

        var bestMatchPath: String? = null
        var bestModTime = 0L

        // Structure: {Nintendo 3DS}/{id0}/{id1}/title/{category}/{shortTitleId}/data
        fal.listFiles(basePath)?.filter { it.isDirectory }?.forEach { id0Folder ->
            fal.listFiles(id0Folder.path)?.filter { it.isDirectory }?.forEach { id1Folder ->
                val titleBasePath = "${id1Folder.path}/title"
                if (!fal.exists(titleBasePath) || !fal.isDirectory(titleBasePath)) return@forEach

                fal.listFiles(titleBasePath)?.filter { it.isDirectory }?.forEach { categoryDir ->
                    val matchingFolder = fal.listFiles(categoryDir.path)?.firstOrNull {
                        it.isDirectory && it.name.equals(shortTitleId, ignoreCase = true)
                    }
                    if (matchingFolder != null) {
                        val dataPath = "${matchingFolder.path}/data"
                        if (fal.exists(dataPath) && fal.isDirectory(dataPath)) {
                            val modTime = findNewestFileTime(dataPath)
                            Logger.debug(TAG, "Found candidate | path=$dataPath, modTime=$modTime")
                            if (modTime > bestModTime) {
                                bestModTime = modTime
                                bestMatchPath = dataPath
                            }
                        }
                    }
                }
            }
        }

        if (bestMatchPath != null) {
            Logger.debug(TAG, "Save found | path=$bestMatchPath")
        }
        return bestMatchPath
    }

    fun constructSavePath(baseDir: String, titleId: String): String? {
        val category = if (titleId.length >= 16) titleId.take(8) else DEFAULT_CATEGORY
        val shortTitleId = if (titleId.length > 8) titleId.takeLast(8) else titleId

        // Find existing id0/id1 structure
        val id0Folder = fal.listFiles(baseDir)?.firstOrNull { it.isDirectory }
        if (id0Folder == null) {
            Logger.debug(TAG, "No id0 folder found | baseDir=$baseDir")
            return null
        }

        val id1Folder = fal.listFiles(id0Folder.path)?.firstOrNull { it.isDirectory }
        if (id1Folder == null) {
            Logger.debug(TAG, "No id1 folder found | id0=${id0Folder.path}")
            return null
        }

        val savePath = "${id1Folder.path}/title/$category/$shortTitleId/data"
        Logger.debug(TAG, "Constructed save path | path=$savePath")
        return savePath
    }

    fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? {
        if (basePathOverride != null) {
            return if (basePathOverride.endsWith("/sdmc/Nintendo 3DS") || basePathOverride.endsWith("/sdmc/Nintendo 3DS/")) {
                basePathOverride.trimEnd('/')
            } else {
                "$basePathOverride/sdmc/Nintendo 3DS"
            }
        }

        val resolvedPaths = SavePathRegistry.resolvePath(config, "3ds", null)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
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
