package com.nendo.argosy.core.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementUpdateBus @Inject constructor() {
    data class AchievementUpdate(
        val gameId: Long,
        val totalCount: Int,
        val earnedCount: Int
    )

    private val _updates = MutableSharedFlow<AchievementUpdate>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<AchievementUpdate> = _updates.asSharedFlow()

    suspend fun emit(update: AchievementUpdate) {
        _updates.emit(update)
    }
}
