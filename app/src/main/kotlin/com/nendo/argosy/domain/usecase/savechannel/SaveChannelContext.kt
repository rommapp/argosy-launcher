package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.StateSupportResolver
import com.nendo.argosy.data.local.dao.GameDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a save-channel operation needs to know about the game it is acting on.
 *
 * [movesStates] is the one gate every state side effect sits behind. It is answered here rather
 * than at each call site because a caller that has to remember the state half is a caller that can
 * forget it, and the states then belong to a channel nothing will ask for again.
 */
data class SaveChannelContext(
    val gameId: Long,
    val emulatorId: String?,
    val emulatorPackage: String?,
    val coreId: String?,
    val romPath: String?,
    val platformSlug: String?,
    val supportsStates: Boolean
) {
    val movesStates: Boolean get() = supportsStates && emulatorPackage != null
}

/**
 * Resolves a game's emulator, core and state capability once per channel operation.
 *
 * The emulator comes from [EmulatorResolver.getEmulatorPackageForGame], the same answer the launch
 * path and the game-detail save modal use. Resolving it here keeps every channel operation on one
 * emulator answer; a second opinion about which emulator a game uses puts states in a directory
 * nothing reads.
 */
@Singleton
class SaveChannelContextResolver @Inject constructor(
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver,
    private val stateSupportResolver: StateSupportResolver,
    private val coreVersionExtractor: CoreVersionExtractor
) {
    suspend fun resolve(gameId: Long, coreId: String? = null): SaveChannelContext {
        val game = gameDao.getById(gameId)
            ?: return SaveChannelContext(gameId, null, null, null, null, null, supportsStates = false)

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(
            gameId,
            game.platformId,
            game.platformSlug
        )
        val emulatorId = emulatorResolver.getEmulatorIdForGame(
            gameId,
            game.platformId,
            game.platformSlug
        )
        val effectiveCoreId = coreId
            ?: emulatorId?.let { coreVersionExtractor.getCoreIdForEmulator(it, game.platformSlug) }

        val supported = stateSupportResolver.supportsStates(
            emulatorId = emulatorId,
            gameId = gameId,
            platformId = game.platformId,
            platformSlug = game.platformSlug
        )

        return SaveChannelContext(
            gameId = gameId,
            emulatorId = emulatorId,
            emulatorPackage = emulatorPackage,
            coreId = effectiveCoreId,
            romPath = game.localPath,
            platformSlug = game.platformSlug,
            supportsStates = supported
        )
    }
}
