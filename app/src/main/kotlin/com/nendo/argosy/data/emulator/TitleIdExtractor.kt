package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.Logger
import com.nendo.sigil.Sigil
import com.nendo.sigil.SigilResult
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Title ID extraction result.
 *
 * - [titleId]: canonical save-matching form expected by the platform.
 * - [fromBinary]: whether [titleId] was decoded from the file content
 *   (high-confidence - locks the game record so filename heuristics can't
 *   override) or fell back to a filename pattern (lower confidence).
 * - [rawSerial]: ID as it appears in the binary, before normalization. Useful
 *   for UI/logging and cross-referencing external databases.
 * - [usage]: how the platform uses [titleId] for save artifacts on disk.
 *   `EXACT` = single save folder/file per game; `PREFIX` = multiple
 *   artifacts share the ID as a prefix (PSP profiles, GameCube GCI files);
 *   `FOLDER_SPLIT` = one folder, but the id nests as equal-length path
 *   segments rather than a flat name (3DS `0004000000033500` lands at
 *   `00040000/00033500`).
 */
data class TitleIdResult(
    val titleId: String,
    val fromBinary: Boolean,
    val rawSerial: String = titleId,
    val saveId: String = titleId,
    val usage: SaveUsage = SaveUsage.FOLDER_EXACT,
    val features: Int = 0,
    val n64Header: String = ""
) {
    enum class SaveUsage { FOLDER_EXACT, FOLDER_PREFIX, FILE_EXACT, FILE_PREFIX, FOLDER_SPLIT }
}

/**
 * Title ID extraction facade. All work delegates to the native sigil
 * library (com.nendo.sigil.Sigil); this class exists only to:
 *   - expose the legacy Kotlin API surface used by SavePathResolver,
 *     TitleIdDownloadObserver, and SaveDownloader, and
 *   - resolve the Switch prod.keys path (Android-specific filesystem
 *     discovery - sigil knows about ROMs, not about Android storage), and
 *   - map a Switch patch or add-on content id to its application id
 *     ([SwitchTitleIds.baseApplicationId]), the id saves are keyed by.
 */
@Singleton
class TitleIdExtractor @Inject constructor(
    private val switchKeyManager: SwitchKeyManager
) {
    private val TAG = "TitleIdExtractor"

    private data class ExtractionCacheKey(
        val absolutePath: String,
        val lastModified: Long,
        val length: Long,
        val platformId: String,
        val prodKeysPath: String?
    )

    private data class CachedExtraction(
        val key: ExtractionCacheKey,
        val result: TitleIdResult?
    )

    @Volatile
    private var lastExtraction: CachedExtraction? = null

    fun extractTitleId(romFile: File, platformId: String, emulatorPackage: String? = null): String? =
        extractTitleIdWithSource(romFile, platformId, emulatorPackage)?.titleId

    fun extractTitleIdWithSource(
        romFile: File,
        platformId: String,
        emulatorPackage: String? = null
    ): TitleIdResult? {
        val isSwitch = platformId == "switch" || platformId == "nsw"
        val prodKeys = if (isSwitch) {
            emulatorPackage?.let { switchKeyManager.findProdKeysPath(it) }
        } else null

        val cacheKey = ExtractionCacheKey(
            absolutePath = romFile.absolutePath,
            lastModified = romFile.lastModified(),
            length = romFile.length(),
            platformId = platformId,
            prodKeysPath = prodKeys
        )
        lastExtraction?.let { cached ->
            if (cached.key == cacheKey && cached.key.lastModified > 0L) {
                return cached.result
            }
        }

        val r = Sigil.extract(
            path = romFile.absolutePath,
            platformSlug = platformId,
            prodKeysPath = prodKeys
        )
        if (r == null) {
            Logger.debug(TAG, "[SaveSync] DETECT | sigil returned null | file=${romFile.name}, platform=$platformId")
            lastExtraction = CachedExtraction(cacheKey, null)
            return null
        }
        Logger.debug(
            TAG,
            "[SaveSync] DETECT | sigil hit | file=${romFile.name}, platform=$platformId, " +
                "titleId=${r.titleId}, raw=${r.rawSerial}, saveId=${r.saveId}, source=${r.source}, usage=${r.usage}, " +
                "switchContentType=${r.switchContentType}"
        )
        val titleId = if (isSwitch) SwitchTitleIds.baseApplicationId(r.titleId) else r.titleId
        val saveId = r.saveId.ifBlank { r.titleId }
            .let { if (isSwitch) SwitchTitleIds.baseApplicationId(it) else it }
        val result = TitleIdResult(
            titleId = titleId,
            fromBinary = r.source == SigilResult.Source.Binary,
            rawSerial = r.rawSerial,
            saveId = saveId,
            usage = when (r.usage) {
                SigilResult.Usage.FolderExact  -> TitleIdResult.SaveUsage.FOLDER_EXACT
                SigilResult.Usage.FolderPrefix -> TitleIdResult.SaveUsage.FOLDER_PREFIX
                SigilResult.Usage.FileExact    -> TitleIdResult.SaveUsage.FILE_EXACT
                SigilResult.Usage.FilePrefix   -> TitleIdResult.SaveUsage.FILE_PREFIX
                SigilResult.Usage.FolderSplit  -> TitleIdResult.SaveUsage.FOLDER_SPLIT
            },
            features = r.features,
            n64Header = r.n64Header
        )
        lastExtraction = CachedExtraction(cacheKey, result)
        return result
    }

    fun resolveTextureFolderKey(
        strategy: TextureFolderKeyStrategy,
        platformSlug: String,
        game: GameEntity?,
        romFile: File?,
        zipArchive: File? = null,
        extractedResult: TitleIdResult? = null
    ): String? {
        val canonicalSlug = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
        return when (strategy) {
            TextureFolderKeyStrategy.N64_HEADER_NAME -> {
                extractedResult?.n64Header?.takeIf { it.isNotBlank() }
                    ?: romFile?.takeIf { it.isFile }
                        ?.let { extractTitleIdWithSource(it, canonicalSlug)?.n64Header }
                        ?.takeIf { it.isNotBlank() }
                    ?: zipArchive?.let { extractZipSingleTopLevelDir(it) }
            }
            TextureFolderKeyStrategy.GAME_ID -> {
                val raw = resolveGameIdFolderKey(canonicalSlug, game, romFile, zipArchive, extractedResult)
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                if (canonicalSlug == "psp") {
                    raw?.replace("-", "")?.replace("_", "")
                } else {
                    raw
                }
            }
        }
    }

    private fun resolveGameIdFolderKey(
        canonicalSlug: String,
        game: GameEntity?,
        romFile: File?,
        zipArchive: File?,
        extractedResult: TitleIdResult?
    ): String? {
        if (canonicalSlug == "ps2") {
            return listOfNotNull(
                game?.titleId,
                extractedResult?.titleId,
                extractedResult?.rawSerial,
                game?.saveId,
                romFile?.takeIf { it.isFile }?.let { extractTitleId(it, canonicalSlug) }
            ).firstNotNullOfOrNull(::normalizePs2Serial)
                ?: zipArchive?.let { extractZipRootSerialFallback(it, canonicalSlug) }
        }

        if (canonicalSlug == "gc" || canonicalSlug == "ngc" || canonicalSlug == "wii") {
            romFile?.takeIf { it.isFile }?.let { file ->
                readNintendoDisc6CharGameId(file)?.let { return it }
            }

            extractedResult?.rawSerial?.takeIf { it.isNotBlank() }?.let { return it }
            game?.saveId?.takeIf { it.isNotBlank() && matchesPlatformSerialFormat(it, canonicalSlug) }?.let { return it }
            romFile?.takeIf { it.isFile }?.let { extractTitleIdWithSource(it, canonicalSlug)?.rawSerial }
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }

            return zipArchive?.let { extractZipRootSerialFallback(it, canonicalSlug) }
                ?.takeIf { it.isNotBlank() }
        }

        game?.titleId?.takeIf { it.isNotBlank() }?.let { return it }
        extractedResult?.titleId?.takeIf { it.isNotBlank() }?.let { return it }
        romFile?.takeIf { it.isFile }?.let { extractTitleId(it, canonicalSlug) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return zipArchive?.let { extractZipRootSerialFallback(it, canonicalSlug) }
    }

    companion object {
        private val NGC_WII_SERIAL_REGEX = Regex("^([A-Z0-9]{3,4}|[A-Z0-9]{6})$")
        private val PSP_SERIAL_REGEX = Regex("^[A-Z]{4}[_-]?\\d{5}$")
        private val DC_SERIAL_REGEX = Regex("^(?=.*\\d)[A-Z0-9_-]{4,12}$")
        private val AZAHAR_TITLE_ID_REGEX = Regex("^[0-9A-Fa-f]{16}$")
        private val PS2_SAVE_PREFIX_REGEX = Regex("^B[A-Z](?=[A-Z]{4}[-_]?\\d)")
        private val PS2_SERIAL_REGEX = Regex("^([A-Z]{4})[-_]?(\\d{3})\\.?(\\d{2})(?:\\D.*)?$")

        /**
         * The form PCSX2 derives from a disc's boot ELF and names its per-game folders with:
         * `SLUS_203.12;1` and the memory card folder name `BASLUS-20312AC04` are both `SLUS-20312`.
         */
        fun normalizePs2Serial(raw: String): String? {
            val cleaned = raw.trim().uppercase(Locale.ROOT).replace(PS2_SAVE_PREFIX_REGEX, "")
            val match = PS2_SERIAL_REGEX.matchEntire(cleaned) ?: return null
            val (letters, head, tail) = match.destructured
            return "$letters-$head$tail"
        }

        fun readNintendoDisc6CharGameId(file: File): String? {
            return runCatching {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    if (raf.length() < 6) return@use null
                    val header = ByteArray(4)
                    raf.seek(0)
                    raf.readFully(header)
                    val magic = String(header, Charsets.US_ASCII)
                    val offset = when {
                        magic == "WBFS" && raf.length() >= 0x09 -> {
                            raf.seek(0x08)
                            val shift = raf.readByte().toInt() and 0xFF
                            if (shift in 9..16) (1L shl shift) else 0x200L
                        }
                        (magic == "RVZ\u0001" || magic == "WIA\u0001") && raf.length() >= 0x5E -> 0x58L
                        else -> 0L
                    }
                    if (raf.length() < offset + 6) return@use null
                    raf.seek(offset)
                    val bytes = ByteArray(6)
                    raf.readFully(bytes)
                    val id = String(bytes, Charsets.US_ASCII)
                    if (id.all { it in 'A'..'Z' || it in '0'..'9' }) id else null
                }
            }.getOrNull()
        }

        fun extractZipSingleTopLevelDir(zipArchive: File): String? {
            if (!zipArchive.isFile) return null
            return try {
                ZipFile(zipArchive).use { zf ->
                    val topDirs = mutableSetOf<String>()
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val normalized = entry.name.replace('\\', '/').trim('/')
                        if (normalized.isEmpty() || normalized.startsWith("__MACOSX")) continue
                        val slashIndex = normalized.indexOf('/')
                        if (slashIndex <= 0) {
                            if (!entry.isDirectory) return@use null
                            topDirs.add(normalized)
                        } else {
                            topDirs.add(normalized.substring(0, slashIndex))
                        }
                    }
                    topDirs.singleOrNull()?.takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
        }

        fun extractZipRootSerialFallback(zipArchive: File, platformSlug: String): String? {
            val singleRoot = extractZipSingleTopLevelDir(zipArchive) ?: return null
            val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
            if (!matchesPlatformSerialFormat(singleRoot, canonical)) return null
            if (canonical == "ps2") return normalizePs2Serial(singleRoot)
            val clean = singleRoot.trim().uppercase(Locale.ROOT)
            return if (canonical == "psp") {
                clean.replace("-", "").replace("_", "")
            } else {
                clean
            }
        }

        fun matchesPlatformSerialFormat(candidate: String, platformSlug: String): Boolean {
            val trimmed = candidate.trim()
            if (trimmed.isEmpty()) return false
            val upper = trimmed.uppercase(Locale.ROOT)
            val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
            return when (canonical) {
                "gc", "ngc", "wii" -> NGC_WII_SERIAL_REGEX.matches(upper)
                "psp" -> PSP_SERIAL_REGEX.matches(upper)
                "dc" -> DC_SERIAL_REGEX.matches(upper)
                "3ds" -> AZAHAR_TITLE_ID_REGEX.matches(upper)
                "ps2" -> normalizePs2Serial(upper) != null
                else -> true
            }
        }
    }
}
