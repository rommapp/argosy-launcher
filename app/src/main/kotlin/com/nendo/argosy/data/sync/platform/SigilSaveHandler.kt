package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.BuiltinSaveBase
import com.nendo.argosy.data.emulator.CartFeatureScanner
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.local.dao.SigilSyncStateDao
import com.nendo.argosy.data.local.entity.SigilSyncStateEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.SwitchProfileParser
import com.nendo.argosy.data.emulator.savepath.FlycastStandaloneConfig
import com.nendo.argosy.data.emulator.savepath.PpssppMemoryStick
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.FalSigilFileAccess
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.sigil.PosixFileAccess
import com.nendo.sigil.Sigil
import com.nendo.sigil.SigilException
import com.nendo.sigil.SigilResult
import com.nendo.sigil.SigilSaveUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SigilRoute(
    val layout: String,
    val root: String,
    val emulatorPackage: String?,
    val profile: String?,
    val options: Map<String, String> = emptyMap(),
    val libretro: Boolean = false
)

sealed class SigilCollect {
    data object NotRouted : SigilCollect()
    data object Absent : SigilCollect()
    data class Unreadable(val reason: String) : SigilCollect()
    /**
     * [root] is the save root the unit was collected from. [pending] is the Sigil sync state this
     * collect produced; it is stored through [SigilSaveHandler.commit] once the unit reached RomM.
     */
    data class Found(
        val data: ByteArray,
        val artifact: String,
        val contentHash: String,
        val identityHash: String,
        val shape: String = SHAPE_SINGLE,
        val root: String? = null,
        val pending: SigilPendingState? = null
    ) : SigilCollect()

    companion object {
        const val SHAPE_SINGLE = "SINGLE"
        const val SHAPE_MULTI = "MULTI"
        const val SHAPE_FOLDER = "FOLDER"
    }
}

class SigilPendingState internal constructor(internal val row: SigilSyncStateEntity)

internal class NativeArrangement(val layout: String, val files: Map<String, ByteArray>, val options: Map<String, String>)

sealed class SigilRestore {
    data object NotRouted : SigilRestore()
    data class Restored(val hardcoreMarker: Boolean) : SigilRestore()

    /**
     * [code] is the [SigilException] code Sigil refused with, or null for a refusal Argosy made
     * itself.
     */
    data class Refused(val reason: String, val code: Int? = null) : SigilRestore()
}

/**
 * The save under a route's root as it is now, read without storing any Sigil sync state.
 * [anchored] says a sync state from an earlier restore or upload exists for the root, and
 * [changed] whether the saves differ from it; [changed] means nothing when [anchored] is false.
 */
sealed class SigilProbe {
    data object Absent : SigilProbe()
    data class Unreadable(val reason: String) : SigilProbe()
    data class Found(val unit: SigilCollect.Found, val anchored: Boolean, val changed: Boolean) : SigilProbe()
}

/**
 * The save handler for every emulator whose saves Sigil collects and restores: memory cards,
 * GCI folders and profile save trees. Sigil decides which files are the game's and what travels
 * to RomM; Argosy supplies the save root, the file access and the decision to write.
 */
@Singleton
class SigilSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val switchProfileParser: SwitchProfileParser,
    private val titleDbRepository: TitleDbRepository,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val fal: FileAccessLayer,
    private val connectionManager: dagger.Lazy<RomMConnectionManager>,
    private val saveUnitResolver: SaveUnitResolver,
    private val builtinSaveBase: BuiltinSaveBase,
    private val retroArchPathResolver: RetroArchPathResolver,
    private val sigilSyncStateDao: SigilSyncStateDao,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val cartFeatureScanner: CartFeatureScanner,
    private val saveLocator: SigilSaveLocator
) : PlatformSaveHandler {

    companion object {
        private const val TAG = "SigilSaveHandler"

        private val LAYOUTS: Map<String, Map<String, String>> = mapOf(
            "gc" to mapOf("dolphin" to "dolphin_standalone", "dolphin_mmjr" to "dolphin_standalone"),
            "wii" to mapOf("dolphin_wii" to "dolphin_standalone", "dolphin_mmjr_wii" to "dolphin_standalone"),
            "psx" to mapOf("duckstation" to "duckstation"),
            "ps2" to listOf("nethersx2", "aethersx2", "pcsx2", "armsx2_refresh", "armsx2")
                .associateWith { "pcsx2_standalone" },
            "psp" to mapOf("ppsspp" to "ppsspp_standalone", "ppsspp_gold" to "ppsspp_standalone"),
            "vita" to mapOf("vita3k" to "vita3k", "vita3k-zx" to "vita3k", "vita3k-plus" to "vita3k"),
            "ps3" to mapOf("aps3e" to "aps3e", "armsx3" to "armsx3", "armsx3_play" to "armsx3"),
            "switch" to listOf(
                "yuzu", "eden", "citron", "sudachi", "lemon", "skyline", "strato", "ryujinx", "kenjinx"
            ).associateWith { it } + ("eden_duo" to "eden"),
            "wiiu" to mapOf("cemu" to "cemu", "cemu_dualscreen" to "cemu"),
            "3ds" to mapOf(
                "citra" to "citra", "citra_mmj" to "citra", "borked3ds" to "citra",
                "azahar" to "azahar", "lime3ds" to "lime3ds"
            ),
            "dreamcast" to mapOf("flycast" to FLYCAST_STANDALONE_LAYOUT)
        )

        private val ROOT_ANCHORS: Map<String, List<String>> = mapOf(
            "dolphin_standalone" to listOf("GC", "Wii"),
            "duckstation" to listOf("memcards"),
            "pcsx2_standalone" to listOf("memcards"),
            "ppsspp_standalone" to listOf("PSP"),
            "vita3k" to listOf("ux0"),
            "aps3e" to listOf("dev_hdd0"),
            "armsx3" to listOf("dev_hdd0"),
            "yuzu" to listOf("nand"),
            "eden" to listOf("nand"),
            "citron" to listOf("nand"),
            "sudachi" to listOf("nand"),
            "lemon" to listOf("nand"),
            "skyline" to listOf("switch"),
            "strato" to listOf("switch"),
            "ryujinx" to listOf("bis"),
            "kenjinx" to listOf("bis"),
            "cemu" to listOf("mlc01"),
            "citra" to listOf("sdmc"),
            "azahar" to listOf("sdmc"),
            "lime3ds" to listOf("sdmc")
        )

        private val GB_CORES = setOf("gambatte", "mgba", "vbam", "sameboy", "tgbdual")

        private val LIBRETRO_LAYOUTS: Map<String, Set<String>> = mapOf(
            "saturn" to setOf("mednafen_saturn", "kronos", "yabause", "yabasanshiro"),
            "scd" to setOf("genesis_plus_gx"),
            "dreamcast" to setOf("flycast"),
            "gb" to GB_CORES,
            "gbc" to GB_CORES,
            "n64" to setOf("mupen64plus_next", "parallel_n64"),
            "wii" to setOf("dolphin"),
            "psp" to setOf("ppsspp"),
            "3ds" to setOf("citra")
        )

        private val BUILTIN_LIBRETRO_LAYOUTS: Map<String, Set<String>> = mapOf(
            "psx" to setOf("mednafen_psx_hw", "pcsx_rearmed"),
            "3ds" to setOf("azahar"),
            "gc" to setOf("dolphin"),
            "ps2" to setOf("pcsx2")
        )

        const val GENERIC_LIBRETRO_LAYOUT = "libretro"

        private val LIBRETRO_USER_DIRS: Map<String, String> = mapOf("azahar" to "Azahar", "citra" to "Citra")

        private val LIBRETRO_LAYOUT_ALIASES: Map<String, String> = mapOf(
            "mupen64plus_next_gles3" to "mupen64plus_next",
            "mupen64plus_next_gles2" to "mupen64plus_next",
            "armsx2" to "pcsx2"
        )

        private const val PCSX2_LAYOUT = "pcsx2"
        private const val PCSX2_MEMCARD_DIR = "pcsx2/memcards"
        private const val PCSX2_STANDALONE_LAYOUT = "pcsx2_standalone"
        private const val PCSX2_STANDALONE_CARD_DIR = "memcards"
        private const val PCSX2_DEFAULT_CARD = "Mcd001.ps2"
        private const val PS2_CARD_SUFFIX = ".ps2"

        private const val FLYCAST_LAYOUT = "flycast"
        private const val FLYCAST_PER_CONTENT_VMUS = "reicast_per_content_vmus"
        private val FLYCAST_PER_GAME_VALUES = setOf("VMU A1", "All VMUs")
        private const val FLYCAST_SHARED_DIR = "dc"
        private const val FLYCAST_STANDALONE_LAYOUT = "flycast_standalone"

        private const val PCSX2_SLOT1_OPTION = "Slot1_Filename"

        private val CLOSED_EMULATOR_LAYOUTS = setOf("ryujinx", "kenjinx")

        private val PROFILE_FROM_EMULATOR_LAYOUTS = setOf("yuzu", "eden", "citron", "sudachi", "lemon")

        /**
         * The unowned shared-volume saves that were not unowned at the previous collect, given that
         * collect's newline-joined names. Null [previous] means no collect has seen the volume yet,
         * and then nothing is new.
         */
        fun newlyUnowned(previous: String?, unowned: List<String>): List<String> {
            if (previous == null) return emptyList()
            val known = previous.split('\n').filter { it.isNotEmpty() }.toSet()
            return unowned.filterNot { it in known }
        }

        private val NO_STATE = ByteArray(0)

        fun collectRow(stored: SigilSyncStateEntity?, collected: SigilSyncStateEntity, claimed: Boolean): SigilSyncStateEntity =
            if (claimed) collected else collected.copy(state = stored?.state ?: NO_STATE)

        fun commitRow(stored: SigilSyncStateEntity?, pending: SigilSyncStateEntity, now: Long): SigilSyncStateEntity =
            pending.copy(unowned = stored?.unowned ?: pending.unowned, updatedAt = now)

        fun committedState(stored: SigilSyncStateEntity?): ByteArray? = stored?.state?.takeIf { it.isNotEmpty() }

        /**
         * The shape RomM records for a unit's bytes. [sigilShape] is what collect reported, or null
         * for a unit read back from the cache, which is then read from the bytes: not a zip is
         * SINGLE, a flat zip MULTI, and a zip of folders FOLDER.
         */
        fun unitShape(sigilShape: SigilSaveUnit.Shape?, data: ByteArray): String {
            if (sigilShape == SigilSaveUnit.Shape.Single || sigilShape == SigilSaveUnit.Shape.None) return SigilCollect.SHAPE_SINGLE
            if (sigilShape == SigilSaveUnit.Shape.Multi) return SigilCollect.SHAPE_MULTI
            if (sigilShape == SigilSaveUnit.Shape.Folder) return SigilCollect.SHAPE_FOLDER
            val names = runCatching {
                java.util.zip.ZipInputStream(data.inputStream()).use { zip ->
                    generateSequence { zip.nextEntry }.map { it.name }.toList()
                }
            }.getOrDefault(emptyList())
            return when {
                names.isEmpty() -> SigilCollect.SHAPE_SINGLE
                names.none { '/' in it.trimEnd('/') } -> SigilCollect.SHAPE_MULTI
                else -> SigilCollect.SHAPE_FOLDER
            }
        }

        fun layoutFor(emulatorId: String, platformSlug: String): String? {
            val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
            val platform = if (canonical == "n3ds") "3ds" else canonical
            return LAYOUTS[platform]?.get(emulatorId)
        }

        /**
         * The Sigil layout a libretro [core] saves in on [platformSlug], or null when Sigil does not
         * route it. The built-in-only layouts never route under RetroArch.
         */
        fun libretroLayoutFor(platformSlug: String, core: String, retroArch: Boolean): String? {
            val platform = PlatformDefinitions.getCanonicalSlug(platformSlug)
            val builtinOnly = if (retroArch) emptySet() else BUILTIN_LIBRETRO_LAYOUTS[platform].orEmpty()
            val cores = LIBRETRO_LAYOUTS[platform].orEmpty() + builtinOnly
            return libretroLayoutName(core).takeIf { it in cores }
        }

        /**
         * The name Sigil knows a libretro [core]'s save layout by. Core option overrides stay keyed
         * by [core] itself.
         */
        fun libretroLayoutName(core: String): String = LIBRETRO_LAYOUT_ALIASES[core] ?: core

        /**
         * Sigil's generic libretro layout ({stem}.srm and {stem}.rtc) for a [core] that has no layout
         * of its own on [platformSlug] under either frontend, or null when it has one. The generic
         * layout places saves on this device only and never decides what travels to RomM.
         */
        fun genericLibretroLayoutFor(platformSlug: String, core: String): String? {
            val platform = PlatformDefinitions.getCanonicalSlug(platformSlug)
            val covered = LIBRETRO_LAYOUTS[platform].orEmpty() + BUILTIN_LIBRETRO_LAYOUTS[platform].orEmpty()
            return GENERIC_LIBRETRO_LAYOUT.takeUnless { libretroLayoutName(core) in covered }
        }

        internal fun nativeArrangements(
            layout: String,
            subdirs: List<String>,
            options: Map<String, String>,
            files: Map<String, ByteArray>
        ): List<NativeArrangement> {
            val asTheyAre = (listOf("") + subdirs).map { prefix ->
                NativeArrangement(layout, if (prefix.isEmpty()) files else files.mapKeys { "$prefix/${it.key}" }, options)
            }
            return listOfNotNull(folderCardArrangement(layout, options, files)) + asTheyAre
        }

        private fun folderCardArrangement(
            layout: String,
            options: Map<String, String>,
            files: Map<String, ByteArray>
        ): NativeArrangement? {
            val cardOptions = folderCardOptions(layout, options) ?: return null
            val entries = folderCardEntries(files) ?: return null
            val card = "$PCSX2_STANDALONE_CARD_DIR/${cardOptions.getValue(PCSX2_SLOT1_OPTION)}"
            val laid = entries.mapKeys { "$card/${it.key}" }
            val superblock = "$card/${Ps2FolderCardSuperblock.FILE_NAME}"
            val formatted = if (Ps2FolderCardSuperblock.isFormatted(laid[superblock])) {
                laid
            } else {
                laid + (superblock to Ps2FolderCardSuperblock.formatted8Mb())
            }
            return NativeArrangement(PCSX2_STANDALONE_LAYOUT, formatted, cardOptions)
        }

        private fun folderCardOptions(layout: String, options: Map<String, String>): Map<String, String>? =
            when (layout) {
                PCSX2_STANDALONE_LAYOUT -> {
                    val card = options[PCSX2_SLOT1_OPTION]?.takeIf { it.isNotBlank() } ?: PCSX2_DEFAULT_CARD
                    options + (PCSX2_SLOT1_OPTION to card)
                }
                PCSX2_LAYOUT -> mapOf(PCSX2_SLOT1_OPTION to PCSX2_DEFAULT_CARD)
                else -> null
            }

        private fun folderCardEntries(files: Map<String, ByteArray>): Map<String, ByteArray>? {
            if (files.keys.any { '/' !in it }) return null
            val root = files.keys.map { it.substringBefore('/') }.distinct().singleOrNull()
            val cardRooted = root != null && (
                root.endsWith(PS2_CARD_SUFFIX, ignoreCase = true) ||
                    "$root/${Ps2FolderCardSuperblock.FILE_NAME}" in files
                )
            return if (cardRooted) files.mapKeys { it.key.removePrefix("$root/") } else files
        }

        /**
         * Where the built-in PS2 core keeps its shared memory cards: `pcsx2/memcards` under the
         * libretro system directory, not the per-game save directory.
         */
        fun pcsx2MemcardRoot(libretroSystemDir: File): String =
            File(libretroSystemDir, PCSX2_MEMCARD_DIR).absolutePath

        fun rootFor(basePath: String, layout: String): String? =
            ROOT_ANCHORS[layout]?.let { rootFor(basePath, it) }

        /**
         * The save root a layout expects, found in [basePath] by the first folder of one of the
         * layout's subfolders: `memcards` gives the folder that holds `memcards/`.
         */
        fun rootFor(basePath: String, subdirs: List<String>): String? {
            val path = basePath.trimEnd('/')
            for (subdir in subdirs) {
                val index = anchorIndex(path, subdir.substringBefore('/')) ?: continue
                return path.substring(0, index)
            }
            return null
        }

        /**
         * Whether deleting [path] would take more than one game's saves out of a folder Sigil
         * owns: a save root, its anchor folder, or a card or volume directly inside it. Deeper
         * paths, such as one game's folder on a PCSX2 folder card, are not protected.
         */
        fun isProtectedSavePath(path: String): Boolean {
            val trimmed = path.trimEnd('/')
            return ROOT_ANCHORS.values.flatten().distinct().any { anchor ->
                val index = anchorIndex(trimmed, anchor) ?: return@any false
                val below = trimmed.substring(index + anchor.length + 1).trimStart('/')
                below.isEmpty() || !below.contains('/')
            }
        }

        private fun anchorIndex(path: String, anchor: String): Int? {
            val marker = "/$anchor"
            return path.indexOf("$marker/").takeIf { it >= 0 }
                ?: path.takeIf { it.endsWith(marker) }?.let { it.length - marker.length }
        }
    }

    private val fileAccess = FalSigilFileAccess(fal)

    /**
     * Whether every routed save travels as a Sigil unit: on a RomM 5.5+ server, and while
     * disconnected from a server last seen at 5.5+. Older servers, and a server never reached,
     * keep the legacy formats for every save [route] does not route.
     */
    fun unitsEnabled(): Boolean = connectionManager.get().snapshotsEnabled()

    fun routes(emulatorId: String, platformSlug: String): Boolean =
        unitsEnabled() && layoutFor(emulatorId, platformSlug) != null

    /**
     * The game's Sigil route for what travels to RomM. While [unitsEnabled] is false it routes only
     * a game whose saves sit on a container every game shares and on none of its own files, as
     * [SigilSaveLocation.onSharedContainer] reads the root now.
     */
    suspend fun route(gameId: Long, emulatorId: String? = null): SigilRoute? = withContext(Dispatchers.IO) {
        val resolved = resolveRoute(gameId, emulatorId, allowGeneric = false) ?: return@withContext null
        resolved.takeIf { unitsEnabled() || savesOnSharedContainer(resolved, gameId) }
    }

    private suspend fun savesOnSharedContainer(route: SigilRoute, gameId: Long): Boolean {
        val location = locate(route, gameId) ?: return false
        if (location.onSharedContainer) {
            Logger.debug(TAG, "[SaveSync] SIGIL | game $gameId keeps its saves on shared ${location.shared} under ${route.layout}, routing it as a unit")
        }
        return location.onSharedContainer
    }

    private suspend fun locate(route: SigilRoute, gameId: Long): SigilSaveLocation? {
        if (!fal.isDirectory(route.root)) return null
        val game = gameDao.getById(gameId) ?: return null
        fal.prepareSaveAccess(*accessPaths(route))
        return saveLocator.locate(route, identity(game, titleIdCandidates(game)), contentName(game))
    }

    /**
     * The save root of [gameId]'s libretro route for [emulatorId] when the game's saves sit only
     * on containers every game shares there, on any server.
     */
    suspend fun sharedLibretroRoot(gameId: Long, emulatorId: String?): String? = withContext(Dispatchers.IO) {
        val route = resolveRoute(gameId, emulatorId, allowGeneric = false)?.takeIf { it.libretro }
            ?: return@withContext null
        route.root.takeIf { locate(route, gameId)?.onSharedContainer == true }
    }

    /**
     * The files on this device that [gameId]'s saves sit in under its local route for
     * [emulatorId]: the shared cards or volumes and the game's own files present there.
     */
    suspend fun liveSaveFiles(gameId: Long, emulatorId: String?): List<File> = withContext(Dispatchers.IO) {
        val route = localRoute(gameId, emulatorId) ?: return@withContext emptyList()
        val location = locate(route, gameId) ?: return@withContext emptyList()
        (location.shared + location.own).map { File(route.root, it) }
    }

    /**
     * The game's Sigil route whatever server Argosy last reached, for placing saves on this
     * device. [route] is the route for what travels to RomM. Only this route falls back to
     * [GENERIC_LIBRETRO_LAYOUT] for a libretro core with no layout of its own.
     */
    suspend fun localRoute(gameId: Long, emulatorId: String? = null): SigilRoute? =
        resolveRoute(gameId, emulatorId, allowGeneric = true)

    private suspend fun resolveRoute(
        gameId: Long,
        emulatorId: String?,
        allowGeneric: Boolean
    ): SigilRoute? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
        val effectiveId = emulatorId?.takeIf { it.isNotBlank() && it != "default" }
            ?: emulatorPackage?.let { emulatorResolver.resolveEmulatorId(it) }
            ?: emulatorResolver.getPreferredEmulatorId(game.platformSlug)
            ?: return@withContext null
        if (effectiveId in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) {
            return@withContext libretroRoute(game, effectiveId, allowGeneric)
        }
        val config = SavePathRegistry.getConfigForPlatform(effectiveId, game.platformSlug)
            ?: emulatorPackage?.let { SavePathRegistry.getConfigForPlatformByPackage(it, game.platformSlug) }
            ?: return@withContext null
        val layout = layoutFor(config.emulatorId, game.platformSlug) ?: return@withContext null
        val found = rootFor(game, config, layout, emulatorPackage) ?: return@withContext null
        if (layout == FLYCAST_STANDALONE_LAYOUT) return@withContext flycastStandaloneRoute(found, emulatorPackage)
        SigilRoute(
            layout = layout,
            root = found,
            emulatorPackage = emulatorPackage,
            profile = profileFor(layout, emulatorPackage, found),
            options = optionsFor(layout, game.id, config.emulatorId)
        )
    }

    private fun flycastStandaloneRoute(dataDir: String, emulatorPackage: String?): SigilRoute? {
        val settings = FlycastStandaloneConfig.read(dataDir, fal)
        val vmuDir = settings.vmuDir ?: run {
            Logger.warn(TAG, "[SaveSync] SIGIL | flycast VMU folder has no file path, not routing")
            return null
        }
        return SigilRoute(
            layout = FLYCAST_STANDALONE_LAYOUT,
            root = vmuDir,
            emulatorPackage = emulatorPackage,
            profile = null,
            options = settings.sigilOptions
        )
    }

    private suspend fun libretroRoute(game: GameEntity, emulatorId: String, allowGeneric: Boolean): SigilRoute? {
        val retroArch = emulatorId in PlatformSaveHandlerRegistry.RETROARCH_EMULATOR_IDS
        val core = saveUnitResolver.layoutFor(game, emulatorId, null) ?: return null
        val specific = libretroLayoutFor(game.platformSlug, core, retroArch)
        val layout = specific
            ?: genericLibretroLayoutFor(game.platformSlug, core)?.takeIf { allowGeneric }
            ?: return null
        val options = if (retroArch || specific == null) {
            emptyMap()
        } else {
            saveUnitResolver.optionsFor(core, game.id.takeIf { game.perGameSettingsEnabled })
        }
        val root = if (retroArch) retroArchRoot(game, emulatorId, core, layout) else builtinRoot(game, layout, options)
        if (root == null) {
            Logger.warn(TAG, "[SaveSync] SIGIL | no root for layout=$layout emulator=$emulatorId")
            return null
        }
        return SigilRoute(
            layout = layout,
            root = root,
            emulatorPackage = null,
            profile = null,
            options = options,
            libretro = true
        )
    }

    private suspend fun builtinRoot(game: GameEntity, layout: String, options: Map<String, String>): String {
        val sharedVmus = layout == FLYCAST_LAYOUT &&
            options[FLYCAST_PER_CONTENT_VMUS]?.let { it !in FLYCAST_PER_GAME_VALUES } == true
        return when {
            sharedVmus -> File(AppPaths.libretroSystemDir(context.filesDir), FLYCAST_SHARED_DIR).absolutePath
            layout == PCSX2_LAYOUT -> pcsx2MemcardRoot(AppPaths.libretroSystemDir(context.filesDir))
            else -> withUserDir(builtinSaveBase.forGame(game), layout)
        }
    }

    private suspend fun retroArchRoot(game: GameEntity, emulatorId: String, core: String, layout: String): String? {
        val dirs = retroArchPathResolver.resolveSaveDirectories(
            RetroArchPathResolver.Request(emulatorId = emulatorId, coreName = core, romPath = game.localPath)
        ).map { withUserDir(it, layout) }
        return dirs.firstOrNull { fal.isDirectory(it) } ?: dirs.firstOrNull()
    }

    private fun withUserDir(saveDir: String, layout: String): String =
        LIBRETRO_USER_DIRS[layout]?.let { File(saveDir, it).absolutePath } ?: saveDir

    private suspend fun optionsFor(layout: String, gameId: Long, configEmulatorId: String): Map<String, String> {
        if (layout != PCSX2_STANDALONE_LAYOUT) return emptyMap()
        val card = emulatorConfigDao.getSelectedMemcardForGame(gameId)
            ?: emulatorSaveConfigRepository.getByEmulator(configEmulatorId)?.selectedMemcardPath
        val name = card?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return mapOf(PCSX2_SLOT1_OPTION to name)
    }

    private suspend fun rootFor(game: GameEntity, config: SavePathConfig, layout: String, pkg: String?): String? {
        val registered = SavePathRegistry.resolvePathWithPackage(
            config, pkg, context.filesDir.absolutePath, fal.externalStorageRoots()
        )
        val chosen = emulatorSaveConfigRepository.resolveUserSavePath(config.emulatorId, game.platformSlug)
            ?.takeIf { it.isNotBlank() }
            ?: PpssppMemoryStick.liveSaveData(config, registered, fal)
            ?: emulatorSaveConfigRepository.resolveEvaluatedSavePath(config.emulatorId)
        val candidates = listOfNotNull(chosen) + registered
        val roots = candidates.mapNotNull {
            if (layout == FLYCAST_STANDALONE_LAYOUT) it.trimEnd('/') else rootFor(it, layout)
        }.distinct()
        if (roots.isEmpty()) {
            Logger.warn(TAG, "[SaveSync] SIGIL | no root for layout=$layout in ${candidates.firstOrNull()}")
        }
        return roots.firstOrNull { fal.isDirectory(it) } ?: roots.firstOrNull()
    }

    private fun accessPaths(route: SigilRoute): Array<String> = saveLocator.accessPaths(route).toTypedArray()

    private fun profileFor(layout: String, pkg: String?, root: String): String? =
        if (layout in PROFILE_FROM_EMULATOR_LAYOUTS && pkg != null) {
            runCatching { switchProfileParser.parseActiveProfile(pkg, root) }.getOrNull()
        } else null

    /**
     * The game's unit as it is on disk now. [claimNewSaves] is for a caller that runs while or
     * right after this game played: shared-volume saves no game owned that were not there at the
     * previous collect are given to this game. On the first collect of a volume nothing is claimed.
     */
    suspend fun collect(
        gameId: Long,
        emulatorId: String? = null,
        claimNewSaves: Boolean = false
    ): SigilCollect = withContext(Dispatchers.IO) {
        val route = route(gameId, emulatorId) ?: return@withContext SigilCollect.NotRouted
        val game = gameDao.getById(gameId) ?: return@withContext SigilCollect.NotRouted
        collect(route, game, claimNewSaves)
    }

    private suspend fun collect(route: SigilRoute, game: GameEntity, claimNewSaves: Boolean): SigilCollect {
        if (!fal.isDirectory(route.root)) return SigilCollect.Absent
        fal.prepareSaveAccess(*accessPaths(route))
        val ids = titleIdCandidates(game)
        val stateKey = stateKey(route, game)
        val stored = loadState(stateKey)
        val sigilGame = identity(game, ids)
        return try {
            fun run(claimed: List<String>) = Sigil.collect(
                game = sigilGame,
                core = route.layout,
                contentPath = contentName(game),
                saveRoot = route.root,
                options = route.options,
                gameIds = ids,
                state = committedState(stored),
                unmanaged = true,
                claimed = claimed,
                profile = route.profile,
                fileAccess = fileAccess
            )
            val first = run(emptyList())
            val fresh = if (claimNewSaves) newlyUnowned(stored?.unowned, first.unowned) else emptyList()
            val result = if (fresh.isEmpty()) first else run(fresh)
            if (fresh.isNotEmpty()) {
                Logger.info(TAG, "[SaveSync] SIGIL | claimed ${fresh.size} new shared-volume saves for game ${game.id} | $fresh")
            }
            if (result.restoreAgain) {
                Logger.warn(TAG, "[SaveSync] SIGIL | the emulator overwrote the last restore for game ${game.id}")
            }
            val collected = row(stateKey, result.state, result.unowned)
            sigilSyncStateDao.upsert(collectRow(stored, collected, claimed = fresh.isNotEmpty()))
            val data = result.data
            if (data == null || data.isEmpty()) {
                SigilCollect.Absent
            } else {
                SigilCollect.Found(
                    data, result.artifact, result.contentHash, result.identityHash, unitShape(result.shape, data),
                    root = route.root,
                    pending = SigilPendingState(collected)
                )
            }
        } catch (e: SigilException) {
            when (e.code) {
                SigilException.NOT_FOUND -> SigilCollect.Absent
                else -> SigilCollect.Unreadable(describe(e))
            }
        }
    }

    suspend fun restore(
        gameId: Long,
        unitFile: File,
        emulatorId: String? = null
    ): SigilRestore = withContext(Dispatchers.IO) {
        val route = route(gameId, emulatorId) ?: return@withContext SigilRestore.NotRouted
        restoreInto(route, gameId, unitFile.readBytes())
    }

    /**
     * Restores [unit] under [route], overwriting what is there, and stores the sync state the
     * restore produced for that root. Only this game's entries of a shared container change.
     */
    suspend fun restoreInto(route: SigilRoute, gameId: Long, unit: ByteArray): SigilRestore = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext SigilRestore.NotRouted
        if (route.layout in CLOSED_EMULATOR_LAYOUTS && !emulatorIsClosed(route.emulatorPackage)) {
            return@withContext SigilRestore.Refused("the emulator must be closed before its saves are restored")
        }
        fal.prepareSaveAccess(*accessPaths(route))
        try {
            restore(route, game, unit)
        } catch (e: SigilException) {
            SigilRestore.Refused(describe(e), e.code)
        }
    }

    suspend fun probe(route: SigilRoute, gameId: Long): SigilProbe = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext SigilProbe.Absent
        if (!fal.isDirectory(route.root)) return@withContext SigilProbe.Absent
        fal.prepareSaveAccess(*accessPaths(route))
        val ids = titleIdCandidates(game)
        val anchor = committedState(loadState(stateKey(route, game)))
        try {
            val result = Sigil.collect(
                game = identity(game, ids),
                core = route.layout,
                contentPath = contentName(game),
                saveRoot = route.root,
                options = route.options,
                gameIds = ids,
                state = anchor,
                unmanaged = true,
                profile = route.profile,
                fileAccess = fileAccess
            )
            val data = result.data
            if (data == null || data.isEmpty()) {
                SigilProbe.Absent
            } else {
                SigilProbe.Found(
                    SigilCollect.Found(
                        data, result.artifact, result.contentHash, result.identityHash, unitShape(result.shape, data),
                        root = route.root
                    ),
                    anchored = anchor != null,
                    changed = result.changed
                )
            }
        } catch (e: SigilException) {
            if (e.code == SigilException.NOT_FOUND) SigilProbe.Absent else SigilProbe.Unreadable(describe(e))
        }
    }

    /**
     * Turns a save the emulator of [route] wrote, given as [files] by root-relative path, into its
     * Sigil unit. The files are laid out under a scratch root, PCSX2 save folders on a folder card
     * collected under the standalone PS2 layout for either PS2 route, and any save at the save root
     * itself or under one of the layout's subfolders, and collected there with no sync state;
     * nothing is stored and the scratch root is deleted.
     */
    suspend fun convertNative(route: SigilRoute, gameId: Long, files: Map<String, ByteArray>): SigilCollect =
        withContext(Dispatchers.IO) {
            val game = gameDao.getById(gameId) ?: return@withContext SigilCollect.NotRouted
            if (files.isEmpty()) return@withContext SigilCollect.Absent
            val ids = titleIdCandidates(game)
            val sigilGame = identity(game, ids)
            var refusal: String? = null
            val arrangements = nativeArrangements(route.layout, Sigil.layoutSubdirs(route.layout), route.options, files)
            for (arrangement in arrangements) {
                val scratch = File(context.cacheDir, "sigil_convert_${System.nanoTime()}")
                try {
                    if (!layOut(scratch, arrangement.files)) return@withContext SigilCollect.Unreadable("a save file name leaves the save root")
                    val result = Sigil.collect(
                        game = sigilGame,
                        core = arrangement.layout,
                        contentPath = contentName(game),
                        saveRoot = scratch.absolutePath,
                        options = arrangement.options,
                        gameIds = ids,
                        state = null,
                        unmanaged = true,
                        profile = route.profile,
                        fileAccess = PosixFileAccess
                    )
                    val data = result.data
                    if (data != null && data.isNotEmpty()) {
                        return@withContext SigilCollect.Found(
                            data, result.artifact, result.contentHash, result.identityHash, unitShape(result.shape, data)
                        )
                    }
                } catch (e: SigilException) {
                    if (e.code != SigilException.NOT_FOUND) refusal = describe(e)
                } finally {
                    scratch.deleteRecursively()
                }
            }
            refusal?.let { SigilCollect.Unreadable(it) } ?: SigilCollect.Absent
        }

    private fun layOut(scratch: File, files: Map<String, ByteArray>): Boolean {
        val base = scratch.canonicalFile
        for ((path, bytes) in files) {
            val target = File(base, path).canonicalFile
            if (!target.path.startsWith(base.path + File.separator)) return false
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        return true
    }

    private suspend fun restore(route: SigilRoute, game: GameEntity, unit: ByteArray): SigilRestore {
        val ids = titleIdCandidates(game)
        val stateKey = stateKey(route, game)
        val result = Sigil.restore(
            unit = unit,
            game = identity(game, ids),
            core = route.layout,
            contentPath = contentName(game),
            saveRoot = route.root,
            options = route.options,
            gameIds = ids,
            state = committedState(loadState(stateKey)),
            unmanaged = true,
            overwriteLocal = true,
            profile = route.profile,
            fileAccess = fileAccess
        )
        if (!fal.commitSaveAccess(*accessPaths(route))) {
            return SigilRestore.Refused("the restored files did not reach ${route.root}")
        }
        sigilSyncStateDao.upsert(row(stateKey, result.state, result.unowned))
        return SigilRestore.Restored(result.hardcoreMarker)
    }

    /**
     * Stores the Sigil sync state a collect produced. Called once the collected unit reached RomM,
     * either pushed or found already there.
     */
    suspend fun commit(pending: SigilPendingState): Unit = withContext(Dispatchers.IO) {
        val key = pending.row
        val stored = sigilSyncStateDao.get(key.ownerUserId, key.platformSlug, key.layout, key.root)
        sigilSyncStateDao.upsert(commitRow(stored, pending.row, System.currentTimeMillis()))
    }

    private data class StateKey(val ownerUserId: Long, val platformSlug: String, val layout: String, val root: String)

    private suspend fun stateKey(route: SigilRoute, game: GameEntity): StateKey =
        StateKey(
            ownerUserId = syncPreferencesRepository.getRommUserId() ?: SigilSyncStateEntity.NO_OWNER,
            platformSlug = Sigil.platformSlug(game.platformSlug),
            layout = route.layout,
            root = route.root
        )

    private suspend fun loadState(key: StateKey): SigilSyncStateEntity? =
        sigilSyncStateDao.get(key.ownerUserId, key.platformSlug, key.layout, key.root)

    private fun row(key: StateKey, state: ByteArray, unowned: List<String>): SigilSyncStateEntity =
        SigilSyncStateEntity(
            ownerUserId = key.ownerUserId,
            platformSlug = key.platformSlug,
            layout = key.layout,
            root = key.root,
            state = state,
            unowned = unowned.joinToString("\n"),
            updatedAt = System.currentTimeMillis()
        )

    private fun emulatorIsClosed(pkg: String?): Boolean {
        if (pkg == null || !RootShell.isAvailable) return false
        val pid = RootShell.execute("pidof ${RootShell.quote(pkg)}").getOrNull()?.trim()
        return pid.isNullOrEmpty()
    }

    private suspend fun titleIdCandidates(game: GameEntity): List<String> =
        if (game.titleId.isNullOrBlank() && PlatformDefinitions.getCanonicalSlug(game.platformSlug) == "switch") {
            titleDbRepository.getCachedCandidates(game.id)
        } else emptyList()

    private suspend fun identity(game: GameEntity, candidates: List<String>): SigilResult =
        SigilResult.persisted(
            game.platformSlug,
            game.titleId?.takeIf { it.isNotBlank() } ?: candidates.firstOrNull().orEmpty(),
            game.saveId ?: "",
            cartFeatureScanner.featuresFor(game)
        )

    private fun contentName(game: GameEntity): String =
        game.localPath?.let { File(it).name } ?: game.title

    private fun describe(e: SigilException): String =
        listOf(e.message, e.problem.takeIf { it.isNotEmpty() }).filterNotNull().joinToString(": ")

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? {
        val collected = collect(context.gameId, context.emulatorId) as? SigilCollect.Found ?: return null
        val dir = File(this.context.cacheDir, "sigil_upload_${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, collected.artifact)
        file.writeBytes(collected.data)
        return PreparedSave(file, isTemporary = true, originalPaths = listOf(localPath), sigilState = collected.pending)
    }

    /**
     * The game's own save files under its route, never a container other games share and never
     * the root itself. Empty when the game owns nothing there or Sigil cannot list the root.
     */
    override suspend fun sourcePathsFor(localPath: String, context: SaveContext): List<String> =
        withContext(Dispatchers.IO) {
            val route = route(context.gameId, context.emulatorId) ?: return@withContext emptyList()
            val location = locate(route, context.gameId) ?: return@withContext emptyList()
            location.own.map { File(route.root, it).path }
        }

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        when (val restored = restore(context.gameId, tempFile, context.emulatorId)) {
            is SigilRestore.Restored -> ExtractResult(true, route(context.gameId, context.emulatorId)?.root)
            is SigilRestore.Refused -> ExtractResult(false, null, restored.reason)
            SigilRestore.NotRouted -> ExtractResult(false, null, "no Sigil layout for ${context.emulatorId}")
        }
}
