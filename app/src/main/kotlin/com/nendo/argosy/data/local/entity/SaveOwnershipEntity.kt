package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Records which RomM account the bytes currently sitting at a live save path belong to.
 *
 * The save path is derived from the ROM basename or sigil save id and carries no account
 * dimension, so two accounts playing the same game resolve to the same file. Without this
 * record a save found on disk is indistinguishable from one this account wrote, and gets
 * adopted and uploaded under whoever happens to be signed in.
 *
 * Keyed on the path rather than on a cache channel: several channels can write the same
 * live file, and it is the bytes on disk that have exactly one owner.
 */
@Entity(
    tableName = "save_ownership",
    indices = [
        Index(value = ["savePath", "emulatorId"], unique = true),
        Index("ownerUserId")
    ]
)
data class SaveOwnershipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val savePath: String,
    val emulatorId: String,
    val ownerUserId: Long?,
    val contentHash: String?,
    val transitionState: String = STATE_STABLE,
    val updatedAt: Instant
) {
    companion object {
        const val STATE_STABLE = "stable"
        const val STATE_RECLAIMING = "reclaiming"
        const val STATE_RECLAIMED = "reclaimed"
        const val STATE_CLEARED = "cleared"
        const val STATE_APPLYING = "applying"
    }
}
