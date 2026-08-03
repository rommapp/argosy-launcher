package com.nendo.argosy.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Games a finished download wants to put on the curated grid, waiting for a screen to ask on.
 *
 * A download completes wherever the user happens to be, including in a game or with the launcher
 * closed, so the ask cannot happen where the decision is made. Offers queue here instead and the
 * home surface drains them when it is next visible; a queue rather than a single slot because a
 * batch download would otherwise ask about one game and silently drop the rest.
 */
@Singleton
class HomeTilePromptQueue @Inject constructor() {

    private val _pending = MutableStateFlow<List<Long>>(emptyList())
    val pending: StateFlow<List<Long>> = _pending.asStateFlow()

    fun offer(gameId: Long) = _pending.update { current ->
        if (gameId in current) current else current + gameId
    }

    fun resolve(gameId: Long) = _pending.update { current -> current.filterNot { it == gameId } }

    fun clear() = _pending.update { emptyList() }
}
