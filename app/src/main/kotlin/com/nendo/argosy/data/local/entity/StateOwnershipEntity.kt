package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Records which RomM account the bytes currently sitting at a live save-state path belong to.
 *
 * Scoping the state_cache reads per account does not help here: the live state directory is
 * derived from the rom basename and the emulator's own layout, carries no account dimension, and
 * for the built-in core is a device-global folder. A state file one account left behind is
 * otherwise indistinguishable from one this account wrote, so session end discovers it, caches it
 * and uploads it under whoever happens to be signed in.
 *
 * Keyed on the path plus emulator exactly as save_ownership is, because it is the bytes on disk
 * that have exactly one owner. States are multi-slot per game, so the slot identity that
 * state_cache is uniquely indexed on ([slotNumber], [channelName], [coreId] alongside the game and
 * emulator) is carried here too, which is what lets a placement find the cache row for this slot
 * rather than for the game as a whole.
 *
 * During an account switch the row doubles as the durable per-artifact state machine.
 * [transitionState] advances one artifact at a time and is written as each step completes, so a
 * process death mid-switch leaves every file in a state whose recovery action is unambiguous.
 */
@Entity(
    tableName = "state_ownership",
    indices = [
        Index(value = ["statePath", "emulatorId"], unique = true),
        Index("ownerUserId"),
        Index("transitionState"),
        Index("gameId")
    ]
)
data class StateOwnershipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val statePath: String,
    val emulatorId: String,
    val ownerUserId: Long?,
    val contentHash: String?,
    val transitionState: String = STATE_STABLE,
    val updatedAt: Instant,
    val gameId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val slotNumber: Int = 0,
    val channelName: String? = null,
    val coreId: String? = null,
    val pendingOwnerUserId: Long? = null,
    val archivedCacheId: Long? = null,
    val incomingCacheId: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val needsSync: Boolean = false
) {
    companion object {
        const val STATE_STABLE = "stable"
        const val STATE_RECLAIMING = "reclaiming"
        const val STATE_RECLAIMED = "reclaimed"
        const val STATE_CLEARED = "cleared"
        const val STATE_APPLYING = "applying"
    }
}
