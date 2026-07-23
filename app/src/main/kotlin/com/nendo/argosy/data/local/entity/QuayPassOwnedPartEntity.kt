package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "quaypass_owned_parts")
data class QuayPassOwnedPartEntity(
    @PrimaryKey
    val partKey: String,
    val acquiredAt: Instant,
    val synced: Boolean = false
)
