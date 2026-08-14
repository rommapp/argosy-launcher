package com.nendo.argosy.data.remote.jellyfin

import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType

/**
 * Which season each episode of one series belongs to.
 *
 * An episode's own `SeasonId` names the directory its file sits in rather than the season it is part
 * of. Where a whole run shares one directory the server hands every episode that same id, so a series
 * arrives reporting all of itself as a single season - on the reference instance nine of ninety
 * series do, about 1,220 episodes between them. The season *number* on the episode stays correct in
 * every one of those cases, and the server raises a season container for each number it read, so the
 * two are matched on the number and never on the id.
 *
 * Numbers are looked up, not indexed: a series can report seasons 1, 2, 4, 5, 6 with no 3, can start
 * at 0 for specials, and can carry episodes with no number at all.
 *
 * The reported id remains the fallback. When a number has no container behind it there is nothing
 * here that can place the episode, and the id the server asserted at least names a real season of the
 * same series, which beats filing it nowhere.
 */
class JellyfinSeasonPlacement private constructor(
    private val byNumber: Map<Int, String>,
    private val unnumbered: String?
) {
    /**
     * [seasonNumber] is the episode's `ParentIndexNumber` and [reportedParent] the `SeasonId` it
     * claims, used only when the number resolves to nothing.
     */
    fun parentFor(seasonNumber: Int?, reportedParent: String?): String? =
        if (seasonNumber == null) unnumbered ?: reportedParent
        else byNumber[seasonNumber] ?: reportedParent

    companion object {
        /**
         * Places nothing, so every episode keeps the parent the server reported. For an episode that
         * arrived without its series, which the home rails routinely serve.
         */
        val EMPTY = JellyfinSeasonPlacement(emptyMap(), null)

        /**
         * Reads the placement off the season containers of one series; anything that is not a season
         * is ignored, so a caller may pass a mixed list.
         *
         * An episode with no season number goes to the container that has no number either, which is
         * where the server files what it could not read a number for. That holds only while exactly
         * one such container exists. Past that nothing here distinguishes them and the episode keeps
         * the id it came with, which is the right answer rather than a resignation: a show carrying
         * several unnumbered containers has a directory per set of extras, and the id names the one
         * the file is actually in.
         */
        fun of(items: List<MediaItemEntity>): JellyfinSeasonPlacement {
            val seasons = items.filter { it.itemType == MediaItemType.SEASON.wireValue }
            val byNumber = LinkedHashMap<Int, String>()
            for (season in seasons) {
                val number = season.indexNumber ?: continue
                if (!byNumber.containsKey(number)) byNumber[number] = season.itemId
            }
            return JellyfinSeasonPlacement(
                byNumber = byNumber,
                unnumbered = seasons.singleOrNull { it.indexNumber == null }?.itemId
            )
        }
    }
}
