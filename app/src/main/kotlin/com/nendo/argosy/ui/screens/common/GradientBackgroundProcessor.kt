package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.ui.common.GradientColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradientBackgroundProcessor @Inject constructor(
    private val gameDao: GameDao,
    private val gradientColorExtractor: GradientColorExtractor
) {
    private var job: Job? = null
    @Volatile
    private var paused = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    fun start(scope: CoroutineScope, onGameProcessed: (Long) -> Unit = {}) {
        if (job?.isActive == true) return
        paused = false
        job = scope.launch(dispatcher) {
            val localGames = gameDao.getLocalGamesNeedingGradients()
            for (game in localGames) {
                yield()
                if (paused) return@launch
                val coverPath = game.coverPath ?: continue
                processGame(game.id, coverPath, onGameProcessed)
            }
        }
    }

    private suspend fun processGame(
        gameId: Long,
        coverPath: String,
        onGameProcessed: (Long) -> Unit
    ) {
        val presets = gradientColorExtractor.extractAllPresets(coverPath) ?: return
        val json = gradientColorExtractor.serializeAllPresets(presets)
        gameDao.updateGradientColors(gameId, json)
        onGameProcessed(gameId)
    }

    fun pause() {
        paused = true
    }

    fun resume(scope: CoroutineScope, onGameProcessed: (Long) -> Unit = {}) {
        paused = false
        if (job?.isActive != true) {
            start(scope, onGameProcessed)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
