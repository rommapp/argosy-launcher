package com.nendo.argosy.libretro.ui

import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent

class LibretroMenuInputHandler(
    private val swapAB: Boolean,
    private val swapXY: Boolean,
    private val swapStartSelect: Boolean
) {
    fun mapKeyToEvent(keyCode: Int): GamepadEvent? =
        mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)
}
