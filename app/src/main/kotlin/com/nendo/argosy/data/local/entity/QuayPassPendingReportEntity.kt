package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per distinct accepted meeting (one per peer per local 12h window),
 * awaiting report to the server. Kept separate from the collapsed display ledger
 * so an offline run of several meetings with the same peer is transmitted in full
 * rather than collapsed to the latest - the server dedups per account. Holds
 * exactly what report_quaypass_encounter needs: the received credential bundle,
 * the peer's attestation, our per-meeting nonce, and the frozen card. Drained and
 * deleted by QuayPassEncounterReporter.
 */
@Entity(
    tableName = "quaypass_pending_reports",
    indices = [Index("localOwnerUserId")]
)
data class QuayPassPendingReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val peerAccountId: String,
    val credentialBase64: String,
    val attestationBase64: String,
    val nonceBase64: String,
    val tsSecs: Long,
    val cardMessage: String?,
    val cardIgdbId: Long?,
    val cardAvatarPngBase64: String?,
    @ColumnInfo(defaultValue = "0")
    val localOwnerUserId: Long = QuayPassEncounterEntity.NO_OWNER
)
