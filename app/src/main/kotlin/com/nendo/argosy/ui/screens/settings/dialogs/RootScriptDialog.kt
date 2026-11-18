package com.nendo.argosy.ui.screens.settings.dialogs

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.model.RootScript
import com.nendo.argosy.data.model.RootScriptOutcome
import com.nendo.argosy.data.model.RootScriptResult
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.settings.RootScriptDialogState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import kotlinx.coroutines.launch

private const val REBOOT_FOCUS_INDEX = 1

@Composable
fun RootScriptDialog(
    state: RootScriptDialogState,
    onMoveFocus: (Int) -> Unit,
    onReboot: () -> Unit,
    onConfirmBackup: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { (Dimens.menuRowHeight * 3).toPx() }
    val currentState by rememberUpdatedState(state)
    val currentOnMoveFocus by rememberUpdatedState(onMoveFocus)
    val currentOnReboot by rememberUpdatedState(onReboot)
    val currentOnConfirmBackup by rememberUpdatedState(onConfirmBackup)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            private fun scroll(direction: Int) {
                scope.launch { scrollState.animateScrollBy(direction * scrollStepPx) }
            }

            override fun onUp(): InputResult {
                scroll(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                scroll(1)
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                currentOnMoveFocus(-1)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                currentOnMoveFocus(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                (currentState as? RootScriptDialogState.ConfirmBackup)?.let { confirm ->
                    if (confirm.focusIndex == RootScriptDialogState.ConfirmBackup.CONTINUE_FOCUS) {
                        currentOnConfirmBackup()
                    } else {
                        currentOnDismiss()
                    }
                    return InputResult.HANDLED
                }
                val finished = currentState as? RootScriptDialogState.Finished ?: return InputResult.HANDLED
                if (finished.offersReboot && finished.focusIndex == REBOOT_FOCUS_INDEX) {
                    currentOnReboot()
                } else {
                    currentOnDismiss()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (currentState is RootScriptDialogState.Running) return InputResult.HANDLED
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

    ModalScaffold(
        visible = true,
        onDismiss = { if (state !is RootScriptDialogState.Running) onDismiss() },
        maxWidth = Dimens.modalWidthLg
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                when (state) {
                    is RootScriptDialogState.ConfirmBackup -> ConfirmBackupContent(state.backupPath)
                    is RootScriptDialogState.Running -> RunningContent()
                    is RootScriptDialogState.Finished -> when (val result = state.result) {
                        is RootScriptResult.Ran -> RanContent(result)
                        is RootScriptResult.Written -> WrittenContent(result)
                        is RootScriptResult.Error -> ErrorContent(result)
                    }
                }
            }

            if (state is RootScriptDialogState.ConfirmBackup) {
                HorizontalDivider(color = theme.hairlineLow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
                ) {
                    ActionButton(
                        label = stringResource(R.string.settings_root_script_backup_cancel_button),
                        onClick = onDismiss,
                        focused = state.focusIndex == RootScriptDialogState.ConfirmBackup.CANCEL_FOCUS
                    )
                    ActionButton(
                        label = stringResource(R.string.settings_root_script_backup_continue_button),
                        onClick = onConfirmBackup,
                        primary = true,
                        focused = state.focusIndex == RootScriptDialogState.ConfirmBackup.CONTINUE_FOCUS
                    )
                }
            }

            if (state is RootScriptDialogState.Finished) {
                HorizontalDivider(color = theme.hairlineLow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
                ) {
                    ActionButton(
                        label = stringResource(R.string.settings_systemize_dialog_close_button),
                        onClick = onDismiss,
                        primary = !state.offersReboot,
                        focused = state.focusIndex != REBOOT_FOCUS_INDEX || !state.offersReboot
                    )
                    if (state.offersReboot) {
                        ActionButton(
                            label = stringResource(R.string.settings_root_script_dialog_reboot_button),
                            onClick = onReboot,
                            primary = true,
                            focused = state.focusIndex == REBOOT_FOCUS_INDEX
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmBackupContent(backupPath: String) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.settings_root_script_backup_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary
        )
        Text(
            text = stringResource(R.string.settings_root_script_backup_export_note, backupPath),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary
        )
        Text(
            text = stringResource(R.string.settings_root_script_backup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary
        )
    }
}

@Composable
private fun RunningContent() {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(color = theme.focusAccent)
        Text(
            text = stringResource(R.string.settings_root_script_dialog_running),
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
    }
}

@Composable
private fun RanContent(result: RootScriptResult.Ran) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(
                when (result.outcome) {
                    RootScriptOutcome.OK -> R.string.settings_root_script_dialog_ok_title
                    RootScriptOutcome.PARTIAL -> R.string.settings_root_script_dialog_partial_title
                    RootScriptOutcome.FAILED -> R.string.settings_root_script_dialog_failed_title
                }
            ),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary
        )
        if (result.outcome == RootScriptOutcome.OK && result.script.needsReboot) {
            Text(
                text = stringResource(
                    when (result.script) {
                        RootScript.SYSTEMIZE -> R.string.settings_root_script_dialog_reboot_hint
                        RootScript.UNSYSTEMIZE -> R.string.settings_root_script_dialog_unsystemize_reboot_hint
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary
            )
        }
        if (result.output.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.spacingXs))
            Text(
                text = stringResource(R.string.settings_root_script_dialog_output_heading),
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold
            )
            result.output.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textDim
                )
            }
        }
    }
}

@Composable
private fun WrittenContent(result: RootScriptResult.Written) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.settings_systemize_dialog_success_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary
        )
        Text(
            text = stringResource(R.string.settings_systemize_dialog_detected_device, result.vendor.deviceLabel),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
        Text(
            text = result.scriptPath,
            style = MaterialTheme.typography.bodySmall,
            color = theme.focusAccent
        )

        Spacer(Modifier.height(Dimens.spacingXs))
        Text(
            text = stringResource(R.string.settings_systemize_dialog_next_steps_heading),
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary,
            fontWeight = FontWeight.Bold
        )
        result.vendor.steps.forEachIndexed { index, step ->
            Text(
                text = stringResource(R.string.settings_systemize_dialog_step_format, index + 1, step),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary
            )
        }

        if (result.script == RootScript.SYSTEMIZE) {
            Spacer(Modifier.height(Dimens.spacingXs))
            Text(
                text = stringResource(R.string.settings_systemize_dialog_success_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
    }
}

@Composable
private fun ErrorContent(result: RootScriptResult.Error) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(
                if (result.duringRun) R.string.settings_root_script_dialog_failed_title
                else R.string.settings_systemize_dialog_error_title
            ),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary
        )
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        if (!result.duringRun) {
            Text(
                text = stringResource(R.string.settings_systemize_dialog_error_hint),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
    }
}
