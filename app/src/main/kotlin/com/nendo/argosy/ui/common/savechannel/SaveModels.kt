package com.nendo.argosy.ui.common.savechannel

enum class SaveFocusColumn { SLOTS, HISTORY }

data class SaveSlotItem(
    val channelName: String?,
    val displayName: String,
    val isActive: Boolean,
    val saveCount: Int,
    val latestTimestamp: Long?,
    val isCreateAction: Boolean = false,
    val isMigrationCandidate: Boolean = false
)

data class SaveHistoryItem(
    val cacheId: Long,
    val timestamp: Long,
    val size: Long,
    val channelName: String?,
    val isLocal: Boolean,
    val isSynced: Boolean,
    val isActiveRestorePoint: Boolean,
    val isLatest: Boolean,
    val isHardcore: Boolean,
    val isRollback: Boolean
)
