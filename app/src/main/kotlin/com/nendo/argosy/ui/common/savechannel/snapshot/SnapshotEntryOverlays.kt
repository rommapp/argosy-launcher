package com.nendo.argosy.ui.common.savechannel.snapshot

import android.view.KeyEvent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.GamepadHoldTracker
import com.nendo.argosy.ui.components.HoldToConfirmButton
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
internal fun SnapshotEntryOverlays(state: SnapshotViewState, actions: SnapshotViewActions) {
    state.labelEntry?.let { entry -> SnapshotLabelOverlay(entry, state.isBusy, actions) }
    when (val confirm = state.confirm) {
        is SnapshotConfirmUi.Share -> ArgosyConfirmModalHost(
            visible = true,
            title = stringResource(R.string.save_channels_share_confirm_title),
            message = stringResource(R.string.save_channels_share_confirm_body),
            confirmLabel = stringResource(R.string.save_channels_share_confirm_action),
            onConfirm = actions::confirmShare,
            onDismiss = actions::dismissConfirm
        )
        SnapshotConfirmUi.HardcoreDowngrade -> ArgosyConfirmModalHost(
            visible = true,
            title = stringResource(R.string.save_channels_hardcore_confirm_title),
            message = stringResource(R.string.save_channels_hardcore_confirm_body),
            confirmLabel = stringResource(R.string.save_channels_hardcore_confirm_action),
            onConfirm = actions::confirmHardcore,
            onDismiss = actions::dismissConfirm,
            destructive = true
        )
        is SnapshotConfirmUi.Delete -> SnapshotDeleteOverlay(confirm, actions)
        null -> Unit
    }
}

@Composable
private fun SnapshotLabelOverlay(entry: SnapshotLabelEntryUi, isBusy: Boolean, actions: SnapshotViewActions) {
    val title = when (entry.mode) {
        SnapshotLabelMode.NEW_CHANNEL -> stringResource(R.string.save_channels_label_title_new)
        SnapshotLabelMode.RENAME -> stringResource(R.string.save_channels_label_title_rename)
        SnapshotLabelMode.FORK -> stringResource(R.string.save_channels_label_title_fork)
    }
    val confirmLabel = when (entry.mode) {
        SnapshotLabelMode.NEW_CHANNEL -> stringResource(R.string.save_channels_label_confirm_new)
        SnapshotLabelMode.RENAME -> stringResource(R.string.save_channels_label_confirm_rename)
        SnapshotLabelMode.FORK -> stringResource(R.string.save_channels_label_confirm_fork)
    }
    TextEntryModal(
        title = title,
        label = title,
        confirmLabel = confirmLabel,
        cancelLabel = stringResource(R.string.save_channels_label_cancel),
        text = entry.text,
        onTextChange = actions::updateLabelText,
        onDismiss = actions::dismissLabel,
        onSubmit = actions::confirmLabel,
        focus = entry.focus,
        placeholder = stringResource(R.string.save_channels_label_field_placeholder),
        errorMessage = entry.error?.let { stringResource(it) },
        canSubmit = entry.text.isNotBlank() && !isBusy
    )
}

@Composable
private fun SnapshotDeleteOverlay(confirm: SnapshotConfirmUi.Delete, actions: SnapshotViewActions) {
    val theme = LocalArgosyTheme.current
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val tracker = remember { GamepadHoldTracker() }
    val currentOnConfirm by rememberUpdatedState(actions::confirmDelete)
    val currentOnDismiss by rememberUpdatedState(actions::dismissConfirm)

    DisposableEffect(gamepadInputHandler) {
        val listener: (KeyEvent) -> Boolean = { event ->
            val mapped = gamepadInputHandler?.mapKeyToEvent(event.keyCode)
            when {
                mapped == GamepadEvent.Confirm -> when (event.action) {
                    KeyEvent.ACTION_DOWN -> tracker.onConfirmKeyDown(isRepeat = event.repeatCount > 0)
                    KeyEvent.ACTION_UP -> tracker.onConfirmKeyUp()
                    else -> Unit
                }
                event.action != KeyEvent.ACTION_DOWN -> Unit
                mapped == GamepadEvent.Back -> if (tracker.isHeld) tracker.forceRelease() else currentOnDismiss()
                else -> Unit
            }
            true
        }
        gamepadInputHandler?.setRawKeyEventListener(listener)
        onDispose { gamepadInputHandler?.setRawKeyEventListener(null) }
    }

    val modalHandler = remember {
        object : InputHandler {
            override fun onBack(): InputResult {
                if (tracker.isHeld) {
                    tracker.forceRelease()
                    return InputResult.HANDLED
                }
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onUp(): InputResult = InputResult.HANDLED
            override fun onDown(): InputResult = InputResult.HANDLED
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.handled(SoundType.SILENT)
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
        }
    }
    ModalInputEffect(active = true, handler = modalHandler)

    NestedModal(
        title = stringResource(R.string.save_channels_delete_confirm_title, confirm.label),
        onDismiss = actions::dismissConfirm
    ) {
        Text(
            text = stringResource(R.string.save_channels_delete_confirm_body, confirm.pinnedCount, confirm.olderSaveCount),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        HoldToConfirmButton(
            label = stringResource(R.string.save_channels_delete_confirm_hold),
            isFocused = true,
            gamepadTracker = tracker,
            onConfirmed = { currentOnConfirm() }
        )
    }
}
