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
import kotlinx.coroutines.delay
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
 * How long a playback gap is treated as an episode switch rather than a stop. The player closes
 * the outgoing item before the incoming one has negotiated, so every switch passes through a
 * multi-second window with no open item; tearing the episode browser down to the rails during
 * that window is what blanked the panel between episodes. A gap that outlives this is a real
 * stop and falls through to the rails as before.
 */
private const val PLAYBACK_SWITCH_GRACE_MS = 5_000L

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
 * watching. A standing [requestedItem] also keeps the work alive: the information surface renders
 * this state through the home content rather than the panel, so activity cannot be read from the
 * panel's visibility alone.
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
    private val seriesDelegate: com.nendo.argosy.ui.screens.media.delegates.MediaSeriesDelegate? = null,
    private val requestedItem: StateFlow<String?> = MutableStateFlow(null)
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
            combine(playback.map { it?.itemId }, requestedItem) { playing, requested ->
                requested ?: playing
            }
                .distinctUntilChanged()
                .collectLatest { itemId ->
                    if (itemId == null && _uiState.value.isShowMode) {
                        delay(PLAYBACK_SWITCH_GRACE_MS)
                    }
                    selectedSeasonId.value = null
                    if (shouldObserve()) restart(itemId)
                }
        }
        availabilityVerifier?.let { verifier ->
            viewModelScope.launch {
                verifier.availability.collect {
                    val itemId = effectiveItemId()
                    if (itemId == null && _uiState.value.isShowMode) return@collect
                    if (shouldObserve()) restart(itemId)
                }
            }
        }
    }

    /**
     * An explicit request outranks the playback item: it is the viewer asking to see one title's
     * information whether or not anything is playing. Clearing it returns the panel to following
     * the playback, which is the standing behaviour.
     */
    private fun effectiveItemId(): String? = requestedItem.value ?: playback.value?.itemId

    private fun shouldObserve(): Boolean = isActive || requestedItem.value != null

    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        if (active) {
            availabilityVerifier?.verifyOnOpen()
            restart(effectiveItemId())
        } else if (requestedItem.value == null) {
            loadJob?.cancel()
            loadJob = null
            _uiState.update {
                it.copy(
                    rows = emptyList(),
                    focusedRowIndex = -1,
                    isLoading = false,
                    seasons = emptyList(),
                    selectedSeasonIndex = -1,
                    episodes = emptyList(),
                    focusedEpisodeIndex = -1,
                    nowPlayingEpisodeId = null,
                    isEpisodeBrowse = false
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
    }

    fun moveEpisodeFocus(delta: Int) {
        val state = _uiState.value
        if (state.episodes.isEmpty()) return
        val current = state.focusedEpisodeIndex.coerceIn(0, state.episodes.lastIndex)
        val next = (current + delta).mod(state.episodes.size)
        _uiState.update { it.copy(focusedEpisodeIndex = next) }
    }

    fun moveSeason(delta: Int) {
        val state = _uiState.value
        if (state.seasons.isEmpty()) return
        val current = state.selectedSeasonIndex.coerceIn(0, state.seasons.lastIndex)
        selectSeason((current + delta).mod(state.seasons.size))
    }

    /**
     * Where the episode cursor lands when a season's episodes publish. A season change resets it to
     * the anchor episode (the one this panel describes) or the top; the same season keeps whatever
     * the viewer had, unless the refreshed list no longer contains that index.
     */
    private fun episodeCursorFor(
        previous: DualMediaUiState,
        newSeasonIndex: Int,
        episodes: List<MediaItemUi>,
        anchorEpisodeId: String?
    ): Int {
        val anchorIndex = episodes.indexOfFirst { it.itemId == anchorEpisodeId }
            .takeIf { it >= 0 } ?: 0
        return when {
            episodes.isEmpty() -> -1
            previous.selectedSeasonIndex != newSeasonIndex -> anchorIndex
            previous.focusedEpisodeIndex in episodes.indices -> previous.focusedEpisodeIndex
            else -> anchorIndex
        }
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
                    seasonId != null -> observeShow(seasonId, entity)
                    entity.itemType == com.nendo.argosy.data.local.entity.MediaItemType.SERIES.wireValue ->
                        observeSeries(entity)
                    else -> showTitleDetail(entity)
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
            ensureSeasonEpisodes(seriesId, activeSeasonId)
            episodesFor(activeSeasonId).collectLatest { episodes ->
                val nowPlaying = playingEntity.toUi(
                    mediaRepository.getUserData(playingEntity.itemId)
                )
                _uiState.update { state ->
                    val seasonIndex = seasons.indexOfFirst { it.itemId == activeSeasonId }
                    state.copy(
                        nowPlaying = nowPlaying,
                        overview = playingEntity.overview,
                        rows = emptyList(),
                        focusedRowIndex = -1,
                        cast = emptyList(),
                        seasons = seasons,
                        selectedSeasonIndex = seasonIndex,
                        episodes = episodes,
                        focusedEpisodeIndex = episodeCursorFor(
                            previous = state,
                            newSeasonIndex = seasonIndex,
                            episodes = episodes,
                            anchorEpisodeId = playingEntity.itemId
                        ),
                        nowPlayingEpisodeId = playingEntity.itemId,
                        isEpisodeBrowse = false,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * The browse mode for a series asked about directly, with nothing of it playing: the series
     * hero, its seasons, and the selected season's episodes. There is no now-playing marker to
     * anchor the season, so the first season stands in until the viewer picks one; a series the
     * delegate has no seasons for falls back to the title detail rather than an empty browser.
     */
    private suspend fun observeSeries(entity: MediaItemEntity) {
        val delegate = seriesDelegate
        if (delegate == null) {
            showTitleDetail(entity)
            return
        }
        combine(delegate.seasonsFlow(entity.itemId), selectedSeasonId) { seasons, chosen ->
            seasons to (chosen ?: seasons.firstOrNull()?.itemId)
        }.collectLatest { (seasons, activeSeasonId) ->
            if (activeSeasonId == null) {
                showTitleDetail(entity)
                return@collectLatest
            }
            ensureSeasonEpisodes(entity.itemId, activeSeasonId)
            episodesFor(activeSeasonId).collectLatest { episodes ->
                val hero = entity.toUi(mediaRepository.getUserData(entity.itemId))
                _uiState.update { state ->
                    val seasonIndex = seasons.indexOfFirst { it.itemId == activeSeasonId }
                    state.copy(
                        nowPlaying = hero,
                        overview = entity.overview,
                        rows = emptyList(),
                        focusedRowIndex = -1,
                        cast = emptyList(),
                        seasons = seasons,
                        selectedSeasonIndex = seasonIndex,
                        episodes = episodes,
                        focusedEpisodeIndex = episodeCursorFor(
                            previous = state,
                            newSeasonIndex = seasonIndex,
                            episodes = episodes,
                            anchorEpisodeId = null
                        ),
                        nowPlayingEpisodeId = null,
                        isEpisodeBrowse = true,
                        isLoading = false
                    )
                }
            }
        }
    }

    private val refreshedSeasonIds = mutableSetOf<String>()
    private val fetchingSeasonIds = mutableSetOf<String>()

    /**
     * Fetches a season's episodes if this panel has never asked for them. Episodes are stored a
     * season at a time and only on request; nothing else asks on this screen, so a season shown
     * here would otherwise list only whatever a previous resolve happened to store. The stored copy
     * stays on screen while the fetch runs, and a failure is surfaced rather than left reading as
     * an empty season; a failed season is retried on its next showing.
     */
    private fun ensureSeasonEpisodes(seriesId: String?, seasonId: String) {
        val delegate = seriesDelegate ?: return
        if (seriesId == null) return
        if (seasonId in refreshedSeasonIds || seasonId in fetchingSeasonIds) return
        fetchingSeasonIds.add(seasonId)
        _uiState.update { it.copy(isFetchingEpisodes = true, episodeFetchError = null) }
        viewModelScope.launch {
            val failure = delegate.refreshEpisodes(seriesId, seasonId)
            fetchingSeasonIds.remove(seasonId)
            if (failure == null) refreshedSeasonIds.add(seasonId)
            _uiState.update {
                it.copy(
                    isFetchingEpisodes = fetchingSeasonIds.isNotEmpty(),
                    episodeFetchError = failure
                )
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
                focusedEpisodeIndex = -1,
                nowPlayingEpisodeId = null,
                isEpisodeBrowse = false,
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
                focusedEpisodeIndex = -1,
                nowPlayingEpisodeId = null,
                isEpisodeBrowse = false,
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
