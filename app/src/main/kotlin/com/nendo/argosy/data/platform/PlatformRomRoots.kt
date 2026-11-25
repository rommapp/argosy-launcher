package com.nendo.argosy.data.platform

import com.nendo.argosy.data.local.entity.PlatformEntity
import java.io.File

/**
 * The folder [platform]'s roms download into: its custom folder, else the shared folder named by
 * [platformFolderName].
 */
fun platformDownloadDir(
    platform: PlatformEntity?,
    fallbackSlug: String,
    storageBase: File,
    allPlatforms: List<PlatformEntity>,
    useFsSlug: Boolean
): File = platform?.customRomPath?.let { File(it) }
    ?: File(storageBase, platform?.let { platformFolderName(it, allPlatforms, useFsSlug) } ?: fallbackSlug)

/**
 * The shared folder name for [platform]: its RomM `fs_slug` when [useFsSlug] is on and no other
 * platform owns that name, else its slug.
 */
fun platformFolderName(platform: PlatformEntity, allPlatforms: List<PlatformEntity>, useFsSlug: Boolean): String =
    if (useFsSlug) ownFsSlug(platform, allPlatforms) ?: platform.slug else platform.slug

private fun ownFsSlug(platform: PlatformEntity, allPlatforms: List<PlatformEntity>): String? {
    val fsSlug = platform.fsSlug?.takeIf { it.isNotBlank() && it != platform.slug } ?: return null
    val claimedByAnother = allPlatforms.any { other ->
        other.id != platform.id && (other.slug == fsSlug || other.fsSlug == fsSlug)
    }
    return fsSlug.takeUnless { claimedByAnother }
}

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
 * The shared folder [platformFolderName] picks comes before the other one.
 */
fun platformRomRoots(
    platform: PlatformEntity,
    storageBase: File,
    allPlatforms: List<PlatformEntity>,
    useFsSlug: Boolean = false
): List<File> {
    val roots = mutableListOf<File>()
    platform.customRomPath?.let { roots += File(it) }
    val shared = listOfNotNull(platform.slug, ownFsSlug(platform, allPlatforms)).map { File(storageBase, it) }
    roots += if (useFsSlug) shared.reversed() else shared
    return roots.distinctBy { it.absolutePath }
}
