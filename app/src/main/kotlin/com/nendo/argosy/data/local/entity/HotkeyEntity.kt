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
    RESET_GAME,
    CYCLE_CORE_OPTION,
    SEND_CORE_INPUT
}

enum class HotkeyScopeType { GLOBAL, PLATFORM, CORE }

enum class CoreInputMode { PULSE, HOLD }

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
    val holdMs: Long = 0,
    val coreOptionKey: String? = null,
    val coreOptionDirection: Int = 1,
    val coreOptionValuesJson: String? = null,
    val coreInputRetropadId: Int? = null,
    val coreInputMode: CoreInputMode = CoreInputMode.PULSE,
    val scopeType: HotkeyScopeType = HotkeyScopeType.GLOBAL,
    val scopeKey: String? = null
)
