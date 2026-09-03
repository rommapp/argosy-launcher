package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a resolved save path came from. Callers render this rather than inferring it, so a screen
 * can say whether a path is the user's choice or the packaged default without guessing.
 */
enum class SavePathSource {
    PER_GAME,
    USER_OVERRIDE,
    RETROARCH_CFG,
    EVALUATED_DEFAULT,
    REGISTRY_DEFAULT,
    NONE
}

/**
 * What the caller knows about the thing whose save path is wanted.
 *
 * [platformSlug] is required rather than nullable on purpose. Every save-path defect found in the
 * 2026-08-23 audit came from a call site that had the platform in scope and resolved without it,
 * so the type refuses to let a caller forget.
 */
data class SavePathRequest(
    val platformSlug: String,
    val emulatorId: String? = null,
    val emulatorPackage: String? = null,
    val perGameOverride: String? = null
)

/**
 * One answer about one save path, carrying the reasoning as well as the result.
 *
 * [basePath] is the directory the platform scans from, and [unresolvedShape] is what still gets
 * appended per game, so a screen can show the user where saves really land instead of a base they
 * will not find them in. For a flat platform the shape is null.
 *
 * [preferredPath] is the folder the registry would pick first if it could be read. When an
 * evaluated default settled on something else, the screen can say so rather than presenting the
 * fallback as the answer.
 */
data class SavePathResolution(
    val config: SavePathConfig?,
    val configId: String?,
    val basePath: String?,
    val unresolvedShape: String?,
    val source: SavePathSource,
    val verdict: SavePathVerdict,
    val preferredPath: String? = null
) {
    val isRetroArchManaged: Boolean get() = source == SavePathSource.RETROARCH_CFG
    val isEvaluatedDefault: Boolean get() = source == SavePathSource.EVALUATED_DEFAULT
    val isFallbackDefault: Boolean
        get() = isEvaluatedDefault && preferredPath != null && basePath != preferredPath
}

/**
 * Where the decision about which save path applies is being consolidated.
 *
 * IN PROGRESS, and the count matters to anyone reading this: the settings surfaces resolve through
 * here, the sync layer does not yet. Twenty-one derivations remain live across nine files, most of
 * them in `SavePathResolver`, `SaveDownloader`, `SaveUploader`, `SaveSyncConflictResolver`,
 * `AccountSwitchArtifactService`, `PlaySessionTracker`, `ResolveGameEmulatorContextUseCase` and
 * `EmulatorSettingsDelegate`. This class is not yet the authority its name claims. Do not read it
 * as finished work, and do not add a derivation on the strength of the name.
 *
 * The decision used to be made independently in roughly thirty call sites, disagreeing in four
 * ways that reached users: the config was looked up without the platform, the override was read
 * under one key and written under two others, discovery treated an override as exclusive while
 * construction fell back past it, and one restore path dropped the override entirely. Those are
 * not four bugs so much as one absent abstraction.
 *
 * Two kinds of identifier meet here and must not be confused. An emulator id names an installed
 * app; a save-config id names a save layout and may carry a platform, as `dolphin_wii` does. Only
 * the second is a valid override key, and only the first may be handed to sibling-family logic.
 */
@Singleton
open class SavePathAuthority @Inject constructor(
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val fal: FileAccessLayer
) {
    private val shapeRules: List<SavePathShapeRule> = listOf(
        WiiNandShapeRule(),
        GciShapeRule(),
        N3dsShapeRule(),
        FlatSaveShapeRule()
    )

    /**
     * The save layout for this request, always platform-aware. A multi-platform emulator resolves
     * to the layout of the platform asked about, never to whichever one happens to be listed first.
     *
     * Unsupported layouts answer null, matching `SavePathRegistry.getConfig`. An emulator whose
     * sync is deliberately parked must not be handed a save path to display, or the screen offers
     * a setting that does nothing.
     */
    fun configFor(request: SavePathRequest): SavePathConfig? =
        (
            request.emulatorPackage
                ?.let { SavePathRegistry.getConfigForPlatformByPackage(it, request.platformSlug) }
                ?: request.emulatorId?.let {
                    SavePathRegistry.getConfigForPlatform(it, request.platformSlug)
                }
            )?.takeIf { it.supported }

    /**
     * The one key an override is stored and read under. Every write must use this and no other,
     * or a value is saved somewhere nothing looks.
     *
     * Falls back to the emulator's own canonical id when no layout is registered, so a caller that
     * must name a key still gets a stable one rather than inventing its own. That fallback lives
     * here rather than at call sites, which is how three different derivations grew last time.
     */
    fun configIdFor(request: SavePathRequest): String? =
        configFor(request)?.emulatorId
            ?: request.emulatorId?.let {
                SavePathRegistry.canonicalConfigId(it, request.emulatorPackage)
            }

    /**
     * Whether the layout hands off to RetroArch's own configuration rather than to a packaged path.
     * Kept as a question the caller can ask so the cfg parser stays where it is.
     */
    fun isRetroArchManaged(config: SavePathConfig?): Boolean =
        config != null && RetroArchPathResolver.isRetroArch(config.emulatorId)

    suspend fun resolve(request: SavePathRequest): SavePathResolution {
        val config = configFor(request)
        val configId = config?.emulatorId

        if (config == null) {
            return SavePathResolution(
                config = null,
                configId = null,
                basePath = null,
                unresolvedShape = null,
                source = SavePathSource.NONE,
                verdict = SavePathVerdict.Ok
            )
        }

        if (isRetroArchManaged(config)) {
            return resolution(config, configId, null, SavePathSource.RETROARCH_CFG, request)
        }

        request.perGameOverride?.takeIf { it.isNotBlank() }?.let {
            return resolution(config, configId, it, SavePathSource.PER_GAME, request)
        }

        val override = configId?.let {
            emulatorSaveConfigRepository.resolveUserSavePath(it, request.platformSlug)
        }?.takeIf { it.isNotBlank() }
        if (override != null) {
            return resolution(config, configId, override, SavePathSource.USER_OVERRIDE, request)
        }

        val preferred = candidatePaths(config, request.emulatorPackage).firstOrNull()
        val evaluated = configId?.let { emulatorSaveConfigRepository.resolveEvaluatedSavePath(it) }
        if (evaluated != null) {
            return resolution(config, configId, evaluated, SavePathSource.EVALUATED_DEFAULT, request, preferred)
        }

        return resolution(config, configId, preferred, SavePathSource.REGISTRY_DEFAULT, request, preferred)
    }

    /**
     * Settles which packaged folder [request] uses when the user has chosen none, and remembers
     * the answer. Meant for session start, the one moment the emulator is known to be installed
     * and its folders are about to be written.
     *
     * The candidates are tried in registry order. The first one Argosy can read that already
     * holds saves wins, then the first readable one. When none can be seen nothing is written and
     * the next session looks again. Once written, the choice holds until the user resets it: a
     * folder that later becomes unreadable is reported as unreadable, never swapped for another.
     *
     * Only folder-based layouts on external storage take part. Internal layouts have one folder
     * and RetroArch's own configuration decides its layout.
     */
    suspend fun ensureEvaluatedDefault(request: SavePathRequest): String? {
        val config = configFor(request) ?: return null
        if (!config.usesFolderBasedSaves || config.usesInternalStorage || isRetroArchManaged(config)) return null
        val configId = config.emulatorId
        if (emulatorSaveConfigRepository.resolveUserSavePath(configId, request.platformSlug) != null) return null
        emulatorSaveConfigRepository.resolveEvaluatedSavePath(configId)?.let { return it }

        val candidates = candidatePaths(config, request.emulatorPackage)
        val chosen = candidates.firstOrNull { isReadable(it) && holdsEntries(it) }
            ?: candidates.firstOrNull { isReadable(it) }
            ?: return null
        emulatorSaveConfigRepository.setEvaluatedSavePath(configId, chosen)
        return chosen
    }

    protected open fun candidatePaths(config: SavePathConfig, emulatorPackage: String?): List<String> =
        SavePathRegistry.resolvePathWithPackage(
            config,
            emulatorPackage,
            externalStorageRoots = fal.externalStorageRoots()
        )

    /**
     * Readable means listable, not merely present. For a folder inside another app's
     * `Android/data` the test runs on the package root, which every installed app populates,
     * so a save folder that does not exist yet does not disqualify a location Argosy can see.
     */
    private fun isReadable(path: String): Boolean {
        val probe = packageDataRoot(path) ?: path
        return fal.exists(probe) && fal.isDirectory(probe) && fal.listFiles(probe) != null
    }

    private fun holdsEntries(path: String): Boolean =
        fal.listFiles(path)?.isNotEmpty() == true

    private fun packageDataRoot(path: String): String? =
        packageDataPattern.find(path)?.value

    /**
     * Whether a folder looks like somewhere this platform's saves live. Advisory only: a wrong
     * answer here must never stop the user, because the rules describe the common layout and a
     * legitimate setup can sit outside it.
     */
    fun validate(path: String, config: SavePathConfig?, platformSlug: String): SavePathVerdict {
        if (config == null || path.isBlank()) return SavePathVerdict.Ok
        val rule = shapeRules.firstOrNull { it.appliesTo(config, platformSlug) } ?: return SavePathVerdict.Ok
        return rule.validate(path, config, fal)
    }

    /**
     * What still gets appended below the base once a game is known. Null when the base is already
     * the answer.
     */
    /**
     * What still gets appended below [basePath] once a game is known, given how deep the base
     * already reaches. A Wii base that already names a title type has only the id and `data` left
     * below it, and claiming otherwise prints a path the user will not find.
     */
    private fun unresolvedShapeFor(
        config: SavePathConfig,
        platformSlug: String,
        basePath: String
    ): String? = when {
        config.usesGciFormat -> "<region>/Card A"
        PlatformDefinitions.getCanonicalSlug(platformSlug) == "wii" ->
            if (basePath.contains("/title/")) "<id>/data" else "title/<type>/<id>/data"
        config.usesFolderBasedSaves -> "<save folder>"
        else -> null
    }

    private fun resolution(
        config: SavePathConfig,
        configId: String?,
        basePath: String?,
        source: SavePathSource,
        request: SavePathRequest,
        preferredPath: String? = null
    ): SavePathResolution {
        val shape = basePath?.let { unresolvedShapeFor(config, request.platformSlug, it) }
        return SavePathResolution(
            config = config,
            configId = configId,
            basePath = basePath,
            unresolvedShape = shape,
            source = source,
            verdict = basePath?.let { validate(it, config, request.platformSlug) } ?: SavePathVerdict.Ok,
            preferredPath = preferredPath
        )
    }

    private companion object {
        val packageDataPattern = Regex(".*/Android/data/[^/]+")
    }
}
