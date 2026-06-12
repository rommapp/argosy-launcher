package com.nendo.argosy.data.emulator

import com.nendo.argosy.util.Logger
import com.nendo.sigil.Sigil
import com.nendo.sigil.SigilResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Title ID extraction result.
 *
 * - [titleId]: canonical save-matching form expected by the platform.
 * - [fromBinary]: whether [titleId] was decoded from the file content
 *   (high-confidence — locks the game record so filename heuristics can't
 *   override) or fell back to a filename pattern (lower confidence).
 * - [rawSerial]: ID as it appears in the binary, before normalization. Useful
 *   for UI/logging and cross-referencing external databases.
 * - [usage]: how the platform uses [titleId] for save artifacts on disk.
 *   `EXACT` = single save folder/file per game; `PREFIX` = multiple
 *   artifacts share the ID as a prefix (PSP profiles, GameCube GCI files).
 */
data class TitleIdResult(
    val titleId: String,
    val fromBinary: Boolean,
    val rawSerial: String = titleId,
    val saveId: String = titleId,
    val usage: SaveUsage = SaveUsage.FOLDER_EXACT
) {
    enum class SaveUsage { FOLDER_EXACT, FOLDER_PREFIX, FILE_EXACT, FILE_PREFIX }
}

/**
 * Title ID extraction facade. All work delegates to the native sigil
 * library (com.nendo.sigil.Sigil); this class exists only to:
 *   - expose the legacy Kotlin API surface used by SavePathResolver,
 *     TitleIdDownloadObserver, and SaveDownloader, and
 *   - resolve the Switch prod.keys path (Android-specific filesystem
 *     discovery — sigil knows about ROMs, not about Android storage).
 */
@Singleton
class TitleIdExtractor @Inject constructor(
    private val switchKeyManager: SwitchKeyManager
) {
    private val TAG = "TitleIdExtractor"

    fun extractTitleId(romFile: File, platformId: String, emulatorPackage: String? = null): String? =
        extractTitleIdWithSource(romFile, platformId, emulatorPackage)?.titleId

    fun extractTitleIdWithSource(
        romFile: File,
        platformId: String,
        emulatorPackage: String? = null
    ): TitleIdResult? {
        val prodKeys = if (platformId == "switch" || platformId == "nsw") {
            emulatorPackage?.let { switchKeyManager.findProdKeysPath(it) }
        } else null

        val r = Sigil.extract(
            path = romFile.absolutePath,
            platformSlug = platformId,
            prodKeysPath = prodKeys
        )
        if (r == null) {
            Logger.debug(TAG, "[SaveSync] DETECT | sigil returned null | file=${romFile.name}, platform=$platformId")
            return null
        }
        Logger.debug(
            TAG,
            "[SaveSync] DETECT | sigil hit | file=${romFile.name}, platform=$platformId, " +
                "titleId=${r.titleId}, raw=${r.rawSerial}, saveId=${r.saveId}, source=${r.source}, usage=${r.usage}"
        )
        return TitleIdResult(
            titleId = r.titleId,
            fromBinary = r.source == SigilResult.Source.Binary,
            rawSerial = r.rawSerial,
            saveId = r.saveId.ifBlank { r.titleId },
            usage = when (r.usage) {
                SigilResult.Usage.FolderExact  -> TitleIdResult.SaveUsage.FOLDER_EXACT
                SigilResult.Usage.FolderPrefix -> TitleIdResult.SaveUsage.FOLDER_PREFIX
                SigilResult.Usage.FileExact    -> TitleIdResult.SaveUsage.FILE_EXACT
                SigilResult.Usage.FilePrefix   -> TitleIdResult.SaveUsage.FILE_PREFIX
            }
        )
    }
}
