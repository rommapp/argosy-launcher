package com.nendo.argosy.ui.screens.common

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.common.GradientColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * What has been sampled for one kind of artwork, and what is still in flight for it.
 *
 * Games and films are held apart because their keys are: a game is a local row id, an item is the
 * media server's own id, and the two spaces would collide in one map. The sampling itself is not
 * duplicated, only the bookkeeping it writes into.
 */
private class GradientStore<K> {
    val flow = MutableStateFlow<Map<K, Pair<Color, Color>>>(emptyMap())
    val persistedPresets = mutableMapOf<K, Map<GradientPreset, Pair<Color, Color>>>()
    val pending = mutableSetOf<K>()

    fun has(key: K): Boolean = flow.value.containsKey(key)

    fun put(key: K, colors: Pair<Color, Color>) {
        flow.value = flow.value + (key to colors)
    }

    fun putAll(entries: Map<K, Pair<Color, Color>>) {
        if (entries.isEmpty()) return
        flow.value = flow.value + entries
    }

    fun rederive(preset: GradientPreset) {
        flow.value = persistedPresets.mapNotNull { (key, presets) ->
            presets[preset]?.let { key to it }
        }.toMap()
    }

    fun clear() {
        flow.value = emptyMap()
        persistedPresets.clear()
        pending.clear()
    }
}

@Singleton
class GradientExtractionDelegate @Inject constructor(
    private val gradientColorExtractor: GradientColorExtractor,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val backgroundProcessor: GradientBackgroundProcessor,
    private val imageCacheManager: ImageCacheManager
) {
    private val games = GradientStore<Long>()
    private val media = GradientStore<String>()

    val gradients: StateFlow<Map<Long, Pair<Color, Color>>> = games.flow.asStateFlow()
    val mediaGradients: StateFlow<Map<String, Pair<Color, Color>>> = media.flow.asStateFlow()

    private var currentPreset: GradientPreset = GradientPreset.BALANCED

    @OptIn(ExperimentalCoroutinesApi::class)
    private val extractionDispatcher = Dispatchers.IO.limitedParallelism(2)

    fun updatePreferences(preset: GradientPreset, borderStyle: BoxArtBorderStyle) {
        val presetChanged = preset != currentPreset
        currentPreset = preset
        if (!presetChanged) return

        if (preset != GradientPreset.CUSTOM) {
            games.rederive(preset)
            media.rederive(preset)
        } else {
            games.clear()
            media.clear()
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
        scope.launch {
            imageCacheManager.localCoverWritten.collect { (gameId, coverPath) ->
                extractForGame(scope, gameId, coverPath, prioritize = false)
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

    fun getGradient(gameId: Long): Pair<Color, Color>? = games.flow.value[gameId]

    fun hasGradient(gameId: Long): Boolean = games.has(gameId)

    fun getMediaGradient(itemId: String): Pair<Color, Color>? = media.flow.value[itemId]

    fun loadPersistedGradientsForGames(
        scope: CoroutineScope,
        gameIds: List<Long>
    ) {
        val missing = gameIds.filter { !games.has(it) }
        if (missing.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            loadPersistedGradientsImmediate(missing)
        }
    }

    /**
     * Brings back what was sampled for these items on an earlier run, so a poster already seen does
     * not have to be decoded again before its colours are available.
     */
    fun loadPersistedMediaGradients(
        scope: CoroutineScope,
        itemIds: List<String>
    ) {
        val missing = itemIds.filter { !media.has(it) }
        if (missing.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            val entities = mediaRepository.getItems(missing)
            val loaded = mutableMapOf<String, Pair<Color, Color>>()
            for (entity in entities) {
                val json = entity.gradientColors ?: continue
                val allPresets = gradientColorExtractor.deserializeAllPresets(json) ?: continue
                media.persistedPresets[entity.itemId] = allPresets
                val colors = allPresets[currentPreset] ?: continue
                loaded[entity.itemId] = colors
            }
            media.putAll(loaded)
        }
    }

    private suspend fun loadPersistedGradientsImmediate(gameIds: List<Long>) {
        val missing = gameIds.filter { !games.has(it) }
        if (missing.isEmpty()) return

        val entities = gameRepository.getByIds(missing)
        val loaded = mutableMapOf<Long, Pair<Color, Color>>()
        for (entity in entities) {
            val json = entity.gradientColors ?: continue
            val allPresets = gradientColorExtractor.deserializeAllPresets(json) ?: continue
            games.persistedPresets[entity.id] = allPresets
            val colors = allPresets[currentPreset] ?: continue
            loaded[entity.id] = colors
        }
        games.putAll(loaded)
    }

    fun extractForVisibleGames(
        scope: CoroutineScope,
        requests: List<GameGradientRequest>,
        focusedIndex: Int,
        buffer: Int = 5
    ) {
        if (requests.isEmpty()) return

        val startIndex = (focusedIndex - buffer).coerceAtLeast(0)
        val endIndex = (focusedIndex + buffer).coerceAtMost(requests.size - 1)

        val gamesToLoad = requests.subList(startIndex, endIndex + 1)
            .filter { it.coverPath != null && !hasGradient(it.gameId) }

        if (gamesToLoad.isEmpty()) return

        val gameIds = gamesToLoad.map { it.gameId }

        scope.launch(Dispatchers.IO) {
            loadPersistedGradientsImmediate(gameIds)
        }

        for (request in gamesToLoad) {
            extractForGame(scope, request.gameId, request.coverPath)
        }
    }

    fun extractForGame(
        scope: CoroutineScope,
        gameId: Long,
        coverPath: String?,
        prioritize: Boolean = false
    ) {
        if (coverPath == null) return
        if (!coverPath.startsWith("/")) return
        if (games.has(gameId)) return
        if (games.pending.contains(gameId)) return

        games.pending.add(gameId)
        val dispatcher = if (prioritize) Dispatchers.IO else extractionDispatcher

        scope.launch(dispatcher) {
            try {
                extractAndPersist(gameId, coverPath)
            } finally {
                games.pending.remove(gameId)
            }
        }
    }

    fun extractForGame(
        scope: CoroutineScope,
        gameId: Long,
        bitmap: Bitmap,
        prioritize: Boolean = false
    ) {
        extractFromBitmap(scope, games, gameId, bitmap, prioritize) { json ->
            gameRepository.updateGradientColors(gameId, json)
        }
    }

    /**
     * Samples an item's poster as it is drawn. A poster is fetched over the network rather than
     * living on disk, so the loaded bitmap is the only place its colours can be read from.
     */
    fun extractForMedia(
        scope: CoroutineScope,
        itemId: String,
        bitmap: Bitmap,
        prioritize: Boolean = false
    ) {
        extractFromBitmap(scope, media, itemId, bitmap, prioritize) { json ->
            mediaRepository.updateGradientColors(itemId, json)
        }
    }

    private fun <K> extractFromBitmap(
        scope: CoroutineScope,
        store: GradientStore<K>,
        key: K,
        bitmap: Bitmap,
        prioritize: Boolean,
        persist: suspend (String) -> Unit
    ) {
        if (store.has(key)) return
        if (store.pending.contains(key)) return

        store.pending.add(key)
        val dispatcher = if (prioritize) Dispatchers.Default else extractionDispatcher

        scope.launch(dispatcher) {
            try {
                extractAndPersistFromBitmap(store, key, bitmap, persist)
            } finally {
                store.pending.remove(key)
            }
        }
    }

    private suspend fun <K> extractAndPersistFromBitmap(
        store: GradientStore<K>,
        key: K,
        bitmap: Bitmap,
        persist: suspend (String) -> Unit
    ) {
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return
        } else {
            bitmap
        }
        val ownsCopy = readable !== bitmap

        try {
            if (currentPreset == GradientPreset.CUSTOM) {
                val result = gradientColorExtractor.extractWithMetrics(readable, currentPreset.toConfig())
                store.put(key, result.primary to result.secondary)
                return
            }

            val allPresets = gradientColorExtractor.extractAllPresetsFromBitmap(readable)
            val json = gradientColorExtractor.serializeAllPresets(allPresets)
            persist(json)
            store.persistedPresets[key] = allPresets
            val colors = allPresets[currentPreset] ?: return
            store.put(key, colors)
        } finally {
            if (ownsCopy) readable.recycle()
        }
    }

    private suspend fun extractAndPersist(gameId: Long, coverPath: String) {
        if (currentPreset == GradientPreset.CUSTOM) {
            val colors = gradientColorExtractor.extractForCustomConfig(
                coverPath, currentPreset.toConfig()
            )
            if (colors != null) {
                games.put(gameId, colors)
            }
            return
        }

        val allPresets = gradientColorExtractor.extractAllPresets(coverPath) ?: return
        val json = gradientColorExtractor.serializeAllPresets(allPresets)
        gameRepository.updateGradientColors(gameId, json)
        games.persistedPresets[gameId] = allPresets
        val colors = allPresets[currentPreset] ?: return
        games.put(gameId, colors)
    }

    private suspend fun loadPersistedGradient(gameId: Long) {
        val entity = gameRepository.getById(gameId) ?: return
        val json = entity.gradientColors ?: return
        val allPresets = gradientColorExtractor.deserializeAllPresets(json) ?: return
        games.persistedPresets[gameId] = allPresets
        val colors = allPresets[currentPreset] ?: return
        games.put(gameId, colors)
    }

    fun clear() {
        backgroundProcessor.cancel()
        games.clear()
        media.clear()
    }
}
