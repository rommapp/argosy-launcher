package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class HotkeyAction {
    QUICK_SAVE,
    QUICK_LOAD,
    FAST_FORWARD,
    REWIND,
    IN_GAME_MENU,
    QUICK_SUSPEND
}

@Entity(
    tableName = "hotkeys",
    indices = [Index("action")]
)
data class HotkeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val action: HotkeyAction,
    val buttonComboJson: String,
    val controllerId: String? = null,
    val isEnabled: Boolean = true
)
