package com.nendo.argosy.data.cache

import com.nendo.argosy.data.model.ResolvedGameArt

/**
 * One load of every game's resolved art, keyed by game id. Two snapshots are equal only when
 * they are the same load, so a StateFlow collector compares them in constant time;
 * [ImageCacheManager.gameArt] drops content-equal reloads before a new snapshot is made.
 */
class GameArtSnapshot(val byGame: Map<Long, ResolvedGameArt>) {
    operator fun get(gameId: Long): ResolvedGameArt? = byGame[gameId]

    companion object {
        val EMPTY = GameArtSnapshot(emptyMap())
    }
}
