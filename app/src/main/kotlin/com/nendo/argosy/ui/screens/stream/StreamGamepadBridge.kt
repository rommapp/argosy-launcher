package com.nendo.argosy.ui.screens.stream

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Android WebView has no Gamepad API, so the stream page reads a virtual standard-mapping pad
 * that this bridge keeps current. Key and motion events update button and axis state; [script]
 * renders the state as a call into the page shim installed by [INSTALL_SCRIPT].
 */
class StreamGamepadBridge {
    private val buttons = FloatArray(BUTTON_COUNT)
    private val axes = FloatArray(AXIS_COUNT)
    private val hat = FloatArray(2)

    fun onKey(keyCode: Int, pressed: Boolean): Boolean {
        val index = KEY_BUTTONS[keyCode] ?: return false
        buttons[index] = if (pressed) 1f else 0f
        return true
    }

    fun onMotion(axis: (Int) -> Float): Boolean {
        axes[0] = axis(MotionEvent.AXIS_X)
        axes[1] = axis(MotionEvent.AXIS_Y)
        axes[2] = axis(MotionEvent.AXIS_Z)
        axes[3] = axis(MotionEvent.AXIS_RZ)
        buttons[LEFT_TRIGGER] = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE))
        buttons[RIGHT_TRIGGER] = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS))
        val hatX = axis(MotionEvent.AXIS_HAT_X)
        val hatY = axis(MotionEvent.AXIS_HAT_Y)
        if (hatX != hat[0] || hatY != hat[1]) {
            hat[0] = hatX
            hat[1] = hatY
            buttons[DPAD_LEFT] = if (hatX < -HAT_THRESHOLD) 1f else 0f
            buttons[DPAD_RIGHT] = if (hatX > HAT_THRESHOLD) 1f else 0f
            buttons[DPAD_UP] = if (hatY < -HAT_THRESHOLD) 1f else 0f
            buttons[DPAD_DOWN] = if (hatY > HAT_THRESHOLD) 1f else 0f
        }
        return true
    }

    fun buttonValue(index: Int): Float = buttons[index]

    fun axisValue(index: Int): Float = axes[index]

    fun script(): String =
        "window.__argosyPadSet&&window.__argosyPadSet([${buttons.joinToString(",")}],[${axes.joinToString(",")}]);"

    companion object {
        const val BUTTON_COUNT = 17
        const val AXIS_COUNT = 4
        private const val LEFT_TRIGGER = 6
        private const val RIGHT_TRIGGER = 7
        private const val DPAD_UP = 12
        private const val DPAD_DOWN = 13
        private const val DPAD_LEFT = 14
        private const val DPAD_RIGHT = 15
        private const val HAT_THRESHOLD = 0.5f

        private val KEY_BUTTONS = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to 0,
            KeyEvent.KEYCODE_BUTTON_B to 1,
            KeyEvent.KEYCODE_BUTTON_X to 2,
            KeyEvent.KEYCODE_BUTTON_Y to 3,
            KeyEvent.KEYCODE_BUTTON_L1 to 4,
            KeyEvent.KEYCODE_BUTTON_R1 to 5,
            KeyEvent.KEYCODE_BUTTON_L2 to LEFT_TRIGGER,
            KeyEvent.KEYCODE_BUTTON_R2 to RIGHT_TRIGGER,
            KeyEvent.KEYCODE_BUTTON_SELECT to 8,
            KeyEvent.KEYCODE_BUTTON_START to 9,
            KeyEvent.KEYCODE_BUTTON_THUMBL to 10,
            KeyEvent.KEYCODE_BUTTON_THUMBR to 11,
            KeyEvent.KEYCODE_DPAD_UP to DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN to DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_MODE to 16
        )

        val INSTALL_SCRIPT = """
            (function(){
              function install(w) {
                if (w.__argosyPad) return;
                var pad = {
                  id: 'Xbox 360 Controller (XInput STANDARD GAMEPAD)',
                  index: 0, connected: true, mapping: 'standard', timestamp: w.performance.now(),
                  axes: [0,0,0,0], buttons: [], vibrationActuator: null, hapticActuators: []
                };
                for (var i = 0; i < $BUTTON_COUNT; i++) pad.buttons.push({pressed: false, touched: false, value: 0});
                w.__argosyPad = pad;
                w.navigator.getGamepads = function(){ return [pad, null, null, null]; };
                var e = new w.Event('gamepadconnected'); e.gamepad = pad; w.dispatchEvent(e);
                console.log('argosyPad installed in ' + w.location.pathname);
              }
              function each(w, fn) {
                try { fn(w); } catch (err) { return; }
                for (var i = 0; i < w.frames.length; i++) each(w.frames[i], fn);
              }
              window.__argosyPadSet = function(b, a) {
                each(window, function(w) {
                  install(w);
                  var pad = w.__argosyPad;
                  for (var i = 0; i < b.length; i++) {
                    var v = b[i]; pad.buttons[i].value = v; pad.buttons[i].pressed = v > 0.1; pad.buttons[i].touched = v > 0;
                  }
                  for (var j = 0; j < a.length; j++) pad.axes[j] = a[j];
                  pad.timestamp = w.performance.now();
                  if (!w.__argosyPadAnnounced) {
                    w.__argosyPadAnnounced = true;
                    var e = new w.Event('gamepadconnected'); e.gamepad = pad; w.dispatchEvent(e);
                  }
                });
              };
              each(window, install);
            })();
        """.trimIndent()
    }
}
