package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "social_game_cache",
    indices = [
        Index("fetchedAt")
    ]
)
data class SocialGameCacheEntity(
    @PrimaryKey
    val igdbId: Int,
    val title: String,
    val coverUrl: String? = null,
    val platformSlug: String? = null,
    val releaseYear: Int? = null,
    val fetchedAt: Instant
)
