package com.nendo.argosy.ui.common.savechannel.snapshot

import com.nendo.argosy.R
import com.nendo.argosy.data.sync.snapshot.SnapshotChannels
import com.nendo.argosy.ui.common.savechannel.SaveChannelStateHolder
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Focus, overlays and confirm routing for the snapshot channel view. Every gamepad button and
 * every tap lands here; server work goes through [SnapshotActionRunner].
 */
class SnapshotViewDelegate @Inject constructor(
    private val holder: SaveChannelStateHolder,
    private val runner: SnapshotActionRunner
) {
    private val snapshot: SnapshotViewState? get() = holder.state.value.snapshot

    suspend fun isAvailable(gameId: Long): Boolean = runner.isAvailable(gameId)

    fun start(scope: CoroutineScope, showsTimeline: Boolean = false) = runner.start(scope, showsTimeline)

    fun clear() = runner.clear()

    fun moveVertical(delta: Int) {
        val state = snapshot ?: return
        when {
            state.confirm != null -> Unit
            state.labelEntry != null -> updateLabelFocus { it.copy(row = if (delta < 0) TextEntryRow.FIELD else TextEntryRow.BUTTONS) }
            state.hasOverlay -> holder.updateSnapshot { SnapshotFocus.moveOverlay(it, delta) }
            else -> holder.updateSnapshot { SnapshotFocus.moveVertical(it, delta) }
        }
    }

    fun moveHorizontal(delta: Int) {
        val state = snapshot ?: return
        when {
            state.confirm != null -> Unit
            state.labelEntry != null -> updateLabelFocus { focus ->
                if (focus.row != TextEntryRow.BUTTONS) focus
                else focus.copy(buttonIndex = if (delta < 0) TEXT_ENTRY_CANCEL_BUTTON else TEXT_ENTRY_CONFIRM_BUTTON)
            }
            state.hasOverlay -> holder.updateSnapshot { SnapshotFocus.moveDetailColumn(it, delta) }
            else -> holder.updateSnapshot { SnapshotFocus.moveHorizontal(it, delta) }
        }
    }

    fun confirm(scope: CoroutineScope, onSaveStatusChanged: (SaveStatusEvent) -> Unit) {
        val state = snapshot ?: return
        if (state.isBusy || state.confirm != null) return
        when {
            state.labelEntry != null -> when {
                state.labelEntry.focus.row == TextEntryRow.FIELD -> updateLabelFocus { it.requestKeyboard() }
                state.labelEntry.focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> dismissLabel()
                else -> confirmLabel(scope)
            }
            state.copyPicker != null -> confirmCopyTarget(scope)
            state.channelMenu != null -> runChannelAction(scope, onSaveStatusChanged)
            state.detail != null -> runDetailAction(scope, onSaveStatusChanged)
            else -> confirmStop(scope, state)
        }
    }

    /**
     * Closes the topmost open layer and returns true, or returns false when only the base
     * view is showing.
     */
    fun back(): Boolean {
        val state = snapshot ?: return false
        when {
            state.confirm != null -> dismissConfirm()
            state.labelEntry != null -> holder.updateSnapshot { it.copy(labelEntry = null) }
            state.copyPicker != null -> holder.updateSnapshot { it.copy(copyPicker = null) }
            state.channelMenu != null -> holder.updateSnapshot { it.copy(channelMenu = null) }
            state.detail != null -> holder.updateSnapshot { it.copy(detail = null) }
            state.stop == SnapshotStop.Cards -> holder.updateSnapshot(SnapshotFocus::collapse)
            else -> return false
        }
        return true
    }

    val mapper: SnapshotUiMapper get() = runner.mapper

    fun openChannelActions() {
        val state = snapshot ?: return
        if (state.hasOverlay) return
        state.focusedTile?.let(::openChannelMenu)
    }

    fun openChannelActions(channelId: String) {
        val state = snapshot ?: return
        if (state.hasOverlay) return
        state.tile(channelId)?.let(::openChannelMenu)
    }

    private fun openChannelMenu(tile: SnapshotTileUi) {
        val actions = buildList {
            if (!tile.isDeviceChannel) add(SnapshotChannelAction.USE_ON_DEVICE)
            if (tile.isOwn) {
                if (!SnapshotChannels.isDefaultLabel(tile.label)) add(SnapshotChannelAction.RENAME)
                add(if (tile.isShared) SnapshotChannelAction.STOP_SHARING else SnapshotChannelAction.SHARE)
                add(SnapshotChannelAction.DELETE)
            }
        }
        if (actions.isEmpty()) return
        holder.updateSnapshot { it.copy(channelMenu = SnapshotChannelMenuUi(tile.channelId, tile.label, actions)) }
    }

    /**
     * Closes every overlay and points the view at [channelId]'s tile, so the view shows that
     * channel when it is next on screen. A null or unknown [channelId] keeps the current focus.
     */
    fun returnToChannel(channelId: String?) {
        runner.dropPending()
        holder.updateSnapshot { state ->
            val closed = state.copy(
                isBusy = false,
                detail = null,
                channelMenu = null,
                copyPicker = null,
                labelEntry = null,
                confirm = null
            )
            val mineIndex = closed.mine.indexOfFirst { it.channelId == channelId }
            val communityIndex = closed.community.indexOfFirst { it.channelId == channelId }
            if (mineIndex < 0 && communityIndex < 0) return@updateSnapshot closed
            if (closed.expanded?.channelId == channelId && closed.stop == SnapshotStop.Cards) {
                return@updateSnapshot closed
            }
            val collapsed = SnapshotFocus.collapse(closed)
            if (mineIndex >= 0) collapsed.copy(stop = SnapshotStop.MineTiles, mineIndex = mineIndex)
            else collapsed.copy(stop = SnapshotStop.CommunityTiles, communityIndex = communityIndex)
        }
    }

    fun tapNewChannel() {
        val state = snapshot ?: return
        if (state.isStopFocused(SnapshotStop.NewChannel)) openLabelEntry()
        else focusStop(SnapshotStop.NewChannel)
    }

    fun tapTile(scope: CoroutineScope, isMine: Boolean, index: Int) {
        val state = snapshot ?: return
        val stop = if (isMine) SnapshotStop.MineTiles else SnapshotStop.CommunityTiles
        val focusedIndex = if (isMine) state.mineIndex else state.communityIndex
        if (state.isStopFocused(stop) && focusedIndex == index) {
            confirmStop(scope, state)
            return
        }
        holder.updateSnapshot {
            if (isMine) it.copy(stop = stop, mineIndex = index) else it.copy(stop = stop, communityIndex = index)
        }
    }

    fun longPressTile(isMine: Boolean, index: Int) {
        val stop = if (isMine) SnapshotStop.MineTiles else SnapshotStop.CommunityTiles
        holder.updateSnapshot {
            if (isMine) it.copy(stop = stop, mineIndex = index) else it.copy(stop = stop, communityIndex = index)
        }
        openChannelActions()
    }

    fun tapCard(scope: CoroutineScope, index: Int) {
        val state = snapshot ?: return
        val expanded = state.expanded ?: return
        if (state.isStopFocused(SnapshotStop.Cards) && expanded.focusIndex == index) {
            confirmStop(scope, state)
            return
        }
        holder.updateSnapshot { it.copy(stop = SnapshotStop.Cards, expanded = expanded.copy(focusIndex = index)) }
    }

    fun tapBackup(index: Int) {
        val state = snapshot ?: return
        val stop = SnapshotStop.Backup(index)
        if (state.isStopFocused(stop)) openBackupCopy(index)
        else focusStop(stop)
    }

    fun tapOverlayRow(scope: CoroutineScope, index: Int, onSaveStatusChanged: (SaveStatusEvent) -> Unit) {
        val state = snapshot ?: return
        val focused = state.copyPicker?.focusIndex ?: state.channelMenu?.focusIndex ?: state.detail?.focusIndex
        if (focused == index) {
            confirm(scope, onSaveStatusChanged)
            return
        }
        holder.updateSnapshot {
            when {
                it.copyPicker != null -> it.copy(copyPicker = it.copyPicker.copy(focusIndex = index))
                it.channelMenu != null -> it.copy(channelMenu = it.channelMenu.copy(focusIndex = index))
                it.detail != null -> it.copy(detail = it.detail.copy(focusIndex = index))
                else -> it
            }
        }
    }

    fun updateLabelText(text: String) {
        holder.updateSnapshot { state -> state.copy(labelEntry = state.labelEntry?.copy(text = text, error = null)) }
    }

    fun confirmLabel(scope: CoroutineScope) {
        val state = snapshot ?: return
        val entry = state.labelEntry ?: return
        val label = entry.text.trim()
        if (label.isEmpty() || state.isBusy) return
        if (SnapshotChannels.isReservedLabel(label)) {
            holder.updateSnapshot { it.copy(labelEntry = it.labelEntry?.copy(error = R.string.save_channels_label_error_reserved)) }
            return
        }
        when (entry.mode) {
            SnapshotLabelMode.NEW_CHANNEL -> runner.newChannel(scope, label, entry.backupSaveId)
            SnapshotLabelMode.RENAME -> entry.channelId?.let { runner.rename(scope, it, label) }
            SnapshotLabelMode.FORK -> {
                val romFileId = entry.romFileId ?: return
                val snapshotId = entry.snapshotId ?: return
                runner.push(scope, SnapshotPush.Fork(romFileId, snapshotId, label))
            }
        }
    }

    fun dismissLabel() {
        holder.updateSnapshot { it.copy(labelEntry = null) }
    }

    private fun updateLabelFocus(transform: (TextEntryFocus) -> TextEntryFocus) {
        holder.updateSnapshot { state ->
            state.copy(labelEntry = state.labelEntry?.let { it.copy(focus = transform(it.focus)) })
        }
    }

    fun confirmShare(scope: CoroutineScope) {
        val target = snapshot?.confirm as? SnapshotConfirmUi.Share ?: return
        holder.updateSnapshot { it.copy(confirm = null) }
        runner.setShared(scope, target.channelId, shared = true)
    }

    fun confirmDelete(scope: CoroutineScope) {
        val target = snapshot?.confirm as? SnapshotConfirmUi.Delete ?: return
        holder.updateSnapshot { it.copy(confirm = null) }
        runner.delete(scope, target.channelId)
    }

    fun confirmHardcore(scope: CoroutineScope) {
        if (snapshot?.confirm != SnapshotConfirmUi.HardcoreDowngrade) return
        holder.updateSnapshot { it.copy(confirm = null) }
        runner.approvePending(scope)
    }

    fun dismissConfirm() {
        runner.dropPending()
        holder.updateSnapshot { it.copy(confirm = null) }
    }

    private fun focusStop(stop: SnapshotStop) {
        holder.updateSnapshot { if (it.hasOverlay) it else it.copy(stop = stop) }
    }

    private fun confirmStop(scope: CoroutineScope, state: SnapshotViewState) {
        when (val stop = state.stop) {
            SnapshotStop.Timeline -> Unit
            SnapshotStop.NewChannel -> openLabelEntry()
            SnapshotStop.MineTiles, SnapshotStop.CommunityTiles -> state.focusedTile?.let {
                toggleExpanded(scope, it, isMine = stop == SnapshotStop.MineTiles)
            }
            SnapshotStop.Cards -> {
                val expanded = state.expanded ?: return
                if (expanded.isLoadMoreFocused) {
                    if (!expanded.isLoadingMore) runner.loadHistory(scope, expanded.channelId, more = true)
                } else {
                    expanded.focusedCard?.let { openDetail(expanded.channelId, it) }
                }
            }
            is SnapshotStop.Backup -> openBackupCopy(stop.index)
        }
    }

    private fun toggleExpanded(scope: CoroutineScope, tile: SnapshotTileUi, isMine: Boolean) {
        if (snapshot?.expanded?.channelId == tile.channelId) {
            holder.updateSnapshot(SnapshotFocus::collapse)
            return
        }
        holder.updateSnapshot {
            it.copy(expanded = SnapshotExpandedUi(channelId = tile.channelId, isMine = isMine), stop = SnapshotStop.Cards)
        }
        runner.loadHistory(scope, tile.channelId, more = false)
    }

    fun openDetail(channelId: String, card: SnapshotCardUi) {
        if (snapshot?.hasOverlay != false) return
        val entry = runner.entryOf(channelId) ?: return
        val channel = entry.channel
        val snapshotData = card.snapshotId?.let { id ->
            holder.snapshotHistories.value[channelId]?.firstOrNull { it.id == id }
        }
        val canWrite = channel.isOwn || channel.isPublic
        val actions = if (card.isOlderClient) {
            listOfNotNull(SnapshotDetailAction.MAKE_SNAPSHOT.takeIf { channel.isOwn })
        } else {
            buildList {
                if (card.snapshotId != null) add(SnapshotDetailAction.ACTIVATE)
                if (runner.romFileIdFor(channel) != null) add(SnapshotDetailAction.FORK)
                if (runner.copyTargets(channel).isNotEmpty()) add(SnapshotDetailAction.COPY_OVER)
                if (canWrite) add(if (card.isPinned) SnapshotDetailAction.UNPIN else SnapshotDetailAction.PIN)
            }
        }
        holder.updateSnapshot {
            it.copy(
                detail = SnapshotDetailUi(
                    channelId = channelId,
                    channelLabel = channel.label,
                    card = card,
                    parentId = snapshotData?.parentSnapshotId,
                    states = snapshotData?.let(runner.mapper::states).orEmpty(),
                    actions = actions
                )
            )
        }
    }

    private fun runDetailAction(scope: CoroutineScope, onSaveStatusChanged: (SaveStatusEvent) -> Unit) {
        val detail = snapshot?.detail ?: return
        val channel = runner.entryOf(detail.channelId)?.channel ?: return
        val snapshotId = detail.card.snapshotId
        when (detail.focusedAction ?: return) {
            SnapshotDetailAction.ACTIVATE ->
                runner.useOnDevice(scope, detail.channelId, onSaveStatusChanged, snapshotId)
            SnapshotDetailAction.FORK -> holder.updateSnapshot {
                it.copy(
                    labelEntry = SnapshotLabelEntryUi(
                        mode = SnapshotLabelMode.FORK,
                        snapshotId = snapshotId,
                        romFileId = runner.romFileIdFor(channel)
                    )
                )
            }
            SnapshotDetailAction.COPY_OVER -> holder.updateSnapshot {
                it.copy(
                    copyPicker = SnapshotCopyPickerUi(
                        source = SnapshotCopySource.Snapshot(snapshotId ?: return@updateSnapshot it),
                        targets = runner.copyTargets(channel).map { target -> SnapshotPickTargetUi(target.id, target.label) }
                    )
                )
            }
            SnapshotDetailAction.PIN -> runner.setPinned(scope, detail.channelId, snapshotId ?: return, pinned = true)
            SnapshotDetailAction.UNPIN -> runner.setPinned(scope, detail.channelId, snapshotId ?: return, pinned = false)
            SnapshotDetailAction.SHARE -> runner.setPublic(scope, detail.channelId, snapshotId ?: return, public = true)
            SnapshotDetailAction.UNSHARE -> runner.setPublic(scope, detail.channelId, snapshotId ?: return, public = false)
            SnapshotDetailAction.MAKE_SNAPSHOT ->
                runner.push(scope, SnapshotPush.MakeSnapshot(channel, detail.card.saveId ?: return))
        }
    }

    private fun confirmCopyTarget(scope: CoroutineScope) {
        val picker = snapshot?.copyPicker ?: return
        val source = picker.source
        if (picker.isNewChannelFocused && source is SnapshotCopySource.Backup) {
            holder.updateSnapshot {
                it.copy(
                    copyPicker = null,
                    labelEntry = SnapshotLabelEntryUi(mode = SnapshotLabelMode.NEW_CHANNEL, backupSaveId = source.saveId)
                )
            }
            return
        }
        val target = picker.focusedTarget?.let { runner.entryOf(it.channelId)?.channel } ?: return
        when (source) {
            is SnapshotCopySource.Snapshot -> runner.push(scope, SnapshotPush.CopyOver(source.snapshotId, target))
            is SnapshotCopySource.Backup -> runner.push(scope, SnapshotPush.MakeSnapshot(target, source.saveId))
        }
    }

    private fun runChannelAction(scope: CoroutineScope, onSaveStatusChanged: (SaveStatusEvent) -> Unit) {
        val menu = snapshot?.channelMenu ?: return
        when (menu.focusedAction ?: return) {
            SnapshotChannelAction.USE_ON_DEVICE -> runner.useOnDevice(scope, menu.channelId, onSaveStatusChanged)
            SnapshotChannelAction.RENAME -> holder.updateSnapshot {
                it.copy(
                    labelEntry = SnapshotLabelEntryUi(
                        mode = SnapshotLabelMode.RENAME,
                        text = menu.label,
                        channelId = menu.channelId
                    )
                )
            }
            SnapshotChannelAction.SHARE -> holder.updateSnapshot {
                it.copy(confirm = SnapshotConfirmUi.Share(menu.channelId))
            }
            SnapshotChannelAction.STOP_SHARING -> runner.setShared(scope, menu.channelId, shared = false)
            SnapshotChannelAction.DELETE -> {
                val entry = runner.entryOf(menu.channelId) ?: return
                val pinned = holder.snapshotHistories.value[menu.channelId].orEmpty().count { it.isPinned }
                holder.updateSnapshot {
                    it.copy(
                        confirm = SnapshotConfirmUi.Delete(
                            channelId = menu.channelId,
                            label = menu.label,
                            pinnedCount = pinned,
                            olderSaveCount = entry.olderClientSaves.size
                        )
                    )
                }
            }
        }
    }

    private fun openLabelEntry() {
        val state = snapshot ?: return
        if (!state.canCreateChannel) return
        holder.updateSnapshot { it.copy(labelEntry = SnapshotLabelEntryUi(mode = SnapshotLabelMode.NEW_CHANNEL)) }
    }

    private fun openBackupCopy(index: Int) {
        val state = snapshot ?: return
        val backup = state.backups.getOrNull(index) ?: return
        val targets = runner.ownChannels().map { SnapshotPickTargetUi(it.id, it.label) }
        if (targets.isEmpty() && !state.canCreateChannel) return
        holder.updateSnapshot {
            it.copy(copyPicker = SnapshotCopyPickerUi(source = SnapshotCopySource.Backup(backup.saveId), targets = targets))
        }
    }
}
