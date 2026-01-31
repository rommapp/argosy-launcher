package com.nendo.argosy.libretro

import android.view.KeyEvent
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.InputConfigRepository
import org.json.JSONArray

class HotkeyManager(
    private val inputConfigRepository: InputConfigRepository
) {
    private var hotkeys: List<HotkeyConfig> = emptyList()
    private val pressedKeys = mutableSetOf<Int>()
    private var triggeredAction: HotkeyAction? = null
    private var limitToPlayer1 = true
    private var player1ControllerId: String? = null
    private var controllerMappedButtons: Map<String, Set<Int>> = emptyMap()

    data class HotkeyConfig(
        val action: HotkeyAction,
        val keyCodes: Set<Int>,
        val controllerId: String?,
        val isEnabled: Boolean
    )

    fun setHotkeys(entities: List<HotkeyEntity>) {
        hotkeys = entities.map { entity ->
            HotkeyConfig(
                action = entity.action,
                keyCodes = parseComboJson(entity.buttonComboJson).toSet(),
                controllerId = entity.controllerId,
                isEnabled = entity.isEnabled
            )
        }
    }

    fun setLimitToPlayer1(limit: Boolean) {
        limitToPlayer1 = limit
    }

    fun setPlayer1ControllerId(controllerId: String?) {
        player1ControllerId = controllerId
    }

    fun setControllerMappedButtons(mappings: Map<String, Set<Int>>) {
        controllerMappedButtons = mappings
    }

    fun onKeyDown(keyCode: Int, controllerId: String?): HotkeyAction? {
        if (!isHotkeyKey(keyCode)) return null

        if (limitToPlayer1 && player1ControllerId != null && controllerId != player1ControllerId) {
            return null
        }

        pressedKeys.add(keyCode)
        triggeredAction = null

        for (hotkey in hotkeys) {
            if (!hotkey.isEnabled) continue
            if (hotkey.controllerId != null && hotkey.controllerId != controllerId) continue
            if (hotkey.keyCodes.isNotEmpty() && pressedKeys.containsAll(hotkey.keyCodes)) {
                if (hotkey.keyCodes.size == 1 && controllerId != null) {
                    val mappedButtons = controllerMappedButtons[controllerId] ?: emptySet()
                    if (hotkey.keyCodes.first() in mappedButtons) {
                        continue
                    }
                }
                triggeredAction = hotkey.action
                return hotkey.action
            }
        }

        return null
    }

    fun onKeyUp(keyCode: Int): HotkeyAction? {
        pressedKeys.remove(keyCode)
        val action = triggeredAction
        if (pressedKeys.isEmpty()) {
            triggeredAction = null
        }
        return action
    }

    fun isHotkeyActive(action: HotkeyAction): Boolean {
        val hotkey = hotkeys.find { it.action == action && it.isEnabled } ?: return false
        return hotkey.keyCodes.isNotEmpty() && pressedKeys.containsAll(hotkey.keyCodes)
    }

    fun getTriggeredAction(): HotkeyAction? = triggeredAction

    fun clearState() {
        pressedKeys.clear()
        triggeredAction = null
    }

    private fun parseComboJson(jsonStr: String): List<Int> {
        return try {
            val result = mutableListOf<Int>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.getInt(i))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val HOTKEY_KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_C,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_Z,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BACK
        )

        fun isHotkeyKey(keyCode: Int): Boolean = keyCode in HOTKEY_KEYS

        fun getKeyName(keyCode: Int): String {
            return when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> "A"
                KeyEvent.KEYCODE_BUTTON_B -> "B"
                KeyEvent.KEYCODE_BUTTON_C -> "M1"
                KeyEvent.KEYCODE_BUTTON_X -> "X"
                KeyEvent.KEYCODE_BUTTON_Y -> "Y"
                KeyEvent.KEYCODE_BUTTON_Z -> "M2"
                KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
                KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
                KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
                KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
                KeyEvent.KEYCODE_BUTTON_START -> "Start"
                KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
                KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
                KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
                KeyEvent.KEYCODE_BACK -> "Back"
                else -> KeyEvent.keyCodeToString(keyCode)
            }
        }

        fun formatCombo(keyCodes: List<Int>): String {
            if (keyCodes.isEmpty()) return "Not set"
            return keyCodes.joinToString(" + ") { getKeyName(it) }
        }
    }
}
