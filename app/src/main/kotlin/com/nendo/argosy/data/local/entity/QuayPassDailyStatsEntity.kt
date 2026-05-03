package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quaypass_daily_stats")
data class QuayPassDailyStatsEntity(
    @PrimaryKey
    val date: String,
    val encounterCount: Int = 0,
    val ticketsEarned: Int = 0
)
