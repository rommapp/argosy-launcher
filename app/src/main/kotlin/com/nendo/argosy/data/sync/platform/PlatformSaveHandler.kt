package com.nendo.argosy.data.sync.platform

import com.nendo.argosy.data.emulator.SavePathConfig
import java.io.File

/**
 * Per-platform save bundling, extraction, and discovery. The two `prepareForUpload`/
 * `extractDownload` methods are total -- every handler implements them. The folder-discovery
 * methods (`findSaveFolderByTitleId`, `resolveBasePath`, `constructSavePath`) are optional and
 * return null when the platform doesn't follow a per-title-id folder layout (e.g. RetroArch's
 * single-file saves, GCI memory cards, Default file-based saves).
 *
 * Adding a new folder-based platform = register a new entry in [PlatformSaveHandlerRegistry];
 * no other call site changes.
 */
interface PlatformSaveHandler {
    suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave?
    suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult

    /**
     * Locate an existing save folder under [basePath] for [saveId]. Returns null when the
     * platform doesn't store saves per-save-id, or when no match is found.
     */
    fun findSaveFolderBySaveId(basePath: String, saveId: String): String? = null

    /**
     * Locate ALL save folders under [basePath] that belong to [saveId]. Default behavior
     * narrows to whatever [findSaveFolderBySaveId] returns. Platforms whose disc id maps to
     * many on-disk profile folders (PSP) override this to enumerate every match.
     */
    fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> =
        listOfNotNull(findSaveFolderBySaveId(basePath, saveId))

    /**
     * Resolve the platform's save root, applying any user override. Returns null when the
     * platform doesn't expose a single base path (the default for non-folder handlers).
     */
    fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? = null

    /**
     * Construct the path where a save for [saveId] should live under [baseDir]. Default returns
     * null (handler does not own a folder layout). Folder-based handlers override this.
     */
    fun constructSavePath(baseDir: String, saveId: String): String? = null

    fun isCanonicalFolderPath(savePath: String, saveId: String): Boolean = true
}

data class SaveContext(
    val config: SavePathConfig,
    val romPath: String?,
    val saveId: String?,
    val emulatorPackage: String?,
    val gameId: Long,
    val gameTitle: String,
    val platformSlug: String,
    val emulatorId: String,
    val localSavePath: String? = null,
    val coreName: String? = null
)

data class PreparedSave(
    val file: File,
    val isTemporary: Boolean,
    val originalPaths: List<String> = emptyList()
)

data class ExtractResult(
    val success: Boolean,
    val targetPath: String?,
    val error: String? = null,
    /** True when the failure was caused by a corrupt server-side zip
     * (deflate stream broken, central directory mismatched, etc.).
     * SaveDownloader uses this to skip future attempts at the same
     * server timestamp. */
    val corruptZip: Boolean = false
)

data class MemcardInfo(
    val name: String,
    val path: String,
    val gameFolderCount: Int,
    val lastModified: Long
)
