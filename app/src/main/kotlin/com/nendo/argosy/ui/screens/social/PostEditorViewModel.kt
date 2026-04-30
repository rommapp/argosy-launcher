package com.nendo.argosy.ui.screens.social

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.ui.screens.doodle.GamePickerItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PostEditorSection {
    BODY, GAME, VISIBILITY, DOODLE
}

data class PostEditorUiState(
    val body: String = "",
    val currentSection: PostEditorSection = PostEditorSection.BODY,
    val linkedGameId: Int? = null,
    val linkedGameTitle: String? = null,
    val linkedGameCoverPath: String? = null,
    val isPublic: Boolean = false,
    val isPosting: Boolean = false,
    val showPostConfirm: Boolean = false,
    val postConfirmFocusIndex: Int = 0,
    val showDiscardDialog: Boolean = false,
    val discardFocusIndex: Int = 0,
    val showGamePicker: Boolean = false,
    val gamePickerQuery: String = "",
    val gamePickerResults: List<GamePickerItem> = emptyList(),
    val gamePickerFocusIndex: Int = 0,
    val gamePickerSearchFocused: Boolean = true,
    val doodleData: String? = null,
    val doodleSize: Int? = null
) {
    val canPost: Boolean get() = body.isNotBlank() || doodleData != null
    val hasContent: Boolean get() = body.isNotBlank() || linkedGameId != null || doodleData != null
    val showVisibility: Boolean get() = linkedGameId != null
    val hasDoodle: Boolean get() = doodleData != null
}

sealed class PostEditorEvent {
    data object Posted : PostEditorEvent()
    data class Error(val message: String) : PostEditorEvent()
}

private const val TAG = "PostEditorViewModel"
private const val SEARCH_DEBOUNCE_MS = 300L
private const val MAX_BODY_LENGTH = 2000

@HiltViewModel
class PostEditorViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostEditorUiState())
    val uiState: StateFlow<PostEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PostEditorEvent>()
    val events = _events.asSharedFlow()

    private var gameSearchJob: Job? = null

    fun setBody(text: String) {
        if (text.length <= MAX_BODY_LENGTH) {
            _uiState.update { it.copy(body = text) }
        }
    }

    private var lastLeftColumnSection: PostEditorSection = PostEditorSection.BODY

    private fun leftColumnSections(): List<PostEditorSection> = buildList {
        add(PostEditorSection.BODY)
        add(PostEditorSection.GAME)
        if (_uiState.value.showVisibility) add(PostEditorSection.VISIBILITY)
    }

    fun nextSection() {
        _uiState.update { state ->
            if (state.currentSection == PostEditorSection.DOODLE) return@update state
            val sections = leftColumnSections()
            val currentIndex = sections.indexOf(state.currentSection)
            val next = sections.getOrElse(currentIndex + 1) { sections.last() }
            lastLeftColumnSection = next
            state.copy(currentSection = next)
        }
    }

    fun previousSection() {
        _uiState.update { state ->
            if (state.currentSection == PostEditorSection.DOODLE) return@update state
            val sections = leftColumnSections()
            val currentIndex = sections.indexOf(state.currentSection)
            val prev = sections.getOrElse(currentIndex - 1) { sections.first() }
            lastLeftColumnSection = prev
            state.copy(currentSection = prev)
        }
    }

    fun focusDoodle() {
        val current = _uiState.value.currentSection
        if (current != PostEditorSection.DOODLE) {
            lastLeftColumnSection = current
        }
        _uiState.update { it.copy(currentSection = PostEditorSection.DOODLE) }
    }

    fun focusFromDoodle() {
        _uiState.update { it.copy(currentSection = lastLeftColumnSection) }
    }

    fun togglePublic() {
        _uiState.update { it.copy(isPublic = !it.isPublic) }
    }

    fun showGamePicker() {
        _uiState.update {
            it.copy(
                showGamePicker = true,
                gamePickerQuery = "",
                gamePickerFocusIndex = 0,
                gamePickerSearchFocused = true
            )
        }
        viewModelScope.launch {
            val recent = gameRepository.getRecentlyPlayed(10)
            val items = recent.map { it.toPickerItem() }
            _uiState.update { it.copy(gamePickerResults = items) }
        }
    }

    fun hideGamePicker() {
        _uiState.update { it.copy(showGamePicker = false) }
    }

    fun updateGamePickerQuery(query: String) {
        _uiState.update { it.copy(gamePickerQuery = query, gamePickerFocusIndex = 0) }
        gameSearchJob?.cancel()
        if (query.isBlank()) {
            viewModelScope.launch {
                val recent = gameRepository.getRecentlyPlayed(10)
                _uiState.update { it.copy(gamePickerResults = recent.map { g -> g.toPickerItem() }) }
            }
            return
        }
        gameSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = gameRepository.searchForQuickMenu(query, 10).first()
            _uiState.update { it.copy(gamePickerResults = results.map { g -> g.toPickerItem() }) }
        }
    }

    fun moveGamePickerFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = state.gamePickerResults.size
            val newIndex = (state.gamePickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(gamePickerFocusIndex = newIndex)
        }
    }

    fun focusGamePickerSearch() {
        _uiState.update { it.copy(gamePickerSearchFocused = true) }
    }

    fun focusGamePickerList() {
        _uiState.update { it.copy(gamePickerSearchFocused = false, gamePickerFocusIndex = 0) }
    }

    fun selectGame() {
        val state = _uiState.value
        if (state.gamePickerFocusIndex == 0) {
            clearLinkedGame()
            hideGamePicker()
            return
        }
        val item = state.gamePickerResults.getOrNull(state.gamePickerFocusIndex - 1) ?: return
        _uiState.update {
            it.copy(
                linkedGameId = item.igdbId,
                linkedGameTitle = item.title,
                linkedGameCoverPath = item.coverPath
            )
        }
        hideGamePicker()
    }

    fun setLinkedGame(gameId: Int?, gameTitle: String?, gameCoverPath: String?) {
        _uiState.update {
            it.copy(
                linkedGameId = gameId,
                linkedGameTitle = gameTitle,
                linkedGameCoverPath = gameCoverPath
            )
        }
    }

    fun clearLinkedGame() {
        _uiState.update {
            it.copy(
                linkedGameId = null,
                linkedGameTitle = null,
                linkedGameCoverPath = null,
                isPublic = false
            )
        }
    }

    fun attachDoodle(data: String, size: Int) {
        _uiState.update { it.copy(doodleData = data, doodleSize = size) }
    }

    fun removeDoodle() {
        _uiState.update { it.copy(doodleData = null, doodleSize = null) }
    }

    fun showPostConfirm() {
        _uiState.update { it.copy(showPostConfirm = true, postConfirmFocusIndex = 0) }
    }

    fun hidePostConfirm() {
        _uiState.update { it.copy(showPostConfirm = false) }
    }

    fun movePostConfirmFocus(delta: Int) {
        _uiState.update { state ->
            state.copy(postConfirmFocusIndex = (state.postConfirmFocusIndex + delta).coerceIn(0, 1))
        }
    }

    fun showDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = true, discardFocusIndex = 0) }
    }

    fun hideDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun moveDiscardFocus(delta: Int) {
        _uiState.update { state ->
            state.copy(discardFocusIndex = (state.discardFocusIndex + delta).coerceIn(0, 1))
        }
    }

    fun post() {
        val state = _uiState.value
        if (!state.canPost) return

        _uiState.update { it.copy(isPosting = true, showPostConfirm = false) }

        viewModelScope.launch {
            try {
                socialRepository.createPost(
                    body = state.body,
                    canvasSize = state.doodleSize,
                    doodleData = state.doodleData,
                    igdbId = state.linkedGameId,
                    gameTitle = state.linkedGameTitle,
                    public = state.isPublic
                )
                _uiState.update { it.copy(isPosting = false) }
                _events.emit(PostEditorEvent.Posted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create post", e)
                _uiState.update { it.copy(isPosting = false) }
                _events.emit(PostEditorEvent.Error("Failed to create post"))
            }
        }
    }

    private fun com.nendo.argosy.data.local.entity.GameEntity.toPickerItem() = GamePickerItem(
        id = id,
        igdbId = igdbId?.toInt(),
        title = title,
        platform = platformSlug,
        coverPath = coverPath
    )
}
