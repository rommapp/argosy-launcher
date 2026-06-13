package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "quaypass_encounters",
    indices = [
        Index("encounteredAt"),
        Index("seenByUser")
    ]
)
data class QuayPassEncounterEntity(
    @PrimaryKey
    val credentialFingerprint: String,
    val username: String,
    val displayName: String?,
    val avatarColor: String?,
    val avatarBlobBase64: String?,
    val greeting: String?,
    val lastGameTitle: String?,
    val lastGamePlatform: String?,
    val lastGamePlaytimeMinutes: Int?,
    val lastGameIgdbId: Long?,
    val encounteredAt: Instant,
    val seenByUser: Boolean = false,
    val accountId: String? = null,
    val reported: Boolean = false
)
