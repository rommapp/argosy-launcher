package com.nendo.argosy.libretro.ui

import android.view.KeyEvent
import com.nendo.argosy.ui.input.GamepadEvent

class LibretroMenuInputHandler(
    private val swapAB: Boolean,
    private val swapXY: Boolean,
    private val swapStartSelect: Boolean
) {
    fun mapKeyToEvent(keyCode: Int): GamepadEvent? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> GamepadEvent.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> GamepadEvent.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> GamepadEvent.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadEvent.Right
        KeyEvent.KEYCODE_BUTTON_A -> if (swapAB) GamepadEvent.Back else GamepadEvent.Confirm
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> GamepadEvent.Confirm
        KeyEvent.KEYCODE_BUTTON_B -> if (swapAB) GamepadEvent.Confirm else GamepadEvent.Back
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> GamepadEvent.Back
        KeyEvent.KEYCODE_BUTTON_X -> if (swapXY) GamepadEvent.ContextMenu else GamepadEvent.SecondaryAction
        KeyEvent.KEYCODE_BUTTON_Y -> if (swapXY) GamepadEvent.SecondaryAction else GamepadEvent.ContextMenu
        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadEvent.PrevSection
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadEvent.NextSection
        KeyEvent.KEYCODE_BUTTON_START -> if (swapStartSelect) GamepadEvent.Select else GamepadEvent.Menu
        KeyEvent.KEYCODE_BUTTON_SELECT -> if (swapStartSelect) GamepadEvent.Menu else GamepadEvent.Select
        else -> null
    }
}
