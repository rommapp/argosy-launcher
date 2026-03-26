package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "steam_accounts",
    indices = [
        Index(value = ["steamId"], unique = true)
    ]
)
data class SteamAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val steamId: Long,
    val username: String,
    val avatarHash: String? = null,

    val refreshToken: String,
    val accessToken: String? = null,
    val accessTokenExpiry: Instant? = null,
    val clientId: Long? = null,

    val isActive: Boolean = true,
    val lastLoginAt: Instant = Instant.now(),
    val createdAt: Instant = Instant.now()
)
