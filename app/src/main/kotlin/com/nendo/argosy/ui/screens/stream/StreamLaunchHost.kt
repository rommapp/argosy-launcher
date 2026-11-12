package com.nendo.argosy.ui.screens.stream

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.EnumValueControl
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun StreamLaunchHost(controller: StreamLaunchController) {
    val state by controller.state.collectAsState()
    val handler = remember(controller) { controller.inputHandler() }
    val current = state
    ModalInputEffect(active = current != null, handler = handler)
    ModalScaffold(
        visible = current != null,
        onDismiss = { if (current?.step != StreamLaunchStep.Starting) controller.dismiss() },
        maxWidth = Dimens.modalWidth
    ) {
        if (current != null) StreamLaunchContent(current, controller)
    }
}

@Composable
private fun StreamLaunchContent(state: StreamLaunchUiState, controller: StreamLaunchController) {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.padding(Dimens.spacingLg)) {
        Text(
            text = stringResource(R.string.stream_launch_title, state.containerLabel),
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
        Text(
            text = state.title,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        Spacer(Modifier.height(Dimens.spacingMd))
        when (val step = state.step) {
            StreamLaunchStep.Loading -> Progress(stringResource(R.string.stream_launch_loading))
            StreamLaunchStep.Starting -> Progress(stringResource(R.string.stream_launch_starting))
            StreamLaunchStep.Pick -> PickBody(state, controller)
            is StreamLaunchStep.Busy -> BodyText(busyMessage(step))
            is StreamLaunchStep.CardDecision -> CardBody(step)
            is StreamLaunchStep.Notice -> {
                BodyText(stringResource(step.message))
                step.detail?.let { BodyText(it) }
            }
        }
        val actions = state.rows.filter { it != StreamLaunchRow.STATE && it != StreamLaunchRow.SAVE }
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.spacingLg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
            ) {
                actions.forEach { row ->
                    ModalActionButton(
                        label = stringResource(row.labelRes()),
                        tint = theme.focusAccent,
                        restLabelColor = theme.textPrimary,
                        focused = state.focusedRow == row,
                        onClick = {
                            controller.focusRow(row)
                            controller.activate(row)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickBody(state: StreamLaunchUiState, controller: StreamLaunchController) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        if (state.showsStatePicker) {
            PickerRow(
                label = stringResource(R.string.stream_launch_state_label),
                value = state.states.getOrNull(state.stateIndex)?.let { optionLabel(it, freshStart = true) }.orEmpty(),
                focused = state.focusedRow == StreamLaunchRow.STATE,
                onFocus = { controller.focusRow(StreamLaunchRow.STATE) },
                onCycle = { controller.cycle(StreamLaunchRow.STATE, it) }
            )
        }
        if (state.showsSavePicker) {
            PickerRow(
                label = stringResource(R.string.stream_launch_save_label),
                value = state.saves.getOrNull(state.saveIndex)?.let { optionLabel(it, freshStart = false) }.orEmpty(),
                focused = state.focusedRow == StreamLaunchRow.SAVE,
                onFocus = { controller.focusRow(StreamLaunchRow.SAVE) },
                onCycle = { controller.cycle(StreamLaunchRow.SAVE, it) }
            )
        }
        if (!state.showsStatePicker && !state.showsSavePicker) {
            BodyText(stringResource(R.string.stream_launch_ready))
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    focused: Boolean,
    onFocus: () -> Unit,
    onCycle: (Int) -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().clickableNoFocus(onClick = onFocus),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) theme.focusAccent else theme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        EnumValueControl(
            value = value,
            focused = focused,
            onPrev = {
                onFocus()
                onCycle(-1)
            },
            onNext = {
                onFocus()
                onCycle(1)
            },
            onOpen = onFocus
        )
    }
}

@Composable
private fun CardBody(step: StreamLaunchStep.CardDecision) {
    if (step.unreadable) {
        BodyText(stringResource(R.string.stream_launch_card_unreadable))
        step.reason?.let { BodyText(stringResource(R.string.stream_launch_card_reason, it)) }
        BodyText(stringResource(R.string.stream_launch_card_erase_warning))
        return
    }
    BodyText(pluralStringResource(R.plurals.stream_launch_card_found, step.fileCount, step.fileCount))
    if (step.gameCodes.isNotEmpty()) {
        BodyText(stringResource(R.string.stream_launch_card_games, step.gameCodes.joinToString()))
    }
}

@Composable
private fun busyMessage(step: StreamLaunchStep.Busy): String {
    val context = LocalContext.current
    val since = step.sinceMillis
    return when {
        step.draining -> stringResource(R.string.stream_launch_busy_draining)
        step.romName != null && since != null -> stringResource(
            R.string.stream_launch_busy_playing_since,
            step.romName,
            DateUtils.formatDateTime(context, since, DateUtils.FORMAT_SHOW_TIME)
        )
        else -> stringResource(R.string.stream_launch_busy_fallback)
    }
}

@Composable
private fun optionLabel(option: StreamOptionUi, freshStart: Boolean): String {
    if (option.id == null && freshStart) return stringResource(R.string.stream_launch_state_fresh)
    val context = LocalContext.current
    val time = option.timestampMillis?.let {
        DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
    }
    val owner = option.owner
    return when {
        time != null && owner != null -> stringResource(R.string.stream_launch_option_by_owner, time, owner)
        time != null -> time
        else -> option.title
    }
}

@Composable
private fun Progress(label: String) {
    val theme = LocalArgosyTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconSm),
            strokeWidth = Dimens.borderMedium,
            color = theme.focusAccent
        )
        Spacer(Modifier.width(Dimens.spacingSm))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = theme.textDim)
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(bottom = Dimens.spacingXs)
    )
}

private fun StreamLaunchRow.labelRes(): Int = when (this) {
    StreamLaunchRow.STATE -> R.string.stream_launch_state_label
    StreamLaunchRow.SAVE -> R.string.stream_launch_save_label
    StreamLaunchRow.START -> R.string.stream_launch_action_start
    StreamLaunchRow.CANCEL -> R.string.stream_launch_action_cancel
    StreamLaunchRow.JOIN -> R.string.stream_launch_action_join
    StreamLaunchRow.BACK -> R.string.stream_launch_action_back
    StreamLaunchRow.IMPORT_CARD -> R.string.stream_launch_action_import_card
    StreamLaunchRow.FRESH_CARD -> R.string.stream_launch_action_fresh_card
    StreamLaunchRow.DISMISS -> R.string.stream_launch_action_dismiss
}
