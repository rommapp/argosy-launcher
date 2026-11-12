package com.nendo.argosy.ui.screens.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * What the stream window draws over the page: the launch wait, the end of the session, and the
 * hold-Back controls.
 */
@Composable
fun StreamPlayerOverlay(
    state: StreamPlayerUiState,
    onRow: (StreamControlRow) -> Unit,
    onCloseControls: () -> Unit,
    onLeave: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    when {
        state.stage == StreamPlayerStage.LAUNCHING -> Centered {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconMd),
                    strokeWidth = Dimens.borderMedium,
                    color = theme.focusAccent
                )
                Spacer(Modifier.width(Dimens.spacingMd))
                Column {
                    Text(
                        text = stringResource(R.string.stream_player_starting, state.containerLabel),
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.textPrimary
                    )
                    if (state.preparing) {
                        Text(
                            text = stringResource(R.string.stream_player_preparing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textDim
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.spacingLg))
            ModalActionButton(
                label = stringResource(R.string.stream_player_cancel),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = true,
                onClick = onLeave
            )
        }
        state.stage == StreamPlayerStage.ENDED -> Centered {
            Text(
                text = if (state.launchFailed) {
                    stringResource(R.string.stream_player_launch_failed)
                } else {
                    stringResource(R.string.stream_player_ended)
                },
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            Spacer(Modifier.height(Dimens.spacingLg))
            ModalActionButton(
                label = stringResource(R.string.stream_player_close),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = true,
                onClick = onLeave
            )
        }
        state.controlsOpen -> ControlsPanel(state, onRow, onCloseControls)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(LocalArgosyTheme.current.surfaceBase),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun ControlsPanel(
    state: StreamPlayerUiState,
    onRow: (StreamControlRow) -> Unit,
    onClose: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickableNoFocus(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidth)
                .background(theme.surfaceElevated, RoundedCornerShape(Dimens.radiusLg))
                .clickableNoFocus {}
                .padding(Dimens.spacingLg)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            Text(
                text = stringResource(R.string.stream_controls_subtitle, state.containerLabel),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
            Spacer(Modifier.height(Dimens.spacingMd))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                items(state.rows, key = { it.name }) { row ->
                    ModalActionButton(
                        label = stringResource(row.labelRes(state.muted)),
                        tint = if (row == StreamControlRow.SAVE_AND_EXIT) theme.destructive else theme.focusAccent,
                        restLabelColor = theme.textPrimary,
                        focused = state.focusedRow == row,
                        onClick = { onRow(row) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            state.notice?.let {
                Spacer(Modifier.height(Dimens.spacingSm))
                Text(text = stringResource(it), style = MaterialTheme.typography.bodySmall, color = theme.textDim)
            }
        }
    }
}

private fun StreamControlRow.labelRes(muted: Boolean): Int = when (this) {
    StreamControlRow.RESUME -> R.string.stream_controls_resume
    StreamControlRow.SAVE_STATE -> R.string.stream_controls_save_state
    StreamControlRow.LOAD_STATE -> R.string.stream_controls_load_state
    StreamControlRow.NEXT_DISC -> R.string.stream_controls_next_disc
    StreamControlRow.MUTE -> if (muted) R.string.stream_controls_unmute else R.string.stream_controls_mute
    StreamControlRow.SAVE_AND_EXIT -> R.string.stream_controls_save_and_exit
    StreamControlRow.LEAVE -> R.string.stream_controls_leave
}

private const val SCRIM_ALPHA = 0.55f
