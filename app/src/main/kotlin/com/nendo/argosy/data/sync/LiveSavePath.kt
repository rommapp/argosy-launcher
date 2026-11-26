package com.nendo.argosy.data.sync

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cached SaveSyncEntity.localSavePath as seen by the build the game launches with now: a path
 * in the Android/data folder of a build sharing its save layout, such as Eden stable for Eden
 * nightly, moved to the launched build's folder. Every file check on a cached path reads it here.
 */
@Singleton
class LiveSavePath @Inject constructor(
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao
) {
    suspend fun currentLocalSavePath(entity: SaveSyncEntity?, game: GameEntity): String? {
        if (entity?.localSavePath == null) return null
        return forPackage(entity, launchPackage(game))
    }

    suspend fun currentLocalSavePath(entity: SaveSyncEntity?): String? {
        val path = entity?.localSavePath ?: return null
        val game = gameDao.getById(entity.gameId) ?: return path
        return forPackage(entity, launchPackage(game))
    }

    /**
     * A resolver that looks up each game's launch package once, for callers walking many rows.
     */
    fun perGame(): PerGame = PerGame()

    inner class PerGame internal constructor() {
        private val packages = HashMap<Long, String?>()

        suspend fun currentLocalSavePath(entity: SaveSyncEntity?, game: GameEntity): String? {
            if (entity?.localSavePath == null) return null
            if (game.id !in packages) packages[game.id] = launchPackage(game)
            return forPackage(entity, packages[game.id])
        }
    }

    private suspend fun launchPackage(game: GameEntity): String? =
        emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)

    companion object {
        fun forPackage(entity: SaveSyncEntity?, emulatorPackage: String?): String? =
            entity?.localSavePath?.let { SavePathRegistry.pathForPackage(it, emulatorPackage) }
    }
}
