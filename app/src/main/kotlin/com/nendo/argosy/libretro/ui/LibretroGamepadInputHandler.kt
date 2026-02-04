package com.nendo.argosy.libretro.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.RawInputInterceptor

class LibretroGamepadInputHandler(
    private val menuInputHandler: LibretroMenuInputHandler,
    private val getActiveHandler: () -> InputHandler?
) : RawInputInterceptor {

    override var lastInputDevice: InputDevice? = null
        private set
    private var rawKeyEventListener: ((KeyEvent) -> Boolean)? = null
    private var rawMotionEventListener: ((MotionEvent) -> Boolean)? = null

    val hasRawKeyListener: Boolean get() = rawKeyEventListener != null

    override fun setRawKeyEventListener(listener: ((KeyEvent) -> Boolean)?) {
        rawKeyEventListener = listener
    }

    override fun setRawMotionEventListener(listener: ((MotionEvent) -> Boolean)?) {
        rawMotionEventListener = listener
    }

    override fun mapKeyToEvent(keyCode: Int): GamepadEvent? =
        menuInputHandler.mapKeyToEvent(keyCode)

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastInputDevice = event.device
        }
        rawKeyEventListener?.let { return it(event) }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        val gamepadEvent = menuInputHandler.mapKeyToEvent(event.keyCode) ?: return false
        val handler = getActiveHandler() ?: return false
        return dispatchToHandler(gamepadEvent, handler).handled
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        rawMotionEventListener?.let { return it(event) }
        return false
    }

    private fun dispatchToHandler(event: GamepadEvent, handler: InputHandler): InputResult {
        return when (event) {
            GamepadEvent.Up -> handler.onUp()
            GamepadEvent.Down -> handler.onDown()
            GamepadEvent.Left -> handler.onLeft()
            GamepadEvent.Right -> handler.onRight()
            GamepadEvent.Confirm -> handler.onConfirm()
            GamepadEvent.Back -> handler.onBack()
            GamepadEvent.SecondaryAction -> handler.onSecondaryAction()
            GamepadEvent.ContextMenu -> handler.onContextMenu()
            GamepadEvent.PrevSection -> handler.onPrevSection()
            GamepadEvent.NextSection -> handler.onNextSection()
            GamepadEvent.Menu -> handler.onMenu()
            GamepadEvent.Select -> handler.onSelect()
            GamepadEvent.PrevTrigger -> handler.onPrevTrigger()
            GamepadEvent.NextTrigger -> handler.onNextTrigger()
            GamepadEvent.LeftStickClick -> handler.onLeftStickClick()
            GamepadEvent.RightStickClick -> handler.onRightStickClick()
        }
    }
}
