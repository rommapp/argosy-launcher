package com.nendo.argosy.data.repository

import android.view.MotionEvent

sealed class InputSource {
    data class Button(val keyCode: Int) : InputSource()
    data class AnalogDirection(val axis: Int, val positive: Boolean) : InputSource()

    companion object {
        const val ANALOG_THRESHOLD = 0.5f
        const val ANALOG_DEADZONE = 0.2f

        fun getAnalogDirectionName(axis: Int, positive: Boolean): String {
            val axisName = when (axis) {
                MotionEvent.AXIS_X -> "LS"
                MotionEvent.AXIS_Y -> "LS"
                MotionEvent.AXIS_Z -> "RS"
                MotionEvent.AXIS_RZ -> "RS"
                MotionEvent.AXIS_HAT_X -> "D-Pad"
                MotionEvent.AXIS_HAT_Y -> "D-Pad"
                MotionEvent.AXIS_LTRIGGER -> return "LT"
                MotionEvent.AXIS_RTRIGGER -> return "RT"
                else -> "Axis $axis"
            }

            val direction = when (axis) {
                MotionEvent.AXIS_X, MotionEvent.AXIS_Z, MotionEvent.AXIS_HAT_X ->
                    if (positive) "Right" else "Left"
                MotionEvent.AXIS_Y, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_Y ->
                    if (positive) "Down" else "Up"
                else -> if (positive) "+" else "-"
            }

            return "$axisName $direction"
        }

        fun getInputSourceDisplayName(source: InputSource): String {
            return when (source) {
                is Button -> InputPresets.getAndroidButtonName(source.keyCode)
                is AnalogDirection -> getAnalogDirectionName(source.axis, source.positive)
            }
        }
    }
}
