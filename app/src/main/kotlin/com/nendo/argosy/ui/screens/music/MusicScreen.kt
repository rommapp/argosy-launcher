package com.nendo.argosy.ui.screens.music

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
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.ui.dualscreen.PresentOnCompanionLive
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.dualscreen.presentationTakesSlotsState
import com.nendo.argosy.ui.dualscreen.selectSwapModeState
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.LetterJumpOverlay
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.music.MusicPlaylistPermissionModal
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerDialogs
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.EnumValueControl
import com.nendo.argosy.ui.screens.home.homeColumnsSlotCount
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

private const val MUSIC_SLOT_OWNER = "music"

@Composable
fun MusicScreen(
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenMusicSettings: () -> Unit,
    onOpenListening: () -> Unit,
    onOpenRommSettings: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val quickNavigation by viewModel.quickNavigationEnabled.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack, onOpenMusicSettings) {
        MusicInputHandler(viewModel = viewModel, onExit = onBack, onOpenSettings = onOpenMusicSettings)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MUSIC)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MUSIC)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, onOpenNowPlaying) {
        viewModel.playbackStarted.collect { onOpenNowPlaying() }
    }

    val configuration = LocalConfiguration.current
    val slots = homeColumnsSlotCount(configuration.screenWidthDp.toFloat(), configuration.screenHeightDp.toFloat())
    LaunchedEffect(slots) { viewModel.setSlotCount(slots) }

    val split = presentationTakesSlotsState()
    val slotOwner = remember(viewModel) { SlotOwner.of(MUSIC_SLOT_OWNER, viewModel) }
    if (split) {
        uiState.showcaseSlot()?.let { slot -> PresentOnCompanionLive(owner = slotOwner, slot = slot) }
    }
    val selectSwaps = selectSwapModeState() == SelectSwapMode.TAP

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (split) Modifier.surfaceBackdrop(BackdropRole.CONTENT) else Modifier)
    ) {
        val contents = uiState.contents
        if (contents != null) {
            MusicContentsView(
                contents = contents,
                playingTrackId = uiState.playing.trackId,
                onBack = { inputHandler.onBack() },
                onRetry = viewModel::retryContents,
                onSelectAlbum = viewModel::selectAlbum,
                onPlayTrack = viewModel::playTrackAt,
                onTrackOptions = viewModel::openTrackOptionsAt,
                onPlayColumn = { viewModel.playContentsColumn() },
                onMoveTrack = { delta -> viewModel.playlistActions.moveFocused(delta) },
                onEndMove = { viewModel.playlistActions.endMove() },
                split = split
            )
        } else {
            MusicColumnsPane(
                uiState = uiState,
                viewModel = viewModel,
                split = split,
                onOpenMusicSettings = onOpenMusicSettings,
                onOpenListening = onOpenListening
            )
        }
    }

    FooterHints(
        hints = musicFooterHints(
            uiState,
            jumpButton = if (quickNavigation) InputButton.RIGHT_STICK_VERTICAL else InputButton.LB_RB,
            selectSwaps = selectSwaps
        ),
        onHintClick = { button ->
            when (button) {
                InputButton.DPAD_VERTICAL -> inputHandler.onDown()
                InputButton.A -> inputHandler.onConfirm()
                InputButton.RIGHT_STICK_VERTICAL, InputButton.LB_RB -> inputHandler.onRightStickDown()
                InputButton.LT_RT -> inputHandler.onNextTrigger()
                InputButton.Y -> inputHandler.onSecondaryAction()
                InputButton.X -> inputHandler.onContextMenu()
                InputButton.SELECT -> if (selectSwaps) {
                    DualScreenManagerHolder.instance?.swapRoles()
                } else {
                    inputHandler.onSelect()
                }
                else -> Unit
            }
        }
    )

    LetterJumpOverlay(
        letter = uiState.jumpLabel.orEmpty(),
        visible = uiState.showsJumpLabel && uiState.contents == null
    )

    MusicOptionsModalHost(
        options = uiState.options,
        onMove = viewModel.actions::moveOptionsFocus,
        onFocus = viewModel.actions::focusOption,
        onConfirm = { viewModel.actions.confirmOption(onOpenNowPlaying, onOpenListening) },
        onBack = viewModel.actions::backOptions,
        onDismiss = viewModel.actions::dismissOptions
    )

    val picker = viewModel.playlistActions.picker
    MusicPlaylistPickerDialogs(
        picker = uiState.options?.picker,
        onSubmitName = picker::submitName,
        onCancelName = picker::cancelNaming,
        onConfirmConflict = picker::confirmConflict,
        onDismissConflict = picker::dismissConflict
    )

    MusicPlaylistDialogHost(
        dialog = uiState.playlistDialog,
        onSubmitRename = viewModel.playlistActions::submitRename,
        onConfirmDelete = viewModel.playlistActions::confirmDelete,
        onDismiss = viewModel.playlistActions::dismissDialog
    )

    MusicPlaylistPermissionModal(
        visible = uiState.showsPlaylistPermission,
        onOpenSettings = {
            viewModel.playlistActions.dismissPermission()
            onOpenRommSettings()
        },
        onDismiss = viewModel.playlistActions::dismissPermission
    )

    MusicFilterModalHost(
        filter = uiState.filterPicker,
        selected = uiState.filter,
        onMove = viewModel.actions::moveFilterFocus,
        onFocus = viewModel.actions::focusFilter,
        onConfirm = viewModel::confirmFilter,
        onDismiss = viewModel.actions::dismissFilter
    )
}

@Composable
private fun MusicColumnsPane(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    split: Boolean,
    onOpenMusicSettings: () -> Unit,
    onOpenListening: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MusicHeader(
            uiState = uiState,
            onCycleGrouping = viewModel::cycleGrouping,
            onOpenListening = onOpenListening
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                uiState.hasNoSources -> MusicMessageState(
                    icon = Icons.Outlined.LibraryMusic,
                    title = stringResource(R.string.music_library_empty_title),
                    message = stringResource(R.string.music_library_empty_message),
                    actionLabel = stringResource(R.string.music_library_empty_action),
                    onAction = onOpenMusicSettings
                )
                uiState.loadFailed -> MusicMessageState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.music_library_error_title),
                    message = stringResource(R.string.music_library_error_message),
                    actionLabel = stringResource(R.string.music_library_error_retry),
                    onAction = viewModel::retry
                )
                uiState.columns.isEmpty() && (uiState.isLoading || uiState.sources == null) -> MusicLoadingState()
                uiState.columns.isEmpty() -> MusicMessageState(
                    icon = Icons.Outlined.MusicOff,
                    title = stringResource(R.string.music_library_no_columns_title),
                    message = stringResource(R.string.music_library_no_columns_message)
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
                ) {
                    MusicColumnsStrip(
                        state = uiState,
                        onColumnTap = viewModel::tapColumn,
                        onColumnLongPress = { fromFocus ->
                            if (fromFocus == 0) viewModel.openFocusedColumnOptions() else viewModel.tapColumn(fromFocus)
                        },
                        onFlip = { delta -> viewModel.moveStack(delta) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(if (split) 1f else ComponentDefaults.HomeColumns.stripWeightRatio)
                            .padding(
                                top = Dimens.spacingSm,
                                bottom = if (split) withNavBarClearance(Dimens.spacingLg) else 0.dp
                            )
                    )
                    if (!split) {
                        MusicDetails(
                            uiState = uiState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(ComponentDefaults.HomeColumns.bandWeightRatio)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicHeader(uiState: MusicUiState, onCycleGrouping: (Int) -> Unit, onOpenListening: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, top = Dimens.spacingLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.music_library_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (!uiState.hasNoSources && uiState.sources != null) {
            val chipShape = RoundedCornerShape(Dimens.radiusPill)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier
                    .padding(end = Dimens.spacingLg)
                    .clip(chipShape)
                    .background(theme.surfaceRaised)
                    .clickableNoFocus(onClick = onOpenListening)
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            ) {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = null,
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconSm)
                )
                Text(
                    text = stringResource(R.string.music_stats_entry_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.textPrimary
                )
            }
            Text(
                text = stringResource(R.string.music_library_group_by_label),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                modifier = Modifier.padding(end = Dimens.spacingSm)
            )
            EnumValueControl(
                value = stringResource(uiState.grouping.labelRes),
                focused = false,
                onPrev = { onCycleGrouping(-1) },
                onNext = { onCycleGrouping(1) },
                onOpen = { onCycleGrouping(1) },
                valueColor = theme.textPrimary
            )
        }
    }
}

@Composable
private fun MusicDetails(uiState: MusicUiState, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val column = uiState.focusedColumn ?: return
    Column(
        modifier = modifier.padding(
            start = Dimens.spacingLg,
            end = Dimens.spacingLg,
            bottom = withNavBarClearance(Dimens.footerHeight)
        )
    ) {
        Text(
            text = musicColumnTitle(column),
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary,
            maxLines = ComponentDefaults.MusicColumns.detailsTitleMaxLines,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = musicColumnFacts(column),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimens.spacingXs)
        )
        val frontAlbum = uiState.focusedStack?.front
        val frontTitle = frontAlbum?.title ?: column.sleeveTitles.firstOrNull()
            ?.takeIf { column.kind != MusicColumnKind.ALBUM }
        if (frontTitle != null) {
            val year = frontAlbum?.year?.toString()
            Text(
                text = listOfNotNull(frontTitle, year).joinToString(stringResource(R.string.music_library_separator)),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
        if (uiState.page?.offline == true) {
            Text(
                text = stringResource(R.string.music_library_offline),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMute,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}

@Composable
private fun musicFooterHints(
    uiState: MusicUiState,
    jumpButton: InputButton,
    selectSwaps: Boolean
): List<Pair<InputButton, String>> {
    val groupBy = stringResource(R.string.music_library_footer_group_by)
    val flip = stringResource(R.string.music_library_footer_flip)
    val jump = stringResource(R.string.music_library_footer_jump)
    val play = stringResource(R.string.music_library_footer_play)
    val filter = stringResource(R.string.music_library_footer_filter)
    val options = stringResource(R.string.music_library_footer_options)
    val playAlbum = stringResource(R.string.music_library_footer_play_album)
    val playAll = stringResource(R.string.music_library_footer_play_all)
    val playColumn = stringResource(R.string.music_library_footer_play_column)
    val trackOptions = stringResource(R.string.music_library_footer_track_options)
    val swapScreens = stringResource(R.string.music_library_footer_swap_screens)
    val contents = uiState.contents
    return buildList {
        when {
            contents != null && contents.isMoving -> Unit
            contents != null -> {
                if (contents.canPlayColumn) add(InputButton.X to playColumn)
                if (contents.tracks.isNotEmpty()) {
                    add(InputButton.Y to if (contents.isFlat) playAll else playAlbum)
                    add(InputButton.SELECT to if (selectSwaps) swapScreens else trackOptions)
                }
            }
            uiState.hasNoSources || uiState.sources == null -> Unit
            else -> {
                if (uiState.canFlip) add(InputButton.DPAD_VERTICAL to flip)
                if (uiState.columns.size > uiState.slotCount) add(jumpButton to jump)
                add(InputButton.LT_RT to groupBy)
                if (uiState.showsColumns) add(InputButton.Y to play)
                add(InputButton.X to filter)
                if (uiState.showsColumns) add(InputButton.SELECT to if (selectSwaps) swapScreens else options)
            }
        }
    }
}
