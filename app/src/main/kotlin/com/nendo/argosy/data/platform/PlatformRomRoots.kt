package com.nendo.argosy.data.platform

import com.nendo.argosy.data.local.entity.PlatformEntity
import java.io.File

/**
 * Every directory a platform's roms may legitimately sit directly inside, most specific first.
 *
 * Discovery and launch must agree on this set. Discovery links a game to whatever it finds here,
 * and the launcher decides whether a rom's parent is a platform root or a game folder; if the
 * launcher knows fewer roots than discovery, a rom discovered in a root it does not recognise
 * gets treated as living in a game folder, and the folder logic then repoints the game at the
 * largest file in the whole platform directory.
 *
 * Resolution is read-only. Nothing here creates a directory, so asking the question cannot leave
 * empty folders behind for roots that do not exist.
 *
 * The `fs_slug` root is RomM's own on-disk layout, which is what a mirrored RomM or ES-DE library
 * looks like. It is dropped when another platform already owns that directory by slug or fs_slug,
 * so a platform whose `fs_slug` collides with a second platform's slug cannot reach into it.
 */
fun platformRomRoots(
    platform: PlatformEntity,
    storageBase: File,
    allPlatforms: List<PlatformEntity>
): List<File> {
    val roots = mutableListOf<File>()
    platform.customRomPath?.let { roots += File(it) }
    roots += File(storageBase, platform.slug)

    val fsSlug = platform.fsSlug?.takeIf { it.isNotBlank() && it != platform.slug }
    if (fsSlug != null) {
        val claimedByAnother = allPlatforms.any { other ->
            other.id != platform.id && (other.slug == fsSlug || other.fsSlug == fsSlug)
        }
        if (!claimedByAnother) roots += File(storageBase, fsSlug)
    }
    return roots.distinctBy { it.absolutePath }
}
