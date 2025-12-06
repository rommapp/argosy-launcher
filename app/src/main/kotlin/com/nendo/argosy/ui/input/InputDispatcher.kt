package com.nendo.argosy.ui.input

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

private const val TAG = "InputDispatcher"

@Stable
class InputDispatcher(
    private val hapticManager: HapticFeedbackManager? = null
) {
    private var activeHandler: InputHandler? = null
    private var drawerHandler: InputHandler? = null
    private var isDrawerOpen: Boolean = false
    private var pendingEvent: GamepadEvent? = null

    fun setActiveScreen(handler: InputHandler?) {
        Log.d(TAG, "setActiveScreen: ${handler?.javaClass?.simpleName}")
        activeHandler = handler
        if (handler != null && !isDrawerOpen) {
            pendingEvent?.let { event ->
                Log.d(TAG, "Replaying pending event: $event")
                pendingEvent = null
                dispatch(event)
            }
        }
    }

    fun setDrawerHandler(handler: InputHandler?) {
        Log.d(TAG, "setDrawerHandler: ${handler?.javaClass?.simpleName}")
        drawerHandler = handler
        if (handler != null && isDrawerOpen) {
            pendingEvent?.let { event ->
                Log.d(TAG, "Replaying pending event to drawer: $event")
                pendingEvent = null
                dispatch(event)
            }
        }
    }

    fun setDrawerOpen(open: Boolean) {
        Log.d(TAG, "setDrawerOpen: $open (was: $isDrawerOpen)")
        isDrawerOpen = open
    }

    fun dispatch(event: GamepadEvent): Boolean {
        Log.d(TAG, "dispatch: event=$event, isDrawerOpen=$isDrawerOpen, " +
            "drawerHandler=${drawerHandler?.javaClass?.simpleName}, " +
            "activeHandler=${activeHandler?.javaClass?.simpleName}")
        val handler = if (isDrawerOpen) drawerHandler else activeHandler

        if (handler == null) {
            Log.d(TAG, "No handler available, buffering event: $event")
            pendingEvent = event
            return false
        }

        pendingEvent = null
        val handled = dispatchToHandler(event, handler)

        if (handled) {
            when (event) {
                GamepadEvent.Up, GamepadEvent.Down, GamepadEvent.Left, GamepadEvent.Right,
                GamepadEvent.PrevSection, GamepadEvent.NextSection -> {
                    hapticManager?.vibrate(HapticPattern.FOCUS_CHANGE)
                }
                GamepadEvent.Confirm -> {
                    hapticManager?.vibrate(HapticPattern.SELECTION)
                }
                else -> {}
            }
        }

        Log.d(TAG, "dispatch result: handled=$handled")
        return handled
    }

    private fun dispatchToHandler(event: GamepadEvent, handler: InputHandler): Boolean {
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
            GamepadEvent.Select -> handler.onSelect()
        }
    }
}

val LocalInputDispatcher = staticCompositionLocalOf<InputDispatcher> {
    error("No InputDispatcher provided")
}

val LocalNintendoLayout = staticCompositionLocalOf { false }

val LocalSwapStartSelect = staticCompositionLocalOf { false }
