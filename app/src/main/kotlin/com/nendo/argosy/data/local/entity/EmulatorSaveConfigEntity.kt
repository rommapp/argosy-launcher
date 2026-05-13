package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "emulator_save_config",
    indices = [
        Index(value = ["emulatorId"], unique = true)
    ]
)
data class EmulatorSaveConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val emulatorId: String,
    val savePathPattern: String,
    val isAutoDetected: Boolean,
    val isUserOverride: Boolean = false,
    val lastVerifiedAt: Instant? = null,
    val statePathPattern: String? = null,
    val isUserStateOverride: Boolean = false,
    val selectedMemcardPath: String? = null
)
