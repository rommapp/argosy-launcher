package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import javax.inject.Inject

/**
 * Copies a save into a slot of its own, taking the source channel's states with it.
 *
 * The states copied are the channel's current ones, not the ones that existed when the chosen save
 * was written: a state carries no link back to a save, so there is nothing to select them by. A
 * slot locked from an old point therefore pairs that save with today's states.
 */
class CopySaveChannelUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val stateCacheManager: StateCacheManager
) {
    suspend operator fun invoke(
        gameId: Long,
        sourceChannel: String?,
        targetChannel: String,
        localCacheId: Long?,
        serverSaveId: Long?,
        emulatorId: String?
    ): Boolean {
        val copied = when {
            localCacheId != null ->
                saveCacheManager.copyToChannel(localCacheId, targetChannel) != null
            serverSaveId != null ->
                saveSyncRepository.downloadSaveAsChannel(gameId, serverSaveId, targetChannel, emulatorId)
            else -> false
        }
        if (!copied) return false

        stateCacheManager.duplicateStatesForChannel(
            gameId = gameId,
            sourceChannel = sourceChannel,
            targetChannel = targetChannel
        )
        return true
    }
}
