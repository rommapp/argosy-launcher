package com.nendo.argosy.ui.screens.savetimeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.GameArtBackdrop
import com.nendo.argosy.ui.common.rememberResolvedBackgroundPath
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotEntryOverlays
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotOverlays
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val DEVICE_PILL_FILL_ALPHA = 0.18f

@Composable
fun SaveTimelineScreen(
    onBack: () -> Unit,
    viewModel: SaveTimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snapshot by viewModel.snapshotView.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack) { SaveTimelineInputHandler(viewModel, onBack) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SAVE_TIMELINE)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SAVE_TIMELINE)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val coverPath = rememberResolvedCoverPath(state.gameId, null)
    val overlayOpen = snapshot?.hasOverlay == true
    val graphActions = remember(viewModel) {
        SaveTimelineGraphActions(
            onTapNode = viewModel::tapNode,
            onLongPressNode = viewModel::longPressNode,
            onTapLane = viewModel::tapLane,
            onLongPressLane = viewModel::longPressLane,
            onTapFloater = viewModel::openFocusedDetail
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GameArtBackdrop(rememberResolvedBackgroundPath(state.gameId, null) ?: coverPath)
        Column(modifier = Modifier.fillMaxSize()) {
            SaveTimelineTopBar(
                header = state.header,
                deviceChannel = state.deviceChannel,
                coverPath = coverPath
            )
            HorizontalDivider(color = LocalArgosyTheme.current.hairlineLow)
            if (snapshot?.isBusy == true) {
                ArgosyProgressBar(progress = null, style = ProgressBarStyle.Working)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.loadFailed -> TimelineMessage(stringResource(R.string.save_channels_timeline_load_failed))
                    state.isLoading -> SaveTimelineSkeleton()
                    state.lanes.isEmpty() -> TimelineMessage(stringResource(R.string.save_channels_timeline_no_channels))
                    else -> SaveTimelineGraph(
                        state = state,
                        overlayOpen = overlayOpen,
                        coverPath = coverPath,
                        actions = graphActions
                    )
                }
            }
            FooterSpacer()
        }
        snapshot?.let { view ->
            SnapshotOverlays(state = view, coverPath = coverPath, actions = viewModel.viewActions)
            SnapshotEntryOverlays(state = view, actions = viewModel.viewActions)
        }
    }

    val jumpLabel = stringResource(R.string.save_channels_timeline_footer_jump)
    val channelLabel = stringResource(R.string.save_channels_timeline_footer_channel)
    val hints = buildList {
        if (overlayOpen) return@buildList
        if (state.focusedNode != null) add(InputButton.LB_RB to jumpLabel)
        if (state.focusedLane != null) add(InputButton.X to channelLabel)
    }
    FooterHints(
        hints = hints,
        onHintClick = { button ->
            if (button == InputButton.X) inputHandler.onContextMenu()
        }
    )
}

@Composable
private fun SaveTimelineTopBar(
    header: SaveTimelineHeaderUi?,
    deviceChannel: String?,
    coverPath: String?
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(saveTimelinePanelColor())
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        HeaderCover(coverPath)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = header?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            header?.platformName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (deviceChannel != null) {
            Text(
                text = stringResource(R.string.save_channels_timeline_header_device, deviceChannel),
                style = MaterialTheme.typography.labelMedium,
                color = theme.focusAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(theme.focusAccent.copy(alpha = DEVICE_PILL_FILL_ALPHA))
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
            )
        }
    }
}

@Composable
private fun HeaderCover(coverPath: String?) {
    val theme = LocalArgosyTheme.current
    val model = rememberFileImageModel(coverPath)
    Box(
        modifier = Modifier
            .size(Dimens.saveTimelineHeaderCover)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(theme.surfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = theme.textMute,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
    }
}

@Composable
private fun TimelineMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg)
    )
}
