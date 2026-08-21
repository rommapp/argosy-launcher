package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.domain.usecase.state.RestoreCachedStatesUseCase
import javax.inject.Inject

/**
 * Opens an empty save slot and makes the game start from nothing.
 *
 * An empty slot has to be empty of states too. Clearing the save alone leaves the previous
 * channel's states loaded, and the first session in the new slot then caches them as if they had
 * always belonged to it. Restoring the new channel is what empties the directory: it has nothing
 * cached, so the restore clears what is there and puts nothing back.
 */
class CreateSaveChannelUseCase @Inject constructor(
    private val activeSaveRepository: ActiveSaveRepository,
    private val restoreCachedSaveUseCase: RestoreCachedSaveUseCase,
    private val restoreCachedStatesUseCase: RestoreCachedStatesUseCase,
    private val contextResolver: SaveChannelContextResolver
) {
    suspend operator fun invoke(gameId: Long, channelName: String, coreId: String? = null) {
        activeSaveRepository.createChannel(gameId, channelName)

        val context = contextResolver.resolve(gameId, coreId)
        context.emulatorId?.let { restoreCachedSaveUseCase.clearActiveSave(gameId, it) }

        if (context.movesStates) {
            restoreCachedStatesUseCase(
                gameId = gameId,
                channelName = channelName,
                emulatorPackage = context.emulatorPackage!!,
                coreId = context.coreId
            )
        }
    }
}
