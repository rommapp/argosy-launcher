package com.nendo.argosy.domain.usecase.media

import com.nendo.argosy.data.local.entity.MediaItemEntity
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
        knownResumeItemId?.takeIf { isStillWatchable(it) }?.let { return MediaPlayTarget.Play(it) }
        nextUpHint?.takeIf { isStillWatchable(it) }?.let { return MediaPlayTarget.Play(it) }
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

    /**
     * A rail hint is only trusted while the episode it names is still unfinished locally. The rails
     * are cached, so just after an episode completes they still name it - honouring that would
     * replay what was just watched instead of moving on.
     */
    private suspend fun isStillWatchable(itemId: String): Boolean =
        mediaRepository.getUserData(itemId)?.played != true

    /**
     * Specials sort as season zero, so a fully-watched show would restart on a special rather than
     * on the first episode proper. They go to the back of the run instead of being dropped: a show
     * with nothing but specials still has to answer with something.
     */
    private suspend fun episodeToPlay(seriesId: String): String? {
        val episodes = mediaRepository.getSeriesEpisodes(seriesId)
        val ordered = episodes.filter { it.parentIndexNumber != SPECIALS_SEASON_NUMBER } +
            episodes.filter { it.parentIndexNumber == SPECIALS_SEASON_NUMBER }
        return nextOf(ordered.map { it.itemId })
    }

    /**
     * The episode that follows [itemId] in its series, crossing a season boundary by fetching the
     * following season when the stored run ends at one. Null means there is genuinely nothing next:
     * the last episode of the show, a film, an episode whose series is unknown, or a next season
     * that cannot be reached - and the caller exits to the list rather than offering a dead button.
     *
     * When the stored table does not hold the episode at all, its own season is fetched first, so a
     * viewing that began from a rail on a never-opened show still learns what follows it.
     */
    suspend fun nextEpisodeAfter(itemId: String): MediaItemEntity? {
        val entity = mediaRepository.getItem(itemId) ?: return null
        if (MediaItemType.fromWire(entity.itemType) != MediaItemType.EPISODE) return null
        val seriesId = entity.seriesId ?: return null
        var episodes = mediaRepository.getSeriesEpisodes(seriesId)
        if (episodes.none { it.itemId == entity.itemId }) {
            val seasonId = entity.parentId ?: return null
            if (mediaRepository.refreshEpisodes(seriesId, seasonId) !is JellyfinResult.Success) {
                return null
            }
            episodes = mediaRepository.getSeriesEpisodes(seriesId)
        }
        val position = episodes.indexOfFirst { it.itemId == entity.itemId }
        if (position < 0) return null
        episodes.getOrNull(position + 1)?.let { return it }
        val nextSeasonId = nextSeasonId(seriesId, entity) ?: return null
        if (mediaRepository.refreshEpisodes(seriesId, nextSeasonId) !is JellyfinResult.Success) {
            return null
        }
        val refreshed = mediaRepository.getSeriesEpisodes(seriesId)
        val refreshedPosition = refreshed.indexOfFirst { it.itemId == entity.itemId }
        return if (refreshedPosition >= 0) refreshed.getOrNull(refreshedPosition + 1) else null
    }

    /**
     * The episode before [itemId] in its series, crossing a season boundary by fetching the season
     * that precedes when the stored run starts at one. Null means there is genuinely nothing
     * earlier: the first episode of the show, a film, or an episode whose series is unknown.
     *
     * Specials are passed over when the current episode is an ordinary one: season zero sorts below
     * every numbered season, so without the filter the first episode proper would answer with the
     * last special, which is nobody's idea of what came before the show started.
     */
    suspend fun previousEpisodeBefore(itemId: String): MediaItemEntity? {
        val entity = mediaRepository.getItem(itemId) ?: return null
        if (MediaItemType.fromWire(entity.itemType) != MediaItemType.EPISODE) return null
        val seriesId = entity.seriesId ?: return null
        var episodes = orderedRunFor(entity, mediaRepository.getSeriesEpisodes(seriesId))
        if (episodes.none { it.itemId == entity.itemId }) {
            val seasonId = entity.parentId ?: return null
            if (mediaRepository.refreshEpisodes(seriesId, seasonId) !is JellyfinResult.Success) {
                return null
            }
            episodes = orderedRunFor(entity, mediaRepository.getSeriesEpisodes(seriesId))
        }
        val position = episodes.indexOfFirst { it.itemId == entity.itemId }
        if (position < 0) return null
        if (position > 0) return episodes[position - 1]
        val previousSeasonId = previousSeasonId(seriesId, entity) ?: return null
        if (mediaRepository.refreshEpisodes(seriesId, previousSeasonId) !is JellyfinResult.Success) {
            return null
        }
        val refreshed = orderedRunFor(entity, mediaRepository.getSeriesEpisodes(seriesId))
        val refreshedPosition = refreshed.indexOfFirst { it.itemId == entity.itemId }
        return if (refreshedPosition > 0) refreshed.getOrNull(refreshedPosition - 1) else null
    }

    /**
     * The run an ordinary episode is walked in excludes specials; a special is walked among its own.
     */
    private fun orderedRunFor(
        episode: MediaItemEntity,
        episodes: List<MediaItemEntity>
    ): List<MediaItemEntity> =
        if (episode.parentIndexNumber == SPECIALS_SEASON_NUMBER) {
            episodes
        } else {
            episodes.filter { it.parentIndexNumber != SPECIALS_SEASON_NUMBER }
        }

    /**
     * The season after the one [episode] belongs to, from the stored season list. Specials never
     * follow an ordinary season here - season zero is below every numbered season, so the numeric
     * comparison passes it over on its own.
     */
    private suspend fun nextSeasonId(seriesId: String, episode: MediaItemEntity): String? {
        val seasons = mediaRepository.getSeasons(seriesId)
        if (seasons.isEmpty()) return null
        val currentNumber = episode.parentIndexNumber
        if (currentNumber != null) {
            return seasons
                .filter { (it.indexNumber ?: Int.MIN_VALUE) > currentNumber }
                .minByOrNull { it.indexNumber ?: Int.MAX_VALUE }
                ?.itemId
        }
        val position = seasons.indexOfFirst { it.itemId == episode.parentId }
        return if (position >= 0) seasons.getOrNull(position + 1)?.itemId else null
    }

    /**
     * The season before the one [episode] belongs to. Season zero is excluded for the same reason
     * [previousEpisodeBefore] skips it: the specials shelf is not where a numbered season came from.
     */
    private suspend fun previousSeasonId(seriesId: String, episode: MediaItemEntity): String? {
        val seasons = mediaRepository.getSeasons(seriesId)
        if (seasons.isEmpty()) return null
        val currentNumber = episode.parentIndexNumber
        if (currentNumber != null) {
            return seasons
                .filter {
                    val number = it.indexNumber ?: return@filter false
                    number != SPECIALS_SEASON_NUMBER && number < currentNumber
                }
                .maxByOrNull { it.indexNumber ?: Int.MIN_VALUE }
                ?.itemId
        }
        val position = seasons.indexOfFirst { it.itemId == episode.parentId }
        return if (position > 0) seasons.getOrNull(position - 1)?.itemId else null
    }

    /**
     * Reads one season's episodes into the library. Specials are passed over when the show has an
     * ordinary season to offer, since season zero sorts first and is nobody's idea of where a show
     * starts.
     */
    suspend fun fetchFirstSeason(seriesId: String): Boolean {
        val seasons = mediaRepository.getSeasons(seriesId)
        if (seasons.isEmpty()) return false
        val season = seasons.firstOrNull { it.indexNumber != SPECIALS_SEASON_NUMBER }
            ?: seasons.first()
        return mediaRepository.refreshEpisodes(seriesId, season.itemId) is JellyfinResult.Success
    }
}
