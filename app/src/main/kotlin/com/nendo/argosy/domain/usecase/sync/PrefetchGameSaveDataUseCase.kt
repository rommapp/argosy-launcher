package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.state.GetUnifiedStatesUseCase
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PrefetchGameSaveData"
private const val THROTTLE_MS = 5 * 60 * 1000L

/**
 * Pulls a game's server saves and states into the local cache when its detail page opens, so the
 * launch that follows finds them already here.
 *
 * Pre-launch sync happens in the seconds between pressing play and the emulator starting, which is
 * the worst moment to depend on a server: a slow or failed answer there is a launch that starts
 * from the wrong save. Fetching earlier does not make that call more reliable, it makes it
 * unnecessary - a pre-launch check that fails now finds the data already cached.
 *
 * Nothing here writes the live save or state directory. Only the cache is filled; deciding what
 * the emulator actually sees stays with the launch and with the save manager, where the user's
 * intent is known. That is also why this can never conflict with them: it fills the same cache
 * they read, and holds no lock they wait on.
 */
@Singleton
class PrefetchGameSaveDataUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val activeSaveRepository: ActiveSaveRepository,
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val getUnifiedStatesUseCase: GetUnifiedStatesUseCase,
    private val saveSyncRepository: SaveSyncRepository
) {
    private val lastRunAt = ConcurrentHashMap<Long, Long>()

    /**
     * The attempt is recorded before the work rather than after it, so a game whose prefetch fails
     * waits its turn like any other rather than retrying on every visit to the page.
     */
    suspend operator fun invoke(gameId: Long) {
        val now = System.currentTimeMillis()
        val previous = lastRunAt[gameId]
        if (previous != null && now - previous < THROTTLE_MS) return

        if (!preferencesRepository.userPreferences.first().saveSyncEnabled) return
        val game = gameDao.getById(gameId) ?: return
        if (game.rommId == null) return

        lastRunAt[gameId] = now

        runCatching { prefetchSaves(gameId) }
            .onFailure { Logger.warn(TAG, "Save prefetch failed for gameId=$gameId", it) }
        runCatching { prefetchStates(gameId) }
            .onFailure { Logger.warn(TAG, "State prefetch failed for gameId=$gameId", it) }
    }

    private suspend fun prefetchSaves(gameId: Long) {
        val serverOnly = getUnifiedSavesUseCase(gameId, expandHistory = true)
            .filter { it.source == UnifiedSaveEntry.Source.SERVER && it.serverSaveId != null }
        if (serverOnly.isEmpty()) return

        for (entry in serverOnly) {
            saveSyncRepository.downloadAndCacheSave(
                serverSaveId = entry.serverSaveId!!,
                gameId = gameId,
                channelName = entry.channelName
            )
        }
        Logger.debug(TAG, "Prefetched ${serverOnly.size} server saves for gameId=$gameId")
    }

    /**
     * Reading the active channel's states is what downloads them; the list is a side effect worth
     * nothing here. Only the active channel is fetched because only the active channel is ever
     * shown, and a state is a whole emulator snapshot rather than a few kilobytes of save.
     */
    private suspend fun prefetchStates(gameId: Long) {
        getUnifiedStatesUseCase(
            gameId = gameId,
            channelName = activeSaveRepository.getActiveChannel(gameId)
        )
    }
}
