package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "play_sessions",
    indices = [
        Index("gameId"),
        Index("igdbId"),
        Index("startTime"),
        Index("deviceId"),
        Index("userId"),
        Index("ownerUserId")
    ]
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String?,
    val gameId: Long,
    val igdbId: Long?,
    val gameTitle: String,
    val platformSlug: String,
    val startTime: Instant,
    val endTime: Instant,
    val continued: Boolean = false,
    val deviceId: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val activePlayMs: Long = 0,
    val standbyMs: Long = 0,
    /**
     * RomM user id the session belongs to. Separate from [userId], which is the Argosy Social id:
     * the two identities are independent, a device can be linked to one and not the other, and
     * the RomM ingest must not upload another account's sessions under whoever is connected.
     */
    val ownerUserId: Long? = null,
    /**
     * Id RomM assigned when it accepted this session, or [ROMM_SESSION_ID_UNKNOWN] when it
     * reported the session as already held without saying under which id. Null means the RomM
     * ingest has not accepted the row yet; that is the whole selection rule for what to upload.
     */
    val rommSessionId: Long? = null,
    val raGameId: Long? = null,
    /**
     * The last RetroAchievements rich-presence line seen for this session, and when it was
     * current. Null for sessions recorded on another device or before the line was captured.
     */
    val richPresence: String? = null,
    val richPresenceAt: Instant? = null,
    /**
     * The achievements unlocked between this session's launch and end snapshots, in the encoding
     * of RaUnlockSnapshots. Null when no pair of snapshots was taken.
     */
    val raSnapshotUnlocks: String? = null,
    /**
     * Whether another device's session has been added to the game's play time total. A session
     * recorded here is counted when it ends and never carries this flag.
     */
    @ColumnInfo(defaultValue = "0")
    val foldedIntoTotal: Boolean = false
) {
    companion object {
        const val ROMM_SESSION_ID_UNKNOWN = -1L
    }
}
