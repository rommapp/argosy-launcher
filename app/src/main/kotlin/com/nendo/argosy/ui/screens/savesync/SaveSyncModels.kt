package com.nendo.argosy.ui.screens.savesync

import com.nendo.argosy.data.sync.SyncDirection
import java.time.Instant

enum class AttentionAction { KEEP_LOCAL, KEEP_SERVER, SKIP }

data class SaveSyncUiState(
    val deviceCard: ThisDeviceCard = ThisDeviceCard(),
    val otherDevices: List<DeviceSummary> = emptyList(),
    val otherDevicesHidden: Int = 0,
    val attentionRows: List<AttentionRow> = emptyList(),
    val inProgressRows: List<InProgressRow> = emptyList(),
    val gameRows: List<GameSaveRow> = emptyList(),
    val focusedRowKey: String? = null,
    val attentionAction: AttentionAction = AttentionAction.SKIP,
    val isLoading: Boolean = true
) {
    val allRows: List<SaveSyncRow>
        get() = attentionRows + inProgressRows + gameRows

    val focusedIndex: Int
        get() = allRows.indexOfFirst { it.key == focusedRowKey }.takeIf { it >= 0 } ?: 0

    val focusedRow: SaveSyncRow?
        get() = allRows.find { it.key == focusedRowKey } ?: allRows.firstOrNull()

    val isEmpty: Boolean
        get() = attentionRows.isEmpty() && inProgressRows.isEmpty() && gameRows.isEmpty()
}

data class ThisDeviceCard(
    val deviceName: String? = null,
    val deviceIdShort: String? = null,
    val platform: String? = null,
    val client: String? = null,
    val clientVersion: String? = null,
    val serverVersion: String? = null,
    val saveCount: Int = 0,
    val isConnected: Boolean = false
)

data class DeviceSummary(
    val deviceId: String?,
    val deviceName: String,
    val platform: String?,
    val client: String?,
    val clientVersion: String?,
    val saveCount: Int,
    val latestSyncAt: Instant?,
    val isWeb: Boolean = false
)

sealed interface SaveSyncRow {
    val key: String
}

data class AttentionRow(
    val conflictId: Long,
    val gameId: Long,
    val title: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val channelName: String?,
    val channelDisplay: String,
    val localTime: Instant?,
    val serverTime: Instant?,
    val localDeviceName: String?,
    val serverDeviceName: String?,
    val isLocalNewer: Boolean
) : SaveSyncRow {
    override val key: String get() = "attention:$conflictId"
}

data class InProgressRow(
    val gameId: Long,
    val title: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val direction: SyncDirection,
    val progress: Float,
    val statusLabel: String
) : SaveSyncRow {
    override val key: String get() = "progress:$gameId:$direction"
}

data class GameSaveRow(
    val saveSyncId: Long,
    val gameId: Long,
    val title: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val channelName: String?,
    val channelDisplay: String,
    val syncStatus: String,
    val lastSyncedAt: Instant?,
    val localUpdatedAt: Instant?,
    val serverUpdatedAt: Instant?,
    val lastSyncDeviceName: String?,
    val isLastSyncThisDevice: Boolean,
    val isJustSynced: Boolean,
    val hasConflict: Boolean
) : SaveSyncRow {
    override val key: String get() = "game:$saveSyncId"
}
