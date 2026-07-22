package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached RMS loudness (dBFS) of a local audio file, keyed by path and validated against fileKey (mtime|length). */
@Entity(tableName = "audio_loudness")
data class AudioLoudnessEntity(
    @PrimaryKey
    val filePath: String,
    val fileKey: String,
    val meanDb: Double,
    val measuredAt: Long
)
