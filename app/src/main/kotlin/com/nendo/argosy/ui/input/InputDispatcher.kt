package com.nendo.argosy.ui.input

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class InputDispatcher(
    private val hapticManager: HapticFeedbackManager? = null,
    private val soundManager: SoundFeedbackManager? = null
) {
    private val modalStack = mutableListOf<InputHandler>()
    private var drawerHandler: InputHandler? = null
    private var viewHandler: InputHandler? = null
    private var pendingEvent: GamepadEvent? = null
    private var inputBlockedUntil: Long = 0L
    private var currentRoute: String? = null
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
        modalStack.add(handler)
        processPendingEvent()
    }

    fun popModal() {
        modalStack.removeLastOrNull()
    }

    fun clearModals() {
        modalStack.clear()
    }

    fun resetToMainView() {
        modalStack.clear()
        drawerHandler = null
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
        pendingEvent?.let { event ->
            pendingEvent = null
            dispatch(event)
        }
    }

    fun blockInputFor(durationMs: Long) {
        inputBlockedUntil = System.currentTimeMillis() + durationMs
    }

    fun dispatch(event: GamepadEvent): InputResult {
        if (System.currentTimeMillis() < inputBlockedUntil) {
            return InputResult.HANDLED
        }

        val handler = modalStack.lastOrNull() ?: drawerHandler ?: viewHandler
        if (handler == null) {
            pendingEvent = event
            return InputResult.UNHANDLED
        }

        pendingEvent = null
        val result = dispatchToHandler(event, handler)
        playFeedback(event, result)
        return result
    }

    private fun playFeedback(event: GamepadEvent, result: InputResult) {
        if (!result.handled) return

        when (event) {
            GamepadEvent.Up, GamepadEvent.Down, GamepadEvent.Left, GamepadEvent.Right -> {
                if (result.soundOverride != SoundType.SILENT) {
                    hapticManager?.vibrate(HapticPattern.FOCUS_CHANGE)
                }
                soundManager?.play(result.soundOverride ?: SoundType.NAVIGATE)
            }
            GamepadEvent.PrevSection, GamepadEvent.NextSection,
            GamepadEvent.PrevTrigger, GamepadEvent.NextTrigger -> {
                hapticManager?.vibrate(HapticPattern.FOCUS_CHANGE)
                soundManager?.play(result.soundOverride ?: SoundType.SECTION_CHANGE)
            }
            GamepadEvent.Confirm -> {
                hapticManager?.vibrate(HapticPattern.SELECTION)
                soundManager?.play(result.soundOverride ?: SoundType.SELECT)
            }
            GamepadEvent.Back -> {
                soundManager?.play(result.soundOverride ?: SoundType.BACK)
            }
            else -> {}
        }
    }

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
        }
    }
}

val LocalInputDispatcher = staticCompositionLocalOf<InputDispatcher> {
    error("No InputDispatcher provided")
}

val LocalGamepadInputHandler = staticCompositionLocalOf<GamepadInputHandler?> { null }

val LocalABIconsSwapped = staticCompositionLocalOf { false }

val LocalXYIconsSwapped = staticCompositionLocalOf { false }

val LocalSwapStartSelect = staticCompositionLocalOf { false }
