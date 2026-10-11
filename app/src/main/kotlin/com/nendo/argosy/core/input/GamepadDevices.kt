package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent

private const val OEM_VIRTUAL_DEVICE_PREFIX = "uinput-"

/**
 * Firmware that re-injects its built-in controls through Android's virtual device. The device
 * describes itself as a keyboard, so it counts as a pad only once it sends pad input: an event
 * that carries a pad source, or a gamepad button code.
 */
object VirtualPad {
    @Volatile
    var seen: Boolean = false
        private set

    fun observe(event: InputEvent): Boolean {
        if (seen || event.device?.isVirtual != true) return false
        if (!event.hasPadSource() && !(event is KeyEvent && event.keyCode.isGamepadButtonCode())) {
            return false
        }
        seen = true
        return true
    }

    private fun InputEvent.hasPadSource(): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun Int.isGamepadButtonCode(): Boolean =
        this in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE
}

fun InputDevice.actsAsGamepad(): Boolean = isPhysicalGamepad() || (isVirtual && VirtualPad.seen)

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
