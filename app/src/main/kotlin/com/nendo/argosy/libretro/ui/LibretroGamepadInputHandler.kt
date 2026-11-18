package com.nendo.argosy.libretro.ui

import android.view.KeyEvent
import android.view.MotionEvent
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.GamepadInput
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.RawInputInterceptor

class LibretroGamepadInputHandler(
    private val menuInputHandler: LibretroMenuInputHandler,
    private val dispatcher: InputDispatcher,
    private val getActiveHandler: () -> InputHandler?
) : RawInputInterceptor {

    private var rawKeyEventListener: ((KeyEvent) -> Boolean)? = null
    private var rawKeyDeclinesPassThrough = false
    private var rawMotionEventListener: ((MotionEvent) -> Boolean)? = null

    val hasRawKeyListener: Boolean get() = rawKeyEventListener != null

    override fun setRawKeyEventListener(listener: ((KeyEvent) -> Boolean)?) {
        rawKeyEventListener = listener
        rawKeyDeclinesPassThrough = false
    }

    override fun setRawKeyEventFilter(filter: ((KeyEvent) -> Boolean)?) {
        rawKeyEventListener = filter
        rawKeyDeclinesPassThrough = filter != null
    }

    override fun setRawMotionEventListener(listener: ((MotionEvent) -> Boolean)?) {
        rawMotionEventListener = listener
    }

    override fun mapKeyToEvent(keyCode: Int, deviceId: Int): GamepadEvent? =
        menuInputHandler.mapKeyToEvent(keyCode, deviceId)

    fun handleKeyEvent(event: KeyEvent): Boolean {
        rawKeyEventListener?.let { listener ->
            val consumed = listener(event)
            if (consumed || !rawKeyDeclinesPassThrough) return consumed
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false

        val gamepadEvent = menuInputHandler.mapKeyToEvent(event.keyCode, event.deviceId) ?: return false
        return dispatch(GamepadInput(gamepadEvent, isRepeat = event.repeatCount > 0))
    }

    fun dispatch(input: GamepadInput): Boolean {
        val handler = getActiveHandler() ?: return false
        dispatcher.subscribeView(handler)
        return dispatcher.dispatch(input).handled
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        rawMotionEventListener?.let { return it(event) }
        return false
    }
}
