package com.nendo.argosy.data.sync.snapshot

data class SaveHashes(val contentHash: String, val identityHash: String) {
    fun sameSaveAs(other: SaveHashes): Boolean = identityHash == other.identityHash
}

data class SnapshotPoint(val id: Long, val save: SaveHashes?, val byChoice: Boolean = false)

sealed class SnapshotAction {
    data object Nothing : SnapshotAction()
    data class Push(val expectedCurrentId: Long?, val parentSnapshotId: Long? = null) : SnapshotAction()
    data class Download(val snapshotId: Long) : SnapshotAction()
    data class Adopt(val snapshotId: Long) : SnapshotAction()
    data class Conflict(val currentId: Long) : SnapshotAction()
}

/**
 * The spec's decide table for one channel: [held] is the snapshot this device last pushed or
 * applied, [current] the channel's current on the server, [local] the save on disk now. A missing
 * local save never reads as a change, so a cleared save folder is refilled, not pushed as empty.
 * A held snapshot the user restored ([SnapshotPoint.byChoice]) is kept while clean and, once
 * played on, pushed on top of current. Every push names [held] as its parent.
 */
object SnapshotDecision {
    fun decide(held: SnapshotPoint?, current: SnapshotPoint?, local: SaveHashes?): SnapshotAction {
        if (current == null) {
            return if (local != null) {
                SnapshotAction.Push(expectedCurrentId = null, parentSnapshotId = held?.id)
            } else {
                SnapshotAction.Nothing
            }
        }
        if (held == null) {
            return when {
                local == null -> SnapshotAction.Download(current.id)
                current.save != null && local.sameSaveAs(current.save) -> SnapshotAction.Adopt(current.id)
                else -> SnapshotAction.Conflict(current.id)
            }
        }
        val dirty = local != null && (held.save == null || !local.sameSaveAs(held.save))
        return when {
            local == null && held.byChoice && held.save != null -> SnapshotAction.Download(held.id)
            local == null && held.id == current.id && current.save != null -> SnapshotAction.Download(current.id)
            held.id == current.id -> if (dirty) SnapshotAction.Push(current.id, parentSnapshotId = held.id) else SnapshotAction.Nothing
            held.byChoice -> if (dirty) SnapshotAction.Push(current.id, parentSnapshotId = held.id) else SnapshotAction.Nothing
            dirty -> SnapshotAction.Conflict(current.id)
            else -> SnapshotAction.Download(current.id)
        }
    }
}
