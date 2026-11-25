package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "romm_music_tracks",
    primaryKeys = ["ownerUserId", "romFileId"],
    indices = [Index(value = ["ownerUserId", "romId"])]
)
data class RommMusicTrackEntity(
    val ownerUserId: Long,
    val romFileId: Long,
    val romId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val year: Int?,
    val track: Int?,
    val disc: Int?,
    val durationSeconds: Double?,
    val gameName: String?,
    val platformId: Long,
    val platformSlug: String,
    val platformName: String,
    val streamUrl: String,
    val coverUrl: String?,
    val gameCoverUrl: String?
)

@Entity(tableName = "romm_music_index_state", primaryKeys = ["ownerUserId"])
data class RommMusicIndexStateEntity(
    val ownerUserId: Long,
    val fetchedAt: Long,
    val trackCount: Int
)
