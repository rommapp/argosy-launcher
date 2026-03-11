package com.nendo.argosy.ui.screens.common

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.cache.GradientExtractionConfig
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.ui.common.GradientColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class GameGradientRequest(
    val gameId: Long,
    val coverPath: String?
)

@Singleton
class GradientExtractionDelegate @Inject constructor(
    private val gradientColorExtractor: GradientColorExtractor,
    private val gameDao: GameDao,
    private val backgroundProcessor: GradientBackgroundProcessor
) {
    private val _gradients = MutableStateFlow<Map<Long, Pair<Color, Color>>>(emptyMap())
    val gradients: StateFlow<Map<Long, Pair<Color, Color>>> = _gradients.asStateFlow()

    private val persistedPresets = mutableMapOf<Long, Map<GradientPreset, Pair<Color, Color>>>()

    private val pendingExtractions = mutableSetOf<Long>()
    private var extractionJob: Job? = null
    private var loadJob: Job? = null
    private var currentPreset: GradientPreset = GradientPreset.BALANCED

    @OptIn(ExperimentalCoroutinesApi::class)
    private val extractionDispatcher = Dispatchers.IO.limitedParallelism(2)

    fun updatePreferences(preset: GradientPreset, borderStyle: BoxArtBorderStyle) {
        val presetChanged = preset != currentPreset
        currentPreset = preset

        if (presetChanged && preset != GradientPreset.CUSTOM) {
            rederiveFromCache()
        } else if (presetChanged) {
            _gradients.value = emptyMap()
            persistedPresets.clear()
            pendingExtractions.clear()
            backgroundProcessor.pause()
        }
    }

    fun startBackgroundProcessing(scope: CoroutineScope) {
        if (currentPreset == GradientPreset.CUSTOM) return
        backgroundProcessor.start(scope) { gameId ->
            scope.launch(Dispatchers.Main) {
                loadPersistedGradient(gameId)
            }
        }
    }

    fun pauseBackgroundProcessing() {
        backgroundProcessor.pause()
    }

    fun resumeBackgroundProcessing(scope: CoroutineScope) {
        if (currentPreset == GradientPreset.CUSTOM) return
        backgroundProcessor.resume(scope) { gameId ->
            scope.launch(Dispatchers.Main) {
                loadPersistedGradient(gameId)
            }
        }
    }

    fun getGradient(gameId: Long): Pair<Color, Color>? = _gradients.value[gameId]

    fun hasGradient(gameId: Long): Boolean = _gradients.value.containsKey(gameId)

    fun loadPersistedGradientsForGames(
        scope: CoroutineScope,
        gameIds: List<Long>
    ) {
        val missing = gameIds.filter { !hasGradient(it) }
        if (missing.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            loadPersistedGradientsImmediate(missing)
        }
    }

    private suspend fun loadPersistedGradientsImmediate(gameIds: List<Long>) {
        val missing = gameIds.filter { !hasGradient(it) }
        if (missing.isEmpty()) return

        val entities = gameDao.getByIds(missing)
        val loaded = mutableMapOf<Long, Pair<Color, Color>>()
        for (entity in entities) {
            val json = entity.gradientColors ?: continue
            val allPresets = gradientColorExtractor.deserializeAllPresets(json) ?: continue
            persistedPresets[entity.id] = allPresets
            val colors = allPresets[currentPreset] ?: continue
            loaded[entity.id] = colors
        }
        if (loaded.isNotEmpty()) {
            _gradients.value = _gradients.value + loaded
        }
    }

    fun extractForVisibleGames(
        scope: CoroutineScope,
        games: List<GameGradientRequest>,
        focusedIndex: Int,
        buffer: Int = 5
    ) {
        if (games.isEmpty()) return

        val startIndex = (focusedIndex - buffer).coerceAtLeast(0)
        val endIndex = (focusedIndex + buffer).coerceAtMost(games.size - 1)

        val gamesToLoad = games.subList(startIndex, endIndex + 1)
            .filter { it.coverPath != null && !hasGradient(it.gameId) }

        if (gamesToLoad.isEmpty()) return

        val gameIds = gamesToLoad.map { it.gameId }

        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            loadPersistedGradientsImmediate(gameIds)
        }

        extractionJob?.cancel()
        extractionJob = scope.launch(extractionDispatcher) {
            delay(150)
            for (request in gamesToLoad) {
                if (hasGradient(request.gameId)) continue
                val coverPath = request.coverPath ?: continue
                extractAndPersist(request.gameId, coverPath)
            }
        }
    }

    fun extractForGame(
        scope: CoroutineScope,
        gameId: Long,
        coverPath: String?,
        prioritize: Boolean = false
    ) {
        if (coverPath == null) return
        if (hasGradient(gameId)) return
        if (pendingExtractions.contains(gameId)) return

        pendingExtractions.add(gameId)
        val dispatcher = if (prioritize) Dispatchers.IO else extractionDispatcher

        scope.launch(dispatcher) {
            try {
                extractAndPersist(gameId, coverPath)
            } finally {
                pendingExtractions.remove(gameId)
            }
        }
    }

    private suspend fun extractAndPersist(gameId: Long, coverPath: String) {
        if (currentPreset == GradientPreset.CUSTOM) {
            val colors = gradientColorExtractor.extractForCustomConfig(
                coverPath, currentPreset.toConfig()
            )
            if (colors != null) {
                _gradients.value = _gradients.value + (gameId to colors)
            }
            return
        }

        val allPresets = gradientColorExtractor.extractAllPresets(coverPath) ?: return
        val json = gradientColorExtractor.serializeAllPresets(allPresets)
        gameDao.updateGradientColors(gameId, json)
        persistedPresets[gameId] = allPresets
        val colors = allPresets[currentPreset] ?: return
        _gradients.value = _gradients.value + (gameId to colors)
    }

    private suspend fun loadPersistedGradient(gameId: Long) {
        val entity = gameDao.getById(gameId) ?: return
        val json = entity.gradientColors ?: return
        val allPresets = gradientColorExtractor.deserializeAllPresets(json) ?: return
        persistedPresets[gameId] = allPresets
        val colors = allPresets[currentPreset] ?: return
        _gradients.value = _gradients.value + (gameId to colors)
    }

    private fun rederiveFromCache() {
        val rederived = mutableMapOf<Long, Pair<Color, Color>>()
        for ((gameId, presets) in persistedPresets) {
            val colors = presets[currentPreset] ?: continue
            rederived[gameId] = colors
        }
        _gradients.value = rederived
    }

    fun clear() {
        extractionJob?.cancel()
        backgroundProcessor.cancel()
        _gradients.value = emptyMap()
        persistedPresets.clear()
        pendingExtractions.clear()
    }
}
