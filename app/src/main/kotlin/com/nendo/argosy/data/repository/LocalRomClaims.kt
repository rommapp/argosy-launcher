package com.nendo.argosy.data.repository

data class ClaimCandidate(
    val name: String,
    val isDirectory: Boolean
)

data class ClaimTarget(
    val gameId: Long,
    val rommFileName: String?,
    val title: String
)

private val PLAYLIST_SUFFIXES = setOf("m3u", "m3u8")

private fun stemOf(name: String): String = name.substringBeforeLast('.')

/**
 * Directory names are compared verbatim because a title may legitimately contain a period
 * ("Mr. Do!"), so only a playlist suffix is stripped -- that suffix is a container marker
 * under the ES-DE folder convention, never part of the name.
 */
private fun folderNames(name: String): List<String> =
    if (name.substringAfterLast('.', "").lowercase() in PLAYLIST_SUFFIXES) {
        listOf(name, name.substringBeforeLast('.'))
    } else {
        listOf(name)
    }

private fun normalize(name: String): String = name
    .replace(Regex("[\\\\:*?\"<>|/]"), "_")
    .replace(Regex("\\s+"), " ")
    .lowercase()
    .trim()

private enum class Tier { EXACT_FILE, EXACT_FOLDER, STEM_FILE, STEM_FOLDER, TITLE_FILE, TITLE_FOLDER }

private fun matches(tier: Tier, target: ClaimTarget, candidate: ClaimCandidate): Boolean {
    val romm = target.rommFileName?.takeIf { it.isNotBlank() }
    return when (tier) {
        Tier.EXACT_FILE ->
            !candidate.isDirectory && romm != null && candidate.name.equals(romm, ignoreCase = true)
        Tier.EXACT_FOLDER ->
            candidate.isDirectory && romm != null &&
                folderNames(candidate.name).any { it.equals(romm, ignoreCase = true) }
        Tier.STEM_FILE ->
            !candidate.isDirectory && romm != null &&
                stemOf(candidate.name).equals(stemOf(romm), ignoreCase = true)
        Tier.STEM_FOLDER ->
            candidate.isDirectory && romm != null &&
                folderNames(candidate.name).any { it.equals(stemOf(romm), ignoreCase = true) }
        Tier.TITLE_FILE ->
            !candidate.isDirectory && romm == null &&
                normalize(stemOf(candidate.name)) == normalize(target.title)
        Tier.TITLE_FOLDER ->
            candidate.isDirectory &&
                folderNames(candidate.name).any { normalize(it) == normalize(target.title) }
    }
}

/**
 * Assigns on-disk entries to games, in tiers, across the whole platform at once.
 *
 * Tiers run globally rather than per game: a game whose `rommFileName` stem happens to equal
 * another game's folder name must not take that folder before its owner has made its own exact
 * claim. Within a tier a contested entry goes to nobody, because two games can legitimately
 * carry the same server filename in different subfolders of one platform, and a wrong claim
 * here is sticky - discovery only fills empty paths, so nothing later corrects it.
 *
 * Name-based only. Callers that can afford to read sizes or hashes may resolve what this
 * refuses, but refusing is the safe default.
 *
 * Candidates are identified by name and kind alone. A caller listing several directories must
 * collapse the same name across them to one candidate and keep the most specific directory's
 * copy, or every game whose rom exists in two of those directories claims nothing.
 */
fun claimLocalEntries(
    targets: List<ClaimTarget>,
    candidates: List<ClaimCandidate>
): Map<Long, ClaimCandidate> {
    val claims = mutableMapOf<Long, ClaimCandidate>()
    val taken = mutableSetOf<ClaimCandidate>()

    for (tier in Tier.entries) {
        val proposals = mutableMapOf<ClaimCandidate, MutableList<Long>>()
        for (target in targets) {
            if (claims.containsKey(target.gameId)) continue
            val hits = candidates.filter { it !in taken && matches(tier, target, it) }
            val only = hits.singleOrNull() ?: continue
            proposals.getOrPut(only) { mutableListOf() }.add(target.gameId)
        }
        for ((candidate, contenders) in proposals) {
            val winner = contenders.singleOrNull() ?: continue
            claims[winner] = candidate
            taken += candidate
        }
    }
    return claims
}
