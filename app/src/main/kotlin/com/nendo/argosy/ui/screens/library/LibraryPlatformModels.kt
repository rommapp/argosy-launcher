package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName

private const val NAME_MAX_LENGTH = 16

/**
 * One cell of the library's platform landing.
 *
 * A null [platformId] is the All Games cell, which is why it is nullable rather than a sentinel: the
 * landing has to offer the unfiltered library as a destination, and every other field means the same
 * thing for it as for a platform.
 */
data class LibraryPlatformCellUi(
    val platformId: Long?,
    val name: String,
    val slug: String,
    val slugs: List<String>,
    val gameCount: Int,
    val logoPath: String?
) {
    val key: String get() = platformId?.toString() ?: "all-games"

    val isAllGames: Boolean get() = platformId == null

    /**
     * The one line of small print under the name: what this row is called on disk, and how much is
     * in it. Both halves are single-line by construction, so every cell in a row ends at the same
     * baseline no matter how much the platform has to say for itself.
     */
    val metaLine: String
        get() {
            val count = if (gameCount == 1) "1 game" else "$gameCount games"
            return (slugs + count).joinToString(" · ")
        }
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
fun PlatformEntity.toLibraryPlatformCellUi(gameCount: Int): LibraryPlatformCellUi =
    LibraryPlatformCellUi(
        platformId = id,
        name = getDisplayName(maxLength = NAME_MAX_LENGTH),
        slug = slug,
        slugs = platformSlugCoverage(slug, fsSlug, name, shortName),
        gameCount = gameCount,
        logoPath = logoPath
    )

fun allGamesCell(gameCount: Int): LibraryPlatformCellUi = LibraryPlatformCellUi(
    platformId = null,
    name = "All Games",
    slug = "",
    slugs = emptyList(),
    gameCount = gameCount,
    logoPath = null
)
