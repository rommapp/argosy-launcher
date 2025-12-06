package com.nendo.argosy.ui.input

import android.view.KeyEvent
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GamepadEvent {
    data object Up : GamepadEvent
    data object Down : GamepadEvent
    data object Left : GamepadEvent
    data object Right : GamepadEvent
    data object Confirm : GamepadEvent
    data object Back : GamepadEvent
    data object SecondaryAction : GamepadEvent
    data object ContextMenu : GamepadEvent
    data object PrevSection : GamepadEvent
    data object NextSection : GamepadEvent
    data object Menu : GamepadEvent
    data object Select : GamepadEvent
}

@Singleton
class GamepadInputHandler @Inject constructor(
    preferencesRepository: UserPreferencesRepository
) {

    private val _events = Channel<GamepadEvent>(capacity = Channel.BUFFERED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var nintendoLayout = false
    private var swapStartSelect = false

    init {
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                nintendoLayout = prefs.nintendoButtonLayout
                swapStartSelect = prefs.swapStartSelect
            }
        }
    }

    fun eventFlow(): Flow<GamepadEvent> = _events.receiveAsFlow()

    private var lastKeyTime = 0L
    private var lastKeyCode = 0
    private val repeatInterval = 100L

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()

        if (event.keyCode == lastKeyCode && event.repeatCount > 0) {
            val elapsed = currentTime - lastKeyTime
            if (elapsed < repeatInterval) return true
        }

        lastKeyTime = currentTime
        lastKeyCode = event.keyCode

        val gamepadEvent = mapKeyToEvent(event.keyCode) ?: return false
        _events.trySend(gamepadEvent)
        return true
    }

    private fun mapKeyToEvent(keyCode: Int): GamepadEvent? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> GamepadEvent.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> GamepadEvent.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> GamepadEvent.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadEvent.Right

        KeyEvent.KEYCODE_BUTTON_A -> if (nintendoLayout) GamepadEvent.Back else GamepadEvent.Confirm
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER -> GamepadEvent.Confirm

        KeyEvent.KEYCODE_BUTTON_B -> if (nintendoLayout) GamepadEvent.Confirm else GamepadEvent.Back
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_BACK -> GamepadEvent.Back

        KeyEvent.KEYCODE_BUTTON_X -> if (nintendoLayout) GamepadEvent.ContextMenu else GamepadEvent.SecondaryAction
        KeyEvent.KEYCODE_BUTTON_Y -> if (nintendoLayout) GamepadEvent.SecondaryAction else GamepadEvent.ContextMenu

        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadEvent.PrevSection
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadEvent.NextSection

        KeyEvent.KEYCODE_BUTTON_START -> if (swapStartSelect) GamepadEvent.Select else GamepadEvent.Menu
        KeyEvent.KEYCODE_BUTTON_SELECT -> if (swapStartSelect) GamepadEvent.Menu else GamepadEvent.Select

        else -> null
    }
}
