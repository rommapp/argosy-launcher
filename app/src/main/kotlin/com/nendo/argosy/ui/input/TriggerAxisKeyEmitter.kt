package com.nendo.argosy.ui.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TriggerAxisKeyEmitter @Inject constructor() {
    private val pressedState = mutableMapOf<Pair<Int, Int>, Boolean>()

    fun emit(event: MotionEvent, skipAxis: (axis: Int) -> Boolean = { false }): List<KeyEvent> {
        val device = event.device ?: return emptyList()
        val deviceId = device.id
        val results = mutableListOf<KeyEvent>()
        for ((axis, keyCode) in TRIGGER_AXES) {
            if (skipAxis(axis)) continue
            val isPressed = event.getAxisValue(axis) > THRESHOLD
            val key = deviceId to axis
            val wasPressed = pressedState[key] ?: false
            if (isPressed != wasPressed) {
                pressedState[key] = isPressed
                val action = if (isPressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                results.add(buildKeyEvent(event, deviceId, action, keyCode))
            }
        }
        return results
    }

    fun forget(deviceId: Int) {
        pressedState.keys.removeAll { it.first == deviceId }
    }

    private fun buildKeyEvent(source: MotionEvent, deviceId: Int, action: Int, keyCode: Int): KeyEvent {
        return KeyEvent(
            source.downTime,
            source.eventTime,
            action,
            keyCode,
            0,
            0,
            deviceId,
            0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_GAMEPAD
        )
    }

    companion object {
        private const val THRESHOLD = 0.5f
        private val TRIGGER_AXES = listOf(
            MotionEvent.AXIS_LTRIGGER to KeyEvent.KEYCODE_BUTTON_L2,
            MotionEvent.AXIS_BRAKE to KeyEvent.KEYCODE_BUTTON_L2,
            MotionEvent.AXIS_RTRIGGER to KeyEvent.KEYCODE_BUTTON_R2,
            MotionEvent.AXIS_GAS to KeyEvent.KEYCODE_BUTTON_R2
        )
    }
}
