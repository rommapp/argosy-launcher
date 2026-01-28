package com.nendo.argosy.domain.model

sealed class SyncState {
    data object Idle : SyncState()
    data object CheckingConnection : SyncState()
    data object Downloading : SyncState()
    data object Uploading : SyncState()
    data object Complete : SyncState()
    data class Error(val message: String) : SyncState()
    data object Skipped : SyncState()
    data class HardcoreConflict(val gameId: Long, val gameName: String) : SyncState()
}
