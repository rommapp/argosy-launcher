package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.StateCacheManager
import javax.inject.Inject

/**
 * Gives a channel a new name and moves everything filed under the old one.
 *
 * The name is not a label on one row: it is what every save and every state in the channel is
 * keyed and pathed by. Renaming part of it splits the channel in two, and the half left behind
 * answers to a name the slot list no longer offers.
 */
class RenameSaveChannelUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val stateCacheManager: StateCacheManager,
    private val activeSaveRepository: ActiveSaveRepository,
    private val contextResolver: SaveChannelContextResolver
) {
    suspend operator fun invoke(
        gameId: Long,
        oldName: String,
        newName: String,
        coreId: String? = null
    ) {
        if (oldName == newName) return

        saveCacheManager.renameChannel(gameId, oldName, newName)

        val context = contextResolver.resolve(gameId, coreId)
        if (context.supportsStates) {
            stateCacheManager.moveStatesToChannel(
                gameId = gameId,
                sourceChannel = oldName,
                targetChannel = newName
            )
        }

        if (activeSaveRepository.getActiveChannel(gameId) == oldName) {
            activeSaveRepository.activateChannel(gameId, newName)
        }
    }
}
