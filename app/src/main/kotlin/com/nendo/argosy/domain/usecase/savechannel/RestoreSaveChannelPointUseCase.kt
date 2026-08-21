package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.usecase.state.RestoreCachedStatesUseCase
import javax.inject.Inject

/**
 * Puts a game back to an earlier point in a channel, states included.
 *
 * Restoring a point that is not the newest one has to drop the auto-resume state as well as skip
 * it. The auto-state describes where play stopped, which is ahead of the point being restored, so
 * leaving it on disk means the next launch resumes past the moment the user just chose.
 */
class RestoreSaveChannelPointUseCase @Inject constructor(
    private val restoreCachedStatesUseCase: RestoreCachedStatesUseCase,
    private val stateCacheManager: StateCacheManager,
    private val contextResolver: SaveChannelContextResolver
) {
    suspend operator fun invoke(
        gameId: Long,
        channelName: String?,
        isLatest: Boolean,
        coreId: String? = null
    ) {
        val context = contextResolver.resolve(gameId, coreId)
        if (!context.movesStates) return

        restoreCachedStatesUseCase(
            gameId = gameId,
            channelName = channelName,
            emulatorPackage = context.emulatorPackage!!,
            coreId = context.coreId,
            skipAutoState = !isLatest
        )

        if (isLatest) return
        val romPath = context.romPath ?: return
        val emulatorId = context.emulatorId ?: return
        val platformSlug = context.platformSlug ?: return
        stateCacheManager.deleteAutoResumeStatesFromDisk(
            emulatorId = emulatorId,
            romPath = romPath,
            platformSlug = platformSlug,
            emulatorPackage = context.emulatorPackage,
            coreId = context.coreId,
            gameId = gameId
        )
    }
}
