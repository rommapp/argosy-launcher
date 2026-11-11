package com.nendo.argosy.ui.common.savechannel.snapshot

import androidx.annotation.StringRes
import com.nendo.argosy.ui.components.TextEntryFocus

sealed interface SnapshotDeviceUi {
    data object Unknown : SnapshotDeviceUi
    data object OtherUser : SnapshotDeviceUi
    data class Named(val name: String) : SnapshotDeviceUi
}

data class SnapshotTileUi(
    val channelId: String,
    val label: String,
    val thumbnailUrl: String?,
    val isOwn: Boolean,
    val isHardcore: Boolean,
    val isShared: Boolean,
    val ownerName: String?,
    val hasOnlyOlderSaves: Boolean,
    val isDeviceChannel: Boolean,
    val savedAt: String?,
    val device: SnapshotDeviceUi?
)

data class SnapshotCardUi(
    val key: String,
    val snapshotId: Long?,
    val saveId: Long?,
    val savedAt: String?,
    val device: SnapshotDeviceUi?,
    val fileName: String?,
    val thumbnailUrl: String?,
    val isCurrent: Boolean,
    val isBranch: Boolean,
    val isPinned: Boolean,
    val isHardcore: Boolean,
    val isArchival: Boolean = false,
    val isPublic: Boolean = false
) {
    val isOlderClient: Boolean get() = snapshotId == null
}

data class SnapshotExpandedUi(
    val channelId: String,
    val isMine: Boolean,
    val cards: List<SnapshotCardUi> = emptyList(),
    val isLoading: Boolean = true,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val focusIndex: Int = 0
) {
    val stopCount: Int get() = cards.size + if (hasMore) 1 else 0
    val isLoadMoreFocused: Boolean get() = hasMore && focusIndex == cards.size
    val focusedCard: SnapshotCardUi? get() = cards.getOrNull(focusIndex)
}

data class SnapshotBackupUi(
    val saveId: Long,
    val fileName: String,
    val savedAt: String?,
    val thumbnailUrl: String?
)

data class SnapshotCoreStatesUi(val core: String, val slots: List<String>)

enum class SnapshotDetailAction { ACTIVATE, FORK, COPY_OVER, PIN, UNPIN, SHARE, UNSHARE, MAKE_SNAPSHOT }

data class SnapshotDetailUi(
    val channelId: String,
    val channelLabel: String,
    val card: SnapshotCardUi,
    val parentId: Long?,
    val states: List<SnapshotCoreStatesUi>,
    val actions: List<SnapshotDetailAction>,
    val focusIndex: Int = 0
) {
    val focusedAction: SnapshotDetailAction? get() = actions.getOrNull(focusIndex)
    val hasActivate: Boolean get() = actions.firstOrNull() == SnapshotDetailAction.ACTIVATE
}

enum class SnapshotChannelAction { USE_ON_DEVICE, RENAME, SHARE, STOP_SHARING, DELETE }

data class SnapshotChannelMenuUi(
    val channelId: String,
    val label: String,
    val actions: List<SnapshotChannelAction>,
    val focusIndex: Int = 0
) {
    val focusedAction: SnapshotChannelAction? get() = actions.getOrNull(focusIndex)
}

data class SnapshotPickTargetUi(val channelId: String, val label: String)

sealed interface SnapshotCopySource {
    data class Snapshot(val snapshotId: Long) : SnapshotCopySource
    data class Backup(val saveId: Long) : SnapshotCopySource
}

data class SnapshotCopyPickerUi(
    val source: SnapshotCopySource,
    val targets: List<SnapshotPickTargetUi>,
    val focusIndex: Int = 0
) {
    val offersNewChannel: Boolean get() = source is SnapshotCopySource.Backup
    val rowCount: Int get() = targets.size + if (offersNewChannel) 1 else 0
    val isNewChannelFocused: Boolean get() = offersNewChannel && focusIndex == 0
    val focusedTarget: SnapshotPickTargetUi?
        get() = targets.getOrNull(focusIndex - if (offersNewChannel) 1 else 0)
}

enum class SnapshotLabelMode { NEW_CHANNEL, RENAME, FORK }

data class SnapshotLabelEntryUi(
    val mode: SnapshotLabelMode,
    val text: String = "",
    val channelId: String? = null,
    val snapshotId: Long? = null,
    val romFileId: Long? = null,
    val backupSaveId: Long? = null,
    @StringRes val error: Int? = null,
    val focus: TextEntryFocus = TextEntryFocus()
)

sealed interface SnapshotConfirmUi {
    data class Share(val channelId: String) : SnapshotConfirmUi
    data class Delete(
        val channelId: String,
        val label: String,
        val pinnedCount: Int,
        val olderSaveCount: Int
    ) : SnapshotConfirmUi
    data object HardcoreDowngrade : SnapshotConfirmUi
}

sealed interface SnapshotStop {
    data object Timeline : SnapshotStop
    data object NewChannel : SnapshotStop
    data object MineTiles : SnapshotStop
    data object Cards : SnapshotStop
    data class Backup(val index: Int) : SnapshotStop
    data object CommunityTiles : SnapshotStop
}

data class SnapshotViewState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isBusy: Boolean = false,
    val showsTimeline: Boolean = false,
    val canCreateChannel: Boolean = false,
    val mine: List<SnapshotTileUi> = emptyList(),
    val backups: List<SnapshotBackupUi> = emptyList(),
    val community: List<SnapshotTileUi> = emptyList(),
    val expanded: SnapshotExpandedUi? = null,
    val stop: SnapshotStop = SnapshotStop.MineTiles,
    val mineIndex: Int = 0,
    val communityIndex: Int = 0,
    val detail: SnapshotDetailUi? = null,
    val channelMenu: SnapshotChannelMenuUi? = null,
    val copyPicker: SnapshotCopyPickerUi? = null,
    val labelEntry: SnapshotLabelEntryUi? = null,
    val confirm: SnapshotConfirmUi? = null
) {
    val stops: List<SnapshotStop>
        get() = buildList {
            if (showsTimeline) add(SnapshotStop.Timeline)
            if (mine.isNotEmpty()) add(SnapshotStop.MineTiles)
            if (canCreateChannel) add(SnapshotStop.NewChannel)
            backups.indices.forEach { add(SnapshotStop.Backup(it)) }
            if (community.isNotEmpty()) add(SnapshotStop.CommunityTiles)
        }

    val hasOverlay: Boolean
        get() = detail != null || channelMenu != null || copyPicker != null ||
            labelEntry != null || confirm != null

    val focusedTile: SnapshotTileUi?
        get() = when (stop) {
            SnapshotStop.MineTiles -> mine.getOrNull(mineIndex)
            SnapshotStop.CommunityTiles -> community.getOrNull(communityIndex)
            else -> null
        }

    fun tile(channelId: String): SnapshotTileUi? =
        mine.firstOrNull { it.channelId == channelId } ?: community.firstOrNull { it.channelId == channelId }

    fun isStopFocused(candidate: SnapshotStop): Boolean = !hasOverlay && stop == candidate
}
