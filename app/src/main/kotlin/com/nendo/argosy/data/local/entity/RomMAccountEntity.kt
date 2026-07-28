package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A RomM account this device has paired with.
 *
 * Keyed on [rommUserId] rather than the token: the token is a credential that rotates on
 * re-pairing, while the user id is the stable identity that queued work, saves and library
 * visibility all hang off. Re-pairing after a revoked token therefore resumes an existing
 * account instead of orphaning everything attributed to it.
 */
@Entity(
    tableName = "romm_accounts",
    indices = [
        Index(value = ["rommUserId"], unique = true),
        Index("isActive")
    ]
)
data class RomMAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rommUserId: Long,
    val username: String,
    val baseUrl: String,
    val token: String,
    val deviceId: String? = null,
    val deviceClientVersion: String? = null,
    val avatarPath: String? = null,
    val isActive: Boolean = false,
    val lastLoginAt: Instant,
    val createdAt: Instant
)
