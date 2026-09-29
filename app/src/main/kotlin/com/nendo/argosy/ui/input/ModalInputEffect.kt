package com.nendo.argosy.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Captures gamepad input for a modal while [active] by pushing [handler] on the modal stack. */
@Composable
fun ModalInputEffect(
    active: Boolean,
    handler: InputHandler,
    inputDispatcher: InputDispatcher = LocalInputDispatcher.current
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, handler, active, inputDispatcher) {
        if (!active) return@DisposableEffect onDispose { }
        inputDispatcher.pushModal(handler)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(handler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.removeModal(handler)
        }
    }
}
