package com.nendo.argosy.ui.components.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost

/**
 * Playlist name entry with the controller keyboard. Captures gamepad input while composed;
 * [onSubmit] receives the trimmed name and never a blank one.
 */
@Composable
fun PlaylistNameModal(
    title: String,
    confirmLabel: String,
    initialName: String,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var focus by remember { mutableStateOf(TextEntryFocus()) }
    val submit = {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !busy) onSubmit(trimmed)
    }
    val currentSubmit by rememberUpdatedState(submit)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val isBusy by rememberUpdatedState(busy)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focus = focus.copy(row = TextEntryRow.FIELD)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                focus = focus.copy(row = TextEntryRow.BUTTONS)
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (focus.row == TextEntryRow.BUTTONS) focus = focus.copy(buttonIndex = TEXT_ENTRY_CANCEL_BUTTON)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (focus.row == TextEntryRow.BUTTONS) focus = focus.copy(buttonIndex = TEXT_ENTRY_CONFIRM_BUTTON)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                if (isBusy) return InputResult.handled(SoundType.SILENT)
                when {
                    focus.row == TextEntryRow.FIELD -> focus = focus.requestKeyboard()
                    focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> currentOnDismiss()
                    else -> currentSubmit()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (isBusy) return InputResult.handled(SoundType.SILENT)
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = inputHandler)

    TextEntryModal(
        title = title,
        label = stringResource(R.string.music_library_playlists_name_label),
        confirmLabel = confirmLabel,
        cancelLabel = stringResource(R.string.music_library_playlists_name_cancel),
        text = name,
        onTextChange = { name = it },
        onDismiss = { if (!busy) onDismiss() },
        onSubmit = submit,
        focus = focus,
        enabled = !busy
    )
}

@Composable
fun MusicPlaylistPermissionModal(
    visible: Boolean,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ArgosyConfirmModalHost(
        visible = visible,
        title = stringResource(R.string.music_library_permission_title),
        message = stringResource(R.string.music_library_permission_message),
        confirmLabel = stringResource(R.string.music_library_permission_open_settings),
        cancelLabel = stringResource(R.string.music_library_permission_not_now),
        onConfirm = onOpenSettings,
        onDismiss = onDismiss
    )
}
