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
 *
 * The state move is not behind the emulator's state-support gate. That gate decides whether states
 * may SYNC; these rows exist already, and an emulator that is no longer installed does not make
 * the states it wrote belong to the old name.
 */
class RenameSaveChannelUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val stateCacheManager: StateCacheManager,
    private val activeSaveRepository: ActiveSaveRepository
) {
    suspend operator fun invoke(
        gameId: Long,
        oldName: String,
        newName: String
    ) {
        if (oldName == newName) return

        saveCacheManager.renameChannel(gameId, oldName, newName)

        stateCacheManager.moveStatesToChannel(
            gameId = gameId,
            sourceChannel = oldName,
            targetChannel = newName
        )

        if (activeSaveRepository.getActiveChannel(gameId) == oldName) {
            activeSaveRepository.activateChannel(gameId, newName)
        }
    }
}
