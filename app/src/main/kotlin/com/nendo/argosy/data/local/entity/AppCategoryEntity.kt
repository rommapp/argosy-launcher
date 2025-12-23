package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_category_cache",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class AppCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val category: String?,
    val isGame: Boolean,
    val isManualOverride: Boolean = false,
    val fetchedAt: Long
)
