package com.nendo.argosy.ui.dualscreen.media

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.domain.model.MediaPlayTarget
import com.nendo.argosy.ui.screens.media.MediaCastUi
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.MediaSeasonUi
import com.nendo.argosy.ui.screens.media.toCastUi
import com.nendo.argosy.ui.screens.media.toMediaItemUi
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * What the presentation screen's media panel shows for the title the player has open. An episode
 * brings its series' [seasons] and the selected season's [episodes], with [nowPlayingEpisodeId]
 * marking the one on screen. A film has neither and gets its [overview], [cast] and [related]
 * titles instead.
 */
data class CompanionMediaState(
    val nowPlaying: MediaItemUi? = null,
    val overview: String? = null,
    val cast: List<MediaCastUi> = emptyList(),
    val related: List<MediaItemUi> = emptyList(),
    val seasons: List<MediaSeasonUi> = emptyList(),
    val selectedSeasonIndex: Int = -1,
    val episodes: List<MediaItemUi> = emptyList(),
    val nowPlayingEpisodeId: String? = null,
    val isLoading: Boolean = true,
    val isFetchingEpisodes: Boolean = false,
    val episodeFetchError: String? = null
) {
    val isShowMode: Boolean get() = nowPlayingEpisodeId != null
}

internal class CompanionMediaPresenter(private val manager: DualScreenManager) {
    private val repository = manager.mediaRepository
    private val seriesDelegate = manager.mediaSeriesDelegate
    private val _state = MutableStateFlow(CompanionMediaState())
    val state: StateFlow<CompanionMediaState> = _state.asStateFlow()

    private val selectedSeasonId = MutableStateFlow<String?>(null)
    private val watchStateVersion = MutableStateFlow(0)
    private val refreshedSeasonIds = mutableSetOf<String>()
    private val fetchingSeasonIds = mutableSetOf<String>()

    fun selectSeason(index: Int) {
        val season = _state.value.seasons.getOrNull(index) ?: return
        selectedSeasonId.value = season.itemId
    }

    suspend fun follow(itemId: String) {
        selectedSeasonId.value = null
        repository.observeItem(itemId).collectLatest { entity ->
            val seasonId = entity?.parentId
            when {
                entity == null -> _state.update { it.copy(isLoading = false) }
                seasonId != null -> observeShow(seasonId, entity)
                else -> showTitleDetail(entity)
            }
        }
    }

    /**
     * Sends [itemId] to the player window wherever it is, resolving a series to its next
     * playable episode. A series with nothing playable is ignored.
     */
    suspend fun play(itemId: String) {
        when (val target = manager.resolveMediaPlayTargetUseCase(itemId)) {
            is MediaPlayTarget.Play -> manager.playMediaItem(target.itemId)
            is MediaPlayTarget.OpenDetail ->
                Logger.debug(TAG, "no playable episode for ${target.itemId}")
        }
    }

    private suspend fun observeShow(playingSeasonId: String, playing: MediaItemEntity) {
        val seriesId = playing.seriesId
        val seasonsSource: Flow<List<MediaSeasonUi>> =
            seriesId?.let { seriesDelegate?.seasonsFlow(it) } ?: flowOf(emptyList())
        combine(seasonsSource, selectedSeasonId) { seasons, chosen ->
            seasons to (chosen ?: playingSeasonId)
        }.collectLatest { (seasons, activeSeasonId) ->
            fetchSeasonEpisodesOnce(seriesId, activeSeasonId)
            episodesFor(activeSeasonId).collectLatest { episodes ->
                val nowPlaying = playing.toUi(repository.getUserData(playing.itemId))
                _state.update { state ->
                    state.copy(
                        nowPlaying = nowPlaying,
                        overview = playing.overview,
                        cast = emptyList(),
                        related = emptyList(),
                        seasons = seasons,
                        selectedSeasonIndex = seasons.indexOfFirst { it.itemId == activeSeasonId },
                        episodes = episodes,
                        nowPlayingEpisodeId = playing.itemId,
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun fetchSeasonEpisodesOnce(seriesId: String?, seasonId: String) {
        val seriesDelegate = seriesDelegate ?: return
        if (seriesId == null) return
        if (seasonId in refreshedSeasonIds || seasonId in fetchingSeasonIds) return
        fetchingSeasonIds.add(seasonId)
        _state.update { it.copy(isFetchingEpisodes = true, episodeFetchError = null) }
        val failure = seriesDelegate.refreshEpisodes(seriesId, seasonId)
        fetchingSeasonIds.remove(seasonId)
        if (failure == null) refreshedSeasonIds.add(seasonId)
        _state.update {
            it.copy(isFetchingEpisodes = fetchingSeasonIds.isNotEmpty(), episodeFetchError = failure)
        }
    }

    private suspend fun showTitleDetail(entity: MediaItemEntity) {
        val nowPlaying = entity.toUi(repository.getUserData(entity.itemId))
        _state.update {
            it.copy(
                nowPlaying = nowPlaying,
                overview = entity.overview,
                seasons = emptyList(),
                selectedSeasonIndex = -1,
                episodes = emptyList(),
                nowPlayingEpisodeId = null,
                isLoading = false
            )
        }
        val cast = repository.getCredits(entity.itemId)
            .distinctBy { it.personId }
            .map { it.toCastUi(repository) }
        val related = manager.getRelatedMediaUseCase(entity)
        val relatedUserData = repository.getUserDataFor(related.map { it.itemId })
        _state.update { state ->
            state.copy(cast = cast, related = related.map { it.toUi(relatedUserData[it.itemId]) })
        }
    }

    private fun episodesFor(seasonId: String): Flow<List<MediaItemUi>> =
        seriesDelegate?.episodesFlow(seasonId, watchStateVersion)
            ?: repository.observeEpisodes(seasonId).map { entities ->
                val userData = repository.getUserDataFor(entities.map { it.itemId })
                entities.map { it.toUi(userData[it.itemId]) }
            }

    private fun MediaItemEntity.toUi(userData: MediaUserDataEntity?): MediaItemUi =
        toMediaItemUi(
            repository,
            userData,
            manager.mediaAvailabilityVerifier?.availability?.value.orEmpty(),
            manager.gradientExtractionDelegate.mediaGradients.value
        )

    private companion object {
        const val TAG = "CompanionMediaPresenter"
    }
}
