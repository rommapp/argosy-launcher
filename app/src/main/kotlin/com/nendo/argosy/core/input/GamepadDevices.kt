package com.nendo.argosy.core.input

import android.view.InputDevice

private const val OEM_VIRTUAL_DEVICE_PREFIX = "uinput-"

/**
 * Whether a device is a pad the launcher should act on. The source flags alone are not enough:
 * HyperOS registers a `uinput-xiaomi` device that advertises a gamepad source with no controller
 * behind it, which hid the on-screen controls and claimed player one on a bare phone.
 */
fun InputDevice.isPhysicalGamepad(): Boolean {
    val padSource = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    if (!padSource || isVirtual) return false
    return !name.orEmpty().startsWith(OEM_VIRTUAL_DEVICE_PREFIX, ignoreCase = true)
}
