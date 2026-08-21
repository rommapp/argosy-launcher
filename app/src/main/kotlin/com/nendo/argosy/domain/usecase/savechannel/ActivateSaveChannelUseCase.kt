package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.domain.usecase.state.RestoreCachedStatesUseCase
import javax.inject.Inject

/**
 * Points a game at a save channel and brings that channel's states with it.
 *
 * The state half is not a courtesy. States live in one directory per emulator whatever channel
 * they came from, so a channel switch that moves only the save leaves the previous channel's
 * states loaded; the next session then caches them under the channel now active and the two
 * channels bleed into each other. [RestoreCachedStatesUseCase] clears that directory before it
 * restores, so a channel with nothing cached still ends up clean rather than inheriting.
 */
class ActivateSaveChannelUseCase @Inject constructor(
    private val activeSaveRepository: ActiveSaveRepository,
    private val restoreCachedStatesUseCase: RestoreCachedStatesUseCase,
    private val contextResolver: SaveChannelContextResolver
) {
    suspend operator fun invoke(
        gameId: Long,
        channelName: String?,
        coreId: String? = null
    ): Boolean {
        val activated = activeSaveRepository.activateChannel(gameId, channelName)
        val context = contextResolver.resolve(gameId, coreId)
        if (context.movesStates) {
            restoreCachedStatesUseCase(
                gameId = gameId,
                channelName = channelName,
                emulatorPackage = context.emulatorPackage!!,
                coreId = context.coreId
            )
        }
        return activated
    }
}
