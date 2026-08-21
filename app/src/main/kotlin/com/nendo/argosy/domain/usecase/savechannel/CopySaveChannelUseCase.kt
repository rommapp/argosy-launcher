package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import javax.inject.Inject

/**
 * Copies a save into a slot of its own, taking the states that belong with it.
 *
 * A slot locked from a point in history is that point, and the states saved at that point are part
 * of what the user is preserving. Copying the save alone produces a slot whose states are whatever
 * the source channel goes on to accumulate.
 */
class CopySaveChannelUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val stateCacheManager: StateCacheManager,
    private val contextResolver: SaveChannelContextResolver
) {
    suspend operator fun invoke(
        gameId: Long,
        sourceChannel: String?,
        targetChannel: String,
        localCacheId: Long?,
        serverSaveId: Long?,
        emulatorId: String?,
        coreId: String? = null
    ): Boolean {
        val copied = when {
            localCacheId != null ->
                saveCacheManager.copyToChannel(localCacheId, targetChannel) != null
            serverSaveId != null ->
                saveSyncRepository.downloadSaveAsChannel(gameId, serverSaveId, targetChannel, emulatorId)
            else -> false
        }
        if (!copied) return false

        val context = contextResolver.resolve(gameId, coreId)
        if (context.supportsStates) {
            stateCacheManager.duplicateStatesForChannel(
                gameId = gameId,
                sourceChannel = sourceChannel,
                targetChannel = targetChannel
            )
        }
        return true
    }
}
