package com.nendo.argosy.ui.screens.media.delegates

import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.MediaSeasonUi
import com.nendo.argosy.ui.screens.media.toMediaItemUi
import com.nendo.argosy.ui.screens.media.toMediaSeasonUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The season and episode half of a series detail view.
 *
 * Seasons and episodes are both real lists here rather than a picker: a series on this scale runs to
 * ten seasons and hundreds of episodes, and a modal that has to be opened before anything can be
 * seen hides the thing the screen exists to show.
 *
 * Episodes are stored a season at a time, so opening a season is also what fetches it. The stored
 * copy is served first and the refresh lands on top, which keeps a revisit instant and an offline
 * visit useful.
 */
@Singleton
class MediaSeriesDelegate @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    /**
     * The seasons of one series. Deduplicated because the underlying query re-runs on any write to
     * the item table -- storing a season's episodes is exactly such a write, and an undeduplicated
     * flow would answer with the same season list again and restart the fetch that caused it.
     */
    fun seasonsFlow(seriesId: String): Flow<List<MediaSeasonUi>> =
        mediaRepository.observeSeasons(seriesId)
            .map { entities -> entities.map { it.toMediaSeasonUi() } }
            .distinctUntilChanged()

    /**
     * One season's episodes with their watch state. [watchStateVersion] exists because watch state
     * lives in its own table and is read in one batch rather than joined: a local mark-watched has to
     * push a new value through this flow, and bumping the version is what does it.
     */
    fun episodesFlow(seasonId: String, watchStateVersion: Flow<Int>): Flow<List<MediaItemUi>> =
        combine(mediaRepository.observeEpisodes(seasonId), watchStateVersion) { entities, _ -> entities }
            .map { entities ->
                val userData = mediaRepository.getUserDataFor(entities.map { it.itemId })
                entities.map { it.toMediaItemUi(mediaRepository, userData[it.itemId]) }
            }

    /**
     * Pulls a season's episodes from the server. Returns the failure message, or null when the
     * season arrived; a failure is not fatal because the stored copy is still on screen.
     */
    suspend fun refreshEpisodes(seriesId: String, seasonId: String): String? =
        when (val result = mediaRepository.refreshEpisodes(seriesId, seasonId)) {
            is JellyfinResult.Success -> null
            is JellyfinResult.Error -> result.message
        }
}
