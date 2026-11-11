package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import com.nendo.argosy.hardware.pad.MutedPadDevices
import com.nendo.argosy.hardware.pad.PadDeviceIdentity

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
 * behind it, which hid the on-screen controls and claimed player one on a bare phone. A joystick
 * source without gamepad buttons is not a pad either: a TV remote's consumer-control node reports
 * one, and could never press a button to claim a controller seat. A device the pad driver muted
 * for a pad it serves is not a pad either: the driver's virtual pad stands in for it.
 */
fun InputDevice.isPhysicalGamepad(): Boolean {
    val padSource = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
    if (!padSource || isVirtual) return false
    if (name.orEmpty().startsWith(OEM_VIRTUAL_DEVICE_PREFIX, ignoreCase = true)) return false
    return !isMutedByPadDriver(MutedPadDevices.devices.value)
}

internal fun InputDevice.isMutedByPadDriver(muted: Set<PadDeviceIdentity>): Boolean =
    muted.isNotEmpty() &&
        MutedPadDevices.isDriverMuted(PadDeviceIdentity(vendorId, productId, name.orEmpty()), muted)

/**
 * Whether this pointer device is the touchpad of a connected game controller: a mouse or
 * touchpad device that is itself a physical gamepad, or that belongs to one. A connected gamepad
 * owns it when the two share vendor and product ids, as a DualShock 4 or DualSense touchpad does,
 * or when the pointer carries the gamepad's name, alone or with a suffix ("DualSense Wireless
 * Controller Touchpad"), which still holds where the system re-registers the pad under other ids.
 */
fun InputDevice.isControllerTouchpad(): Boolean {
    val pointer = supportsSource(InputDevice.SOURCE_MOUSE) ||
        supportsSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
        supportsSource(InputDevice.SOURCE_TOUCHPAD)
    if (!pointer) return false
    if (isPhysicalGamepad()) return true
    val pointerName = name.orEmpty()
    return InputDevice.getDeviceIds().any { otherId ->
        otherId != id && InputDevice.getDevice(otherId)?.let { other ->
            other.isPhysicalGamepad() && (
                (vendorId != 0 && other.vendorId == vendorId && other.productId == productId) ||
                    other.ownsPointerNamed(pointerName)
                )
        } == true
    }
}

private fun InputDevice.ownsPointerNamed(pointerName: String): Boolean {
    val padName = name.orEmpty()
    return padName.isNotBlank() && (pointerName == padName || pointerName.startsWith("$padName "))
}
