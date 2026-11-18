package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.KeyEvent
import com.nendo.argosy.util.AynThor

/**
 * Every key Argosy reads as Home. Devices disagree on what their Home button sends: most send
 * [KeyEvent.KEYCODE_HOME], Retroid handhelds send [KeyEvent.KEYCODE_MOVE_HOME], and a controller's
 * guide button sends [KeyEvent.KEYCODE_BUTTON_MODE]. MOVE_HOME counts only from a device without
 * a letter keyboard, so a keyboard's Home key keeps moving the text cursor.
 */
object HomeKeys {

    /**
     * The keycodes a device's own Home button may send. Never bindable: the system or Argosy's
     * accessibility service takes them before an emulator could.
     */
    val SYSTEM: Set<Int> = setOf(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MOVE_HOME)

    /**
     * [SYSTEM] plus the guide button, for the global combos, where the guide already means Home.
     */
    val RESERVED: Set<Int> = SYSTEM + KeyEvent.KEYCODE_BUTTON_MODE

    /**
     * Scancode of the AYN button on the AYN Thor. Its key layout maps it to
     * [KeyEvent.KEYCODE_HOME], and the system opens the AYN menu only when it receives the
     * original event, so on a Thor Argosy never reads it as Home.
     */
    const val THOR_AYN_BUTTON_SCANCODE = 194

    fun isSystemHome(event: KeyEvent): Boolean = isSystemHome(event.keyCode, event.device, event.scanCode)

    fun isSystemHome(
        keyCode: Int,
        device: InputDevice?,
        scanCode: Int = 0,
        onAynThor: Boolean = AynThor.matches()
    ): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HOME -> !(onAynThor && scanCode == THOR_AYN_BUTTON_SCANCODE)
        KeyEvent.KEYCODE_MOVE_HOME -> device?.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
        else -> false
    }

    fun isGuide(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BUTTON_MODE

    fun isHome(event: KeyEvent): Boolean = isGuide(event.keyCode) || isSystemHome(event)
}
