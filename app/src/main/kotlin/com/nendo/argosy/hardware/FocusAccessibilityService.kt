package com.nendo.argosy.hardware

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    fun tapOnDisplay(displayId: Int) {
        val path = Path().apply { moveTo(1f, 1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .setDisplayId(displayId)
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Focus tap completed on display $displayId")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Focus tap cancelled on display $displayId")
            }
        }, null)
        Log.d(TAG, "Focus tap dispatched=$dispatched on display $displayId")
    }

    companion object {
        private const val TAG = "FocusA11y"
        var instance: FocusAccessibilityService? = null
            private set
    }
}
