package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.DualScreenManagerHolder
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySyncBus @Inject constructor() {
    private val _syncCompleted = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val syncCompleted: SharedFlow<Unit> = _syncCompleted.asSharedFlow()

    suspend fun emitSyncCompleted() {
        _syncCompleted.emit(Unit)
        broadcastLibraryRefresh()
    }

    private fun broadcastLibraryRefresh() {
        DualScreenManagerHolder.instance?.companionHost?.onLibraryRefresh()
    }
}
