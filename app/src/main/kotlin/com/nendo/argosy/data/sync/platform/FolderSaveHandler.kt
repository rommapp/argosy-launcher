package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Generic folder-bundle save handler used for platforms whose saves are a per-title directory
 * tree zipped on upload and unzipped on download. Replaces the near-verbatim PSP / Vita / Wii /
 * Wii U / 3DS / PS2 handler classes that diverged only in slug and folder-match predicate.
 *
 * Override hooks let platforms with quirks (3DS id0/id1, PS2 BA-prefixed memory-card folders)
 * supply their own logic without spawning a new handler file.
 */
open class FolderSaveHandler(
    private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver,
    val platformSlug: String,
    private val tag: String = "FolderSaveHandler[$platformSlug]"
) : PlatformSaveHandler {

    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val saveFolder = fal.getTransformedFile(localPath)
        if (!saveFolder.exists() || !saveFolder.isDirectory) {
            Logger.debug(tag, "prepareForUpload: Save folder does not exist | path=$localPath")
            return@withContext null
        }

        val outputFile = File(this@FolderSaveHandler.context.cacheDir, "${saveFolder.name}.zip")
        if (!saveArchiver.zipFolder(saveFolder, outputFile)) {
            Logger.error(tag, "prepareForUpload: Failed to zip folder | source=$localPath")
            return@withContext null
        }

        PreparedSave(outputFile, isTemporary = true, listOf(localPath))
    }

    override suspend fun extractDownload(
        tempFile: File,
        context: SaveContext
    ): ExtractResult = withContext(Dispatchers.IO) {
        val targetPath = context.localSavePath ?: run {
            val basePath = resolveBasePath(context.config, null)
                ?: return@withContext ExtractResult(false, null, "No base path for $platformSlug saves")
            val titleId = context.titleId
                ?: return@withContext ExtractResult(false, null, "No title ID for $platformSlug save")
            constructSavePath(basePath, titleId)
                ?: return@withContext ExtractResult(false, null, "Cannot construct $platformSlug save path")
        }

        val targetFolder = File(targetPath)
        targetFolder.mkdirs()

        val success = saveArchiver.unzipSingleFolder(tempFile, targetFolder)
        if (!success) {
            Logger.error(tag, "extractDownload: Unzip failed | target=$targetPath")
            return@withContext ExtractResult(false, null, "Failed to extract $platformSlug save")
        }

        Logger.debug(tag, "extractDownload: Complete | target=$targetPath")
        ExtractResult(true, targetPath)
    }

    /**
     * Default folder lookup uses case-insensitive equality. Platforms with prefix or normalized-
     * folder matching override [folderMatches].
     */
    override fun findSaveFolderByTitleId(basePath: String, titleId: String): String? {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) {
            Logger.debug(tag, "Base path does not exist | path=$basePath")
            return null
        }

        val match = fal.listFiles(basePath)?.firstOrNull { folder ->
            folder.isDirectory && folderMatches(folder.name, titleId)
        }

        if (match != null) {
            Logger.debug(tag, "Save found | path=${match.path}")
            return match.path
        }

        Logger.debug(tag, "No save found | basePath=$basePath, titleId=$titleId")
        return null
    }

    override fun findAllSaveFoldersByTitleId(basePath: String, titleId: String): List<String> {
        if (!fal.exists(basePath) || !fal.isDirectory(basePath)) return emptyList()
        return fal.listFiles(basePath).orEmpty()
            .filter { it.isDirectory && folderMatches(it.name, titleId) }
            .map { it.path }
    }

    override fun constructSavePath(baseDir: String, titleId: String): String? = "$baseDir/$titleId"

    override fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? {
        if (basePathOverride != null) return basePathOverride

        val resolvedPaths = SavePathRegistry.resolvePath(config, platformSlug, null)
        return resolvedPaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: resolvedPaths.firstOrNull()
    }

    /**
     * Per-platform folder-name match predicate. Default is case-insensitive equality. PSP
     * overrides to use prefix matching; PS2 uses normalized BA-prefix matching.
     */
    protected open fun folderMatches(folderName: String, titleId: String): Boolean =
        folderName.equals(titleId, ignoreCase = true)
}
