/**
 * DUAL-SCREEN COMPONENT - Lower display media panel.
 * Built by SecondaryHomeActivity; reads the shared MediaRepository through DualScreenManager.
 */
package com.nendo.argosy.ui.dualscreen.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.media.ActiveMediaPlayback
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.MediaSeasonUi
import com.nendo.argosy.ui.screens.media.toCastUi
import com.nendo.argosy.ui.screens.media.toMediaItemUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CONTINUE_WATCHING_LABEL = "Continue Watching"
private const val NEXT_UP_LABEL = "Next Up"
private const val MORE_LIKE_THIS_LABEL = "More Like This"

/**
 * The companion's view of what is being watched.
 *
 * It reads rather than commands: the player owns the playback and this only describes it, so the
 * panel survives the player moving between displays without holding anything the player would have
 * to hand over.
 *
 * Nothing is observed while the panel is off screen. [setActive] is what starts and stops the work,
 * because the rails refresh themselves from the server on first collection and a companion that
 * observed them permanently would ask for them again on every boot whether or not anyone was
 * watching.
 *
 * While a playback is live the panel is a touch surface only - the controller drives the player on
 * the other screen - so the focus cursor here moves only in the no-playback rails state.
 */
class DualMediaViewModel(
    private val mediaRepository: MediaRepository,
    private val playback: StateFlow<ActiveMediaPlayback?>,
    private val gradientExtractionDelegate: com.nendo.argosy.ui.screens.common.GradientExtractionDelegate,
    private val getRelatedMedia: com.nendo.argosy.domain.usecase.media.GetRelatedMediaUseCase? = null,
    private val availabilityVerifier: com.nendo.argosy.data.media.MediaAvailabilityVerifier? = null,
    private val seriesDelegate: com.nendo.argosy.ui.screens.media.delegates.MediaSeriesDelegate? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualMediaUiState())
    val uiState: StateFlow<DualMediaUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var isActive = false

    /**
     * The season the viewer tapped, or null to follow the season of the playing episode. Reset on
     * every item change so the next episode's own season is what comes up.
     */
    private val selectedSeasonId = MutableStateFlow<String?>(null)
    private val watchStateVersion = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            mediaRepository.isSignedIn.collect { signedIn ->
                _uiState.update { it.copy(isSignedIn = signedIn) }
            }
        }
        viewModelScope.launch {
            playback.collect { active ->
                _uiState.update {
                    it.copy(
                        isPlaying = active?.isPlaying == true,
                        isPlaybackLive = active != null,
                        nowPlayingTitle = active?.title.orEmpty()
                    )
                }
            }
        }
        viewModelScope.launch {
            playback
                .map { it?.itemId }
                .distinctUntilChanged()
                .collect { itemId ->
                    selectedSeasonId.value = null
                    _uiState.update { it.copy(isSeasonPickerOpen = false) }
                    if (isActive) restart(itemId)
                }
        }
        availabilityVerifier?.let { verifier ->
            viewModelScope.launch {
                verifier.availability
                    .collect { if (isActive) restart(playback.value?.itemId) }
            }
        }
    }

    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        if (active) {
            availabilityVerifier?.verifyOnOpen()
            restart(playback.value?.itemId)
        } else {
            loadJob?.cancel()
            loadJob = null
            _uiState.update {
                it.copy(
                    rows = emptyList(),
                    focusedRowIndex = -1,
                    isLoading = false,
                    seasons = emptyList(),
                    selectedSeasonIndex = -1,
                    isSeasonPickerOpen = false,
                    episodes = emptyList(),
                    nowPlayingEpisodeId = null
                )
            }
        }
    }

    fun moveFocus(delta: Int) {
        val state = _uiState.value
        val indices = state.rows.indices.filter { state.rows[it] is DualMediaRow.Item }
        if (indices.isEmpty()) return
        val current = indices.indexOf(state.focusedRowIndex)
        val next = if (current < 0) 0 else (current + delta).coerceIn(0, indices.lastIndex)
        _uiState.update { it.copy(focusedRowIndex = indices[next]) }
    }

    fun focusRow(index: Int) {
        if (_uiState.value.rows.getOrNull(index) !is DualMediaRow.Item) return
        _uiState.update { it.copy(focusedRowIndex = index) }
    }

    fun selectSeason(index: Int) {
        val season = _uiState.value.seasons.getOrNull(index) ?: return
        selectedSeasonId.value = season.itemId
        _uiState.update { it.copy(isSeasonPickerOpen = false) }
    }

    fun toggleSeasonPicker() {
        _uiState.update { it.copy(isSeasonPickerOpen = !it.isSeasonPickerOpen) }
    }

    fun setEpisodeLayout(layout: DualMediaEpisodeLayout) {
        _uiState.update { it.copy(episodeLayout = layout) }
    }

    /**
     * Returns the episode list to the episode being watched: its season is re-selected and the list
     * scrolls to its row.
     */
    fun jumpToNowPlaying() {
        selectedSeasonId.value = null
        _uiState.update { it.copy(isSeasonPickerOpen = false, jumpNonce = it.jumpNonce + 1) }
    }

    private fun restart(itemId: String?) {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            if (itemId == null) {
                observeRails(nowPlaying = null)
                return@launch
            }
            mediaRepository.observeItem(itemId).collectLatest { entity ->
                val seasonId = entity?.parentId
                when {
                    entity == null -> observeRails(nowPlaying = null)
                    seasonId == null -> showTitleDetail(entity)
                    else -> observeShow(seasonId, entity)
                }
            }
        }
    }

    /**
     * The show mode: the seasons of the playing episode's series, the selected season's episodes,
     * and the episode being watched marked in place. The season list arrives from the shared series
     * delegate so this panel and the detail screen read the same rows; when the entity carries no
     * series id the panel falls back to the playing season alone with no selector.
     */
    private suspend fun observeShow(playingSeasonId: String, playingEntity: MediaItemEntity) {
        val seriesId = playingEntity.seriesId
        val seasonsSource: Flow<List<MediaSeasonUi>> = if (seriesId != null && seriesDelegate != null) {
            seriesDelegate.seasonsFlow(seriesId)
        } else {
            flowOf(emptyList())
        }
        combine(seasonsSource, selectedSeasonId) { seasons, chosen ->
            seasons to (chosen ?: playingSeasonId)
        }.collectLatest { (seasons, activeSeasonId) ->
            episodesFor(activeSeasonId).collectLatest { episodes ->
                val nowPlaying = playingEntity.toUi(
                    mediaRepository.getUserData(playingEntity.itemId)
                )
                _uiState.update { state ->
                    state.copy(
                        nowPlaying = nowPlaying,
                        overview = playingEntity.overview,
                        rows = emptyList(),
                        focusedRowIndex = -1,
                        cast = emptyList(),
                        seasons = seasons,
                        selectedSeasonIndex = seasons.indexOfFirst { it.itemId == activeSeasonId },
                        episodes = episodes,
                        nowPlayingEpisodeId = playingEntity.itemId,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun episodesFor(seasonId: String) =
        seriesDelegate?.episodesFlow(seasonId, watchStateVersion)
            ?: mediaRepository.observeEpisodes(seasonId).map { entities ->
                val userData = mediaRepository.getUserDataFor(entities.map { it.itemId })
                entities.map { it.toUi(userData[it.itemId]) }
            }

    /**
     * What a film gets instead of an episode list: its synopsis, its cast, and titles like it.
     *
     * A movie has no siblings, so the rails this used to fall back on described somebody else's
     * viewing rather than the film on screen. Worse, they only ever published once both rail flows
     * had emitted, and a library with neither left the panel reading "loading" for good.
     */
    private suspend fun showTitleDetail(entity: MediaItemEntity) {
        val userData = mediaRepository.getUserData(entity.itemId)
        val nowPlaying = entity.toUi(userData)
        _uiState.update {
            it.copy(
                nowPlaying = nowPlaying,
                overview = entity.overview,
                rows = emptyList(),
                focusedRowIndex = -1,
                seasons = emptyList(),
                selectedSeasonIndex = -1,
                episodes = emptyList(),
                nowPlayingEpisodeId = null,
                isLoading = false
            )
        }

        val cast = mediaRepository.getCredits(entity.itemId)
            .distinctBy { it.personId }
            .map { it.toCastUi(mediaRepository) }
        val related = getRelatedMedia?.invoke(entity).orEmpty()
        val relatedUserData = mediaRepository.getUserDataFor(related.map { it.itemId })
        val rows = buildList<DualMediaRow> {
            if (related.isNotEmpty()) {
                add(DualMediaRow.Header(MORE_LIKE_THIS_LABEL))
                related.forEach { add(DualMediaRow.Item(it.toUi(relatedUserData[it.itemId]))) }
            }
        }
        _uiState.update { state ->
            state.copy(
                cast = cast,
                rows = rows,
                focusedRowIndex = rows.indexOfFirst { it is DualMediaRow.Item }
            )
        }
    }

    /**
     * The two media rails, which is what the panel offers when nothing is open at all.
     */
    private suspend fun observeRails(nowPlaying: MediaItemUi?) {
        combine(
            mediaRepository.observeContinueWatching(),
            mediaRepository.observeNextUp()
        ) { continuing, next -> continuing to next }
            .collectLatest { (continuing, next) ->
                val userData = mediaRepository.getUserDataFor(
                    (continuing + next).map { it.itemId }.distinct()
                )
                val rows = buildList<DualMediaRow> {
                    if (continuing.isNotEmpty()) {
                        add(DualMediaRow.Header(CONTINUE_WATCHING_LABEL))
                        continuing.forEach {
                            add(DualMediaRow.Item(it.toUi(userData[it.itemId])))
                        }
                    }
                    if (next.isNotEmpty()) {
                        add(DualMediaRow.Header(NEXT_UP_LABEL))
                        next.forEach { add(DualMediaRow.Item(it.toUi(userData[it.itemId]))) }
                    }
                }
                publish(nowPlaying, rows, preferItemId = null)
            }
    }

    /**
     * Keeps the cursor on whatever it was on. A rail refresh reorders and replaces rows underneath
     * it, and an index carried across that lands on a different title than the one under the finger.
     */
    private fun publish(
        nowPlaying: MediaItemUi?,
        rows: List<DualMediaRow>,
        preferItemId: String?
    ) {
        _uiState.update { state ->
            val focused = listOfNotNull(state.focusedItem?.itemId, preferItemId)
                .firstNotNullOfOrNull { wanted ->
                    rows.indexOfFirst { it is DualMediaRow.Item && it.item.itemId == wanted }
                        .takeIf { it >= 0 }
                }
                ?: rows.indexOfFirst { it is DualMediaRow.Item }
            state.copy(
                nowPlaying = nowPlaying,
                rows = rows,
                focusedRowIndex = focused,
                seasons = emptyList(),
                selectedSeasonIndex = -1,
                episodes = emptyList(),
                nowPlayingEpisodeId = null,
                isLoading = false
            )
        }
    }

    private fun MediaItemEntity.toUi(userData: MediaUserDataEntity?): MediaItemUi =
        toMediaItemUi(
            mediaRepository,
            userData,
            availabilityVerifier?.availability?.value.orEmpty(),
            gradientExtractionDelegate.mediaGradients.value
        )
}
