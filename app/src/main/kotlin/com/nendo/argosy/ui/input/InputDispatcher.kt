package com.nendo.argosy.ui.input

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode

@Stable
class InputDispatcher(
    private val hapticManager: HapticFeedbackManager? = null,
    private val soundManager: SoundFeedbackManager? = null
) {
    private val modalStack = mutableListOf<InputHandler>()
    private var criticalHandler: InputHandler? = null
    private var drawerHandler: InputHandler? = null
    private var viewHandler: InputHandler? = null
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
    private var pendingViewSubscription: Pair<InputHandler, String>? = null

    fun setCurrentRoute(route: String?) {
        currentRoute = route
        processPendingViewSubscription()
    }

    private fun processPendingViewSubscription() {
        val pending = pendingViewSubscription ?: return
        if (isRouteMatch(pending.second, currentRoute)) {
            pendingViewSubscription = null
            clearModals()
            viewHandler = pending.first
            processPendingEvent()
        }
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
        pendingViewSubscription = null
    }

    /** Top-priority slot for app-level modals (save-conflict resolution) that must capture input
     * over any screen or drawer. Unlike modalStack, it is never cleared by screen subscriptions. */
    fun setCriticalHandler(handler: InputHandler?) {
        criticalHandler = handler
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

    fun subscribeView(handler: InputHandler) {
        clearModals()
        viewHandler = handler
        processPendingEvent()
    }

    fun subscribeView(handler: InputHandler, forRoute: String): Boolean {
        if (!isRouteMatch(forRoute, currentRoute)) {
            pendingViewSubscription = handler to forRoute
            return false
        }
        pendingViewSubscription = null
        clearModals()
        viewHandler = handler
        processPendingEvent()
        return true
    }

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

        val handler = criticalHandler ?: modalStack.lastOrNull() ?: drawerHandler ?: viewHandler
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
        return result
    }

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
