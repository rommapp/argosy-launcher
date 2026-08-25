package com.nendo.argosy.domain.usecase.media

import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.domain.model.MediaPlayTarget
import javax.inject.Inject
import javax.inject.Singleton

private const val SPECIALS_SEASON_NUMBER = 0

/**
 * What a press on a media item plays, resolved for every surface the same way.
 *
 * A movie and an episode play themselves. A series has to be asked, and the order the answer is
 * looked for in is the order it is cheapest and most likely to be right:
 *
 * 1. [knownResumeItemId], the episode a Continue Watching rail already carries for this show
 * 2. [nextUpHint], the episode a Next Up rail names for it
 * 3. what the episode table holds -- part watched first, then the first unwatched, then the first
 * 4. failing all of that, the first season is fetched and the table asked again
 *
 * Step four is the normal path for a show that has never been opened, because episodes are synced a
 * season at a time and a library sync stores only seasons. A show whose seasons are not stored
 * either, or whose fetch does not answer, resolves to its detail screen rather than to nothing.
 */
@Singleton
class ResolveMediaPlayTargetUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(
        itemId: String,
        knownResumeItemId: String? = null,
        nextUpHint: String? = null
    ): MediaPlayTarget {
        val entity = mediaRepository.getItem(itemId)
        val isSeries = entity != null &&
            MediaItemType.fromWire(entity.itemType) == MediaItemType.SERIES
        if (!isSeries) return MediaPlayTarget.Play(itemId)
        knownResumeItemId?.let { return MediaPlayTarget.Play(it) }
        nextUpHint?.let { return MediaPlayTarget.Play(it) }
        episodeToPlay(itemId)?.let { return MediaPlayTarget.Play(it) }
        if (!fetchFirstSeason(itemId)) return MediaPlayTarget.OpenDetail(itemId)
        return episodeToPlay(itemId)
            ?.let { MediaPlayTarget.Play(it) }
            ?: MediaPlayTarget.OpenDetail(itemId)
    }

    /**
     * Where a run of episodes is up to: the one left part way through, then the first never
     * started, then the first of the run for a viewer who has finished it all. Shared with every
     * caller that already holds an episode list, so all of them read watch state the same way.
     */
    suspend fun nextOf(itemIds: List<String>): String? {
        if (itemIds.isEmpty()) return null
        val watched = mediaRepository.getUserDataFor(itemIds)
        val partWatched = itemIds.firstOrNull {
            val userData = watched[it]
            userData != null && !userData.played && userData.playbackPositionTicks > 0
        }
        val unwatched = itemIds.firstOrNull { watched[it]?.played != true }
        return partWatched ?: unwatched ?: itemIds.first()
    }

    private suspend fun episodeToPlay(seriesId: String): String? =
        nextOf(mediaRepository.getSeriesEpisodes(seriesId).map { it.itemId })

    /**
     * Reads one season's episodes into the library. Specials are passed over when the show has an
     * ordinary season to offer, since season zero sorts first and is nobody's idea of where a show
     * starts.
     */
    private suspend fun fetchFirstSeason(seriesId: String): Boolean {
        val seasons = mediaRepository.getSeasons(seriesId)
        if (seasons.isEmpty()) return false
        val season = seasons.firstOrNull { it.indexNumber != SPECIALS_SEASON_NUMBER }
            ?: seasons.first()
        return mediaRepository.refreshEpisodes(seriesId, season.itemId) is JellyfinResult.Success
    }
}
