package com.nendo.argosy.ui.screens.library

import androidx.annotation.PluralsRes
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.MediaCollectionType
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName

private const val NAME_MAX_LENGTH = 16

/**
 * What a cell of the library landing opens.
 *
 * The landing answers "which of my collections do I want", and a media library is one of those, so
 * the three destinations sit in one list rather than behind separate entry points. A sealed target
 * rather than a nullable id per kind: a cell has exactly one destination, and spelling that out is
 * what lets confirm route without inspecting three fields to work out which one is set.
 */
sealed interface LibraryCellTarget {
    data object AllGames : LibraryCellTarget
    data class Platform(val platformId: Long) : LibraryCellTarget
    data class Media(val libraryId: String) : LibraryCellTarget
}

/**
 * What a media library holds. [itemCountRes] names the plural that counts one of its titles, so a
 * media cell's small print is built the same way a platform's is; the noun itself is resolved at the
 * render site because only there is a configuration to resolve it against.
 */
enum class MediaCellKind(@PluralsRes val itemCountRes: Int) {
    MOVIES(R.plurals.library_cell_movie_count),
    SHOWS(R.plurals.library_cell_show_count)
}

/**
 * One cell of the library's landing: a platform, a media library, or the All Games shortcut.
 *
 * [mediaKind] is what separates the two families at render time - it is null for everything that
 * holds games - and it is also the one thing a media cell knows that a platform does not, since a
 * library has no slug and no bundled mark.
 */
data class LibraryCellUi(
    val target: LibraryCellTarget,
    val name: String,
    val slug: String,
    val slugs: List<String>,
    val itemCount: Int,
    val logoPath: String?,
    val mediaKind: MediaCellKind? = null
) {
    val key: String
        get() = when (target) {
            LibraryCellTarget.AllGames -> "all-games"
            is LibraryCellTarget.Platform -> "platform-${target.platformId}"
            is LibraryCellTarget.Media -> "media-${target.libraryId}"
        }

    val isAllGames: Boolean get() = target == LibraryCellTarget.AllGames

    val isPlatform: Boolean get() = target is LibraryCellTarget.Platform

    val isMedia: Boolean get() = target is LibraryCellTarget.Media

    /**
     * The one line of small print under the name: what this row is called on disk, and how much is
     * in it. Both halves are single-line by construction, so every cell in a row ends at the same
     * baseline no matter how much the platform has to say for itself.
     *
     * A media library has no on-disk name to print, so it prints only the count - which is already
     * how a platform whose slug merely respells its own name reads, and is why the two families make
     * one grid rather than two.
     *
     * [countText] is the already-counted phrase, resolved from [itemCountRes] at the render site.
     */
    fun metaLine(countText: String): String = (slugs + countText).joinToString(" · ")

    /**
     * The plural that counts what this cell holds: its media kind's titles, or games for everything
     * else.
     */
    @get:PluralsRes
    val itemCountRes: Int
        get() = mediaKind?.itemCountRes ?: R.plurals.library_cell_game_count
}

/**
 * The slugs this row answers to - its own, not its family's.
 *
 * `PlatformDefinitions` reverse-maps many alternate slugs onto one canonical slug, so asking the
 * registry what a platform covers returns the whole family: two rows that both resolve to `snes`
 * would print the same alias list and claim each other's games. A row owns exactly the slug it was
 * synced under plus the `fs_slug` an arcade row was split out by, so that is what it shows. A slug
 * that merely respells the platform's own name or short name is dropped, since printing "nes" under
 * "NES" costs a line and tells nobody anything.
 */
fun platformSlugCoverage(slug: String, fsSlug: String?, name: String, shortName: String): List<String> {
    val alreadyShown = setOf(normalizeSlug(name), normalizeSlug(shortName))
    val seen = mutableSetOf<String>()
    return listOfNotNull(slug, fsSlug).mapNotNull { candidate ->
        val normalized = normalizeSlug(candidate)
        when {
            normalized.isEmpty() -> null
            normalized in alreadyShown -> null
            !seen.add(normalized) -> null
            else -> candidate.lowercase()
        }
    }
}

private fun normalizeSlug(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

/**
 * A cell names its platform in one line. Past [NAME_MAX_LENGTH] the entity's short name takes over,
 * so "Super Nintendo Entertainment System" arrives as "SNES" and reads at a glance instead of
 * ellipsing into a guess.
 */
fun PlatformEntity.toLibraryCellUi(gameCount: Int): LibraryCellUi =
    LibraryCellUi(
        target = LibraryCellTarget.Platform(id),
        name = getDisplayName(maxLength = NAME_MAX_LENGTH),
        slug = slug,
        slugs = platformSlugCoverage(slug, fsSlug, name, shortName),
        itemCount = gameCount,
        logoPath = logoPath
    )

/**
 * A media library as a cell, or null for one whose collection type this build cannot place.
 *
 * An unplaceable type is not a cell that could be repaired into a working one: the item type a
 * library is browsed by comes from its collection type, so a cell built without it would open a
 * screen that can only ever be empty. The sync already refuses to store such a row, so this only
 * ever sees one written by an older build.
 *
 * A library carries one name and no short form, so a long one ellipses rather than falling back the
 * way a platform's does. There is nothing to fall back to.
 */
fun MediaLibraryEntity.toLibraryCellUi(itemCount: Int): LibraryCellUi? {
    val kind = when (MediaCollectionType.fromWire(collectionType)) {
        MediaCollectionType.MOVIES -> MediaCellKind.MOVIES
        MediaCollectionType.TV_SHOWS -> MediaCellKind.SHOWS
        null -> return null
    }
    return LibraryCellUi(
        target = LibraryCellTarget.Media(libraryId),
        name = name,
        slug = "",
        slugs = emptyList(),
        itemCount = itemCount,
        logoPath = null,
        mediaKind = kind
    )
}

/**
 * The shortcut cell that opens the whole library unfiltered. It carries no [LibraryCellUi.name]:
 * unlike a platform, whose name is data, this one is interface text and is resolved at the render
 * site, which branches on [LibraryCellUi.isAllGames] for its mark already.
 */
fun allGamesCell(gameCount: Int): LibraryCellUi = LibraryCellUi(
    target = LibraryCellTarget.AllGames,
    name = "",
    slug = "",
    slugs = emptyList(),
    itemCount = gameCount,
    logoPath = null
)
