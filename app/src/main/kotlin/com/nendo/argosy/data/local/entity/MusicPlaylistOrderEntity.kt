package com.nendo.argosy.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "music_playlist_orders",
    primaryKeys = ["ownerKey", "playlistName"]
)
data class MusicPlaylistOrderEntity(
    val ownerKey: Long,
    val playlistName: String,
    val entries: String,
    val updatedAt: Long
)
