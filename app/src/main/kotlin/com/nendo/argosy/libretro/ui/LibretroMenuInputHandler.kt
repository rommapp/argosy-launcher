package com.nendo.argosy.libretro.ui

import com.nendo.argosy.core.input.ControllerProfiles
import com.nendo.argosy.core.input.forDevice
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.gamepadEventFor

class LibretroMenuInputHandler(
    private val controllerProfiles: ControllerProfiles
) {
    fun mapKeyToEvent(keyCode: Int, deviceId: Int): GamepadEvent? =
        controllerProfiles.forDevice(deviceId).gamepadEventFor(keyCode)
}
