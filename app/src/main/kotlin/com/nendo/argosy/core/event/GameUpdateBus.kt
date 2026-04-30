package com.nendo.argosy.core.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameUpdateBus @Inject constructor() {
    data class GameUpdate(
        val gameId: Long,
        val playTimeMinutes: Int? = null,
        val status: String? = null
    )

    private val _updates = MutableSharedFlow<GameUpdate>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<GameUpdate> = _updates.asSharedFlow()

    suspend fun emit(update: GameUpdate) {
        _updates.emit(update)
    }
}
