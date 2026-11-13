package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.KeyEvent

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

    fun isSystemHome(event: KeyEvent): Boolean = isSystemHome(event.keyCode, event.device)

    fun isSystemHome(keyCode: Int, device: InputDevice?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HOME -> true
        KeyEvent.KEYCODE_MOVE_HOME -> device?.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
        else -> false
    }

    fun isGuide(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BUTTON_MODE

    fun isHome(event: KeyEvent): Boolean = isGuide(event.keyCode) || isSystemHome(event)
}
