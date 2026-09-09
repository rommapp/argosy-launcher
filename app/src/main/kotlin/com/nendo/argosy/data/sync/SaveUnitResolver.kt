package com.nendo.argosy.data.sync

import com.nendo.argosy.data.emulator.BuiltinCoreResolver
import com.nendo.argosy.data.emulator.CartFeatureScanner
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.libretro.coreoptions.CoreOptionResolver
import com.nendo.argosy.util.Logger
import com.nendo.sigil.Sigil
import com.nendo.sigil.SigilResult
import com.nendo.sigil.SigilSaveMember
import com.nendo.sigil.SigilSaveUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A resolved save unit anchored at the save root it was listed from. Member paths are
 * relative to [root]; [absolute] joins them.
 */
data class ResolvedSaveUnit(
    val root: String,
    val layout: String,
    val unit: SigilSaveUnit
) {
    val members: List<SigilSaveMember> get() = unit.members
    val isMulti: Boolean get() = unit.shape == SigilSaveUnit.Shape.Multi
    val isEmpty: Boolean get() = unit.members.isEmpty()
    val primary: SigilSaveMember? get() = unit.members.firstOrNull()
    val primaryPath: String? get() = primary?.let { absolute(it) }
    val memberPaths: List<String> get() = unit.members.map { absolute(it) }

    fun absolute(member: SigilSaveMember): String = "$root/${member.path}"

    fun memberForEntry(entry: String): SigilSaveMember? = unit.members.firstOrNull { it.entry == entry }
}

/**
 * Argosy's half of Sigil's save-unit contract: the save root, the listing under it, the
 * core options in force, and the cart features already persisted on the game. Sigil does
 * the naming, membership and hashing.
 */
@Singleton
class SaveUnitResolver @Inject constructor(
    private val fal: FileAccessLayer,
    private val coreOptionResolver: CoreOptionResolver,
    private val cartFeatureScanner: CartFeatureScanner,
    private val builtinCoreResolver: BuiltinCoreResolver,
    private val emulatorConfigDao: EmulatorConfigDao
) {
    companion object {
        private const val TAG = "SaveUnitResolver"
        private const val SUBDIR_LIST_DEPTH = 3
        private const val ROOT_ASCENT = 3
    }

    suspend fun layoutFor(game: GameEntity, emulatorId: String, coreName: String?): String? {
        if (emulatorId !in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) return null
        coreName?.let { return it }
        if (emulatorId in PlatformSaveHandlerRegistry.RETROARCH_EMULATOR_IDS) {
            return emulatorConfigDao.getByGameId(game.id)?.coreName
                ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)?.coreName
        }
        return builtinCoreResolver.resolveCoreId(game.id, game.platformId, game.platformSlug)
    }

    suspend fun resolveForSavePath(
        savePath: String,
        game: GameEntity,
        emulatorId: String,
        coreName: String?,
        hash: Boolean
    ): ResolvedSaveUnit? {
        val layout = layoutFor(game, emulatorId, coreName) ?: return null
        val contentName = game.localPath?.let { File(it).name } ?: return null
        return resolveForMember(savePath, layout, game.platformSlug, contentName, game, hash)
    }

    suspend fun resolve(
        root: String,
        layout: String,
        platformSlug: String?,
        contentName: String,
        game: GameEntity?,
        hash: Boolean
    ): ResolvedSaveUnit? = withContext(Dispatchers.IO) {
        if (fal.exists(root) && !fal.isDirectory(root)) return@withContext null
        val listing = if (fal.exists(root)) listRoot(root, layout) else emptyList()
        val options = optionsFor(layout, game?.id)
        val identity = persisted(platformSlug, game, game?.let { cartFeatureScanner.featuresFor(it) } ?: 0)
        val unit = runCatching {
            val located = Sigil.locateSaves(identity, layout, contentName, listing = listing, options = options)
            if (hash) Sigil.hashSaves(located, fal.getTransformedFile(root).absolutePath) else located
        }.getOrElse { return@withContext null }
        Logger.debug(
            TAG,
            "[SaveSync] UNIT | root=$root layout=$layout content=$contentName shape=${unit.shape} " +
                "members=${unit.members.map { it.path }} expected=${unit.expected.map { it.path }} " +
                "unkeyed=${unit.unkeyed} hash=${unit.contentHash}"
        )
        ResolvedSaveUnit(root, layout, unit)
    }

    /**
     * Re-derives the unit a member path belongs to, walking up from the member's folder until
     * a root reproduces that path, so a caller holding only the primary path (the way every
     * sync row does) gets the whole set back.
     */
    suspend fun resolveForMember(
        memberPath: String,
        layout: String,
        platformSlug: String?,
        contentName: String,
        game: GameEntity?,
        hash: Boolean
    ): ResolvedSaveUnit? {
        var root = memberPath.substringBeforeLast('/', "")
        repeat(ROOT_ASCENT) {
            if (root.isEmpty()) return null
            val resolved = resolve(root, layout, platformSlug, contentName, game, hash)
            if (resolved != null && resolved.memberPaths.any { it == memberPath }) return resolved
            root = root.substringBeforeLast('/', "")
        }
        return null
    }

    fun placeEntries(
        entries: List<String>,
        layout: String,
        platformSlug: String?,
        contentName: String,
        game: GameEntity?,
        options: Map<String, String>
    ): Map<String, SigilSaveMember> {
        val subdirs = Sigil.layoutSubdirs(layout)
        val candidates = entries.flatMap { entry ->
            listOf(entry) + subdirs.map { "$it/$entry" }
        }
        val identity = persisted(platformSlug, game, game?.saveFeatures ?: 0)
        val unit = runCatching {
            Sigil.locateSaves(identity, layout, contentName, listing = candidates, options = options)
        }.getOrElse { return emptyMap() }
        return entries.mapNotNull { entry -> unit.members.firstOrNull { it.entry == entry }?.let { entry to it } }.toMap()
    }

    private fun persisted(platformSlug: String?, game: GameEntity?, features: Int): SigilResult =
        SigilResult.persisted(platformSlug ?: "", game?.titleId ?: "", game?.saveId ?: "", features)

    /**
     * Destinations for a bundle's entries under the root an anchor path sits in, or null when
     * an entry names no member of the layout, which is how a save that is itself a zip is told
     * apart from a bundle.
     */
    suspend fun placeBundle(
        entries: List<String>,
        anchorPath: String,
        layout: String,
        platformSlug: String?,
        contentName: String,
        game: GameEntity?
    ): Map<String, String>? {
        if (entries.isEmpty()) return null
        val placed = placeEntries(entries, layout, platformSlug, contentName, game, optionsFor(layout, game?.id))
        if (placed.size != entries.size) return null
        val root = rootForAnchor(anchorPath, placed.values.map { it.path })
        return placed.mapValues { (_, member) -> "$root/${member.path}" }
    }

    /**
     * The absolute path the layout's primary member takes under the root an anchor path sits
     * in, present or not, so a fresh download lands under the name the core will read.
     */
    suspend fun expectedPrimaryPath(
        anchorPath: String,
        layout: String,
        platformSlug: String?,
        contentName: String,
        game: GameEntity?
    ): String? {
        val root = anchorPath.substringBeforeLast('/', "")
        if (root.isEmpty()) return null
        val resolved = resolve(root, layout, platformSlug, contentName, game, hash = false)
            ?: return null
        val primary = resolved.unit.members.firstOrNull { it.role == SigilSaveMember.Role.Primary }
            ?: resolved.unit.expected.firstOrNull { it.role == SigilSaveMember.Role.Primary }
            ?: return null
        return "$root/${primary.path}"
    }

    fun rootForAnchor(anchor: String, memberPaths: List<String>): String {
        val parent = anchor.substringBeforeLast('/')
        val suffix = memberPaths.firstOrNull { anchor.endsWith("/$it") } ?: return parent
        return anchor.removeSuffix("/$suffix")
    }

    suspend fun optionsFor(layout: String, gameId: Long?): Map<String, String> =
        coreOptionResolver.resolveVariables(layout, gameId)
            .mapNotNull { v -> v.key?.let { k -> v.value?.let { k to it } } }
            .toMap()

    private fun listRoot(root: String, layout: String): List<String> {
        val out = ArrayList<String>()
        fal.listFiles(root)?.forEach { if (it.isFile) out.add(it.name) }
        Sigil.layoutSubdirs(layout).forEach { subdir ->
            listRecursive("$root/$subdir", subdir, SUBDIR_LIST_DEPTH, out)
        }
        return out
    }

    private fun listRecursive(dir: String, relative: String, depth: Int, out: MutableList<String>) {
        if (depth == 0 || !fal.exists(dir) || !fal.isDirectory(dir)) return
        fal.listFiles(dir)?.forEach { info ->
            val rel = "$relative/${info.name}"
            if (info.isFile) out.add(rel)
            else if (info.isDirectory) listRecursive("$dir/${info.name}", rel, depth - 1, out)
        }
    }
}
