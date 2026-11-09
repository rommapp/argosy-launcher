package com.nendo.argosy.hardware

import android.view.KeyEvent

class SystemKeyFilter(
    private val backGate: BackHoldGate,
    private val hotkeys: SystemHotkeyDetector
) {
    fun onKey(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        val isRepeat = repeatCount > 0
        val isBack = keyCode == KeyEvent.KEYCODE_BACK
        return when (action) {
            KeyEvent.ACTION_DOWN -> if (isBack) backGate.onDown(isRepeat) else hotkeys.onKeyDown(keyCode, isRepeat)
            KeyEvent.ACTION_UP -> if (isBack) backGate.onUp() else hotkeys.onKeyUp(keyCode)
            else -> false
        }
    }

    fun reset() {
        backGate.reset()
        hotkeys.reset()
    }
}
