package com.nendo.argosy.ui.input

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode

@Stable
class InputDispatcher(
    private val hapticManager: HapticFeedbackManager? = null,
    private val soundManager: SoundFeedbackManager? = null
) {
    private val modalStack = mutableStateListOf<InputHandler>()
    private val shownModals = mutableStateMapOf<Any, Unit>()
    private var interceptHandler: InputHandler? = null
    private var criticalInputHandler by mutableStateOf<InputHandler?>(null)
    private var drawerHandler: InputHandler? = null
    private var viewHandler: InputHandler? = null
    private var viewRoute: String? = null
    private var pendingInput: GamepadInput? = null
    private var inputBlockedUntil: Long = 0L
    private var currentRoute: String? = null

    companion object {
        var currentIsRepeat: Boolean = false
            internal set

        fun computeWrappedIndex(
            current: Int, delta: Int, maxIndex: Int, wrapMode: MenuWrapMode
        ): Int {
            val raw = current + delta
            return when {
                raw in 0..maxIndex -> raw
                wrapMode == MenuWrapMode.OFF -> raw.coerceIn(0, maxIndex)
                wrapMode == MenuWrapMode.HARD_STOP && currentIsRepeat -> raw.coerceIn(0, maxIndex)
                else -> if (raw < 0) maxIndex else 0
            }
        }
    }
    private val pendingViewSubscriptions = LinkedHashMap<String, InputHandler>()

    fun setCurrentRoute(route: String?) {
        currentRoute = route
        processPendingViewSubscription()
    }

    private fun processPendingViewSubscription() {
        val route = pendingViewSubscriptions.keys
            .filter { isRouteMatch(it, currentRoute) }
            .maxByOrNull { it.length }
            ?: return
        val handler = pendingViewSubscriptions.remove(route) ?: return
        clearModals()
        viewHandler = handler
        viewRoute = route
        processPendingEvent()
    }

    fun pushModal(handler: InputHandler) {
        modalStack.remove(handler)
        modalStack.add(handler)
        processPendingEvent()
    }

    fun popModal() {
        modalStack.removeLastOrNull()
    }

    fun hasActiveModal(): Boolean = modalStack.isNotEmpty()

    fun hasCapturingOverlay(): Boolean =
        criticalInputHandler != null || modalStack.isNotEmpty() || shownModals.isNotEmpty()

    fun markModalShown(token: Any) {
        shownModals[token] = Unit
    }

    fun markModalHidden(token: Any) {
        shownModals.remove(token)
    }

    fun removeModal(handler: InputHandler) {
        modalStack.remove(handler)
    }

    fun clearModals() {
        modalStack.clear()
    }

    fun resetToMainView() {
        modalStack.clear()
        drawerHandler = null
    }

    /**
     * Drops any handler waiting on a route match. Call after a dual-screen topology
     * change so a handler subscribed for a route that will never become active doesn't
     * remain parked indefinitely.
     */
    fun clearPendingViewSubscription() {
        pendingViewSubscriptions.clear()
    }

    /**
     * The one tier that sees an event before anyone else and may decline it. A handler here claims
     * the buttons it overrides and returns UNHANDLED for the rest, which then reach the normal
     * chain, so a transient prompt can own a single button without freezing the screen behind it.
     * Every other tier is all-or-nothing.
     */
    fun setInterceptHandler(handler: InputHandler?) {
        interceptHandler = handler
        processPendingEvent()
    }

    /** Top-priority slot for app-level modals (save-conflict resolution) that must capture input
     * over any screen or drawer. Unlike modalStack, it is never cleared by screen subscriptions. */
    fun setCriticalHandler(handler: InputHandler?) {
        criticalInputHandler = handler
        processPendingEvent()
    }

    fun subscribeDrawer(handler: InputHandler) {
        clearModals()
        drawerHandler = handler
        processPendingEvent()
    }

    fun unsubscribeDrawer() {
        drawerHandler = null
    }

    fun releaseDrawer(handler: InputHandler): Boolean {
        if (drawerHandler !== handler) return false
        drawerHandler = null
        return true
    }
    fun subscribeView(handler: InputHandler) {
        clearModals()
        viewHandler = handler
        viewRoute = null
        processPendingEvent()
    }

    fun subscribeView(handler: InputHandler, forRoute: String): Boolean {
        if (!isRouteMatch(forRoute, currentRoute)) {
            pendingViewSubscriptions[forRoute] = handler
            return false
        }
        pendingViewSubscriptions.remove(forRoute)
        clearModals()
        viewHandler = handler
        viewRoute = forRoute
        processPendingEvent()
        return true
    }

    private fun viewOwnsCurrentRoute(): Boolean =
        viewRoute?.let { isRouteMatch(it, currentRoute) } ?: true

    private fun isRouteMatch(subscriberRoute: String, activeRoute: String?): Boolean {
        if (activeRoute == null) return false
        return activeRoute.startsWith(subscriberRoute) ||
            activeRoute.substringBefore("?").substringBefore("/") == subscriberRoute
    }

    private fun processPendingEvent() {
        pendingInput?.let { input ->
            pendingInput = null
            dispatch(input)
        }
    }

    fun blockInputFor(durationMs: Long) {
        inputBlockedUntil = System.currentTimeMillis() + durationMs
    }

    fun dispatch(input: GamepadInput): InputResult {
        if (System.currentTimeMillis() < inputBlockedUntil) {
            return InputResult.HANDLED
        }

        interceptHandler?.let { intercept ->
            Companion.currentIsRepeat = input.isRepeat
            val intercepted = try {
                dispatchToHandler(input.event, intercept)
            } finally {
                Companion.currentIsRepeat = false
            }
            if (intercepted.handled) {
                pendingInput = null
                playFeedback(input.event, intercepted)
                return intercepted
            }
        }

        val overlay = criticalInputHandler ?: modalStack.lastOrNull() ?: drawerHandler
        if (overlay == null && viewHandler != null && !viewOwnsCurrentRoute()) {
            pendingInput = null
            return InputResult.HANDLED
        }
        val handler = overlay ?: viewHandler
        if (handler == null) {
            pendingInput = input
            return InputResult.UNHANDLED
        }

        pendingInput = null
        Companion.currentIsRepeat = input.isRepeat
        val result = try {
            dispatchToHandler(input.event, handler)
        } finally {
            Companion.currentIsRepeat = false
        }
        playFeedback(input.event, result)
        return claimSelectOverOverlay(input.event, handler, result)
    }

    private fun claimSelectOverOverlay(
        event: GamepadEvent,
        handler: InputHandler,
        result: InputResult
    ): InputResult =
        if (event.isSelectGesture() && handler !== viewHandler && !result.handled) {
            InputResult.HANDLED
        } else {
            result
        }

    private fun GamepadEvent.isSelectGesture(): Boolean =
        this == GamepadEvent.Select || this == GamepadEvent.LongSelect

    private val feedbackPlayer = InputFeedbackPlayer(hapticManager, soundManager)

    private fun playFeedback(event: GamepadEvent, result: InputResult) =
        feedbackPlayer.play(event, result)

    private fun dispatchToHandler(event: GamepadEvent, handler: InputHandler): InputResult {
        return when (event) {
            GamepadEvent.Up -> handler.onUp()
            GamepadEvent.Down -> handler.onDown()
            GamepadEvent.Left -> handler.onLeft()
            GamepadEvent.Right -> handler.onRight()
            GamepadEvent.Confirm -> handler.onConfirm()
            GamepadEvent.Back -> handler.onBack()
            GamepadEvent.Menu -> handler.onMenu()
            GamepadEvent.SecondaryAction -> handler.onSecondaryAction()
            GamepadEvent.ContextMenu -> handler.onContextMenu()
            GamepadEvent.PrevSection -> handler.onPrevSection()
            GamepadEvent.NextSection -> handler.onNextSection()
            GamepadEvent.PrevTrigger -> handler.onPrevTrigger()
            GamepadEvent.NextTrigger -> handler.onNextTrigger()
            GamepadEvent.Select -> handler.onSelect()
            GamepadEvent.LeftStickClick -> handler.onLeftStickClick()
            GamepadEvent.RightStickClick -> handler.onRightStickClick()
            GamepadEvent.Home -> InputResult.UNHANDLED
            GamepadEvent.LongConfirm -> handler.onLongConfirm()
            GamepadEvent.LongSelect -> handler.onLongSelect()
        }
    }
}

val LocalInputDispatcher = staticCompositionLocalOf<InputDispatcher> {
    error("No InputDispatcher provided")
}

val LocalGamepadInputHandler = staticCompositionLocalOf<RawInputInterceptor?> { null }

val LocalABIconsSwapped = staticCompositionLocalOf { false }

val LocalXYIconsSwapped = staticCompositionLocalOf { false }

val LocalSwapStartSelect = staticCompositionLocalOf { false }
