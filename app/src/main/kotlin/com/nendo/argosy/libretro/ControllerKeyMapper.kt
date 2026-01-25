package com.nendo.argosy.libretro

import android.view.InputDevice
import android.view.KeyEvent
import com.nendo.argosy.data.repository.RetroButton
import com.swordfish.libretrodroid.KeyMapper

class ControllerKeyMapper : KeyMapper {
    private var controllerMappings: Map<String, Map<Int, Int>> = emptyMap()

    fun setMappings(mappings: Map<String, Map<Int, Int>>) {
        controllerMappings = mappings
    }

    fun setMappingForController(controllerId: String, mapping: Map<Int, Int>) {
        controllerMappings = controllerMappings + (controllerId to mapping)
    }

    fun clearMappings() {
        controllerMappings = emptyMap()
    }

    override fun mapKey(device: InputDevice, keyCode: Int): Int {
        val controllerId = getControllerId(device)
        val mapping = controllerMappings[controllerId]

        if (mapping != null) {
            val targetRetroButton = mapping[keyCode]
            if (targetRetroButton != null) {
                return retroButtonToAndroidKeyCode(targetRetroButton)
            }
        }

        return defaultSwap(keyCode)
    }

    private fun getControllerId(device: InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    private fun defaultSwap(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }
    }

    companion object {
        fun retroButtonToAndroidKeyCode(retroButton: Int): Int {
            return when (retroButton) {
                RetroButton.A -> KeyEvent.KEYCODE_BUTTON_A
                RetroButton.B -> KeyEvent.KEYCODE_BUTTON_B
                RetroButton.X -> KeyEvent.KEYCODE_BUTTON_X
                RetroButton.Y -> KeyEvent.KEYCODE_BUTTON_Y
                RetroButton.START -> KeyEvent.KEYCODE_BUTTON_START
                RetroButton.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
                RetroButton.L -> KeyEvent.KEYCODE_BUTTON_L1
                RetroButton.R -> KeyEvent.KEYCODE_BUTTON_R1
                RetroButton.L2 -> KeyEvent.KEYCODE_BUTTON_L2
                RetroButton.R2 -> KeyEvent.KEYCODE_BUTTON_R2
                RetroButton.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
                RetroButton.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
                RetroButton.UP -> KeyEvent.KEYCODE_DPAD_UP
                RetroButton.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                RetroButton.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                RetroButton.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                else -> KeyEvent.KEYCODE_UNKNOWN
            }
        }
    }
}
