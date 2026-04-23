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
    QUICK_SUSPEND,
    RESET_GAME
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
    val isEnabled: Boolean = true,
    /** 0 = fire immediately on combo match; >0 = combo must be held this many ms to fire. */
    val holdMs: Long = 0
)
