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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.SystemizeWriteResult
import kotlinx.coroutines.launch

@Composable
fun SystemizeResultDialog(result: SystemizeWriteResult, onDismiss: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { (Dimens.menuRowHeight * 3).toPx() }
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

            override fun onConfirm(): InputResult {
                currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
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
        onDismiss = onDismiss,
        maxWidth = Dimens.modalWidthLg
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                when (result) {
                    is SystemizeWriteResult.Success -> SuccessContent(result)
                    is SystemizeWriteResult.Error -> ErrorContent(result)
                }
            }

            HorizontalDivider(color = theme.hairlineLow)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                horizontalArrangement = Arrangement.End
            ) {
                ActionButton(
                    label = stringResource(R.string.settings_systemize_dialog_close_button),
                    onClick = onDismiss,
                    primary = true,
                    focused = true
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(result: SystemizeWriteResult.Success) {
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

        Spacer(Modifier.height(Dimens.spacingXs))
        Text(
            text = stringResource(R.string.settings_systemize_dialog_success_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}

@Composable
private fun ErrorContent(result: SystemizeWriteResult.Error) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.settings_systemize_dialog_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary
        )
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        Text(
            text = stringResource(R.string.settings_systemize_dialog_error_hint),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}
