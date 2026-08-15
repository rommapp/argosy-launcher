package com.nendo.argosy.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.domain.usecase.media.GetRelatedMediaUseCase
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
    private val downloadDelegate: MediaDownloadDelegate,
    private val availabilityVerifier: MediaAvailabilityVerifier,
    private val getRelatedMedia: GetRelatedMediaUseCase,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState: StateFlow<MediaDetailUiState> = _uiState.asStateFlow()

    private val _backdropSettings = MutableStateFlow(MediaBackdropSettings())
    val backdropSettings: StateFlow<MediaBackdropSettings> = _backdropSettings.asStateFlow()

    private val watchStateVersion = MutableStateFlow(0)

    private var loadedItemId: String? = null
    private var openedSeasonId: String? = null
    private var siblingLibraryId: String? = null
    private var itemJob: Job? = null
    private var seasonsJob: Job? = null
    private var episodesJob: Job? = null
    private var downloadJob: Job? = null
    private var playJob: Job? = null
    private var siblingsJob: Job? = null
    private var extrasJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _backdropSettings.value = MediaBackdropSettings(
                    blur = prefs.backgroundBlur,
                    saturation = prefs.backgroundSaturation,
                    opacity = prefs.backgroundOpacity
                )
            }
        }
    }

    /**
     * Opens the title the route names, and only ever the first one it names. The shoulder buttons
     * walk this screen along its library without the route changing, so a second call - a rotation,
     * a return from the player - would otherwise drag the screen back to the title the route still
     * carries rather than the one the user walked to.
     */
    fun load(itemId: String) {
        if (loadedItemId != null) return
        openItem(itemId)
    }

    /**
     * Swaps the screen to a title wholesale: a new item, its own seasons and episodes, and focus back
     * on the rail's first action, which is where opening a title from anywhere else starts.
     *
     * The sibling run is the one thing carried across. It belongs to the library rather than to the
     * title, so a press held down walks it at the speed of the presses instead of waiting for each
     * title to land before the next step is possible.
     */
    private fun openItem(itemId: String) {
        if (loadedItemId == itemId) return
        loadedItemId = itemId
        openedSeasonId = null
        itemJob?.cancel()
        seasonsJob?.cancel()
        episodesJob?.cancel()
        downloadJob?.cancel()
        playJob?.cancel()
        extrasJob?.cancel()
        itemJob = null
        seasonsJob = null
        episodesJob = null
        downloadJob = null
        playJob = null
        extrasJob = null
        val siblings = _uiState.value.siblingItemIds
        _uiState.value = MediaDetailUiState(
            isLoading = true,
            siblingItemIds = siblings,
            currentItemIndex = siblings.indexOf(itemId)
        )

        availabilityVerifier.verifyOnOpen()
        itemJob = viewModelScope.launch {
            combine(
                mediaRepository.observeItem(itemId),
                mediaRepository.observeUserData(itemId),
                availabilityVerifier.availability
            ) { entity, userData, verified ->
                entity?.toMediaItemUi(mediaRepository, userData, verified)
            }.collect { item -> applyItem(itemId, item) }
        }
    }

    /**
     * Applies what one title's observers report, and drops anything a title that has since been
     * walked away from is still saying. A cancelled collector can have one emission already in
     * flight, and the screen holding the show before the one the user just stepped to is worse than
     * the screen holding nothing yet.
     */
    private fun applyItem(itemId: String, item: MediaItemUi?) {
        if (itemId != loadedItemId) return
        if (item == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "This title is no longer in the library."
                )
            }
            return
        }
        observeSiblings(item.libraryId)
        val mode = if (item.isSeries) MediaDetailMode.SERIES else MediaDetailMode.MOVIE
        _uiState.update {
            it.copy(
                item = item,
                mode = mode,
                isLoading = false,
                errorMessage = null
            ).withRail()
        }
        if (mode == MediaDetailMode.SERIES && seasonsJob == null) observeSeasons(itemId)
        if (downloadJob == null) observeDownloadSummary(item)
        if (extrasJob == null) loadExtras(itemId)
    }

    /**
     * Fills the cast and the titles like this one. Both are answered from what is already stored, so
     * a title opened with no network still draws them; a title synced before credits were collected
     * simply has none until its library syncs again.
     */
    private fun loadExtras(itemId: String) {
        extrasJob = viewModelScope.launch {
            val entity = mediaRepository.getItem(itemId) ?: return@launch
            val cast = mediaRepository.getCredits(itemId)
                .distinctBy { it.personId }
                .map { it.toCastUi(mediaRepository) }
            val similar = getRelatedMedia(entity).map { it.toMediaItemUi(mediaRepository, null) }
            if (itemId != loadedItemId) return@launch
            _uiState.update { it.copy(cast = cast, similar = similar).withRail() }
        }
    }

    /**
     * Tracks the library this title sits in, which is the run the shoulder buttons walk.
     *
     * It is the library's own query rather than a remembered list of whatever surface the user came
     * from, so the order here is the order the grid showed and the two cannot drift. A title with no
     * library has no run: it keeps an empty one, and the shoulder buttons refuse rather than invent a
     * neighbour.
     */
    private fun observeSiblings(libraryId: String?) {
        if (libraryId == null) {
            siblingsJob?.cancel()
            siblingsJob = null
            siblingLibraryId = null
            _uiState.update { it.copy(siblingItemIds = emptyList(), currentItemIndex = -1) }
            return
        }
        if (siblingLibraryId == libraryId && siblingsJob?.isActive == true) return
        siblingsJob?.cancel()
        siblingLibraryId = libraryId
        siblingsJob = viewModelScope.launch {
            mediaRepository.observeLibraryItems(libraryId).collect { entities ->
                val ids = entities.map { it.itemId }
                val current = loadedItemId
                _uiState.update {
                    it.copy(
                        siblingItemIds = ids,
                        currentItemIndex = ids.indexOfFirst { id -> id == current }
                    )
                }
            }
        }
    }

    /**
     * Steps to the title beside this one, in the order its library is shown in. Answers false at
     * either end of that run and for a title that has none, which is the caller's cue to sound a
     * boundary: walking off the end of a grid is not a move to its other end.
     */
    private fun openSiblingTitle(direction: Int): Boolean {
        val state = _uiState.value
        if (state.currentItemIndex < 0) return false
        val target = state.siblingItemIds.getOrNull(state.currentItemIndex + direction) ?: return false
        openItem(target)
        return true
    }

    /**
     * Keeps the aggregate fresh. A series' downloaded state is a count against a count, and both
     * halves move without the series row itself changing - an episode finishing rewrites the episode,
     * the queue shrinking is not a database write at all, and a card being pulled changes how many of
     * the copies can be reached without changing how many exist.
     */
    private fun observeDownloadSummary(item: MediaItemUi) {
        downloadJob = viewModelScope.launch {
            combine(
                downloadDelegate.pendingCount(item.itemId, item.isSeries),
                availabilityVerifier.availability
            ) { pending, _ -> pending }.collect { pending ->
                if (item.itemId != loadedItemId) return@collect
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
        if (prompt.step == MediaDownloadStep.EPISODES) {
            when {
                prompt.isEpisodeCancelFocused -> dismissDownloadPrompt()
                prompt.isEpisodeDownloadFocused -> commitEpisodeSelection()
                else -> _uiState.update {
                    it.copy(downloadPrompt = downloadDelegate.toggleEpisode(prompt))
                }
            }
            return
        }
        viewModelScope.launch {
            val next = downloadDelegate.advance(prompt, item, state.episodes)
            _uiState.update { it.copy(downloadPrompt = next) }
            if (next == null) refreshDownloadSummary()
        }
    }

    /**
     * Folds a season away in the chooser. Separate from confirm because in that step confirm means
     * "tick this", and a press that both ticks and folds would make one of the two unreachable.
     */
    fun moveDownloadSideways(towardsEnd: Boolean) {
        val prompt = _uiState.value.downloadPrompt ?: return
        if (prompt.step != MediaDownloadStep.EPISODES) return
        _uiState.update {
            it.copy(downloadPrompt = downloadDelegate.moveSideways(prompt, towardsEnd))
        }
    }

    /**
     * Leaves the chooser with whatever is ticked and asks for a quality.
     */
    fun commitEpisodeSelection() {
        val prompt = _uiState.value.downloadPrompt ?: return
        if (prompt.step != MediaDownloadStep.EPISODES) return
        viewModelScope.launch {
            val next = downloadDelegate.confirmEpisodeSelection(prompt)
            _uiState.update { it.copy(downloadPrompt = next) }
            if (next == null) refreshDownloadSummary()
        }
    }

    fun dismissDownloadPrompt() {
        _uiState.update { it.copy(downloadPrompt = null) }
    }

    /**
     * Raises the options menu over whatever is focused.
     *
     * This is the half the permanent rail cannot do. The rail is always on screen and so always acts
     * on the title; this is raised on demand and takes its target from the moment it opens, which is
     * how an episode gets marked watched or a season re-fetched without the rail having to grow a row
     * whose meaning changes depending on where focus happens to be. An episode list gives it the
     * focused episode and the rest of the screen gives it the title, and it says which one it took
     * rather than leaving the user to guess which watched flag a confirm will move.
     */
    fun openMenu() {
        val state = _uiState.value
        val item = state.item ?: return
        val target = if (state.section == MediaDetailSection.EPISODES) state.focusedEpisode ?: item else item
        _uiState.update {
            it.copy(
                menu = MediaMenuState(
                    targetItemId = target.itemId,
                    title = target.title,
                    subtitle = menuSubtitleFor(target, item),
                    actions = buildMediaMenu(
                        MediaMenuContext(
                            canRefreshEpisodes = item.isSeries && state.selectedSeason != null,
                            hasDownloads = state.downloadSummary.downloaded > 0,
                            hasLibrary = item.libraryId != null
                        )
                    ),
                    targetPlayed = target.played,
                    targetIsFavorite = target.isFavorite
                )
            )
        }
    }

    private fun menuSubtitleFor(target: MediaItemUi, item: MediaItemUi): String? =
        if (target.itemId == item.itemId) item.year?.toString() else target.episodeLabel ?: item.title

    fun moveMenuFocus(delta: Int) {
        _uiState.update { state ->
            val menu = state.menu ?: return@update state
            if (menu.actions.isEmpty()) return@update state
            state.copy(menu = menu.copy(focusedIndex = (menu.focusedIndex + delta).mod(menu.actions.size)))
        }
    }

    fun focusMenuOption(index: Int) {
        _uiState.update { state ->
            val menu = state.menu ?: return@update state
            if (menu.actions.isEmpty()) return@update state
            state.copy(menu = menu.copy(focusedIndex = index.coerceIn(0, menu.actions.lastIndex)))
        }
    }

    fun dismissMenu() {
        _uiState.update { it.copy(menu = null) }
    }

    fun confirmMenuOption(onNavigateToLibrary: (String) -> Unit) {
        val state = _uiState.value
        val menu = state.menu ?: return
        if (menu.isBusy) return
        when (menu.focusedAction) {
            MediaMenuAction.ToggleWatched -> {
                setPlayed(menu.targetItemId, !menu.targetPlayed)
                dismissMenu()
            }
            MediaMenuAction.ToggleFavorite -> {
                setFavorite(menu.targetItemId, !menu.targetIsFavorite)
                dismissMenu()
            }
            MediaMenuAction.Download -> {
                dismissMenu()
                openDownloadPrompt()
            }
            MediaMenuAction.RemoveDownloads -> {
                dismissMenu()
                openRemovalPrompt()
            }
            MediaMenuAction.RefreshSeries -> refreshFromServer()
            MediaMenuAction.GoToLibrary -> {
                val libraryId = state.item?.libraryId ?: return
                dismissMenu()
                onNavigateToLibrary(libraryId)
            }
            null -> Unit
        }
    }

    /**
     * Re-fetches the open season. A series stores its episodes a season at a time, so this is the
     * whole of what "refresh this series" can mean without pulling seasons the screen is not showing.
     */
    private fun refreshFromServer() {
        val state = _uiState.value
        val seriesId = state.item?.itemId ?: return
        val seasonId = state.selectedSeason?.itemId ?: return
        _uiState.update { it.copy(menu = it.menu?.copy(isBusy = true)) }
        viewModelScope.launch {
            val failure = seriesDelegate.refreshEpisodes(seriesId, seasonId)
            _uiState.update { it.copy(menu = null, episodesErrorMessage = failure) }
        }
    }

    /**
     * Takes the removal branch of the download prompt straight to its confirmation, so removing what
     * is already downloaded does not require walking back through the download choices to find it.
     */
    private fun openRemovalPrompt() {
        val state = _uiState.value
        val item = state.item ?: return
        viewModelScope.launch {
            val prompt = downloadDelegate.openRemovalPrompt(item)
            _uiState.update { it.copy(downloadPrompt = prompt) }
        }
    }

    private fun setPlayed(itemId: String, played: Boolean) {
        viewModelScope.launch {
            mediaRepository.setPlayed(itemId, played)
            watchStateVersion.update { it + 1 }
        }
    }

    private fun setFavorite(itemId: String, isFavorite: Boolean) {
        viewModelScope.launch { mediaRepository.setFavorite(itemId, isFavorite) }
    }

    private fun observeSeasons(seriesId: String) {
        seasonsJob = viewModelScope.launch {
            seriesDelegate.seasonsFlow(seriesId).collect { seasons ->
                if (seriesId != loadedItemId) return@collect
                val previousSeasonId = _uiState.value.selectedSeason?.itemId
                val index = seasons.indexOfFirst { it.itemId == previousSeasonId }.takeIf { it >= 0 } ?: 0
                _uiState.update {
                    it.copy(
                        seasons = seasons,
                        seasonIndex = index.coerceAtMost((seasons.size - 1).coerceAtLeast(0))
                    ).withRail()
                }
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
                if (seasonId != openedSeasonId) return@launch
                _uiState.update { it.copy(isLoadingEpisodes = false, episodesErrorMessage = failure) }
            }
            seriesDelegate.episodesFlow(seasonId, watchStateVersion).collect { episodes ->
                if (seasonId != openedSeasonId) return@collect
                _uiState.update { state ->
                    state.copy(
                        episodes = episodes,
                        episodeIndex = state.episodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)),
                        isLoadingEpisodes = false
                    ).withRail()
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
        _uiState.update { it.copy(seasonIndex = target, episodes = emptyList(), episodeIndex = 0).withRail() }
        openSeason(seriesId, state.seasons[target].itemId)
    }

    /**
     * Opens a season and puts focus on the tabs, which is what a tap on a tab means. The shoulder
     * buttons take the other path and change the season without moving focus at all.
     */
    fun focusSeason(index: Int) {
        setSection(MediaDetailSection.SEASONS)
        selectSeason(index)
    }

    /**
     * Moves focus between the rail and the regions beside it, keeping the rail's own selection on the
     * row that names wherever focus went. That single rule is what leaves a dim marker on Seasons or
     * Episodes while focus is out in them.
     *
     * Returning to the rail lands on the last action row instead of the row focus came from. A
     * section row is entered by arriving on it, so standing on one after leaving its section would
     * re-open what was just left; the row below the divider that is a destination rather than a
     * doorway is Options, and that is where every path back to the rail terminates.
     */
    fun setSection(section: MediaDetailSection) {
        _uiState.update { state ->
            val index = when (section) {
                MediaDetailSection.SEASONS -> state.rowIndexOf(MediaDetailRow.SEASONS) ?: state.rowIndex
                MediaDetailSection.EPISODES -> state.rowIndexOf(MediaDetailRow.EPISODES) ?: state.rowIndex
                MediaDetailSection.CAST -> state.rowIndexOf(MediaDetailRow.CAST) ?: state.rowIndex
                MediaDetailSection.SIMILAR -> state.rowIndexOf(MediaDetailRow.SIMILAR) ?: state.rowIndex
                MediaDetailSection.MENU -> state.lastActionIndex
            }
            state.copy(section = section, rowIndex = index)
        }
    }

    /**
     * What a tap on a rail row means. A tap is the move and the press at once, so it lands where the
     * gamepad would have landed after both: a section row opens its region rather than parking the
     * rail on a doorway, and every other row is selected and then run.
     */
    fun activateRow(index: Int, onPlay: (itemId: String, startOver: Boolean) -> Unit) {
        val rows = _uiState.value.rows
        val row = rows.getOrNull(index) ?: return
        val section = row.section
        if (section != null) {
            openSection(section)
            return
        }
        _uiState.update { it.copy(section = MediaDetailSection.MENU, rowIndex = index) }
        confirmRow(onPlay)
    }

    /**
     * Steps the rail selection, clamping at the top of the rail and at the last action row. Answers
     * false when it is already against the end it was asked to move past, which is the caller's cue
     * to cross into the content below or sound a boundary rather than wrap: the rows past the
     * divider are doorways, so the rail's own run ends at Options.
     */
    private fun moveRowFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.rows.isEmpty()) return false
        val target = (state.rowIndex + delta).coerceIn(0, state.lastActionIndex)
        if (target == state.rowIndex) return false
        _uiState.update { it.copy(rowIndex = target) }
        return true
    }

    /**
     * Runs whatever the rail currently points at. Shared by the gamepad's confirm and a tap on the
     * row, so the two cannot drift. A section row runs by opening its region, which is the whole of
     * what it does - the rail names the region, the region holds the content.
     */
    private fun confirmRow(onPlay: (itemId: String, startOver: Boolean) -> Unit): Boolean =
        when (_uiState.value.focusedRow) {
            MediaDetailRow.PLAY -> { playPrimary(onPlay); true }
            MediaDetailRow.DOWNLOAD -> { openDownloadPrompt(); true }
            MediaDetailRow.FAVORITE -> { toggleFavorite(); true }
            MediaDetailRow.WATCHED -> { toggleWatched(); true }
            MediaDetailRow.OPTIONS -> { openMenu(); true }
            MediaDetailRow.SEASONS -> openSection(MediaDetailSection.SEASONS)
            MediaDetailRow.EPISODES -> openSection(MediaDetailSection.EPISODES)
            MediaDetailRow.CAST -> openSection(MediaDetailSection.CAST)
            MediaDetailRow.SIMILAR -> openSection(MediaDetailSection.SIMILAR)
            null -> false
        }

    /**
     * Moves focus off the rail and into the region beside it, entering the first region the title
     * has. The rail's selection only ever rests on an action row, so there is no named region to
     * prefer over that one. Answers false when there is no region to enter, which is every movie and
     * any series whose seasons have not arrived.
     */
    private fun enterContent(): Boolean = openSection(_uiState.value.contentEntrySection)

    private fun openSection(section: MediaDetailSection?): Boolean {
        val state = _uiState.value
        return when (section) {
            MediaDetailSection.SEASONS -> {
                if (!state.hasSeasons) return false
                setSection(MediaDetailSection.SEASONS)
                true
            }
            MediaDetailSection.EPISODES -> {
                if (state.episodes.isEmpty()) return false
                setEpisodeIndex(state.episodeIndex)
                true
            }
            MediaDetailSection.CAST -> {
                if (state.cast.isEmpty()) return false
                setSection(MediaDetailSection.CAST)
                true
            }
            MediaDetailSection.SIMILAR -> {
                if (state.similar.isEmpty()) return false
                setSection(MediaDetailSection.SIMILAR)
                true
            }
            else -> false
        }
    }

    /**
     * Steps focus out of a horizontal rail and up to whatever sits above it, which is the region
     * before it when the title has one and the left rail when it does not.
     */
    private fun leaveSectionUpwards(state: MediaDetailUiState, section: MediaDetailSection) {
        val previous = state.sectionBefore(section)
        if (previous == null || !openSection(previous)) setSection(MediaDetailSection.MENU)
    }

    fun setCastIndex(index: Int) {
        val size = _uiState.value.cast.size
        if (size == 0) return
        _uiState.update { state ->
            state.copy(
                section = MediaDetailSection.CAST,
                castIndex = index.coerceIn(0, size - 1),
                rowIndex = state.rowIndexOf(MediaDetailRow.CAST) ?: state.rowIndex
            )
        }
    }

    fun setSimilarIndex(index: Int) {
        val size = _uiState.value.similar.size
        if (size == 0) return
        _uiState.update { state ->
            state.copy(
                section = MediaDetailSection.SIMILAR,
                similarIndex = index.coerceIn(0, size - 1),
                rowIndex = state.rowIndexOf(MediaDetailRow.SIMILAR) ?: state.rowIndex
            )
        }
    }

    /**
     * Opens the title the similar rail is pointing at, replacing this screen's subject the way the
     * shoulder buttons do. Going somewhere from here is going to another title, not to a new screen
     * stacked on this one.
     */
    fun openSimilarTitle() {
        val target = _uiState.value.focusedSimilar ?: return
        openItem(target.itemId)
    }

    fun setEpisodeIndex(index: Int) {
        val size = _uiState.value.episodes.size
        if (size == 0) return
        _uiState.update { state ->
            state.copy(
                section = MediaDetailSection.EPISODES,
                episodeIndex = index.coerceIn(0, size - 1),
                rowIndex = state.rowIndexOf(MediaDetailRow.EPISODES) ?: state.rowIndex
            )
        }
    }

    fun toggleFavorite() {
        val item = _uiState.value.item ?: return
        setFavorite(item.itemId, !item.isFavorite)
    }

    fun toggleWatched() {
        val item = _uiState.value.item ?: return
        setPlayed(item.itemId, !item.played)
    }

    fun toggleEpisodeWatched(index: Int) {
        val episode = _uiState.value.episodes.getOrNull(index) ?: return
        setPlayed(episode.itemId, !episode.played)
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

        /**
         * Up is Down read backwards: out of the episodes only once the first one is reached, out of
         * the seasons onto the rail, and up the rail's action rows to Play. Every step out of a
         * section lands on something that acts rather than on the row that would let focus straight
         * back in.
         */
        override fun onUp(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.MENU ->
                    if (moveRowFocus(-1)) InputResult.HANDLED
                    else InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.SEASONS -> {
                    setSection(MediaDetailSection.MENU)
                    InputResult.HANDLED
                }
                MediaDetailSection.EPISODES -> {
                    if (state.episodeIndex > 0) setEpisodeIndex(state.episodeIndex - 1)
                    else setSection(if (state.hasSeasons) MediaDetailSection.SEASONS else MediaDetailSection.MENU)
                    InputResult.HANDLED
                }
                MediaDetailSection.CAST, MediaDetailSection.SIMILAR -> {
                    leaveSectionUpwards(state, state.section)
                    InputResult.HANDLED
                }
            }
        }

        /**
         * Down walks the rail's action rows and then keeps going, into the content rather than onto
         * the doorway rows that name it. From the seasons it drops into the episodes at the episode
         * that was last left, so coming back down after bumping a season resumes where it was.
         */
        override fun onDown(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.MENU ->
                    if (moveRowFocus(1) || enterContent()) InputResult.HANDLED
                    else InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.SEASONS ->
                    if (openSection(MediaDetailSection.EPISODES)) InputResult.HANDLED
                    else InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.EPISODES -> {
                    if (state.episodeIndex >= state.episodes.lastIndex) {
                        return if (openSection(state.sectionAfter(MediaDetailSection.EPISODES))) {
                            InputResult.HANDLED
                        } else {
                            InputResult.handled(SoundType.BOUNDARY)
                        }
                    }
                    setEpisodeIndex(state.episodeIndex + 1)
                    InputResult.HANDLED
                }
                MediaDetailSection.CAST, MediaDetailSection.SIMILAR ->
                    if (openSection(state.sectionAfter(state.section))) InputResult.HANDLED
                    else InputResult.handled(SoundType.BOUNDARY)
            }
        }

        /**
         * Left runs back along the content towards the rail, one region at a time. From the episodes
         * it reaches the seasons, which is the quick way to bump the season without walking an
         * episode list to its top first. The tabs then spend their own horizontal axis - Left is how
         * a tab row steps backwards - and only once there is no earlier season does the press carry
         * on to the rail. Up is the one-press exit from the seasons for anyone who does not want to
         * walk them.
         */
        override fun onLeft(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.MENU -> InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.SEASONS -> {
                    if (state.seasonIndex > 0) selectSeason(state.seasonIndex - 1)
                    else setSection(MediaDetailSection.MENU)
                    InputResult.HANDLED
                }
                MediaDetailSection.EPISODES -> {
                    setSection(if (state.hasSeasons) MediaDetailSection.SEASONS else MediaDetailSection.MENU)
                    InputResult.HANDLED
                }
                MediaDetailSection.CAST -> {
                    if (state.castIndex > 0) setCastIndex(state.castIndex - 1)
                    else setSection(MediaDetailSection.MENU)
                    InputResult.HANDLED
                }
                MediaDetailSection.SIMILAR -> {
                    if (state.similarIndex > 0) setSimilarIndex(state.similarIndex - 1)
                    else setSection(MediaDetailSection.MENU)
                    InputResult.HANDLED
                }
            }
        }

        override fun onRight(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.MENU ->
                    if (enterContent()) InputResult.HANDLED
                    else InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.SEASONS -> {
                    if (state.seasonIndex >= state.seasons.lastIndex) {
                        return InputResult.handled(SoundType.BOUNDARY)
                    }
                    selectSeason(state.seasonIndex + 1)
                    InputResult.HANDLED
                }
                MediaDetailSection.EPISODES -> InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.CAST -> {
                    if (state.castIndex >= state.cast.lastIndex) {
                        return InputResult.handled(SoundType.BOUNDARY)
                    }
                    setCastIndex(state.castIndex + 1)
                    InputResult.HANDLED
                }
                MediaDetailSection.SIMILAR -> {
                    if (state.similarIndex >= state.similar.lastIndex) {
                        return InputResult.handled(SoundType.BOUNDARY)
                    }
                    setSimilarIndex(state.similarIndex + 1)
                    InputResult.HANDLED
                }
            }
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            return when (state.section) {
                MediaDetailSection.MENU ->
                    if (confirmRow(onPlay)) InputResult.HANDLED
                    else InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.SEASONS ->
                    if (openSection(MediaDetailSection.EPISODES)) InputResult.HANDLED
                    else InputResult.handled(SoundType.BOUNDARY)
                MediaDetailSection.EPISODES -> {
                    val episode = state.focusedEpisode ?: return InputResult.handled(SoundType.BOUNDARY)
                    onPlay(episode.itemId, false)
                    InputResult.HANDLED
                }
                MediaDetailSection.CAST -> InputResult.handled(SoundType.SILENT)
                MediaDetailSection.SIMILAR -> {
                    if (state.focusedSimilar == null) return InputResult.handled(SoundType.BOUNDARY)
                    openSimilarTitle()
                    InputResult.HANDLED
                }
            }
        }

        override fun onLongConfirm(): InputResult {
            val state = _uiState.value
            val target = when (state.section) {
                MediaDetailSection.EPISODES -> state.focusedEpisode
                MediaDetailSection.MENU ->
                    if (state.focusedRow == MediaDetailRow.PLAY) state.playTarget else null
                MediaDetailSection.SEASONS -> null
                MediaDetailSection.CAST -> null
                MediaDetailSection.SIMILAR -> state.focusedSimilar?.takeIf { it.isPlayable }
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
            openMenu()
            return InputResult.HANDLED
        }

        /**
         * The shoulders walk the library rather than the title's own contents: the show or film
         * before this one, and the one after. A season is changed from the tabs beside the rail,
         * which is where the seasons are.
         */
        override fun onPrevSection(): InputResult =
            if (openSiblingTitle(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onNextSection(): InputResult =
            if (openSiblingTitle(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }
    }
}
