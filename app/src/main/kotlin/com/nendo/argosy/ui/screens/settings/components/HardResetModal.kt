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
import androidx.compose.ui.unit.dp
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
        title = "Hard Reset",
        subtitle = "This cannot be undone",
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
                text = "$downloadedGamesCount downloaded games - ${formatBytes(downloadedGamesBytes)} on disk",
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            ScopeList(
                header = "Deletes",
                headerColor = theme.destructive,
                items = listOf(
                    "All downloaded game files on disk",
                    "The entire library database - play time, collections, download history, per-game settings",
                    "Every cache - images, saves and states, extracted ROMs, sound effects, emulator installers, Steam staging, presence covers"
                )
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            ScopeList(
                header = "Keeps",
                headerColor = semanticColors.success,
                items = listOf(
                    "Settings and sign-ins - RomM and Steam stay connected",
                    "Saves already synced to the server",
                    "Emulator apps and their own save data",
                    "Your music folder"
                )
            )
            if (blocked) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = "$pendingUploads saves waiting to upload",
                    style = MaterialTheme.typography.titleSmall,
                    color = semanticColors.warning
                )
                Text(
                    text = if (canSyncNow) {
                        "Sync them first - resetting now would discard them forever."
                    } else {
                        "Reconnect to the server and sync them first - resetting now would discard them forever."
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
                    text = "Resetting...",
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
                    label = "Cancel",
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = focusIndex == FOCUS_CANCEL,
                    onClick = onDismiss
                )
                if (blocked) {
                    ModalActionButton(
                        label = "Sync Now",
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
                label = "Hold to Reset",
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
