package com.nendo.argosy.ui.components.keyboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ControllerKeyboardRequest(
    val owner: Any,
    val label: String,
    val placeholder: String?,
    val initialText: String,
    val fieldType: KeyboardFieldType,
    val submitLabel: String,
    val maxLength: Int?,
    val onTextChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onClose: () -> Unit,
    val onSystemKeyboard: (() -> Unit)? = null
)

data class ControllerKeyboardLiveProps(
    val text: String? = null,
    val error: String? = null,
    val canSubmit: Boolean = true,
    val enabled: Boolean = true
)

class ActiveControllerKeyboard(
    val request: ControllerKeyboardRequest,
    val props: ControllerKeyboardLiveProps,
    val generation: Long
)

/**
 * The one open Argosy keyboard in this process. Fields publish a [ControllerKeyboardRequest] while
 * their keyboard is open; the host at each rendered surface draws and drives whichever request is
 * current. A newer request replaces an older one, whose [ControllerKeyboardRequest.onClose] runs.
 */
object ControllerKeyboard {
    private val _active = MutableStateFlow<ActiveControllerKeyboard?>(null)
    val active: StateFlow<ActiveControllerKeyboard?> = _active.asStateFlow()
    private val _dockedHeightPx = MutableStateFlow(0)
    val dockedHeightPx: StateFlow<Int> = _dockedHeightPx.asStateFlow()
    private var generation = 0L

    fun reportDockedHeight(px: Int) {
        _dockedHeightPx.value = px
    }

    fun open(request: ControllerKeyboardRequest, props: ControllerKeyboardLiveProps) {
        val replaced = _active.value
        _active.value = ActiveControllerKeyboard(request, props, ++generation)
        if (replaced != null && replaced.request.owner != request.owner) replaced.request.onClose()
    }

    fun update(owner: Any, props: ControllerKeyboardLiveProps) {
        _active.update { current ->
            if (current == null || current.request.owner != owner || current.props == props) current
            else ActiveControllerKeyboard(current.request, props, current.generation)
        }
    }

    fun close(owner: Any) {
        val current = _active.value ?: return
        if (current.request.owner != owner) return
        _active.value = null
    }

    fun dismissActive() {
        val current = _active.value ?: return
        _active.value = null
        current.request.onClose()
    }
}
