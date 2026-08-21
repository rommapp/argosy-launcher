package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import javax.inject.Inject

/**
 * Removes a save slot wherever it exists: saves and states, on this device and on the server.
 *
 * A state left behind by a deleted slot is not merely stale. The server copy is downloaded again
 * by the next sync, so a slot the user deleted grows its states back and reappears in the states
 * tab under a channel the save side no longer knows about. Purging tombstones each one first, so a
 * server delete that never lands still cannot resurrect it.
 *
 * The purge is not behind the emulator's state-support gate. That gate decides whether states may
 * SYNC; these rows already exist, and whether the emulator that wrote them is still installed says
 * nothing about whether the user asked for them to be gone.
 */
class DeleteSaveChannelUseCase @Inject constructor(
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val stateCacheManager: StateCacheManager,
    private val activeSaveRepository: ActiveSaveRepository
) {
    suspend operator fun invoke(gameId: Long, channelName: String) {
        val wasActive = activeSaveRepository.getActiveChannel(gameId) == channelName
        val entries = getUnifiedSavesUseCase(gameId, expandHistory = true)
            .filter { it.channelName == channelName }

        entries.mapNotNull { it.serverSaveId }
            .takeIf { it.isNotEmpty() }
            ?.let { saveSyncRepository.deleteServerSaves(it) }
        entries.forEach { entry -> entry.localCacheId?.let { saveCacheManager.deleteSave(it) } }

        stateCacheManager.getStatesForChannel(gameId, channelName).forEach { state ->
            stateCacheManager.purgeState(
                gameId = gameId,
                cacheId = state.id,
                serverStateId = state.rommSaveId
            )
        }

        activeSaveRepository.forgetChannel(gameId, channelName)
        if (wasActive) {
            activeSaveRepository.clearActive(gameId)
        }
    }
}
