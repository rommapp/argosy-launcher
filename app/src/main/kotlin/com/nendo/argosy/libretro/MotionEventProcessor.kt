package com.nendo.argosy.libretro

import android.view.InputDevice
import android.view.MotionEvent
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.abs

class MotionEventProcessor(
    private val inputMapper: ControllerInputMapper,
    private val portResolver: ControllerPortResolver,
    private val videoSettings: VideoSettingsManager,
    private val getRetroView: () -> GLRetroView
) {
    fun processGamepadMotion(event: MotionEvent): Boolean {
        val retroView = getRetroView()

        val syntheticEvents = inputMapper.processMotionEvent(event)
        for (synthetic in syntheticEvents) {
            retroView.sendKeyEvent(synthetic.action, synthetic.keyCode, synthetic.port)
        }

        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) {
            return false
        }

        val device = event.device
        val port = if (device != null) portResolver.getPort(device) else 0
        if (port < 0) return false

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val stickX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
        val rStickX = event.getAxisValue(MotionEvent.AXIS_Z)
        val rStickY = event.getAxisValue(MotionEvent.AXIS_RZ)

        var dpadX = hatX
        var dpadY = hatY
        if (videoSettings.currentAnalogAsDpad) {
            if (abs(stickX) > abs(dpadX)) dpadX = stickX
            if (abs(stickY) > abs(dpadY)) dpadY = stickY
        }
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dpadX, dpadY, port)

        var analogX = stickX
        var analogY = stickY
        if (videoSettings.currentDpadAsAnalog) {
            if (abs(hatX) > abs(analogX)) analogX = hatX
            if (abs(hatY) > abs(analogY)) analogY = hatY
        }
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, analogX, analogY, port)
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, rStickX, rStickY, port)

        return true
    }
}
