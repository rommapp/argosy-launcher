package com.nendo.argosy.ui.input

import android.view.KeyEvent

/**
 * Hold-Select alt mode shared by every key path that dispatches mapped [GamepadEvent]s.
 *
 * With combos configured, Select is held back on press and emitted on release, unless a mapped
 * button lands while it is held: that button emits its combo target instead and the Select
 * release is swallowed. With no combos configured Select passes through on press like any other
 * button. Every other event passes through on press, and nothing is emitted for its release.
 */
class SelectModifier(comboMap: Map<GamepadEvent, GamepadEvent> = emptyMap()) {

    private enum class State { IDLE, HELD, COMBO_FIRED }

    private var state = State.IDLE

    var comboMap: Map<GamepadEvent, GamepadEvent> = comboMap

    fun reset() {
        state = State.IDLE
    }

    /**
     * Returns the event to emit now for [event] arriving with [action], or null when this key
     * action emits nothing.
     */
    fun filter(event: GamepadEvent, action: Int): GamepadEvent? {
        if (event == GamepadEvent.Select && comboMap.isNotEmpty()) return filterSelect(action)
        if (action != KeyEvent.ACTION_DOWN) return null
        if (state == State.IDLE) return event
        val comboEvent = comboMap[event] ?: return event
        state = State.COMBO_FIRED
        return comboEvent
    }

    private fun filterSelect(action: Int): GamepadEvent? = when (action) {
        KeyEvent.ACTION_DOWN -> {
            state = State.HELD
            null
        }
        KeyEvent.ACTION_UP -> {
            val wasHeld = state == State.HELD
            state = State.IDLE
            if (wasHeld) GamepadEvent.Select else null
        }
        else -> null
    }

    companion object {
        fun comboMapFrom(selectLCombo: String, selectRCombo: String): Map<GamepadEvent, GamepadEvent> {
            val map = mutableMapOf<GamepadEvent, GamepadEvent>()
            comboActionToEvent(selectLCombo)?.let { map[GamepadEvent.PrevSection] = it }
            comboActionToEvent(selectRCombo)?.let { map[GamepadEvent.NextSection] = it }
            return map
        }

        private fun comboActionToEvent(action: String): GamepadEvent? = when (action) {
            "quick_menu" -> GamepadEvent.LeftStickClick
            "quick_settings" -> GamepadEvent.RightStickClick
            else -> null
        }
    }
}
