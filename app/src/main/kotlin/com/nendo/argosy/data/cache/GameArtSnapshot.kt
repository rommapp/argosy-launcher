package com.nendo.argosy.data.cache

import com.nendo.argosy.data.model.ResolvedGameArt

class GameArtSnapshot(val byGame: Map<Long, ResolvedGameArt>) {
    operator fun get(gameId: Long): ResolvedGameArt? = byGame[gameId]

    companion object {
        val EMPTY = GameArtSnapshot(emptyMap())
    }
}
