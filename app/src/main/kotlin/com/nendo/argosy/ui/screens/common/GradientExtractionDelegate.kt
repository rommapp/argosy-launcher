package com.nendo.argosy.ui.screens.common

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.cache.GradientColorExtractor
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class GameGradientRequest(
    val gameId: Long,
    val coverPath: String?
)

@Singleton
class GradientExtractionDelegate @Inject constructor(
    private val gradientColorExtractor: GradientColorExtractor
) {
    private val _gradients = MutableStateFlow<Map<Long, Pair<Color, Color>>>(emptyMap())
    val gradients: StateFlow<Map<Long, Pair<Color, Color>>> = _gradients.asStateFlow()

    private val pendingExtractions = mutableSetOf<Long>()
    private var extractionJob: Job? = null
    private var currentPreset: GradientPreset = GradientPreset.BALANCED
    private var currentBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID

    @OptIn(ExperimentalCoroutinesApi::class)
    private val extractionDispatcher = Dispatchers.IO.limitedParallelism(2)

    fun updatePreferences(preset: GradientPreset, borderStyle: BoxArtBorderStyle) {
        val presetChanged = preset != currentPreset
        currentPreset = preset
        currentBorderStyle = borderStyle

        if (presetChanged) {
            _gradients.value = emptyMap()
            pendingExtractions.clear()
        }
    }

    fun getGradient(gameId: Long): Pair<Color, Color>? = _gradients.value[gameId]

    fun hasGradient(gameId: Long): Boolean = _gradients.value.containsKey(gameId)

    fun extractForVisibleGames(
        scope: CoroutineScope,
        games: List<GameGradientRequest>,
        focusedIndex: Int,
        buffer: Int = 5
    ) {
        if (currentBorderStyle != BoxArtBorderStyle.GRADIENT) return
        if (games.isEmpty()) return

        extractionJob?.cancel()
        extractionJob = scope.launch {
            val startIndex = (focusedIndex - buffer).coerceAtLeast(0)
            val endIndex = (focusedIndex + buffer).coerceAtMost(games.size - 1)

            val gamesToExtract = games.subList(startIndex, endIndex + 1)
                .filter { it.coverPath != null && !hasGradient(it.gameId) }

            if (gamesToExtract.isEmpty()) return@launch

            val extracted = withContext(Dispatchers.IO) {
                gamesToExtract.mapNotNull { request ->
                    request.coverPath?.let { path ->
                        gradientColorExtractor.getGradientColors(path, currentPreset)?.let { colors ->
                            request.gameId to colors
                        }
                    }
                }
            }

            if (extracted.isNotEmpty()) {
                _gradients.value = _gradients.value + extracted
            }
        }
    }

    fun extractForGame(
        scope: CoroutineScope,
        gameId: Long,
        coverPath: String?,
        prioritize: Boolean = false
    ) {
        if (currentBorderStyle != BoxArtBorderStyle.GRADIENT) return
        if (coverPath == null) return
        if (hasGradient(gameId)) return
        if (pendingExtractions.contains(gameId)) return

        pendingExtractions.add(gameId)
        val dispatcher = if (prioritize) Dispatchers.IO else extractionDispatcher

        scope.launch(dispatcher) {
            try {
                val colors = gradientColorExtractor.getGradientColors(coverPath, currentPreset)
                if (colors != null) {
                    _gradients.value = _gradients.value + (gameId to colors)
                }
            } finally {
                pendingExtractions.remove(gameId)
            }
        }
    }

    fun clear() {
        extractionJob?.cancel()
        _gradients.value = emptyMap()
        pendingExtractions.clear()
    }
}
