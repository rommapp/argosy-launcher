package com.nendo.argosy.data.sync.platform

import com.nendo.argosy.data.emulator.SavePathConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for picking a [PlatformSaveHandler] from a (platformSlug, emulatorId,
 * config) triple. Replaces the four ad-hoc dispatch sites that used to live in
 * [com.nendo.argosy.data.repository.SaveSyncApiClient.getHandler],
 * [com.nendo.argosy.data.sync.SavePathResolver]'s `when (platformSlug)` blocks, and the duplicate
 * platform switches in `SavePathValidator` and `TitleIdDetector`.
 *
 * Adding a new folder-based platform = register one entry in [folderHandlers] (plus a slug
 * mapping in [slugAliases] if needed). Adding a new file-based platform = inject the handler
 * and add a branch to [getHandler].
 */
@Singleton
class PlatformSaveHandlerRegistry @Inject constructor(
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val retroArchSaveHandler: RetroArchSaveHandler,
    private val defaultSaveHandler: DefaultSaveHandler,
    private val pspSaveHandler: PspSaveHandler,
    private val vitaSaveHandler: VitaSaveHandler,
    private val wiiSaveHandler: WiiSaveHandler,
    private val wiiUSaveHandler: WiiUSaveHandler,
    private val n3dsSaveHandler: N3dsSaveHandler,
    private val ps2SaveHandler: PS2SaveHandler
) {

    private val folderHandlers: Map<String, PlatformSaveHandler> by lazy {
        mapOf(
            "vita" to vitaSaveHandler,
            "psp" to pspSaveHandler,
            "wii" to wiiSaveHandler,
            "wiiu" to wiiUSaveHandler,
            "3ds" to n3dsSaveHandler,
            "ps2" to ps2SaveHandler
        )
    }

    private val slugAliases: Map<String, String> = mapOf(
        "psvita" to "vita"
    )

    private fun canonicalSlug(platformSlug: String): String =
        slugAliases[platformSlug] ?: platformSlug

    /**
     * Resolve the handler for a save dispatch. Order matches the legacy `when` in
     * [com.nendo.argosy.data.repository.SaveSyncApiClient.getHandler]: RetroArch first by
     * emulator id, then GCI by config, then platform-keyed folder handlers, finally the
     * fallback file-based default.
     */
    fun getHandler(
        config: SavePathConfig?,
        platformSlug: String,
        emulatorId: String
    ): PlatformSaveHandler {
        if (emulatorId in RETROARCH_EMULATOR_IDS) return retroArchSaveHandler
        if (config?.usesGciFormat == true) return gciSaveHandler

        val canonical = canonicalSlug(platformSlug)
        if (canonical == "switch") return switchSaveHandler
        return folderHandlers[canonical] ?: defaultSaveHandler
    }

    /**
     * Folder handler for [platformSlug], or null when the platform isn't a per-title folder
     * layout. Used by [com.nendo.argosy.data.sync.SavePathResolver] for `findSaveFolderByTitleId`,
     * `resolveBasePath`, and `constructSavePath` dispatches.
     */
    fun getFolderHandler(platformSlug: String): PlatformSaveHandler? =
        folderHandlers[canonicalSlug(platformSlug)]

    companion object {
        private val RETROARCH_EMULATOR_IDS = setOf("retroarch", "retroarch_64")
    }
}
