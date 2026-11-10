package com.nendo.argosy.data.sync.platform

import com.nendo.argosy.data.sync.FalSigilFileAccess
import com.nendo.sigil.Sigil
import com.nendo.sigil.SigilException
import com.nendo.sigil.SigilResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a game's saves sit under a route's root, as Sigil's layout names them without reading a
 * save. [shared] are the files every game shares that are there (a memory card, a backup RAM
 * volume, a folder card counted as its folder) and [own] the game's own files that are there.
 */
data class SigilSaveLocation(val shared: List<String>, val own: List<String>) {
    val onSharedContainer: Boolean get() = shared.isNotEmpty() && own.isEmpty()
}

@Singleton
class SigilSaveLocator @Inject constructor(private val fileAccess: FalSigilFileAccess) {

    /**
     * The folders under [route]'s root that its layout reads and writes, or the root itself for a
     * layout with no subfolders.
     */
    fun accessPaths(route: SigilRoute): List<String> =
        Sigil.layoutSubdirs(route.layout)
            .map { "${route.root.trimEnd('/')}/$it" }
            .ifEmpty { listOf(route.root) }

    /**
     * The location of [game]'s saves under [route], or null when Sigil cannot list the root. The
     * caller prepares save access on [accessPaths] first.
     */
    fun locate(route: SigilRoute, game: SigilResult, contentPath: String): SigilSaveLocation? = try {
        val base = Sigil.saveBase(route.layout, route.root).first
        val listing = withFolders(Sigil.listSaveRoot(base, route.layout, fileAccess))
        val unit = Sigil.locateSaves(
            game = game,
            core = route.layout,
            contentPath = contentPath,
            saveRoot = route.root,
            listing = listing,
            options = route.options,
            profile = route.profile,
            fileAccess = fileAccess
        )
        SigilSaveLocation(shared = unit.unkeyed, own = unit.members.map { it.path })
    } catch (e: SigilException) {
        null
    }

    companion object {
        /**
         * [listing] with every folder above a listed file added, so a shared file the layout names
         * that is a folder on disk, a PCSX2 folder card, is seen as listed.
         */
        fun withFolders(listing: List<String>): List<String> {
            val folders = listing.flatMap { path ->
                path.split('/').dropLast(1).runningReduce { above, segment -> "$above/$segment" }
            }
            return (listing + folders).distinct()
        }
    }
}
