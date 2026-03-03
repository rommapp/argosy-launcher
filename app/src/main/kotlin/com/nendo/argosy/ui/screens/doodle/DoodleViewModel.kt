package com.nendo.argosy.ui.screens.doodle

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.social.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DoodleEvent {
    data object Posted : DoodleEvent()
    data class Error(val message: String) : DoodleEvent()
}

@HiltViewModel
class DoodleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val socialRepository: SocialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoodleUiState())
    val uiState: StateFlow<DoodleUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DoodleEvent>()
    val events = _events.asSharedFlow()

    init {
        val gameId = savedStateHandle.get<String>("gameId")?.toIntOrNull()
        val gameTitle = savedStateHandle.get<String>("gameTitle")
        if (gameId != null) {
            _uiState.update { it.copy(linkedGameId = gameId, linkedGameTitle = gameTitle) }
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
                DoodleSection.SIZE -> DoodleSection.CAPTION
                DoodleSection.CAPTION -> DoodleSection.CANVAS
            }
            state.copy(currentSection = next)
        }
    }

    fun previousSection() {
        _uiState.update { state ->
            val prev = when (state.currentSection) {
                DoodleSection.CANVAS -> DoodleSection.CAPTION
                DoodleSection.PALETTE -> DoodleSection.CANVAS
                DoodleSection.SIZE -> DoodleSection.PALETTE
                DoodleSection.CAPTION -> DoodleSection.SIZE
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
            val newIndex = (state.sizeFocusIndex + dx).coerceIn(0, 2)
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

    fun setCaption(caption: String) {
        val trimmed = caption.take(MAX_CAPTION_LENGTH)
        _uiState.update { it.copy(caption = trimmed) }
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

    fun showPostMenu() {
        _uiState.update { it.copy(showPostMenu = true, postMenuFocusIndex = 0) }
    }

    fun hidePostMenu() {
        _uiState.update { it.copy(showPostMenu = false) }
    }

    fun movePostMenuFocus(delta: Int) {
        _uiState.update { state ->
            val newIndex = (state.postMenuFocusIndex + delta).coerceIn(0, 1)
            state.copy(postMenuFocusIndex = newIndex)
        }
    }

    fun confirmPostMenuSelection(): Boolean {
        val state = _uiState.value
        return when (state.postMenuFocusIndex) {
            0 -> {
                post()
                true
            }
            else -> {
                hidePostMenu()
                false
            }
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

    fun post() {
        val state = _uiState.value
        if (state.pixels.isEmpty()) {
            viewModelScope.launch {
                _events.emit(DoodleEvent.Error("Cannot post an empty doodle"))
            }
            return
        }

        _uiState.update { it.copy(isPosting = true, showPostMenu = false) }

        viewModelScope.launch {
            try {
                val base64Data = DoodleEncoder.encodeToBase64(state.pixels, state.canvasSize)
                Log.d(TAG, "Posting doodle: size=${state.canvasSize.pixels}x${state.canvasSize.pixels}, data=${base64Data.length} chars")

                socialRepository.createDoodle(
                    canvasSize = state.canvasSize.sizeEnum,
                    data = base64Data,
                    caption = state.caption.takeIf { it.isNotBlank() },
                    igdbId = state.linkedGameId,
                    gameTitle = state.linkedGameTitle
                )

                _uiState.update { it.copy(isPosting = false) }
                _events.emit(DoodleEvent.Posted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post doodle", e)
                _uiState.update { it.copy(isPosting = false) }
                _events.emit(DoodleEvent.Error("Failed to post doodle"))
            }
        }
    }

    fun clearCanvas() {
        _uiState.update { it.copy(pixels = emptyMap(), lineStartX = null, lineStartY = null) }
    }

    companion object {
        private const val TAG = "DoodleViewModel"
        private const val MAX_CAPTION_LENGTH = 200
    }
}
