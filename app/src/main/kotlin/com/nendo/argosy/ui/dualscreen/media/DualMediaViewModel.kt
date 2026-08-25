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
import com.nendo.argosy.ui.screens.media.toCastUi
import com.nendo.argosy.ui.screens.media.toMediaItemUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
 */
class DualMediaViewModel(
    private val mediaRepository: MediaRepository,
    private val playback: StateFlow<ActiveMediaPlayback?>,
    private val gradientExtractionDelegate: com.nendo.argosy.ui.screens.common.GradientExtractionDelegate,
    private val getRelatedMedia: com.nendo.argosy.domain.usecase.media.GetRelatedMediaUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualMediaUiState())
    val uiState: StateFlow<DualMediaUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var isActive = false

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
                        nowPlayingTitle = active?.title.orEmpty()
                    )
                }
            }
        }
        viewModelScope.launch {
            playback
                .map { it?.itemId }
                .distinctUntilChanged()
                .collect { itemId -> if (isActive) restart(itemId) }
        }
    }

    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        if (active) {
            restart(playback.value?.itemId)
        } else {
            loadJob?.cancel()
            loadJob = null
            _uiState.update {
                it.copy(rows = emptyList(), focusedRowIndex = -1, isLoading = false)
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
                    else -> observeSeason(seasonId, entity)
                }
            }
        }
    }

    /**
     * The season the open episode belongs to, with the episode being watched marked in place. The
     * whole season is listed rather than the episodes after it, because "what else is in this one"
     * is the question a second screen is being asked.
     */
    private suspend fun observeSeason(seasonId: String, playingEntity: MediaItemEntity) {
        mediaRepository.observeEpisodes(seasonId).collectLatest { episodes ->
            val ids = (episodes.map { it.itemId } + playingEntity.itemId).distinct()
            val userData = mediaRepository.getUserDataFor(ids)
            val nowPlaying = playingEntity.toUi(userData[playingEntity.itemId])
            val ordered = episodes.sortedBy { it.indexNumber ?: Int.MAX_VALUE }
            val rows = buildList<DualMediaRow> {
                if (ordered.isNotEmpty()) {
                    add(DualMediaRow.Header(playingEntity.seriesName ?: nowPlaying.title))
                    ordered.forEach { episode ->
                        add(
                            DualMediaRow.Item(
                                item = episode.toUi(userData[episode.itemId]),
                                isNowPlaying = episode.itemId == playingEntity.itemId
                            )
                        )
                    }
                }
            }
            publish(nowPlaying, rows, preferItemId = playingEntity.itemId)
        }
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
                nowPlayingSubtitle = subtitleFor(nowPlaying),
                overview = entity.overview,
                rows = emptyList(),
                focusedRowIndex = -1,
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
                nowPlayingSubtitle = nowPlaying?.let { subtitleFor(it) },
                rows = rows,
                focusedRowIndex = focused,
                isLoading = false
            )
        }
    }

    private fun subtitleFor(item: MediaItemUi): String? {
        val series = item.seriesName ?: return item.runtimeLabel
        val episode = item.episodeLabel
        return listOfNotNull(series, episode).joinToString(" - ").takeIf { it.isNotBlank() }
    }

    private fun MediaItemEntity.toUi(userData: MediaUserDataEntity?): MediaItemUi =
        toMediaItemUi(
            mediaRepository,
            userData,
            emptyMap(),
            gradientExtractionDelegate.mediaGradients.value
        )
}
