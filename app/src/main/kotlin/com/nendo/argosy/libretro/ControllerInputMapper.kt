package com.nendo.argosy.libretro

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.RetroButton
import com.swordfish.libretrodroid.KeyMapper

data class SyntheticKeyEvent(
    val keyCode: Int,
    val action: Int,
    val port: Int
)

class ControllerInputMapper : KeyMapper {
    private var extendedMappings: Map<String, Map<InputSource, Int>> = emptyMap()
    private val activeAnalogDirections = mutableMapOf<String, MutableSet<InputSource.AnalogDirection>>()
    private var portResolver: ((InputDevice) -> Int)? = null

    fun setPortResolver(resolver: (InputDevice) -> Int) {
        portResolver = resolver
    }

    fun setExtendedMappings(mappings: Map<String, Map<InputSource, Int>>) {
        extendedMappings = mappings
    }

    fun setExtendedMappingForController(controllerId: String, mapping: Map<InputSource, Int>) {
        extendedMappings = extendedMappings + (controllerId to mapping)
    }

    fun clearMappings() {
        extendedMappings = emptyMap()
        activeAnalogDirections.clear()
    }

    override fun mapKey(device: InputDevice, keyCode: Int): Int {
        val controllerId = getControllerId(device)
        val mapping = extendedMappings[controllerId]

        if (mapping != null) {
            val inputSource = InputSource.Button(keyCode)
            val targetRetroButton = mapping[inputSource]
            if (targetRetroButton != null) {
                return retroButtonToAndroidKeyCode(targetRetroButton)
            }
        }

        return defaultSwap(keyCode)
    }

    fun processMotionEvent(event: MotionEvent): List<SyntheticKeyEvent> {
        val device = event.device ?: return emptyList()
        val controllerId = getControllerId(device)
        val mapping = extendedMappings[controllerId] ?: return emptyList()

        val port = portResolver?.invoke(device) ?: 0
        val results = mutableListOf<SyntheticKeyEvent>()

        val analogMappings = mapping.entries
            .filter { it.key is InputSource.AnalogDirection }
            .associate { (it.key as InputSource.AnalogDirection) to it.value }

        if (analogMappings.isEmpty()) return emptyList()

        val currentActive = activeAnalogDirections.getOrPut(controllerId) { mutableSetOf() }
        val newActive = mutableSetOf<InputSource.AnalogDirection>()

        for ((analogDir, retroButton) in analogMappings) {
            val axisValue = event.getAxisValue(analogDir.axis)
            val isPressed = if (analogDir.positive) {
                axisValue > InputSource.ANALOG_THRESHOLD
            } else {
                axisValue < -InputSource.ANALOG_THRESHOLD
            }

            if (isPressed) {
                newActive.add(analogDir)
                if (analogDir !in currentActive) {
                    val keyCode = retroButtonToAndroidKeyCode(retroButton)
                    results.add(SyntheticKeyEvent(keyCode, KeyEvent.ACTION_DOWN, port))
                }
            }
        }

        for (analogDir in currentActive) {
            if (analogDir !in newActive) {
                val retroButton = analogMappings[analogDir] ?: continue
                val keyCode = retroButtonToAndroidKeyCode(retroButton)
                results.add(SyntheticKeyEvent(keyCode, KeyEvent.ACTION_UP, port))
            }
        }

        activeAnalogDirections[controllerId] = newActive

        return results
    }

    fun getMappedButtonsForController(controllerId: String): Set<Int> {
        val mapping = extendedMappings[controllerId] ?: return emptySet()
        return mapping.entries
            .filter { it.key is InputSource.Button }
            .map { (it.key as InputSource.Button).keyCode }
            .toSet()
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
