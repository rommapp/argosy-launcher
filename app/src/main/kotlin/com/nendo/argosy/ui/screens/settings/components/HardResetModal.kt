package com.nendo.argosy.ui.screens.settings.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.GamepadHoldTracker
import com.nendo.argosy.ui.components.HoldToConfirmButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.util.formatBytes

private const val FOCUS_CANCEL = 0
private const val FOCUS_ACTION = 1

/**
 * Danger confirm modal for the full device reset. Owns input exclusively while visible:
 * a raw key listener drives the 5s hold and swallows every other key, and a fully-guarded
 * modal handler covers the stick-driven dispatcher path.
 */
@Composable
fun HardResetModal(
    downloadedGamesCount: Int,
    downloadedGamesBytes: Long,
    pendingUploads: Int,
    isResetting: Boolean,
    canSyncNow: Boolean,
    onSyncNow: () -> Unit,
    onHoldStart: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val semanticColors = LocalLauncherTheme.current.semanticColors
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val tracker = remember { GamepadHoldTracker() }
    var focusIndex by remember { mutableIntStateOf(FOCUS_CANCEL) }

    val blocked = pendingUploads > 0
    val currentBlocked by rememberUpdatedState(blocked)
    val currentResetting by rememberUpdatedState(isResetting)
    val currentCanSyncNow by rememberUpdatedState(canSyncNow)
    val currentOnSyncNow by rememberUpdatedState(onSyncNow)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    fun activateSyncNow() {
        if (currentCanSyncNow) {
            currentOnDismiss()
            currentOnSyncNow()
        }
    }

    DisposableEffect(gamepadInputHandler) {
        val listener: (KeyEvent) -> Boolean = { event ->
            val mapped = gamepadInputHandler?.mapKeyToEvent(event.keyCode)
            when {
                currentResetting -> {}
                mapped == GamepadEvent.Confirm -> when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (focusIndex == FOCUS_ACTION && !currentBlocked) {
                            tracker.onConfirmKeyDown(isRepeat = event.repeatCount > 0)
                        } else if (event.repeatCount == 0) {
                            if (focusIndex == FOCUS_CANCEL) currentOnDismiss() else activateSyncNow()
                        }
                    }
                    KeyEvent.ACTION_UP -> tracker.onConfirmKeyUp()
                    else -> {}
                }
                event.action != KeyEvent.ACTION_DOWN -> {}
                mapped == GamepadEvent.Back -> {
                    if (tracker.isHeld) tracker.forceRelease() else currentOnDismiss()
                }
                mapped == GamepadEvent.Up || mapped == GamepadEvent.Left -> {
                    if (!tracker.isHeld) focusIndex = FOCUS_CANCEL
                }
                mapped == GamepadEvent.Down || mapped == GamepadEvent.Right -> {
                    if (!tracker.isHeld) focusIndex = FOCUS_ACTION
                }
                else -> {}
            }
            true
        }
        gamepadInputHandler?.setRawKeyEventListener(listener)
        onDispose {
            gamepadInputHandler?.setRawKeyEventListener(null)
        }
    }

    val modalHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (!currentResetting && !tracker.isHeld) focusIndex = FOCUS_CANCEL
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (!currentResetting && !tracker.isHeld) focusIndex = FOCUS_ACTION
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult = onUp()
            override fun onRight(): InputResult = onDown()

            override fun onConfirm(): InputResult {
                if (currentResetting || tracker.isHeld) return InputResult.HANDLED
                when {
                    focusIndex == FOCUS_CANCEL -> currentOnDismiss()
                    currentBlocked -> activateSyncNow()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (currentResetting) return InputResult.HANDLED
                if (tracker.isHeld) {
                    tracker.forceRelease()
                    return InputResult.HANDLED
                }
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

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

    Modal(
        title = stringResource(R.string.settings_hard_reset_title),
        subtitle = stringResource(R.string.settings_hard_reset_subtitle),
        subtitleColor = theme.destructive,
        baseWidth = 440.dp,
        onDismiss = if (isResetting) null else onDismiss
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.settings_hard_reset_games_summary,
                    downloadedGamesCount,
                    downloadedGamesCount,
                    formatBytes(downloadedGamesBytes)
                ),
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            ScopeList(
                header = stringResource(R.string.settings_hard_reset_deletes_header),
                headerColor = theme.destructive,
                items = listOf(
                    stringResource(R.string.settings_hard_reset_deletes_files),
                    stringResource(R.string.settings_hard_reset_deletes_database),
                    stringResource(R.string.settings_hard_reset_deletes_caches)
                )
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            ScopeList(
                header = stringResource(R.string.settings_hard_reset_keeps_header),
                headerColor = semanticColors.success,
                items = listOf(
                    stringResource(R.string.settings_hard_reset_keeps_sign_ins),
                    stringResource(R.string.settings_hard_reset_keeps_synced_saves),
                    stringResource(R.string.settings_hard_reset_keeps_emulator_apps),
                    stringResource(R.string.settings_hard_reset_keeps_music)
                )
            )
            if (blocked) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_hard_reset_pending_uploads,
                        pendingUploads,
                        pendingUploads
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.warning
                )
                Text(
                    text = if (canSyncNow) {
                        stringResource(R.string.settings_hard_reset_sync_first_connected)
                    } else {
                        stringResource(R.string.settings_hard_reset_sync_first_disconnected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        if (isResetting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spacingSm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconSm),
                    strokeWidth = Dimens.borderMedium,
                    color = theme.destructive
                )
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Text(
                    text = stringResource(R.string.settings_hard_reset_in_progress),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
            ) {
                ModalActionButton(
                    label = stringResource(R.string.settings_hard_reset_cancel_button),
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = focusIndex == FOCUS_CANCEL,
                    onClick = onDismiss
                )
                if (blocked) {
                    ModalActionButton(
                        label = stringResource(R.string.settings_hard_reset_sync_now_button),
                        tint = theme.focusAccent,
                        restLabelColor = theme.textPrimary,
                        focused = focusIndex == FOCUS_ACTION,
                        enabled = canSyncNow,
                        onClick = { activateSyncNow() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            HoldToConfirmButton(
                label = stringResource(R.string.settings_hard_reset_hold_button),
                isFocused = !blocked && focusIndex == FOCUS_ACTION,
                enabled = !blocked,
                gamepadTracker = tracker,
                onHoldStart = onHoldStart,
                onConfirmed = { currentOnConfirm() }
            )
        }
    }
}

@Composable
private fun ScopeList(header: String, headerColor: Color, items: List<String>) {
    val theme = LocalArgosyTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Text(
            text = header.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = headerColor
        )
        items.forEach { item ->
            Row {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute
                )
                Spacer(modifier = Modifier.width(Dimens.spacingXs))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            }
        }
    }
}
