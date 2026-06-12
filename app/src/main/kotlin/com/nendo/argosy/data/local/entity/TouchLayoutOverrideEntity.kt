package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import java.time.Instant

@Entity(
    tableName = "touch_layout_overrides",
    primaryKeys = ["platformSlug", "orientation"]
)
data class TouchLayoutOverrideEntity(
    val platformSlug: String,
    val orientation: String,
    val schemaVersion: Int,
    val layoutJson: String,
    val updatedAt: Instant
)
