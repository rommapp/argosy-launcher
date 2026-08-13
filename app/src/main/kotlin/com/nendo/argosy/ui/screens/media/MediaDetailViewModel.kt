package com.nendo.argosy.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.media.delegates.MediaDownloadDelegate
import com.nendo.argosy.ui.screens.media.delegates.MediaSeriesDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val PLAY_TARGET_WAIT_MS = 10_000L

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val seriesDelegate: MediaSeriesDelegate,
    private val downloadDelegate: MediaDownloadDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

    private val watchStateVersion = MutableStateFlow(0)

    private var loadedItemId: String? = null
    private var openedSeasonId: String? = null
    private var itemJob: Job? = null
    private var seasonsJob: Job? = null
    private var episodesJob: Job? = null
    private var downloadJob: Job? = null
    private var playJob: Job? = null

    fun load(itemId: String) {
        if (loadedItemId == itemId) return
        loadedItemId = itemId
        openedSeasonId = null
        itemJob?.cancel()
        seasonsJob?.cancel()
        episodesJob?.cancel()
        downloadJob?.cancel()
        playJob?.cancel()
        itemJob = null
        seasonsJob = null
        episodesJob = null
        downloadJob = null
        playJob = null
        _uiState.value = MediaDetailUiState(isLoading = true)

        itemJob = viewModelScope.launch {
            combine(
                mediaRepository.observeItem(itemId),
                mediaRepository.observeUserData(itemId)
            ) { entity, userData -> entity?.toMediaItemUi(mediaRepository, userData) }
                .collect { item -> applyItem(itemId, item) }
        }
    }

    private fun applyItem(itemId: String, item: MediaItemUi?) {
        if (item == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "This title is no longer in the library."
                )
            }
            return
        }
        val mode = if (item.isSeries) MediaDetailMode.SERIES else MediaDetailMode.MOVIE
        _uiState.update {
            it.copy(
                item = item,
                mode = mode,
                actions = actionsFor(mode),
                actionIndex = it.actionIndex.coerceIn(0, actionsFor(mode).lastIndex),
                isLoading = false,
                errorMessage = null
            )
        }
        if (mode == MediaDetailMode.SERIES && seasonsJob == null) observeSeasons(itemId)
        if (downloadJob == null) observeDownloadSummary(item)
    }

    private fun actionsFor(mode: MediaDetailMode): List<MediaDetailAction> = when (mode) {
        MediaDetailMode.MOVIE -> listOf(
            MediaDetailAction.PLAY,
            MediaDetailAction.DOWNLOAD,
            MediaDetailAction.FAVORITE,
            MediaDetailAction.WATCHED
        )
        MediaDetailMode.SERIES -> listOf(
            MediaDetailAction.PLAY,
            MediaDetailAction.DOWNLOAD,
            MediaDetailAction.FAVORITE
        )
    }

    /**
     * Keeps the aggregate fresh. A series' downloaded state is a count against a count, and both
     * halves move without the series row itself changing - an episode finishing rewrites the episode,
     * and the queue shrinking is not a database write at all.
     */
    private fun observeDownloadSummary(item: MediaItemUi) {
        downloadJob = viewModelScope.launch {
            downloadDelegate.pendingCount(item.itemId, item.isSeries).collect { pending ->
                val current = _uiState.value.item ?: item
                val summary = downloadDelegate.summaryFor(current, pending)
                _uiState.update { it.copy(downloadSummary = summary) }
            }
        }
    }

    private fun refreshDownloadSummary() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            val pending = _uiState.value.downloadSummary.pending
            _uiState.update { it.copy(downloadSummary = downloadDelegate.summaryFor(item, pending)) }
        }
    }

    fun openDownloadPrompt() {
        val state = _uiState.value
        val item = state.item ?: return
        val target = if (state.section == MediaDetailSection.EPISODES) state.focusedEpisode ?: item else item
        viewModelScope.launch {
            val prompt = downloadDelegate.openPrompt(target, state.episodes, state.downloadSummary)
            _uiState.update { it.copy(downloadPrompt = prompt) }
        }
    }

    fun moveDownloadFocus(delta: Int) {
        val prompt = _uiState.value.downloadPrompt ?: return
        _uiState.update { it.copy(downloadPrompt = downloadDelegate.moveFocus(prompt, delta)) }
    }

    fun focusDownloadOption(index: Int) {
        val prompt = _uiState.value.downloadPrompt ?: return
        _uiState.update { it.copy(downloadPrompt = downloadDelegate.focus(prompt, index)) }
    }

    fun confirmDownloadOption() {
        val state = _uiState.value
        val prompt = state.downloadPrompt ?: return
        val item = state.item ?: return
        viewModelScope.launch {
            val next = downloadDelegate.advance(prompt, item, state.episodes)
            _uiState.update { it.copy(downloadPrompt = next) }
            if (next == null) refreshDownloadSummary()
        }
    }

    fun dismissDownloadPrompt() {
        _uiState.update { it.copy(downloadPrompt = null) }
    }

    private fun observeSeasons(seriesId: String) {
        seasonsJob = viewModelScope.launch {
            seriesDelegate.seasonsFlow(seriesId).collect { seasons ->
                val previousSeasonId = _uiState.value.selectedSeason?.itemId
                val index = seasons.indexOfFirst { it.itemId == previousSeasonId }.takeIf { it >= 0 } ?: 0
                _uiState.update { it.copy(seasons = seasons, seasonIndex = index.coerceAtMost((seasons.size - 1).coerceAtLeast(0))) }
                seasons.getOrNull(index)?.let { openSeason(seriesId, it.itemId) }
            }
        }
    }

    private fun openSeason(seriesId: String, seasonId: String) {
        if (openedSeasonId == seasonId && episodesJob?.isActive == true) return
        openedSeasonId = seasonId
        episodesJob?.cancel()
        _uiState.update { it.copy(isLoadingEpisodes = true, episodesErrorMessage = null, episodeIndex = 0) }
        episodesJob = viewModelScope.launch {
            launch {
                val failure = seriesDelegate.refreshEpisodes(seriesId, seasonId)
                _uiState.update { it.copy(isLoadingEpisodes = false, episodesErrorMessage = failure) }
            }
            seriesDelegate.episodesFlow(seasonId, watchStateVersion).collect { episodes ->
                _uiState.update { state ->
                    state.copy(
                        episodes = episodes,
                        episodeIndex = state.episodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)),
                        isLoadingEpisodes = false
                    )
                }
            }
        }
    }

    fun selectSeason(index: Int) {
        val state = _uiState.value
        if (state.seasons.isEmpty()) return
        val target = index.mod(state.seasons.size)
        if (target == state.seasonIndex && state.episodes.isNotEmpty()) return
        val seriesId = state.item?.itemId ?: return
        _uiState.update { it.copy(seasonIndex = target, episodes = emptyList(), episodeIndex = 0) }
        openSeason(seriesId, state.seasons[target].itemId)
    }

    fun setSection(section: MediaDetailSection) {
        _uiState.update { it.copy(section = section) }
    }

    fun setActionIndex(index: Int) {
        val actions = _uiState.value.actions
        if (actions.isEmpty()) return
        _uiState.update { it.copy(section = MediaDetailSection.ACTIONS, actionIndex = index.mod(actions.size)) }
    }

    fun setEpisodeIndex(index: Int) {
        val size = _uiState.value.episodes.size
        if (size == 0) return
        _uiState.update { it.copy(section = MediaDetailSection.EPISODES, episodeIndex = index.coerceIn(0, size - 1)) }
    }

    fun toggleFavorite() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch { mediaRepository.setFavorite(item.itemId, !item.isFavorite) }
    }

    fun toggleWatched() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            mediaRepository.setPlayed(item.itemId, !item.played)
            watchStateVersion.update { it + 1 }
        }
    }

    fun toggleEpisodeWatched(index: Int) {
        val episode = _uiState.value.episodes.getOrNull(index) ?: return
        viewModelScope.launch {
            mediaRepository.setPlayed(episode.itemId, !episode.played)
            watchStateVersion.update { it + 1 }
        }
    }

    /**
     * Raises the resume choice for whatever the given surface would play. Answers false when there
     * is nothing to resume, which is the caller's signal to just start playback.
     */
    fun openResumePrompt(item: MediaItemUi?): Boolean {
        if (item == null || !item.isPlayable || !item.hasResumePosition) return false
        _uiState.update {
            it.copy(
                resumePrompt = MediaResumePrompt(
                    itemId = item.itemId,
                    title = item.title,
                    subtitle = item.episodeLabel ?: item.seriesName ?: item.year?.toString(),
                    resumeTicks = item.resumeTicks
                )
            )
        }
        return true
    }

    fun dismissResumePrompt() {
        _uiState.update { it.copy(resumePrompt = null) }
    }

    /**
     * Plays what the play action points at, waiting for it if it is not known yet.
     *
     * A series has no play target until its episodes arrive, and they arrive from the server after
     * the screen has already drawn. Answering an early press with nothing is what makes the button
     * look broken, so the press is held against the first target the season produces.
     */
    fun playPrimary(onPlay: (itemId: String, startOver: Boolean) -> Unit) {
        val state = _uiState.value
        state.playTarget?.let {
            onPlay(it.itemId, false)
            return
        }
        if (state.mode != MediaDetailMode.SERIES || playJob?.isActive == true) return
        playJob = viewModelScope.launch {
            val target = withTimeoutOrNull(PLAY_TARGET_WAIT_MS) {
                uiState.mapNotNull { it.playTarget }.first()
            } ?: return@launch
            onPlay(target.itemId, false)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun createInputHandler(
        onBack: () -> Unit,
        onPlay: (itemId: String, startOver: Boolean) -> Unit
    ): InputHandler = object : InputHandler {

        override fun onUp(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.ACTIONS -> InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.SEASONS -> {
                    setSection(MediaDetailSection.ACTIONS)
                    InputResult.HANDLED
                }
                MediaDetailSection.EPISODES -> {
                    if (state.episodeIndex > 0) setEpisodeIndex(state.episodeIndex - 1)
                    else setSection(if (state.hasSeasons) MediaDetailSection.SEASONS else MediaDetailSection.ACTIONS)
                    InputResult.HANDLED
                }
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.ACTIONS -> {
                    if (!state.hasSeasons) return InputResult.handled(SoundType.BOUNDARY)
                    setSection(MediaDetailSection.SEASONS)
                    InputResult.HANDLED
                }
                MediaDetailSection.SEASONS -> {
                    if (state.episodes.isEmpty()) return InputResult.handled(SoundType.BOUNDARY)
                    setEpisodeIndex(0)
                    InputResult.HANDLED
                }
                MediaDetailSection.EPISODES -> {
                    if (state.episodeIndex >= state.episodes.lastIndex) {
                        return InputResult.handled(SoundType.BOUNDARY)
                    }
                    setEpisodeIndex(state.episodeIndex + 1)
                    InputResult.HANDLED
                }
            }
        }

        override fun onLeft(): InputResult = adjust(-1)

        override fun onRight(): InputResult = adjust(1)

        private fun adjust(direction: Int): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.ACTIONS -> {
                    setActionIndex(state.actionIndex + direction)
                    InputResult.HANDLED
                }
                MediaDetailSection.SEASONS -> {
                    selectSeason(state.seasonIndex + direction)
                    InputResult.HANDLED
                }
                MediaDetailSection.EPISODES -> InputResult.handled(SoundType.SILENT)
            }
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.ACTIONS -> {
                    confirmAction(state)
                    InputResult.HANDLED
                }
                MediaDetailSection.SEASONS -> {
                    if (state.episodes.isEmpty()) return InputResult.handled(SoundType.BOUNDARY)
                    setEpisodeIndex(0)
                    InputResult.HANDLED
                }
                MediaDetailSection.EPISODES -> {
                    val episode = state.focusedEpisode ?: return InputResult.handled(SoundType.BOUNDARY)
                    onPlay(episode.itemId, false)
                    InputResult.HANDLED
                }
            }
        }

        override fun onLongConfirm(): InputResult {
            val state = _uiState.value
            val target = when (state.section) {
                MediaDetailSection.EPISODES -> state.focusedEpisode
                MediaDetailSection.ACTIONS ->
                    if (state.focusedAction == MediaDetailAction.PLAY) state.playTarget else null
                MediaDetailSection.SEASONS -> null
            } ?: return InputResult.handled(SoundType.SILENT)
            if (!openResumePrompt(target)) onPlay(target.itemId, false)
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = _uiState.value
            if (state.section == MediaDetailSection.EPISODES) {
                toggleEpisodeWatched(state.episodeIndex)
            } else {
                toggleFavorite()
            }
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            openDownloadPrompt()
            return InputResult.HANDLED
        }

        override fun onPrevSection(): InputResult = cycleSeason(-1)

        override fun onNextSection(): InputResult = cycleSeason(1)

        private fun cycleSeason(direction: Int): InputResult {
            val state = _uiState.value
            if (state.seasons.size < 2) return InputResult.handled(SoundType.SILENT)
            selectSeason(state.seasonIndex + direction)
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }

        private fun confirmAction(state: MediaDetailUiState) {
            when (state.focusedAction) {
                MediaDetailAction.PLAY -> playPrimary(onPlay)
                MediaDetailAction.DOWNLOAD -> openDownloadPrompt()
                MediaDetailAction.FAVORITE -> toggleFavorite()
                MediaDetailAction.WATCHED -> toggleWatched()
                null -> Unit
            }
        }
    }
}
