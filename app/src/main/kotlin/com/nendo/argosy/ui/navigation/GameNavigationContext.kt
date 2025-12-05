package com.nendo.argosy.ui.navigation

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameNavigationContext @Inject constructor() {
    private var gameIds: List<Long> = emptyList()

    fun setContext(ids: List<Long>) {
        gameIds = ids
    }

    fun getGameIds(): List<Long> = gameIds

    fun getIndex(gameId: Long): Int = gameIds.indexOf(gameId)

    fun getPreviousGameId(currentId: Long): Long? {
        val index = gameIds.indexOf(currentId)
        return if (index > 0) gameIds[index - 1] else null
    }

    fun getNextGameId(currentId: Long): Long? {
        val index = gameIds.indexOf(currentId)
        return if (index >= 0 && index < gameIds.size - 1) gameIds[index + 1] else null
    }

    fun clear() {
        gameIds = emptyList()
    }
}
