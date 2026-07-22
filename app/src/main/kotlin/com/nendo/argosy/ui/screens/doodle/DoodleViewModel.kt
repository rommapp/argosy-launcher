package com.nendo.argosy.ui.screens.doodle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.GameRepository
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed class DoodleEvent {
    data class Done(
        val doodleData: String,
        val canvasSize: Int,
        val gameId: Int?,
        val gameTitle: String?,
        val gameCoverPath: String?
    ) : DoodleEvent()
    data object AvatarSaved : DoodleEvent()
    data class Error(val message: String) : DoodleEvent()
}

@HiltViewModel
class DoodleViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoodleUiState())
    val uiState: StateFlow<DoodleUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DoodleEvent>()
    val events = _events.asSharedFlow()

    fun initAvatarMode() {
        if (_uiState.value.avatarMode) return
        _uiState.update {
            it.copy(avatarMode = true, canvasSize = CanvasSize.SMALL, sizeFocusIndex = 0)
        }
        viewModelScope.launch {
            val saved = syncPreferencesRepository.preferences.first().socialAvatarDoodle ?: return@launch
            val decoded = runCatching { DoodleEncoder.decodeFromBase64(saved) }.getOrNull() ?: return@launch
            if (decoded.size == CanvasSize.LARGE) return@launch
            _uiState.update {
                it.copy(
                    canvasSize = decoded.size,
                    sizeFocusIndex = decoded.size.sizeEnum,
                    pixels = decoded.pixels
                )
            }
        }
    }

    fun moveCursor(dx: Int, dy: Int) {
        _uiState.update { state ->
            val newX = (state.cursorX + dx).coerceIn(0, state.canvasSize.pixels - 1)
            val newY = (state.cursorY + dy).coerceIn(0, state.canvasSize.pixels - 1)

            val newPixels = if (state.isDrawing && state.selectedTool == DoodleTool.PEN) {
                drawPixel(state.pixels, newX, newY, state.selectedColor)
            } else state.pixels

            state.copy(cursorX = newX, cursorY = newY, pixels = newPixels)
        }
    }

    fun drawAtCursor() {
        pushUndoSnapshot()
        _uiState.update { state ->
            when (state.selectedTool) {
                DoodleTool.PEN -> {
                    val newPixels = drawPixel(state.pixels, state.cursorX, state.cursorY, state.selectedColor)
                    state.copy(pixels = newPixels, isDrawing = true)
                }
                DoodleTool.LINE -> {
                    if (state.lineStartX == null) {
                        state.copy(lineStartX = state.cursorX, lineStartY = state.cursorY, isDrawing = true)
                    } else {
                        state
                    }
                }
                DoodleTool.FILL -> {
                    val newPixels = floodFill(
                        state.pixels,
                        state.cursorX,
                        state.cursorY,
                        state.selectedColor,
                        state.canvasSize.pixels
                    )
                    state.copy(pixels = newPixels)
                }
            }
        }
    }

    fun stopDrawing() {
        _uiState.update { state ->
            if (state.selectedTool == DoodleTool.LINE && state.lineStartX != null) {
                val line = bresenhamLine(
                    state.lineStartX, state.lineStartY!!,
                    state.cursorX, state.cursorY
                )
                var newPixels = state.pixels
                line.forEach { (x, y) ->
                    newPixels = drawPixel(newPixels, x, y, state.selectedColor)
                }
                state.copy(pixels = newPixels, lineStartX = null, lineStartY = null, isDrawing = false)
            } else {
                state.copy(isDrawing = false)
            }
        }
    }

    fun cancelDrawing() {
        _uiState.update { state ->
            state.copy(isDrawing = false, lineStartX = null, lineStartY = null)
        }
    }

    fun drawAt(x: Int, y: Int) {
        _uiState.update { state ->
            if (state.selectedTool == DoodleTool.PEN) {
                val newPixels = drawPixel(state.pixels, x, y, state.selectedColor)
                state.copy(pixels = newPixels, cursorX = x, cursorY = y)
            } else {
                state.copy(cursorX = x, cursorY = y)
            }
        }
    }

    fun tapAt(x: Int, y: Int) {
        _uiState.update { it.copy(cursorX = x, cursorY = y) }
        drawAtCursor()
    }

    private fun drawPixel(
        pixels: Map<Pair<Int, Int>, DoodleColor>,
        x: Int,
        y: Int,
        color: DoodleColor
    ): Map<Pair<Int, Int>, DoodleColor> {
        return if (color == DoodleColor.WHITE) {
            pixels - (x to y)
        } else {
            pixels + ((x to y) to color)
        }
    }

    fun selectColor(color: DoodleColor) {
        _uiState.update { it.copy(selectedColor = color, paletteFocusIndex = color.index) }
    }

    fun cycleTool() {
        _uiState.update { state ->
            val newTool = when (state.selectedTool) {
                DoodleTool.PEN -> DoodleTool.LINE
                DoodleTool.LINE -> DoodleTool.FILL
                DoodleTool.FILL -> DoodleTool.PEN
            }
            state.copy(selectedTool = newTool, lineStartX = null, lineStartY = null)
        }
    }

    fun setCanvasSize(size: CanvasSize) {
        _uiState.update { state ->
            val newCursorX = state.cursorX.coerceIn(0, size.pixels - 1)
            val newCursorY = state.cursorY.coerceIn(0, size.pixels - 1)
            val filteredPixels = state.pixels.filter { (coords, _) ->
                val (x, y) = coords
                x < size.pixels && y < size.pixels
            }
            state.copy(
                canvasSize = size,
                pixels = filteredPixels,
                cursorX = newCursorX,
                cursorY = newCursorY,
                sizeFocusIndex = size.sizeEnum
            )
        }
    }

    fun setSection(section: DoodleSection) {
        _uiState.update { it.copy(currentSection = section) }
    }

    fun nextSection() {
        _uiState.update { state ->
            val next = when (state.currentSection) {
                DoodleSection.CANVAS -> DoodleSection.PALETTE
                DoodleSection.PALETTE -> DoodleSection.SIZE
                DoodleSection.SIZE -> DoodleSection.UNDO
                DoodleSection.UNDO -> DoodleSection.REDO
                DoodleSection.REDO -> if (state.avatarMode) DoodleSection.CANVAS else DoodleSection.GAME
                DoodleSection.GAME -> DoodleSection.CANVAS
            }
            state.copy(currentSection = next)
        }
    }

    fun previousSection() {
        _uiState.update { state ->
            val prev = when (state.currentSection) {
                DoodleSection.CANVAS -> if (state.avatarMode) DoodleSection.REDO else DoodleSection.GAME
                DoodleSection.PALETTE -> DoodleSection.CANVAS
                DoodleSection.SIZE -> DoodleSection.PALETTE
                DoodleSection.UNDO -> DoodleSection.SIZE
                DoodleSection.REDO -> DoodleSection.UNDO
                DoodleSection.GAME -> DoodleSection.REDO
            }
            state.copy(currentSection = prev)
        }
    }

    fun movePaletteFocus(dx: Int, dy: Int) {
        _uiState.update { state ->
            val newIndex = (state.paletteFocusIndex + dx + dy * 8).coerceIn(0, 15)
            state.copy(paletteFocusIndex = newIndex)
        }
    }

    fun selectPaletteColor() {
        _uiState.update { state ->
            val color = DoodleColor.fromIndex(state.paletteFocusIndex)
            state.copy(selectedColor = color, currentSection = DoodleSection.CANVAS)
        }
    }

    fun moveSizeFocus(dx: Int) {
        _uiState.update { state ->
            val newIndex = (state.sizeFocusIndex + dx).coerceIn(0, state.maxSizeFocusIndex)
            state.copy(sizeFocusIndex = newIndex)
        }
    }

    fun confirmSizeSelection() {
        _uiState.update { state ->
            val size = CanvasSize.fromEnum(state.sizeFocusIndex)
            setCanvasSize(size)
            state.copy(currentSection = DoodleSection.CANVAS)
        }
    }

    fun cycleZoom() {
        _uiState.update { state ->
            val newZoom = state.zoomLevel.next()
            val newPanX = if (newZoom == ZoomLevel.FIT) 0f else state.panOffsetX
            val newPanY = if (newZoom == ZoomLevel.FIT) 0f else state.panOffsetY
            state.copy(zoomLevel = newZoom, panOffsetX = newPanX, panOffsetY = newPanY)
        }
    }

    fun pan(dx: Float, dy: Float) {
        _uiState.update { state ->
            if (state.zoomLevel == ZoomLevel.FIT) return@update state
            state.copy(
                panOffsetX = state.panOffsetX + dx,
                panOffsetY = state.panOffsetY + dy
            )
        }
    }

    fun showDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = true, discardDialogFocusIndex = 0) }
    }

    fun hideDiscardDialog() {
        _uiState.update { it.copy(showDiscardDialog = false) }
    }

    fun moveDiscardDialogFocus(delta: Int) {
        _uiState.update { state ->
            val newIndex = (state.discardDialogFocusIndex + delta).coerceIn(0, 1)
            state.copy(discardDialogFocusIndex = newIndex)
        }
    }

    fun confirmDiscardDialogSelection(): Boolean {
        val state = _uiState.value
        return state.discardDialogFocusIndex == 0
    }

    private fun pushUndoSnapshot() {
        _uiState.update { state ->
            val newStack = if (state.undoStack.size >= MAX_UNDO_STACK) {
                state.undoStack.drop(1) + state.pixels
            } else {
                state.undoStack + listOf(state.pixels)
            }
            state.copy(undoStack = newStack, redoStack = emptyList())
        }
    }

    fun undo() {
        _uiState.update { state ->
            val snapshot = state.undoStack.lastOrNull() ?: return@update state
            state.copy(
                pixels = snapshot,
                undoStack = state.undoStack.dropLast(1),
                redoStack = state.redoStack + listOf(state.pixels)
            )
        }
    }

    fun redo() {
        _uiState.update { state ->
            val snapshot = state.redoStack.lastOrNull() ?: return@update state
            state.copy(
                pixels = snapshot,
                redoStack = state.redoStack.dropLast(1),
                undoStack = state.undoStack + listOf(state.pixels)
            )
        }
    }

    private var gameSearchJob: Job? = null

    fun initGame(gameId: Int?, gameTitle: String?, gameCoverPath: String?) {
        if (gameId != null) {
            _uiState.update {
                it.copy(linkedGameId = gameId, linkedGameTitle = gameTitle, linkedGameCoverPath = gameCoverPath)
            }
        }
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
        gameSearchJob?.cancel()
        _uiState.update { it.copy(showGamePicker = false) }
    }

    fun focusGamePickerSearch() {
        _uiState.update { it.copy(gamePickerSearchFocused = true) }
    }

    fun focusGamePickerList() {
        _uiState.update { it.copy(gamePickerSearchFocused = false, gamePickerFocusIndex = 0) }
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

    fun clearLinkedGame() {
        _uiState.update {
            it.copy(linkedGameId = null, linkedGameTitle = null, linkedGameCoverPath = null)
        }
    }

    private fun com.nendo.argosy.data.local.entity.GameEntity.toPickerItem() = GamePickerItem(
        id = id,
        igdbId = igdbId?.toInt(),
        title = title,
        platform = platformSlug,
        coverPath = coverPath
    )

    fun done() {
        val state = _uiState.value
        if (state.pixels.isEmpty()) {
            viewModelScope.launch {
                _events.emit(DoodleEvent.Error("Cannot save an empty doodle"))
            }
            return
        }
        val base64Data = DoodleEncoder.encodeToBase64(state.pixels, state.canvasSize)
        viewModelScope.launch {
            if (state.avatarMode) {
                syncPreferencesRepository.setSocialAvatarDoodle(base64Data)
                _events.emit(DoodleEvent.AvatarSaved)
            } else {
                _events.emit(DoodleEvent.Done(
                    doodleData = base64Data,
                    canvasSize = state.canvasSize.pixels,
                    gameId = state.linkedGameId,
                    gameTitle = state.linkedGameTitle,
                    gameCoverPath = state.linkedGameCoverPath
                ))
            }
        }
    }

    fun clearCanvas() {
        pushUndoSnapshot()
        _uiState.update { it.copy(pixels = emptyMap(), lineStartX = null, lineStartY = null) }
    }

    companion object {
        private const val MAX_UNDO_STACK = 50
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
