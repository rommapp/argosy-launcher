package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySyncBus @Inject constructor(
    @ApplicationContext private val context: Context
) {
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
        val intent = Intent("com.nendo.argosy.LIBRARY_REFRESH").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
