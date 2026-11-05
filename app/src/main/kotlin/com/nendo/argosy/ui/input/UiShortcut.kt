package com.nendo.argosy.ui.input

import android.view.KeyEvent
import com.nendo.argosy.libretro.HotkeyManager

enum class UiShortcut { OPEN_NAVIGATION, OPEN_QUICK_PANEL, HOME, BACK }

/**
 * Decides, at the moment a press begins, whether Argosy's own screens may take a UI shortcut.
 * [hotkeysAllowed] covers the bound navigation and quick panel buttons; [longBackAllowed] covers
 * holding Back, which additionally stands down while the drawer, Quick Settings or the quick menu
 * already own Back.
 */
interface UiShortcutGate {
    fun hotkeysAllowed(): Boolean
    fun longBackAllowed(): Boolean
}

object UiShortcutKeys {
    private val NAVIGATION_KEYS: Set<Int> = setOf(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER
    )

    fun isBindable(keyCode: Int): Boolean =
        HotkeyManager.isHotkeyKey(keyCode) && keyCode !in NAVIGATION_KEYS
}
