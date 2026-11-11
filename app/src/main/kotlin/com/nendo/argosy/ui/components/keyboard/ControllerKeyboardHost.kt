package com.nendo.argosy.ui.components.keyboard

import android.view.ViewConfiguration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.input.ModalPresenceEffect
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ControllerKeyboardEntryPoint {
    fun keyboardLayouts(): KeyboardLayoutRepository
}

private object KeyboardLayoutMemory {
    var lastLayoutId: String? = null
}

/**
 * Draws and drives the open Argosy keyboard for this surface. Place it last in the surface's root
 * box so the sheet lies above every modal it serves.
 */
@Composable
fun ControllerKeyboardHost() {
    val active by ControllerKeyboard.active.collectAsState()
    val current = active ?: return
    DisposableEffect(Unit) {
        onDispose { ControllerKeyboard.reportDockedHeight(0) }
    }
    val request = current.request
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, ControllerKeyboardEntryPoint::class.java)
            .keyboardLayouts()
    }
    val scope = rememberCoroutineScope()
    var engine by remember(request) { mutableStateOf<KeyboardEngine?>(null) }
    var choices by remember(request) { mutableStateOf<List<KeyboardLayoutChoice>>(emptyList()) }

    LaunchedEffect(request) {
        val fallbackTag = configuration.locales[0]?.toLanguageTag() ?: "en"
        val enabled = keyboardLayoutChoices(
            enabledKeyboardLanguageTags(context),
            fallbackTag,
            systemKeyboardAvailable = request.onSystemKeyboard != null
        )
        choices = enabled
        val forced = request.fieldType.requiredLayoutId
        val argosyLayouts = enabled.filter { it.layoutId != SYSTEM_KEYBOARD_CHOICE_ID }
        val choice = forced?.let { id -> enabled.firstOrNull { it.layoutId == id } ?: KeyboardLayoutChoice(id, fallbackTag) }
            ?: argosyLayouts.firstOrNull { it.layoutId == KeyboardLayoutMemory.lastLayoutId }
            ?: argosyLayouts.first()
        val layout = repository.layout(choice.layoutId)
            ?: repository.layout(KeyboardLayout.QWERTY_LAYOUT_ID)
            ?: return@LaunchedEffect
        engine = KeyboardEngine(
            layout = layout,
            session = KeyboardSession(
                label = request.label,
                submitLabel = request.submitLabel,
                languageLabel = languageDisplayName(choice.languageTag),
                initialText = request.initialText,
                placeholder = request.placeholder,
                error = current.props.error,
                fieldType = request.fieldType,
                canSubmit = current.props.canSubmit,
                enabled = current.props.enabled,
                maxLength = request.maxLength
            ),
            doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
            onTextChange = request.onTextChange
        )
    }

    val keyboard = engine ?: return

    LaunchedEffect(keyboard, current.props) {
        keyboard.updateSession(current.props.error, current.props.canSubmit, current.props.enabled)
        current.props.text?.let(keyboard::syncText)
    }

    val cycleLayout: () -> KeyboardFeedback = {
        val layoutId = keyboard.state.value.layoutId
        if (request.fieldType.requiredLayoutId != null || choices.size < 2) {
            KeyboardFeedback.BOUNDARY
        } else {
            val index = choices.indexOfFirst { it.layoutId == layoutId }
            val next = choices[(index + 1).mod(choices.size)]
            if (next.layoutId == SYSTEM_KEYBOARD_CHOICE_ID) {
                request.onSystemKeyboard?.invoke()
                ControllerKeyboard.close(request.owner)
            } else scope.launch {
                repository.layout(next.layoutId)?.let { layout ->
                    KeyboardLayoutMemory.lastLayoutId = next.layoutId
                    keyboard.switchLayout(layout, languageDisplayName(next.languageTag))
                }
            }
            KeyboardFeedback.LAYOUT_REQUESTED
        }
    }
    val onFeedback: (KeyboardFeedback) -> Unit = { feedback ->
        when (feedback) {
            KeyboardFeedback.SUBMITTED -> request.onSubmit()
            KeyboardFeedback.CLOSED -> ControllerKeyboard.dismissActive()
            KeyboardFeedback.LAYOUT_REQUESTED -> cycleLayout()
            else -> Unit
        }
    }
    val handler = remember(keyboard) {
        ControllerKeyboardInputHandler(keyboard, onFeedback, cycleLayout)
    }
    ModalPresenceEffect()
    ModalInputEffect(active = true, handler = handler)

    val state by keyboard.state.collectAsState()
    ControllerKeyboardSheet(
        state = state,
        placement = KeyboardPlacement.DOCKED,
        onKeyTap = { position -> onFeedback(keyboard.tapKey(position)) },
        onKeyLongPress = { position -> onFeedback(keyboard.longTapKey(position)) },
        onAccentTap = { index -> onFeedback(keyboard.tapAccent(index)) },
        onDismissTap = { ControllerKeyboard.dismissActive() },
        onCaretTap = { index -> keyboard.setCaret(index) },
        modifier = Modifier.fillMaxSize()
    )
}
